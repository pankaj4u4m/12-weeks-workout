#!/usr/bin/env python3
"""Deterministically rotates warm-up/cool-down/round exercise *identity* in
every programs/*.json slot, pulling from programs/_pools/exercise-catalog.json.
Never touches reps/seconds values, section titles, or slot counts — only
name/raw/wgerId/exerciseDbId/freeExerciseDbId/externalMediaUrl change per
row. Progress ticks are keyed by position (Workout.keyFor), not by exercise
identity, so this is always safe to re-run.

Deterministic, not random: each slot's pick is a stable hash of its own
coordinates (program, week, workout, section, item), so re-running produces
identical output given the same catalog and every workout in a week resolves
separately. Excludes the same slot's pick from the immediately preceding week,
and every name already used earlier in the same section, where a second
candidate exists. Unilateral moves stay paired: an ' L' row and its ' R'
sibling in one section always land on the two sides of a single exercise, and
bilateral rows never pick a lone side.

Usage: python3 scripts/rotate_program_content.py
"""
import glob
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROGRAMS_DIR = os.path.join(ROOT, "programs")
CATALOG_PATH = os.path.join(ROOT, "programs", "_pools", "exercise-catalog.json")
MIRRORS = [
    os.path.join(ROOT, "app", "src", "main", "assets", "programs"),
    os.path.join(ROOT, "app", "src", "wasmJsMain", "resources", "programs"),
]

def load_catalog():
    with open(CATALOG_PATH) as f:
        return json.load(f)["exercises"]

def section_role(title):
    if title == "Warm up":
        return "WARMUP"
    if title == "Cool Down":
        return "COOLDOWN"
    return "ROUND"

def eligible_candidates(catalog, kind, role, equipment, focus_areas):
    result = []
    for entry in catalog:
        if entry["kind"] != kind:
            continue
        if role not in entry["roles"]:
            continue
        if not set(equipment) & set(entry["equipment"]):
            continue
        if role == "ROUND" and not (set(focus_areas) & set(entry["focus"])):
            continue
        result.append(entry)
    return result

def pick_candidate(candidates, program_id, week, workout_index, section_index, item_index,
                   exclude_names):
    """Deterministic pick via a stable hash of the slot's own coordinates.

    The seed spans the full slot identity (week, workout, section, item) — the
    same tuple the app's Workout.keyFor uses — so two workouts in one week do
    not collapse onto the same exercise.
    """
    pool = [c for c in candidates if c["name"] not in exclude_names] or candidates
    pool = sorted(pool, key=lambda c: c["name"])
    seed = f"{program_id}:w{week}-o{workout_index}-s{section_index}-i{item_index}"
    digest = hashlib.sha256(seed.encode()).hexdigest()
    index = int(digest, 16) % len(pool)
    return pool[index]

def side_suffix(name):
    """Returns ' L'/' R' for a unilateral exercise name, else None."""
    if name.endswith(" L"):
        return " L"
    if name.endswith(" R"):
        return " R"
    return None

def opposite_suffix(suffix):
    return " R" if suffix == " L" else " L"

def side_filtered(candidates, suffix):
    """Restricts a pool to one side of the body (or to bilateral moves only).

    A unilateral row may only be replaced by the same side of another unilateral
    move, and a bilateral row may never be replaced by a lone L/R move — either
    would leave a section holding half a pair.
    """
    if suffix is None:
        return [c for c in candidates if side_suffix(c["name"]) is None]
    return [c for c in candidates if side_suffix(c["name"]) == suffix]

def section_pairs(names):
    """Maps row index -> sibling row index for L/R pairs inside one section.

    Pairs are matched by base name (the name minus its ' L'/' R' suffix) and are
    always section-scoped, which is how the source programs are laid out.
    """
    sides = {}
    for index, name in enumerate(names):
        suffix = side_suffix(name)
        if suffix is not None:
            sides.setdefault((name[:-2], suffix), []).append(index)
    partner = {}
    for (base, suffix), indices in sides.items():
        if suffix != " L":
            continue
        for left, right in zip(indices, sides.get((base, " R"), [])):
            partner[left] = right
            partner[right] = left
    return partner

def raw_for(name, reps, seconds):
    if seconds is not None:
        return f"{seconds}s {name}"
    return f"{reps} {name}"

def apply_pick(ex, picked):
    ex["name"] = picked["name"]
    ex["raw"] = raw_for(picked["name"], ex.get("reps"), ex.get("seconds"))
    ex["wgerId"] = picked["wgerId"]
    ex["exerciseDbId"] = picked["exerciseDbId"]
    ex["freeExerciseDbId"] = picked["freeExerciseDbId"]
    ex["externalMediaUrl"] = picked["externalMediaUrl"]

def rotate_program(program, catalog):
    equipment = program.get("equipment", [])
    focus_areas = program.get("focusAreas", [])
    program_id = program["id"]
    by_name = {entry["name"]: entry for entry in catalog}
    previous_pick = {}
    for week in program["weeks"]:
        current_pick = {}
        for workout in week["workouts"]:
            for s_idx, section in enumerate(workout["sections"]):
                role = section_role(section["title"])
                original_names = [ex["name"] for ex in section["exercises"]]
                partner_of = section_pairs(original_names)
                used_in_section = set()
                resolved = {}  # row index -> catalog entry already fixed by its sibling

                def pick_slot(idx, suffix):
                    """Picks for one row, or None when its pool holds no alternative."""
                    row = section["exercises"][idx]
                    kind = "SECONDS" if row.get("seconds") is not None else "REPS"
                    pool = side_filtered(
                        eligible_candidates(catalog, kind, role, equipment, focus_areas),
                        suffix,
                    )
                    if len(pool) < 2:
                        return None  # not enough variety for this slot type
                    exclude = set(used_in_section)
                    key = (workout["index"], s_idx, idx)
                    if key in previous_pick:
                        exclude.add(previous_pick[key])
                    return pick_candidate(
                        pool, program_id, week["number"], workout["index"], s_idx, idx, exclude
                    )

                for i_idx, ex in enumerate(section["exercises"]):
                    if ex["name"] == "Pause":
                        continue
                    slot_key = (workout["index"], s_idx, i_idx)
                    if i_idx in resolved:
                        picked = resolved.pop(i_idx)
                        if picked is not None:
                            apply_pick(ex, picked)
                            used_in_section.add(picked["name"])
                            current_pick[slot_key] = picked["name"]
                        continue
                    suffix = side_suffix(original_names[i_idx])
                    partner = partner_of.get(i_idx)
                    # A pair always resolves from its L row's own coordinates, so the
                    # result never depends on which side happens to be listed first.
                    lead_idx = i_idx if (partner is None or suffix == " L") else partner
                    lead_suffix = suffix if lead_idx == i_idx else " L"
                    lead_pick = pick_slot(lead_idx, lead_suffix)
                    if lead_pick is None:
                        # Leave the row — and its sibling, so the pair survives — alone.
                        if partner is not None:
                            resolved[partner] = None
                        continue
                    sibling_pick = None
                    if partner is not None:
                        sibling_pick = by_name.get(
                            lead_pick["name"][:-2] + opposite_suffix(lead_suffix)
                        )
                        if sibling_pick is None:
                            # Defensive: the catalog lacks the other side, so both rows
                            # fall back to independent same-side picks.
                            partner = None
                            if lead_idx != i_idx:
                                lead_idx = i_idx
                                lead_pick = pick_slot(i_idx, suffix)
                                if lead_pick is None:
                                    continue
                    if lead_idx == i_idx:
                        picked, other = lead_pick, sibling_pick
                    else:
                        picked, other = sibling_pick, lead_pick
                    apply_pick(ex, picked)
                    used_in_section.add(picked["name"])
                    current_pick[slot_key] = picked["name"]
                    if partner is not None:
                        resolved[partner] = other
        previous_pick = current_pick
    return program

def program_files():
    return sorted(
        f for f in glob.glob(os.path.join(PROGRAMS_DIR, "*.json"))
        if os.path.basename(f) != "index.json"
    )

def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f)  # matches existing minified, no-trailing-newline convention

def main():
    catalog = load_catalog()
    for path in program_files():
        with open(path) as f:
            program = json.load(f)
        rotated = rotate_program(program, catalog)
        write_json(path, rotated)
        name = os.path.basename(path)
        for mirror_dir in MIRRORS:
            write_json(os.path.join(mirror_dir, name), rotated)
        print(f"Rotated {name}")

if __name__ == "__main__":
    main()
