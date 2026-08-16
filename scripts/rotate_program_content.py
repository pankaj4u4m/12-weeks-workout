#!/usr/bin/env python3
"""Deterministically rotates warm-up/cool-down/round exercise *identity* in
every programs/*.json slot, pulling from programs/_pools/exercise-catalog.json.
Never touches reps/seconds values, section titles, or slot counts — only
name/raw/wgerId/exerciseDbId/freeExerciseDbId/externalMediaUrl change per
row. Progress ticks are keyed by position (Workout.keyFor), not by exercise
identity, so this is always safe to re-run.

Deterministic, not random: each slot's pick is a stable hash of its own
coordinates, so re-running produces identical output given the same catalog.
Excludes the same slot's pick from the immediately preceding week where a
second candidate exists, to avoid a repeat on back-to-back weeks.

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

def pick_candidate(candidates, program_id, week, section_index, item_index, exclude_names):
    """Deterministic pick via a stable hash of the slot's own coordinates."""
    pool = [c for c in candidates if c["name"] not in exclude_names] or candidates
    pool = sorted(pool, key=lambda c: c["name"])
    seed = f"{program_id}:w{week}-s{section_index}-i{item_index}"
    digest = hashlib.sha256(seed.encode()).hexdigest()
    index = int(digest, 16) % len(pool)
    return pool[index]

def raw_for(name, reps, seconds):
    if seconds is not None:
        return f"{seconds}s {name}"
    return f"{reps} {name}"

def rotate_program(program, catalog):
    equipment = program.get("equipment", [])
    focus_areas = program.get("focusAreas", [])
    program_id = program["id"]
    previous_pick = {}
    for week in program["weeks"]:
        current_pick = {}
        for workout in week["workouts"]:
            for s_idx, section in enumerate(workout["sections"]):
                role = section_role(section["title"])
                for i_idx, ex in enumerate(section["exercises"]):
                    if ex["name"] == "Pause":
                        continue
                    kind = "SECONDS" if ex.get("seconds") is not None else "REPS"
                    candidates = eligible_candidates(catalog, kind, role, equipment, focus_areas)
                    if len(candidates) < 2:
                        continue  # not enough variety yet for this slot type — leave it alone
                    slot_key = (workout["index"], s_idx, i_idx)
                    exclude = {previous_pick[slot_key]} if slot_key in previous_pick else set()
                    picked = pick_candidate(
                        candidates, program_id, week["number"], s_idx, i_idx, exclude
                    )
                    ex["name"] = picked["name"]
                    ex["raw"] = raw_for(picked["name"], ex.get("reps"), ex.get("seconds"))
                    ex["wgerId"] = picked["wgerId"]
                    ex["exerciseDbId"] = picked["exerciseDbId"]
                    ex["freeExerciseDbId"] = picked["freeExerciseDbId"]
                    ex["externalMediaUrl"] = picked["externalMediaUrl"]
                    current_pick[slot_key] = picked["name"]
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
