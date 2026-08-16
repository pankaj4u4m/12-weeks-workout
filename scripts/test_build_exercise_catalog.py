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
