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

def _entry(name, kind="SECONDS", roles=("COOLDOWN",), equipment=("HOME",), focus=()):
    return {"name": name, "kind": kind, "roles": list(roles), "equipment": list(equipment),
            "focus": list(focus), "wgerId": "w-" + name, "exerciseDbId": "e-" + name,
            "freeExerciseDbId": None, "externalMediaUrl": None}

# Catalog with L/R sibling pairs plus bilateral moves, all in the same slot bucket.
CATALOG_LR = [_entry(n) for n in (
    "Calf Stretch L", "Calf Stretch R",
    "Hamstring Stretch L", "Hamstring Stretch R",
    "Quad Stretch L", "Quad Stretch R",
    "Child Pose", "Cat Cow", "Forward Fold", "Chest Opener",
)]

# Catalog with enough REPS/ROUND variety to exercise the per-workout and
# per-section distinctness rules.
CATALOG_ROUND = [
    _entry(n, kind="REPS", roles=("ROUND",), focus=("LEGS",))
    for n in ("Squats", "Lunges", "Step Ups", "Glute Bridges", "Calf Raises", "Split Squats")
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
        first = pick_candidate(candidates, "p1", 1, 1, 0, 0, set())
        second = pick_candidate(candidates, "p1", 1, 1, 0, 0, set())
        self.assertEqual(first["name"], second["name"])

    def test_pick_candidate_seed_depends_on_workout_index(self):
        """C1: the same slot in different workouts of a week must not be forced equal."""
        candidates = CATALOG_ROUND
        picks = {
            pick_candidate(candidates, "p1", 1, w, 0, 0, set())["name"]
            for w in (1, 2, 3)
        }
        self.assertGreater(len(picks), 1)

    def test_pick_candidate_excludes_given_name_when_alternative_exists(self):
        candidates = eligible_candidates(CATALOG, "REPS", "ROUND", ["HOME"], ["LEGS"])
        picked = pick_candidate(candidates, "p1", 1, 1, 0, 0, {"Squats"})
        self.assertEqual(picked["name"], "Lunges")

    def test_raw_for_reps_and_seconds(self):
        self.assertEqual(raw_for("Squats", 10, None), "10 Squats")
        self.assertEqual(raw_for("Wall Sit", None, 45), "45s Wall Sit")


    def test_eligible_candidates_single_viable_candidate(self):
        """Test the precondition for rotate_program's len(candidates)<2 skip branch."""
        result = eligible_candidates(CATALOG, "SECONDS", "ROUND", ["HOME"], ["LEGS"])
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["name"], "Wall Sit")

    def test_pick_candidate_fallback_when_excluding_empties_pool(self):
        """Test pick_candidate's fallback when exclude_names contains all candidates."""
        candidates = eligible_candidates(CATALOG, "SECONDS", "ROUND", ["HOME"], ["LEGS"])
        # Exclude the only candidate; pick_candidate should fall back to returning it anyway
        picked = pick_candidate(candidates, "p1", 1, 1, 0, 0, {"Wall Sit"})
        self.assertEqual(picked["name"], "Wall Sit")

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

    def test_single_candidate_exercise_is_left_alone(self):
        """Test that rotate_program skips rotation when len(candidates)<2."""
        # Wall Sit is the only SECONDS/ROUND/HOME/LEGS candidate
        program = {
            "id": "p1", "equipment": ["HOME"], "focusAreas": ["LEGS"],
            "weeks": [
                {"number": 1, "workouts": [{"index": 1, "sections": [
                    {"title": "Round 1", "exercises": [
                        {"raw": "45s Wall Sit", "name": "Wall Sit", "reps": None, "seconds": 45,
                         "wgerId": "1", "exerciseDbId": None, "freeExerciseDbId": None,
                         "externalMediaUrl": None},
                    ]},
                ]}]},
            ],
        }
        rotated = rotate_program(program, CATALOG)
        row = rotated["weeks"][0]["workouts"][0]["sections"][0]["exercises"][0]
        # Since there's only 1 eligible SECONDS candidate, name should stay "Wall Sit"
        self.assertEqual(row["name"], "Wall Sit")


def _row(name, reps=None, seconds=None):
    raw = f"{seconds}s {name}" if seconds is not None else f"{reps} {name}"
    return {"raw": raw, "name": name, "reps": reps, "seconds": seconds, "wgerId": None,
            "exerciseDbId": None, "freeExerciseDbId": None, "externalMediaUrl": None}


def _program_with(sections, workouts=1, weeks=1, equipment=None, focus=None):
    return {
        "id": "p1", "equipment": equipment or ["HOME"], "focusAreas": focus or ["LEGS"],
        "weeks": [
            {"number": wk, "workouts": [
                {"index": wo, "sections": [
                    {"title": title, "exercises": [dict(r) for r in rows]}
                    for title, rows in sections
                ]}
                for wo in range(1, workouts + 1)
            ]}
            for wk in range(1, weeks + 1)
        ],
    }


def _names(program, week=0, workout=0, section=0):
    return [e["name"] for e in
            program["weeks"][week]["workouts"][workout]["sections"][section]["exercises"]]


class WorkoutDistinctnessTest(unittest.TestCase):
    """C1: workouts inside one week must not all resolve to the same exercises."""

    def _three_day_week(self):
        rows = [_row("Squats", reps=10), _row("Lunges", reps=10), _row("Step Ups", reps=10)]
        return _program_with([("Round 1", rows)], workouts=3)

    def test_workouts_in_the_same_week_are_not_all_identical(self):
        rotated = rotate_program(self._three_day_week(), CATALOG_ROUND)
        signatures = {tuple(_names(rotated, workout=w)) for w in range(3)}
        self.assertGreater(len(signatures), 1)

    def test_rotation_is_reproducible_across_runs(self):
        first = rotate_program(self._three_day_week(), CATALOG_ROUND)
        second = rotate_program(self._three_day_week(), CATALOG_ROUND)
        self.assertEqual(
            [_names(first, workout=w) for w in range(3)],
            [_names(second, workout=w) for w in range(3)],
        )


class SectionDuplicateTest(unittest.TestCase):
    """C2: a single section must not list the same exercise twice."""

    def test_no_duplicate_names_inside_one_section(self):
        rows = [_row(f"Squats", reps=10) for _ in range(4)]
        rotated = rotate_program(_program_with([("Round 1", rows)]), CATALOG_ROUND)
        names = _names(rotated)
        self.assertEqual(len(names), len(set(names)), names)

    def test_duplicate_check_is_scoped_to_one_section(self):
        """Two sections may legitimately reuse a name; the ban is per-section only."""
        rows = [_row("Squats", reps=10), _row("Lunges", reps=10)]
        program = _program_with([("Round 1", rows), ("Round 2", rows)])
        rotated = rotate_program(program, CATALOG_ROUND)
        for s in (0, 1):
            names = _names(rotated, section=s)
            self.assertEqual(len(names), len(set(names)), names)

    def test_pause_rows_do_not_block_a_section(self):
        rows = [_row("Squats", reps=10), _row("Pause", seconds=30),
                _row("Pause", seconds=30), _row("Lunges", reps=10)]
        rotated = rotate_program(_program_with([("Round 1", rows)]), CATALOG_ROUND)
        names = _names(rotated)
        self.assertEqual(names[1], "Pause")
        self.assertEqual(names[2], "Pause")
        self.assertNotEqual(names[0], names[3])


def _suffix(name):
    return name[-2:] if name.endswith(" L") or name.endswith(" R") else None


class UnilateralPairingTest(unittest.TestCase):
    """C3: unilateral (L/R) moves must stay paired inside their section."""

    def _cooldown(self, rows, **kwargs):
        return _program_with([("Cool Down", rows)], **kwargs)

    def test_lr_pair_rotates_to_a_matching_lr_pair(self):
        rows = [_row("Calf Stretch L", seconds=30), _row("Child Pose", seconds=30),
                _row("Calf Stretch R", seconds=30)]
        rotated = rotate_program(self._cooldown(rows), CATALOG_LR)
        names = _names(rotated)
        self.assertTrue(names[0].endswith(" L"), names)
        self.assertTrue(names[2].endswith(" R"), names)
        self.assertEqual(names[0][:-2], names[2][:-2], names)

    def test_pairing_holds_when_r_row_comes_first(self):
        rows = [_row("Calf Stretch R", seconds=30), _row("Calf Stretch L", seconds=30)]
        rotated = rotate_program(self._cooldown(rows), CATALOG_LR)
        names = _names(rotated)
        self.assertTrue(names[0].endswith(" R"), names)
        self.assertTrue(names[1].endswith(" L"), names)
        self.assertEqual(names[0][:-2], names[1][:-2], names)

    def test_several_pairs_in_one_section_all_stay_paired_and_distinct(self):
        rows = [_row("Calf Stretch L", seconds=30), _row("Calf Stretch R", seconds=30),
                _row("Quad Stretch L", seconds=30), _row("Quad Stretch R", seconds=30)]
        rotated = rotate_program(self._cooldown(rows), CATALOG_LR)
        names = _names(rotated)
        bases = [n[:-2] for n in names]
        self.assertEqual([_suffix(n) for n in names], [" L", " R", " L", " R"], names)
        self.assertEqual(bases[0], bases[1], names)
        self.assertEqual(bases[2], bases[3], names)
        self.assertNotEqual(bases[0], bases[2], names)

    def test_bilateral_rows_never_pick_a_unilateral_exercise(self):
        rows = [_row("Child Pose", seconds=30), _row("Cat Cow", seconds=30),
                _row("Forward Fold", seconds=30)]
        rotated = rotate_program(self._cooldown(rows), CATALOG_LR)
        for name in _names(rotated):
            self.assertIsNone(_suffix(name), name)

    def test_lone_unilateral_row_keeps_its_own_side(self):
        """Defensive: source data always pairs, but a lone L must stay an L."""
        rows = [_row("Calf Stretch L", seconds=30), _row("Child Pose", seconds=30)]
        rotated = rotate_program(self._cooldown(rows), CATALOG_LR)
        names = _names(rotated)
        self.assertEqual(_suffix(names[0]), " L", names)
        self.assertIsNone(_suffix(names[1]), names)

    def test_pairs_stay_paired_across_weeks_and_workouts(self):
        rows = [_row("Calf Stretch L", seconds=30), _row("Calf Stretch R", seconds=30)]
        rotated = rotate_program(self._cooldown(rows, workouts=3, weeks=4), CATALOG_LR)
        for week in range(4):
            for workout in range(3):
                names = _names(rotated, week=week, workout=workout)
                self.assertEqual(_suffix(names[0]), " L", names)
                self.assertEqual(_suffix(names[1]), " R", names)
                self.assertEqual(names[0][:-2], names[1][:-2], names)

    def test_pair_seed_follows_the_l_row_regardless_of_order(self):
        """L-first and R-first sections at the same coordinates resolve to the same base."""
        l_first = [_row("Calf Stretch L", seconds=30), _row("Calf Stretch R", seconds=30)]
        r_first = [_row("Calf Stretch R", seconds=30), _row("Calf Stretch L", seconds=30)]
        a = _names(rotate_program(self._cooldown(l_first), CATALOG_LR))
        b = _names(rotate_program(self._cooldown(r_first), CATALOG_LR))
        self.assertEqual(a[0][:-2], b[1][:-2], (a, b))

    def test_unilateral_row_left_alone_when_no_same_side_alternative(self):
        """A single L candidate means no rotation is possible: keep the original pair."""
        catalog = [c for c in CATALOG_LR if not c["name"].startswith(("Hamstring", "Quad"))]
        rows = [_row("Calf Stretch L", seconds=30), _row("Calf Stretch R", seconds=30)]
        rotated = rotate_program(self._cooldown(rows), catalog)
        self.assertEqual(_names(rotated), ["Calf Stretch L", "Calf Stretch R"])


if __name__ == "__main__":
    unittest.main()
