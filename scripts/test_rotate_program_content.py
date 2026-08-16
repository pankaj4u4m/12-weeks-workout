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
