# Workout Variety, Faster Plan Switching, Media Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rotate warm-up/cool-down/round exercises week-to-week instead of repeating the same list for 12 weeks straight, fix the app's 7 real exercise-media gaps, let users preview a plan's full workout breakdown before switching to it, make plan switching instant (no full-screen "Preparing your plan" loader), and silently prefetch a program's exercise media in the background.

**Architecture:** Three independent, parallelizable tracks plus a final pass:
- **Content track** (Tasks 1–5): pure Python + one JVM test, touches `programs/`, `scripts/`, and one new `app/src/test` file. No app code.
- **Android track** (Tasks 6, 8, 10): touches `MainActivity.kt`, `ui/ProgramPickerScreen.kt`, one new `media/` file. Sequential within itself (all three touch `MainActivity.kt`).
- **Web track** (Tasks 7, 9, 11): touches `WebApp.kt`, one new `web/` file. Sequential within itself, mirrors the Android track task-for-task.
- **Task 12** (smoothness audit) runs last, after all three tracks land.

Spec: `docs/superpowers/specs/2026-08-16-workout-variety-and-speed-design.md`.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform, Android + wasmJs), Python 3 (stdlib only, no dependencies) for content generation, JUnit4 for JVM tests, `kotlin.test`/JUnit4 already used per existing test source sets.

## Global Constraints

- Rotation NEVER changes a slot's `reps`/`seconds` values — only `name`/`raw`/`wgerId`/`exerciseDbId`/`freeExerciseDbId`/`externalMediaUrl` change per row (spec §A step 5).
- Progress ticks are keyed by position (`Workout.keyFor(sectionIndex, itemIndex)` → `"programId:w{week}-o{index}-s{section}-i{item}"`), never by exercise identity — rotation must never change section/item ordering or counts (spec "Key invariant").
- Never a forced/fuzzy media match — an exercise with no real match stays unmatched (spec §B, existing `CREDITS.md` policy).
- The three program-JSON locations (`programs/`, `app/src/main/assets/programs/`, `app/src/wasmJsMain/resources/programs/`) must stay byte-identical after any content change. Existing files are single-line, `json.dumps()`-default-separator, no trailing newline — match that exactly for anything scripted.
- No brand-new exercise names beyond the existing 230 + the 7 gap-fills, no per-user rotation settings, no change to the reps/seconds progression curve, no Wi-Fi-only gating (spec "Explicitly out of scope").
- Section titles in the actual data are `"Warm up"` and `"Cool Down"` (capital D) — verified against `programs/beginner-full-body-home.json`. Round sections are `"Round 1"`..`"Round 6"`.

---

## Task 1: Seed the exercise catalog from existing program data

**Files:**
- Create: `scripts/build_exercise_catalog.py`
- Create: `scripts/test_build_exercise_catalog.py`
- Generated (by running the script in Step 6): `programs/_pools/exercise-catalog.json`

**Interfaces:**
- Produces: `programs/_pools/exercise-catalog.json` with shape `{"exercises": [{"name": str, "kind": "REPS"|"SECONDS", "roles": ["WARMUP"|"COOLDOWN"|"ROUND", ...], "equipment": ["HOME"|"GYM", ...], "focus": ["FULL_BODY"|"LEGS"|"ABS"|"CORE"|"UPPER_BODY"|"STRENGTH", ...], "wgerId": str|null, "exerciseDbId": str|null, "freeExerciseDbId": str|null, "externalMediaUrl": str|null}, ...]}`. Task 2 edits entries in this file directly; Task 3's rotation script consumes it.

- [ ] **Step 1: Write `scripts/build_exercise_catalog.py`**

```python
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
```

- [ ] **Step 2: Write `scripts/test_build_exercise_catalog.py`**

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_exercise_catalog import seed_entries, finalize, section_role  # noqa: E402


class SectionRoleTest(unittest.TestCase):
    def test_warm_up_is_warmup_role(self):
        self.assertEqual(section_role("Warm up"), "WARMUP")

    def test_cool_down_is_cooldown_role(self):
        self.assertEqual(section_role("Cool Down"), "COOLDOWN")

    def test_round_is_round_role(self):
        self.assertEqual(section_role("Round 3"), "ROUND")


class SeedEntriesTest(unittest.TestCase):
    def _program(self, **overrides):
        base = {
            "equipment": ["HOME"],
            "focusAreas": ["FULL_BODY"],
            "weeks": [{
                "number": 1,
                "workouts": [{
                    "index": 1,
                    "sections": [
                        {"title": "Warm up", "exercises": [
                            {"name": "Jumping Jacks", "reps": 30, "seconds": None,
                             "wgerId": "320", "exerciseDbId": None,
                             "freeExerciseDbId": None, "externalMediaUrl": None},
                        ]},
                        {"title": "Round 1", "exercises": [
                            {"name": "Squats", "reps": 10, "seconds": None,
                             "wgerId": "615", "exerciseDbId": None,
                             "freeExerciseDbId": None, "externalMediaUrl": None},
                            {"name": "Pause", "reps": None, "seconds": 30,
                             "wgerId": None, "exerciseDbId": None,
                             "freeExerciseDbId": None, "externalMediaUrl": None},
                        ]},
                    ],
                }],
            }],
        }
        base.update(overrides)
        return base

    def test_pause_is_excluded(self):
        entries = seed_entries([self._program()])
        self.assertNotIn("Pause", entries)

    def test_role_and_kind_captured(self):
        entries = seed_entries([self._program()])
        self.assertEqual(entries["Jumping Jacks"]["roles"], {"WARMUP"})
        self.assertEqual(entries["Jumping Jacks"]["kinds_seen"], {"REPS": 1})
        self.assertEqual(entries["Squats"]["roles"], {"ROUND"})

    def test_focus_only_collected_for_round_role(self):
        entries = seed_entries([self._program()])
        self.assertEqual(entries["Jumping Jacks"]["focus"], set())
        self.assertEqual(entries["Squats"]["focus"], {"FULL_BODY"})

    def test_equipment_union_across_programs(self):
        home = self._program()
        gym = self._program(equipment=["GYM"])
        entries = seed_entries([home, gym])
        self.assertEqual(entries["Squats"]["equipment"], {"HOME", "GYM"})

    def test_finalize_flags_kind_conflicts(self):
        reps_program = self._program()
        seconds_program = self._program()
        seconds_program["weeks"][0]["workouts"][0]["sections"][1]["exercises"][0] = {
            "name": "Squats", "reps": None, "seconds": 45,
            "wgerId": "615", "exerciseDbId": None,
            "freeExerciseDbId": None, "externalMediaUrl": None,
        }
        entries = seed_entries([reps_program, seconds_program])
        _, conflicts = finalize(entries)
        self.assertIn("Squats", [n for n, _ in conflicts])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the test, confirm it fails first (files don't exist yet if written out of order) then passes**

Run: `python3 scripts/test_build_exercise_catalog.py -v`
Expected: all tests PASS (both files were written in Steps 1–2, so this should pass on first run — if any test fails, fix `build_exercise_catalog.py`, not the test).

- [ ] **Step 4: Run the generator**

Run: `python3 scripts/build_exercise_catalog.py`
Expected output: `Wrote 230 exercises to .../programs/_pools/exercise-catalog.json` (230 is the known distinct-name count across all 35 programs as of this plan — close is fine if a few more/fewer, but a wildly different number means something's wrong). Note any "seen as both REPS and SECONDS" conflicts printed.

- [ ] **Step 5: Manual review pass**

Open `programs/_pools/exercise-catalog.json`. Spot-check ~20 entries across different `roles`/`focus` combinations for anything that looks wrong given the exercise's real-world nature — e.g. a plyometric/max-effort move tagged `WARMUP`, or a floor/mat move tagged only `GYM` when it needs no equipment. Fix any conflicts printed in Step 4's output by hand (pick the correct `kind` for that name). This file is hand-reviewed source — pretty-printed on purpose (`indent=2`) so it's diffable, unlike the minified `programs/*.json` files.

- [ ] **Step 6: Commit**

```bash
git add scripts/build_exercise_catalog.py scripts/test_build_exercise_catalog.py programs/_pools/exercise-catalog.json
git commit -m "content: seed exercise catalog from existing program data"
```

---

## Task 2: Fix the 7 real media gaps

**Depends on:** Task 1 (edits the catalog it created).

**Files:**
- Modify: `programs/_pools/exercise-catalog.json` (7 entries: `Broad Jump`, `Curtsy Lunge`, `Side Plank with Rotation L`, `Side Plank with Rotation R`, `Single-Arm Plank L`, `Single-Arm Plank R`, `Skater Jumps`)
- Modify: `CREDITS.md` (only if a Wikimedia-style hotlink is used for any of the 7 — same table as the existing 4 rows)

**Interfaces:**
- Consumes: catalog schema from Task 1.
- Produces: those 7 catalog entries with at least one non-null media field, feeding into Task 4's rotation run.

- [ ] **Step 1: Search wger for each of the 7 names**

wger has a free, keyless public search endpoint. For each name, fetch (replace spaces with `%20`):

```
https://wger.de/api/v2/exercise/search/?term=<name>&language=english
```

Accept a result only if it's genuinely the same movement (e.g. "Side Plank" alone is NOT a match for "Side Plank with Rotation" — the rotation is a distinct movement). Record the numeric id as `wgerId` if accepted.

- [ ] **Step 2: For names with no wger match, search ExerciseDB via the connected RapidAPI tool**

Use `mcp__RapidAPI-Hub-EDB-WITH-VIDEOS-AND-IMAGES-BY-ASCENDAPI__Get_Exercises_By_Search` with each exercise name as the query. Accept only a genuinely matching result; record its exercise id as `exerciseDbId`.

- [ ] **Step 3: For names still unmatched, search free-exercise-db's index**

Fetch `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json`, search its `name` fields (case-insensitive substring match on key words, e.g. "curtsy", "skater", "broad jump", "side plank"). If a genuine match exists, record its `id` field as `freeExerciseDbId`.

- [ ] **Step 4: For anything still unmatched, search Wikimedia Commons for a verified, individually-checked CC-licensed hotlink**

Same discipline as the existing `CREDITS.md` rows: correct movement, redistributable license (CC0/CC-BY/CC-BY-SA), a real file URL under `upload.wikimedia.org`. If found, set `externalMediaUrl` on the catalog entry and add a row to `CREDITS.md`'s "One-off hotlinks" table (same 4 columns: Exercise, Source, Author, License) — follow the exact format of the 4 existing rows.

- [ ] **Step 5: Leave any still-unmatched name alone**

No forced/fuzzy match. If a name genuinely has nothing after Steps 1–4, leave its catalog entry's 4 media fields `null` — it keeps falling back to the external-search buttons, exactly like today. This is an acceptable outcome, not a failure.

- [ ] **Step 6: Verify with a quick script**

```bash
python3 -c "
import json
names = ['Broad Jump', 'Curtsy Lunge', 'Side Plank with Rotation L', 'Side Plank with Rotation R', 'Single-Arm Plank L', 'Single-Arm Plank R', 'Skater Jumps']
catalog = {e['name']: e for e in json.load(open('programs/_pools/exercise-catalog.json'))['exercises']}
for n in names:
    e = catalog[n]
    matched = any(e[f] for f in ('wgerId', 'exerciseDbId', 'freeExerciseDbId', 'externalMediaUrl'))
    print(n, '-> MATCHED' if matched else '-> still unmatched (ok if genuinely no source exists)')
"
```

- [ ] **Step 7: Commit**

```bash
git add programs/_pools/exercise-catalog.json CREDITS.md
git commit -m "content: fill in real media matches for the 7 uncovered exercises"
```

---

## Task 3: Write the rotation script and its tests

**Depends on:** Task 1 (catalog schema). Does NOT run against real `programs/*.json` yet — that's Task 4.

**Files:**
- Create: `scripts/rotate_program_content.py`
- Create: `scripts/test_rotate_program_content.py`

**Interfaces:**
- Consumes: `programs/_pools/exercise-catalog.json` (Task 1/2 schema).
- Produces: `rotate_program(program: dict, catalog: list[dict]) -> dict` (pure, in-memory) and a `main()` that runs it over every `programs/*.json` and mirrors output to the two asset dirs — Task 4 invokes this script for real.

- [ ] **Step 1: Write `scripts/rotate_program_content.py`**

```python
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
```

- [ ] **Step 2: Write `scripts/test_rotate_program_content.py`**

```python
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rotate_program_content import (  # noqa: E402
    section_role, eligible_candidates, pick_candidate, rotate_program, raw_for
)

CATALOG = [
    {"name": "Squats", "kind": "REPS", "roles": ["ROUND"], "equipment": ["HOME", "GYM"],
     "focus": ["LEGS", "FULL_BODY"], "wgerId": "615", "exerciseDbId": "a",
     "freeExerciseDbId": None, "externalMediaUrl": None},
    {"name": "Lunges", "kind": "REPS", "roles": ["ROUND"], "equipment": ["HOME", "GYM"],
     "focus": ["LEGS", "FULL_BODY"], "wgerId": "999", "exerciseDbId": "b",
     "freeExerciseDbId": None, "externalMediaUrl": None},
    {"name": "Wall Sit", "kind": "SECONDS", "roles": ["ROUND"], "equipment": ["HOME"],
     "focus": ["LEGS"], "wgerId": "1", "exerciseDbId": None,
     "freeExerciseDbId": None, "externalMediaUrl": None},
]


class RotationLogicTest(unittest.TestCase):
    def test_section_role_mapping(self):
        self.assertEqual(section_role("Warm up"), "WARMUP")
        self.assertEqual(section_role("Cool Down"), "COOLDOWN")
        self.assertEqual(section_role("Round 2"), "ROUND")

    def test_eligible_candidates_filters_by_kind_role_equipment_focus(self):
        result = eligible_candidates(CATALOG, "REPS", "ROUND", ["HOME"], ["LEGS"])
        self.assertEqual({c["name"] for c in result}, {"Squats", "Lunges"})

    def test_pick_candidate_is_deterministic(self):
        candidates = eligible_candidates(CATALOG, "REPS", "ROUND", ["HOME"], ["LEGS"])
        first = pick_candidate(candidates, "p1", 1, 0, 0, set())
        second = pick_candidate(candidates, "p1", 1, 0, 0, set())
        self.assertEqual(first["name"], second["name"])

    def test_pick_candidate_excludes_given_name_when_alternative_exists(self):
        candidates = eligible_candidates(CATALOG, "REPS", "ROUND", ["HOME"], ["LEGS"])
        picked = pick_candidate(candidates, "p1", 1, 0, 0, {"Squats"})
        self.assertEqual(picked["name"], "Lunges")

    def test_raw_for_reps_and_seconds(self):
        self.assertEqual(raw_for("Squats", 10, None), "10 Squats")
        self.assertEqual(raw_for("Wall Sit", None, 45), "45s Wall Sit")


class RotateProgramTest(unittest.TestCase):
    def _program(self):
        return {
            "id": "p1", "equipment": ["HOME"], "focusAreas": ["LEGS"],
            "weeks": [
                {"number": 1, "workouts": [{"index": 1, "sections": [
                    {"title": "Round 1", "exercises": [
                        {"raw": "10 Squats", "name": "Squats", "reps": 10, "seconds": None,
                         "wgerId": "615", "exerciseDbId": "a", "freeExerciseDbId": None,
                         "externalMediaUrl": None},
                        {"raw": "30s Pause", "name": "Pause", "reps": None, "seconds": 30,
                         "wgerId": None, "exerciseDbId": None, "freeExerciseDbId": None,
                         "externalMediaUrl": None},
                    ]},
                ]}]},
            ],
        }

    def test_reps_and_seconds_never_change(self):
        rotated = rotate_program(self._program(), CATALOG)
        row = rotated["weeks"][0]["workouts"][0]["sections"][0]["exercises"][0]
        self.assertEqual(row["reps"], 10)
        self.assertIsNone(row["seconds"])

    def test_pause_rows_are_never_rotated(self):
        rotated = rotate_program(self._program(), CATALOG)
        row = rotated["weeks"][0]["workouts"][0]["sections"][0]["exercises"][1]
        self.assertEqual(row["name"], "Pause")

    def test_rotated_name_is_a_real_eligible_candidate(self):
        rotated = rotate_program(self._program(), CATALOG)
        row = rotated["weeks"][0]["workouts"][0]["sections"][0]["exercises"][0]
        self.assertIn(row["name"], {"Squats", "Lunges"})


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the tests**

Run: `python3 scripts/test_rotate_program_content.py -v`
Expected: all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add scripts/rotate_program_content.py scripts/test_rotate_program_content.py
git commit -m "content: add deterministic exercise-rotation script"
```

---

## Task 4: Run the rotation across all 35 programs

**Depends on:** Task 2 (catalog with gaps filled), Task 3 (rotation script).

**Files:**
- Modify: all 35 `programs/*.json`, all 35 `app/src/main/assets/programs/*.json`, all 35 `app/src/wasmJsMain/resources/programs/*.json` (105 files total, generated — not hand-edited).

- [ ] **Step 1: Run the rotation script**

Run: `python3 scripts/rotate_program_content.py`
Expected: 35 lines of `Rotated <file>.json` output, no errors.

- [ ] **Step 2: Verify the three locations stayed in sync**

```bash
for f in programs/*.json; do
  name=$(basename "$f")
  [ "$name" = "index.json" ] && continue
  diff -q "$f" "app/src/main/assets/programs/$name" || echo "MISMATCH (assets): $name"
  diff -q "$f" "app/src/wasmJsMain/resources/programs/$name" || echo "MISMATCH (wasmJs): $name"
done
```
Expected: no `MISMATCH` lines.

- [ ] **Step 3: Verify reps/seconds and section structure are untouched**

```bash
python3 -c "
import json, glob
for f in glob.glob('programs/*.json'):
    if f.endswith('index.json'): continue
    d = json.load(open(f))
    for w in d['weeks']:
        for wk in w['workouts']:
            for s in wk['sections']:
                for e in s['exercises']:
                    assert e['reps'] is not None or e['seconds'] is not None or e['name'] == 'Pause', f
 print('OK: every non-Pause row still has reps or seconds')
"
```

- [ ] **Step 4: Spot-check variety landed**

```bash
python3 -c "
import json
d = json.load(open('programs/beginner-full-body-home.json'))
for w in d['weeks'][:4]:
    warm = w['workouts'][0]['sections'][0]
    print('week', w['number'], [e['name'] for e in warm['exercises']])
"
```
Expected: the warm-up exercise names now differ across at least some of weeks 1–4 (before this task, all 4 were identical — see spec §Problem-1).

- [ ] **Step 5: Run the existing Kotlin program-parsing tests to confirm nothing broke**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProgramJsonTest*"`
Expected: PASS (these tests use inline literal JSON, not the bundled assets, so this mainly guards against any accidental schema drift from the rotation script — e.g. a field renamed instead of just its value changed).

- [ ] **Step 6: Commit**

```bash
git add programs/ app/src/main/assets/programs/ app/src/wasmJsMain/resources/programs/
git commit -m "content: rotate warm-up/cool-down/round exercises across all 35 programs"
```

---

## Task 5: Catalog completeness test

**Depends on:** Task 4 (needs the final rotated program files to check against).

**Files:**
- Create: `app/src/test/java/com/personal/twelveweek/programs/ExerciseCatalogCompletenessTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.personal.twelveweek.programs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against silent media gaps: every exercise name used anywhere in
 * the bundled program library, and every entry in the rotation catalog,
 * must have at least one real media identifier — or be "Pause", the only
 * intentional exception (rest, not a movement). Reads the actual repo files
 * on disk; this test lives in app/src/test (JVM-only) rather than
 * commonTest specifically so it can use java.io.File — see ProgramJsonTest
 * in commonTest for the equivalent parsing-behavior tests using inline JSON.
 */
class ExerciseCatalogCompletenessTest {

    // Gradle's working directory for `:app:test` is the `app/` module dir;
    // walk up one level to the repo root where `programs/` lives. If a
    // different Gradle version changes this, adjust here (print
    // `repoRoot.absolutePath` while debugging).
    private val repoRoot = File(System.getProperty("user.dir")).let {
        if (it.name == "app") it.parentFile else it
    }

    private fun hasMedia(obj: JsonObject): Boolean =
        listOf("wgerId", "exerciseDbId", "freeExerciseDbId", "externalMediaUrl")
            .any { key -> obj[key]?.jsonPrimitive?.contentOrNull != null }

    @Test
    fun `every exercise name in every bundled program has real media or is Pause`() {
        val programsDir = File(repoRoot, "programs")
        val programFiles = programsDir.listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }
            ?: error("No program files found under $programsDir")
        assertTrue("Expected bundled programs, found none", programFiles.isNotEmpty())

        val gaps = mutableListOf<String>()
        for (file in programFiles) {
            val program = Json.parseToJsonElement(file.readText()).jsonObject
            for (week in program["weeks"]!!.jsonArray) {
                for (workout in week.jsonObject["workouts"]!!.jsonArray) {
                    for (section in workout.jsonObject["sections"]!!.jsonArray) {
                        for (exercise in section.jsonObject["exercises"]!!.jsonArray) {
                            val obj = exercise.jsonObject
                            val name = obj["name"]!!.jsonPrimitive.content
                            if (name == "Pause") continue
                            if (!hasMedia(obj)) gaps.add("${file.name}: $name")
                        }
                    }
                }
            }
        }
        assertTrue(
            "Exercises with no media match (add to CREDITS.md / catalog): ${gaps.distinct()}",
            gaps.isEmpty()
        )
    }

    @Test
    fun `every catalog entry has real media or is explicitly excluded`() {
        val catalogFile = File(repoRoot, "programs/_pools/exercise-catalog.json")
        assertTrue("Catalog not found at $catalogFile", catalogFile.exists())
        val catalog = Json.parseToJsonElement(catalogFile.readText()).jsonObject
        val gaps = catalog["exercises"]!!.jsonArray
            .map { it.jsonObject }
            .filterNot { hasMedia(it) }
            .map { it["name"]!!.jsonPrimitive.content }
        assertTrue("Catalog entries with no media match: $gaps", gaps.isEmpty())
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :app:testDebugUnitTest --tests "*ExerciseCatalogCompletenessTest*"`
Expected: PASS if Task 2's gap-fill genuinely covered all 7 names; if any stayed intentionally unmatched (Task 2 Step 5), this test FAILS — that's correct behavior surfacing a real gap, not a bug in the test. In that case, either find a match after all, or explicitly special-case that name (add a documented allowlist constant near the top of this file, e.g. `private val INTENTIONALLY_UNMATCHED = setOf("Some Exercise")`, and skip it in both loops) — do not weaken the assertion generally.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/personal/twelveweek/programs/ExerciseCatalogCompletenessTest.kt
git commit -m "test: guard exercise media coverage across programs and catalog"
```

---

## Task 6: Android — instant plan switching

**Files:**
- Modify: `app/src/main/java/com/personal/twelveweek/MainActivity.kt` (the `selectProgram` function, the `LaunchedEffect(selectedProgramId, libraryIndex)` block, and the `AppRoot` `when` block's `else` branch — all inside `AppRoot()`, roughly lines 100–226 in the current file; exact line numbers will have shifted slightly by the time this task runs, search for `fun selectProgram` and `activeProgram == null`)

**Interfaces:**
- No new public functions; behavior-only change to existing `AppRoot()`.

- [ ] **Step 1: Remove the null-out from `selectProgram`**

Find:
```kotlin
    fun selectProgram(id: String) {
        selectedProgramStore.set(id)
        loadFailed = false
        activeProgram = null
        selectedProgramId = id
        screen = Screen.Today
    }
```
Replace with:
```kotlin
    fun selectProgram(id: String) {
        selectedProgramStore.set(id)
        loadFailed = false
        selectedProgramId = id
        screen = Screen.Today
    }
```

- [ ] **Step 2: Handle a switch-time load failure without leaving stale/undefined state**

Find:
```kotlin
    LaunchedEffect(selectedProgramId, libraryIndex) {
        loadFailed = false
        val loaded = library.load(selectedProgramId)
        when {
            loaded != null -> activeProgram = loaded
            selectedProgramId != SelectedProgramStore.DEFAULT_PROGRAM_ID -> {
                selectedProgramStore.set(SelectedProgramStore.DEFAULT_PROGRAM_ID)
                selectedProgramId = SelectedProgramStore.DEFAULT_PROGRAM_ID
            }
            else -> loadFailed = true
        }
    }
```
Replace with:
```kotlin
    LaunchedEffect(selectedProgramId, libraryIndex) {
        loadFailed = false
        val loaded = library.load(selectedProgramId)
        when {
            loaded != null -> activeProgram = loaded
            activeProgram != null -> {
                // A switch away from a working program failed. activeProgram
                // is no longer nulled on switch (Step 1), so silently revert
                // rather than leaving the user on stale/undefined state —
                // matches this app's existing "best-effort and silent on
                // failure" convention (see ProgramSyncRepository.sync()).
                selectedProgramStore.set(activeProgram!!.meta.id)
                selectedProgramId = activeProgram!!.meta.id
            }
            selectedProgramId != SelectedProgramStore.DEFAULT_PROGRAM_ID -> {
                selectedProgramStore.set(SelectedProgramStore.DEFAULT_PROGRAM_ID)
                selectedProgramId = SelectedProgramStore.DEFAULT_PROGRAM_ID
            }
            else -> loadFailed = true
        }
    }
```

- [ ] **Step 3: Crossfade the program swap instead of a hard cut**

Add the import near the other `androidx.compose.*` imports:
```kotlin
import androidx.compose.animation.Crossfade
```
Find the `else -> AppShell(...)` branch:
```kotlin
                else -> AppShell(
                    program = activeProgram!!,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    screen = screen,
                    onScreenChange = { screen = it },
                    onSelectProgram = ::selectProgram,
                    onImport = { importLauncher.launch("application/json") },
                    importError = importError,
                    onDismissImportError = { importError = null }
                )
```
Replace with:
```kotlin
                else -> Crossfade(targetState = activeProgram!!, label = "activeProgram") { program ->
                    AppShell(
                        program = program,
                        libraryIndex = libraryIndex,
                        selectedProgramId = selectedProgramId,
                        progress = progress,
                        screen = screen,
                        onScreenChange = { screen = it },
                        onSelectProgram = ::selectProgram,
                        onImport = { importLauncher.launch("application/json") },
                        importError = importError,
                        onDismissImportError = { importError = null }
                    )
                }
```

- [ ] **Step 4: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification (device/emulator, if available)**

Install and open the app, go to Programs, tap a different plan's "Use this plan". Expected: no full-screen "Preparing your plan" spinner — Today screen updates in place with a brief crossfade. Existing progress ticks on the previous plan are unaffected when you switch back.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/personal/twelveweek/MainActivity.kt
git commit -m "perf(android): instant plan switching, no blocking loader"
```

---

## Task 7: Web — instant plan switching (mirrors Task 6)

**Files:**
- Modify: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt` (search for `onSelectProgram = { id ->`, `LaunchedEffect(selectedProgramId)`, and `else -> WebAppShell(`)

- [ ] **Step 1: Stop nulling `activeProgram` on select**

The `onSelectProgram` lambda passed into `WebAppShell` (around where `WebAppShell(...)` is called) already does NOT null `activeProgram` — confirm this is still the case (it only does `selectedProgramStore.set(id); selectedProgramId = id; screen = WebScreen.Today`). No change needed here; the null-out lives in the `LaunchedEffect` below.

- [ ] **Step 2: Handle switch-time load failure in the `LaunchedEffect`**

Find:
```kotlin
    LaunchedEffect(selectedProgramId) {
        loadFailed = false
        activeProgram = null
        val loaded = library.load(selectedProgramId)
        if (loaded != null) {
            activeProgram = loaded
        } else if (selectedProgramId != SelectedProgramStore.DEFAULT_PROGRAM_ID) {
            selectedProgramStore.set(SelectedProgramStore.DEFAULT_PROGRAM_ID)
            selectedProgramId = SelectedProgramStore.DEFAULT_PROGRAM_ID
        } else {
            loadFailed = true
        }
    }
```
Replace with:
```kotlin
    LaunchedEffect(selectedProgramId) {
        loadFailed = false
        val loaded = library.load(selectedProgramId)
        val current = activeProgram
        if (loaded != null) {
            activeProgram = loaded
        } else if (current != null) {
            // Switch failed but a working program is still showing — revert
            // silently rather than leaving stale/undefined state.
            selectedProgramStore.set(current.meta.id)
            selectedProgramId = current.meta.id
        } else if (selectedProgramId != SelectedProgramStore.DEFAULT_PROGRAM_ID) {
            selectedProgramStore.set(SelectedProgramStore.DEFAULT_PROGRAM_ID)
            selectedProgramId = SelectedProgramStore.DEFAULT_PROGRAM_ID
        } else {
            loadFailed = true
        }
    }
```

- [ ] **Step 3: Crossfade the program swap**

Add import: `import androidx.compose.animation.Crossfade`
Find:
```kotlin
                else -> WebAppShell(
                    program = program,
                    libraryIndex = entries,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    settings = settings,
                    library = library,
                    screen = screen,
                    onScreenChange = { screen = it },
                    onSelectProgram = { id ->
                        selectedProgramStore.set(id)
                        selectedProgramId = id
                        screen = WebScreen.Today
                    },
                    onProgramImported = { appScope.launch { index = library.index() } }
                )
```
Replace with:
```kotlin
                else -> Crossfade(targetState = program, label = "activeProgram") { current ->
                    WebAppShell(
                        program = current,
                        libraryIndex = entries,
                        selectedProgramId = selectedProgramId,
                        progress = progress,
                        settings = settings,
                        library = library,
                        screen = screen,
                        onScreenChange = { screen = it },
                        onSelectProgram = { id ->
                            selectedProgramStore.set(id)
                            selectedProgramId = id
                            screen = WebScreen.Today
                        },
                        onProgramImported = { appScope.launch { index = library.index() } }
                    )
                }
```
Note: `program` here is the local `val program = activeProgram` already bound a few lines above this `when` block — keep using that binding as `Crossfade`'s `targetState`.

- [ ] **Step 4: Compile check**

Run: `./gradlew :app:compileKotlinWasmJs` (or the project's equivalent wasmJs compile task — check `./gradlew tasks --group build` if the exact name differs)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt
git commit -m "perf(web): instant plan switching, no blocking loader"
```

---

## Task 8: Android — plan list inline expand

**Depends on:** Task 6 (same file, `MainActivity.kt` — run after Task 6 lands to avoid conflicting edits).

**Files:**
- Modify: `app/src/main/java/com/personal/twelveweek/ui/ProgramPickerScreen.kt`
- Modify: `app/src/main/java/com/personal/twelveweek/MainActivity.kt` (3 call sites: the `loadFailed` fallback, `OnboardingFlow`, `AppScreenContent`'s `Screen.Programs` branch)

**Interfaces:**
- `ProgramPickerScreen` gains a required `library: ProgramLibrary` parameter (import `com.personal.twelveweek.programs.ProgramLibrary`, `com.personal.twelveweek.programs.LibraryProgram`).

- [ ] **Step 1: Add `library` param, expand state, and the preview fetch to `ProgramPickerScreen`**

In `ProgramPickerScreen.kt`, change the function signature:
```kotlin
@Composable
fun ProgramPickerScreen(
    entries: List<IndexEntry>,
    selectedProgramId: String,
    library: ProgramLibrary,
    onSelect: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    importError: String? = null,
    onDismissImportError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
```
Add near the other `remember`s at the top of the function body:
```kotlin
    var previewCache by remember { mutableStateOf<Map<String, LibraryProgram>>(emptyMap()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val previewScope = rememberCoroutineScope()

    fun toggleExpand(id: String) {
        expandedId = if (expandedId == id) null else id
        if (id !in previewCache) {
            previewScope.launch {
                library.load(id)?.let { loaded -> previewCache = previewCache + (id to loaded) }
            }
        }
    }
```
Add imports: `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`, `com.personal.twelveweek.programs.LibraryProgram`, `com.personal.twelveweek.programs.ProgramLibrary`, `com.personal.twelveweek.Week`.

Update the `items(filtered, key = { it.meta.id }) { entry -> ProgramCard(...) }` call:
```kotlin
                items(filtered, key = { it.meta.id }) { entry ->
                    ProgramCard(
                        entry = entry,
                        selected = entry.meta.id == selectedProgramId,
                        expanded = entry.meta.id == expandedId,
                        preview = previewCache[entry.meta.id],
                        onToggleExpand = { toggleExpand(entry.meta.id) },
                        onUsePlan = { onSelect(entry.meta.id) }
                    )
                }
```

- [ ] **Step 2: Update `ProgramCard` to expand-on-tap with a separate "Use this plan" action**

Replace the whole `ProgramCard` composable with:
```kotlin
@Composable
private fun ProgramCard(
    entry: IndexEntry,
    selected: Boolean,
    expanded: Boolean,
    preview: LibraryProgram?,
    onToggleExpand: () -> Unit,
    onUsePlan: () -> Unit
) {
    val meta = entry.meta
    Surface(
        onClick = onToggleExpand,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(meta.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${meta.level.label()} · ${meta.weekCount} weeks · " +
                            meta.equipment.joinToString("/") { it.label() } +
                            if (meta.sessionMinutes > 0) " · ~${meta.sessionMinutes} min/day" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (selected) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Current plan",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(7.dp).size(18.dp)
                        )
                    }
                }
            }
            if (meta.focusAreas.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    meta.focusAreas.joinToString(" · ") { it.label() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "Hide workouts" else "See workouts")
                }
                Spacer(Modifier.weight(1f))
                if (!selected) {
                    Button(onClick = onUsePlan) { Text("Use this plan") }
                } else {
                    Text(
                        "Current plan",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (expanded) {
                if (preview == null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    WeekPreviewList(preview.weeks)
                }
            }
        }
    }
}

@Composable
private fun WeekPreviewList(weeks: List<Week>) {
    Column(Modifier.padding(top = 12.dp)) {
        weeks.forEach { week ->
            Text("Week ${week.number}", style = MaterialTheme.typography.labelLarge)
            week.workouts.forEach { workout ->
                val names = workout.sections
                    .flatMap { it.exercises }
                    .filterNot { it.isRest }
                    .joinToString(", ") { it.name }
                Text(
                    "${workout.title}: $names",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```
Add imports: `androidx.compose.material3.Button`, `androidx.compose.material3.LinearProgressIndicator`.

Note: `onClick = onToggleExpand` on the outer `Surface` means tapping the card body expands/collapses it; "Use this plan" is now its own `Button`, which intercepts its own click (Compose gives nested clickables their own hit target) so tapping it does not also toggle expand.

- [ ] **Step 3: Thread `library` through all 3 call sites in `MainActivity.kt`**

Find each of the 3 `ProgramPickerScreen(` calls (the `activeProgram == null && loadFailed ->` branch, inside `OnboardingFlow`, and inside `AppScreenContent`'s `Screen.Programs ->` branch) and add `library = library,` as a parameter. For the two calls not inside `AppRoot()` directly (`OnboardingFlow`, `AppScreenContent`), also add `library: ProgramLibrary` to that composable's own parameter list and thread it from `AppRoot()`'s call to it (`OnboardingFlow(..., library = library, ...)` and `AppScreenContent(..., library = library, ...)` inside `AppShell`, which itself needs `library: ProgramLibrary` added to its signature and passed through from `AppRoot()`'s `AppShell(...)` call — now wrapped in `Crossfade` from Task 6).

- [ ] **Step 4: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

Open Programs tab, tap "See workouts" on a non-current plan — expect a loading indicator then the full week-by-week breakdown; tap again to collapse; tap "Use this plan" while collapsed AND while expanded, confirm both switch the plan without needing to expand first.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/personal/twelveweek/ui/ProgramPickerScreen.kt app/src/main/java/com/personal/twelveweek/MainActivity.kt
git commit -m "feat(android): preview a plan's full workout list before switching"
```

---

## Task 9: Web — plan list inline expand (mirrors Task 8)

**Depends on:** Task 7 (same file, `WebApp.kt`).

**Files:**
- Modify: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt` (`ProgramsScreen` function and the `ProgramCard`/`items(filtered, ...)` block within it — search for `private fun ProgramCard(entry: IndexEntry`)

`ProgramsScreen` already receives `library: ProgramLibrary?` (nullable) — no new parameter threading needed, unlike Android.

- [ ] **Step 1: Add expand state to `ProgramsScreen`**

Near its other `remember`s (alongside `levelFilter`, etc.), add:
```kotlin
    var previewCache by remember { mutableStateOf<Map<String, LibraryProgram>>(emptyMap()) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun toggleExpand(id: String) {
        expandedId = if (expandedId == id) null else id
        val lib = library ?: return
        if (id !in previewCache) {
            importScope.launch {
                lib.load(id)?.let { loaded -> previewCache = previewCache + (id to loaded) }
            }
        }
    }
```
`importScope` is the existing `rememberCoroutineScope()` already used by `startImport()` in this function — reuse it rather than creating a second scope.

- [ ] **Step 2: Update the `items(filtered, ...)` call and `ProgramCard`**

Find:
```kotlin
                items(filtered, key = { it.meta.id }) { entry ->
                    ProgramCard(entry = entry, selected = entry.meta.id == selectedProgramId, onClick = { onSelect(entry.meta.id) })
                }
```
Replace with:
```kotlin
                items(filtered, key = { it.meta.id }) { entry ->
                    ProgramCard(
                        entry = entry,
                        selected = entry.meta.id == selectedProgramId,
                        expanded = entry.meta.id == expandedId,
                        preview = previewCache[entry.meta.id],
                        onToggleExpand = { toggleExpand(entry.meta.id) },
                        onUsePlan = { onSelect(entry.meta.id) }
                    )
                }
```
Replace the `ProgramCard` composable (currently `onClick`-only, single button-less card) with the same expand/preview structure as Android's Task 8 Step 2 — same field names (`expanded`, `preview`, `onToggleExpand`, `onUsePlan`), adapted to this file's existing `TrainingCard` wrapper instead of a raw `Surface`:
```kotlin
@Composable
private fun ProgramCard(
    entry: IndexEntry,
    selected: Boolean,
    expanded: Boolean,
    preview: LibraryProgram?,
    onToggleExpand: () -> Unit,
    onUsePlan: () -> Unit
) {
    val meta = entry.meta
    TrainingCard(
        modifier = Modifier.fillMaxWidth(), onClick = onToggleExpand, selected = selected, contentPadding = 18.dp,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(meta.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${meta.level.label()} · ${meta.weekCount} weeks · " +
                        meta.equipment.joinToString("/") { it.label() } +
                        if (meta.sessionMinutes > 0) " · ~${meta.sessionMinutes} min/day" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Current plan",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }
            }
        }
        if (meta.focusAreas.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                meta.focusAreas.joinToString(" · ") { it.label() },
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleExpand) { Text(if (expanded) "Hide workouts" else "See workouts") }
            Spacer(Modifier.weight(1f))
            if (!selected) {
                Button(onClick = onUsePlan) { Text("Use this plan") }
            } else {
                Text("Current plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (expanded) {
            if (preview == null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Column(Modifier.padding(top = 12.dp)) {
                    preview.weeks.forEach { week ->
                        Text("Week ${week.number}", style = MaterialTheme.typography.labelLarge)
                        week.workouts.forEach { workout ->
                            val names = workout.sections.flatMap { it.exercises }.filterNot { it.isRest }.joinToString(", ") { it.name }
                            Text("${workout.title}: $names", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
```
Add imports if not already present in this file: `androidx.compose.material3.Button`, `androidx.compose.material3.LinearProgressIndicator`, `com.personal.twelveweek.programs.LibraryProgram`.

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileKotlinWasmJs` (or the project's equivalent task name)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt
git commit -m "feat(web): preview a plan's full workout list before switching"
```

---

## Task 10: Android — background whole-program media prefetch

**Depends on:** Task 8 (same file, `MainActivity.kt`).

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/MediaPrefetcher.kt`
- Test: `app/src/test/java/com/personal/twelveweek/media/MediaPrefetcherTest.kt`
- Modify: `app/src/main/java/com/personal/twelveweek/MainActivity.kt` (`AppRoot()`)

**Interfaces:**
- Produces: `LibraryProgram.distinctPrefetchableExercises(): List<Exercise>` and `suspend fun prefetchProgramMedia(program: LibraryProgram, repository: ExerciseMediaRepository, throttleMillis: Long = 400L)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Section
import com.personal.twelveweek.Week
import com.personal.twelveweek.Workout
import com.personal.twelveweek.programs.Equipment
import com.personal.twelveweek.programs.FocusArea
import com.personal.twelveweek.programs.LibraryProgram
import com.personal.twelveweek.programs.ProgramLevel
import com.personal.twelveweek.programs.ProgramMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPrefetcherTest {

    private fun program() = LibraryProgram(
        meta = ProgramMeta(
            id = "p1", title = "Test", level = ProgramLevel.BEGINNER,
            focusAreas = listOf(FocusArea.FULL_BODY), equipment = listOf(Equipment.HOME),
            weekCount = 2
        ),
        weeks = listOf(
            Week(1, listOf(Workout("p1", 1, 1, listOf(
                Section("Warm up", listOf(Exercise.parse("30 Jumping Jacks"))),
                Section("Round 1", listOf(Exercise.parse("10 Squats"), Exercise.parse("30s Pause")))
            )))),
            Week(2, listOf(Workout("p1", 2, 1, listOf(
                Section("Warm up", listOf(Exercise.parse("30 Jumping Jacks"))),
                Section("Round 1", listOf(Exercise.parse("10 Squats"), Exercise.parse("30s Pause")))
            ))))
        )
    )

    @Test
    fun `dedupes repeated exercises across weeks and drops rest rows`() {
        val distinct = program().distinctPrefetchableExercises()
        assertEquals(listOf("Jumping Jacks", "Squats"), distinct.map { it.name })
    }
}
```

- [ ] **Step 2: Run it, confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*MediaPrefetcherTest*"`
Expected: FAIL (compile error — `distinctPrefetchableExercises` doesn't exist yet).

- [ ] **Step 3: Write `MediaPrefetcher.kt`**

```kotlin
package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.programs.LibraryProgram
import kotlinx.coroutines.delay

/** Every distinct, non-rest exercise across [this] program's full set of
 *  weeks — the set worth warming the media cache for. Deduplicated by
 *  [Exercise.slug] so a name reused across dozens of weeks/workouts is
 *  only fetched once. */
fun LibraryProgram.distinctPrefetchableExercises(): List<Exercise> =
    weeks
        .flatMap { week -> week.workouts.flatMap { it.sections.flatMap { s -> s.exercises } } }
        .filterNot { it.isRest }
        .distinctBy { it.slug }

/**
 * Silently warms [repository]'s disk cache for every distinct exercise in
 * [program], throttled by [throttleMillis] between requests to stay well
 * inside RapidAPI's free-tier rate limit. A plain suspend function —
 * callers run it inside `LaunchedEffect(program.meta.id)` so switching
 * programs cancels an in-flight prefetch and starts a fresh one for free
 * (Compose cancels the old coroutine when the effect's key changes). Never
 * throws: a failed fetch for one exercise just leaves that one to fall
 * back to the existing on-demand fetch when actually viewed.
 */
suspend fun prefetchProgramMedia(
    program: LibraryProgram,
    repository: ExerciseMediaRepository,
    throttleMillis: Long = 400L
) {
    for (exercise in program.distinctPrefetchableExercises()) {
        runCatching { repository.getBundle(exercise) }
        delay(throttleMillis)
    }
}
```

- [ ] **Step 4: Run the test again, confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*MediaPrefetcherTest*"`
Expected: PASS.

- [ ] **Step 5: Wire it into `AppRoot()`**

Near the other `remember`s in `AppRoot()` (after `val syncRepo = remember { ... }`), add:
```kotlin
    val mediaKeyManager = remember { com.personal.twelveweek.security.ApiKeyManager(context) }
    val mediaRepository = remember {
        com.personal.twelveweek.media.ExerciseMediaRepository.default(context, mediaKeyManager)
    }
```
(Or add proper top-of-file imports — `com.personal.twelveweek.security.ApiKeyManager`, `com.personal.twelveweek.media.ExerciseMediaRepository` — and drop the fully-qualified names above; match this file's existing import style.)

Add a new effect near the existing `LaunchedEffect(selectedProgramId, libraryIndex)`:
```kotlin
    LaunchedEffect(activeProgram?.meta?.id) {
        activeProgram?.let { prefetchProgramMedia(it, mediaRepository) }
    }
```
Add import: `com.personal.twelveweek.media.prefetchProgramMedia`.

- [ ] **Step 6: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/personal/twelveweek/media/MediaPrefetcher.kt app/src/test/java/com/personal/twelveweek/media/MediaPrefetcherTest.kt app/src/main/java/com/personal/twelveweek/MainActivity.kt
git commit -m "perf(android): background-prefetch a program's exercise media"
```

---

## Task 11: Web — background whole-program media prefetch (mirrors Task 10)

**Depends on:** Task 9 (same file, `WebApp.kt`).

**Files:**
- Create: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebMediaPrefetcher.kt`
- Modify: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt` (`WebApp()`)

No separate test: the pure selection logic is identical to Task 10's (already covered by `MediaPrefetcherTest`), and this file's only web-specific part is the `WebExerciseMediaRepository` type, which isn't independently mockable without more refactoring than this task's scope justifies.

- [ ] **Step 1: Write `WebMediaPrefetcher.kt`**

```kotlin
package com.personal.twelveweek.web

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.programs.LibraryProgram
import kotlinx.coroutines.delay

fun LibraryProgram.distinctPrefetchableExercises(): List<Exercise> =
    weeks
        .flatMap { week -> week.workouts.flatMap { it.sections.flatMap { s -> s.exercises } } }
        .filterNot { it.isRest }
        .distinctBy { it.slug }

suspend fun prefetchProgramMedia(
    program: LibraryProgram,
    repository: WebExerciseMediaRepository,
    throttleMillis: Long = 400L
) {
    for (exercise in program.distinctPrefetchableExercises()) {
        runCatching { repository.getBundle(exercise) }
        delay(throttleMillis)
    }
}
```

- [ ] **Step 2: Wire it into `WebApp()`**

Near the other `remember`s in `WebApp()`, add:
```kotlin
    val prefetchKeyManager = remember { WebApiKeyManager() }
    val prefetchRepository = remember { WebExerciseMediaRepository.default(prefetchKeyManager) }
```
Add a new effect near the existing `LaunchedEffect(selectedProgramId)`:
```kotlin
    LaunchedEffect(activeProgram?.meta?.id) {
        activeProgram?.let { prefetchProgramMedia(it, prefetchRepository) }
    }
```

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileKotlinWasmJs` (or the project's equivalent task name)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebMediaPrefetcher.kt app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt
git commit -m "perf(web): background-prefetch a program's exercise media"
```

---

## Task 12: Smoothness audit pass

**Depends on:** Tasks 6–11 (audits their combined result; also a general sweep).

**Files:** whichever `ui/*.kt` / `MainActivity.kt` / `web/*.kt` files the audit steps below turn up issues in — not knowable in advance, hence the concrete audit commands rather than a fixed file list.

- [ ] **Step 1: Find any list without a stable `key`**

Run:
```bash
grep -rn "LazyColumn(\|LazyRow(" app/src/main/java/com/personal/twelveweek/ui app/src/main/java/com/personal/twelveweek/MainActivity.kt app/src/wasmJsMain/kotlin/com/personal/twelveweek/web
```
For each match, open the surrounding `items(...)` call and confirm it passes `key = { ... }` using a stable identifier (an id/slug/index that doesn't change when unrelated list items change) rather than relying on position alone. Add `key = { ... }` wherever missing, following the pattern already used in `ProgramPickerScreen.kt` (`key = { it.meta.id }`).

- [ ] **Step 2: Find any program/media load call happening outside a coroutine scope**

Run:
```bash
grep -rn "\.load(\|\.getBundle(\|\.index()" app/src/main/java/com/personal/twelveweek app/src/wasmJsMain/kotlin/com/personal/twelveweek/web | grep -v "LaunchedEffect\|suspend fun\|\.launch {\|// "
```
For each match, confirm it's inside a `LaunchedEffect`, a `rememberCoroutineScope().launch { }`, or another `suspend fun` — i.e. never invoked directly in composition (which would block the UI thread). Fix any found by wrapping in one of those.

- [ ] **Step 3: Confirm `Crossfade` usages from Tasks 6–7 use a sensible default**

`Crossfade` without an explicit `animationSpec` already uses Compose's default `tween(300)` — confirm no custom (and potentially too-slow or too-abrupt) duration was accidentally introduced; leave the default as-is unless something looks visibly off during manual verification.

- [ ] **Step 4: Full build across both targets**

Run: `./gradlew :app:compileDebugKotlin :app:compileKotlinWasmJs :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 5: Manual verification pass (device/emulator + browser, if available)**

Walk through: switch plans (Task 6/7), expand a plan card (Task 8/9), start a workout whose media should already be warm from background prefetch (Task 10/11) and confirm it opens without a network spinner. Note anything that still feels janky for a follow-up, but do not block this task on subjective polish beyond what Steps 1–3 turned up concretely.

- [ ] **Step 6: Commit** (only if Steps 1–3 found and fixed anything; otherwise this task ends with no changes to commit)

```bash
git add -A
git commit -m "polish: fix list-key and main-thread-blocking issues found in smoothness audit"
```
