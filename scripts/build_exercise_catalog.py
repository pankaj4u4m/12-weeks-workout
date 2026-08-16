#!/usr/bin/env python3
"""Seeds programs/_pools/exercise-catalog.json from the exercise data that
already exists across every programs/*.json (except index.json). One entry
per distinct exercise name, tagged with kind/roles/equipment/focus inferred
from how that name is actually used today, plus whichever media ids it
already carries. "Pause" (rest slots) is intentionally excluded.

Usage: python3 scripts/build_exercise_catalog.py
"""
import glob
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROGRAMS_DIR = os.path.join(ROOT, "programs")
CATALOG_PATH = os.path.join(ROOT, "programs", "_pools", "exercise-catalog.json")

ROLE_WARMUP = "WARMUP"
ROLE_COOLDOWN = "COOLDOWN"
ROLE_ROUND = "ROUND"

ID_FIELDS = ("wgerId", "exerciseDbId", "freeExerciseDbId", "externalMediaUrl")

def section_role(title):
    if title == "Warm up":
        return ROLE_WARMUP
    if title == "Cool Down":
        return ROLE_COOLDOWN
    return ROLE_ROUND

def program_files():
    return sorted(
        f for f in glob.glob(os.path.join(PROGRAMS_DIR, "*.json"))
        if os.path.basename(f) != "index.json"
    )

def load_programs():
    programs = []
    for path in program_files():
        with open(path) as f:
            programs.append(json.load(f))
    return programs

def seed_entries(programs):
    """Returns {name: working-entry} built from every program's actual
    exercise rows. Working entries use sets (not lists) for roles/equipment/
    focus and track a kinds_seen counter — finalize() converts these into
    the catalog's serializable shape."""
    entries = {}
    for program in programs:
        equipment = program.get("equipment", [])
        focus_areas = program.get("focusAreas", [])
        for week in program["weeks"]:
            for workout in week["workouts"]:
                for section in workout["sections"]:
                    role = section_role(section["title"])
                    for ex in section["exercises"]:
                        name = ex["name"]
                        if name == "Pause":
                            continue
                        kind = "SECONDS" if ex.get("seconds") is not None else "REPS"
                        entry = entries.setdefault(name, {
                            "name": name,
                            "kinds_seen": {},
                            "roles": set(),
                            "equipment": set(),
                            "focus": set(),
                            "wgerId": None,
                            "exerciseDbId": None,
                            "freeExerciseDbId": None,
                            "externalMediaUrl": None,
                        })
                        entry["kinds_seen"][kind] = entry["kinds_seen"].get(kind, 0) + 1
                        entry["roles"].add(role)
                        entry["equipment"].update(equipment)
                        if role == ROLE_ROUND:
                            entry["focus"].update(focus_areas)
                        for field in ID_FIELDS:
                            if not entry[field] and ex.get(field):
                                entry[field] = ex[field]
    return entries

def finalize(entries):
    """Converts the working dict into the catalog's serializable shape
    (sorted lists, majority kind). Returns (entries_list, conflicts) where
    conflicts is [(name, {kind: count})] for any name seen as both REPS and
    SECONDS across programs — needs a manual look (Step 5)."""
    result = []
    conflicts = []
    for name in sorted(entries):
        e = entries[name]
        kinds_seen = e["kinds_seen"]
        majority_kind = max(kinds_seen, key=kinds_seen.get)
        if len(kinds_seen) > 1:
            conflicts.append((name, dict(kinds_seen)))
        result.append({
            "name": name,
            "kind": majority_kind,
            "roles": sorted(e["roles"]),
            "equipment": sorted(e["equipment"]),
            "focus": sorted(e["focus"]),
            "wgerId": e["wgerId"],
            "exerciseDbId": e["exerciseDbId"],
            "freeExerciseDbId": e["freeExerciseDbId"],
            "externalMediaUrl": e["externalMediaUrl"],
        })
    return result, conflicts

def main():
    entries, conflicts = finalize(seed_entries(load_programs()))
    os.makedirs(os.path.dirname(CATALOG_PATH), exist_ok=True)
    with open(CATALOG_PATH, "w") as f:
        json.dump({"exercises": entries}, f, indent=2)
        f.write("\n")
    print(f"Wrote {len(entries)} exercises to {CATALOG_PATH}")
    if conflicts:
        print(f"\n{len(conflicts)} name(s) seen as both REPS and SECONDS — review manually:")
        for name, kinds in conflicts:
            print(f"  {name}: {kinds}")

if __name__ == "__main__":
    main()
