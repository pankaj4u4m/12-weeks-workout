package com.personal.twelveweek.programs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProgramJsonTest {

    @Test
    fun `parses index entries`() {
        val json = """
            {"programs": [
              {"id":"program-1","title":"12 Week Full Body","level":"INTERMEDIATE",
               "focusAreas":["FULL_BODY"],"equipment":["HOME"],"weeks":12,
               "file":"programs/program-1.json"}
            ]}
        """.trimIndent()

        val entries = parseIndex(json)
        assertEquals(1, entries.size)
        val meta = entries[0].meta
        assertEquals("program-1", meta.id)
        assertEquals(ProgramLevel.INTERMEDIATE, meta.level)
        assertEquals(listOf(FocusArea.FULL_BODY), meta.focusAreas)
        assertEquals(listOf(Equipment.HOME), meta.equipment)
        assertEquals(12, meta.weekCount)
        assertEquals("programs/program-1.json", entries[0].file)
    }

    @Test
    fun `unknown level falls back to intermediate, unknown enum values are dropped`() {
        val json = """
            {"programs": [
              {"id":"x","title":"X","level":"NIGHTMARE",
               "focusAreas":["FULL_BODY","CARDIO"],"equipment":[],"weeks":1,"file":"programs/x.json"}
            ]}
        """.trimIndent()

        val meta = parseIndex(json)[0].meta
        assertEquals(ProgramLevel.INTERMEDIATE, meta.level)
        assertEquals(listOf(FocusArea.FULL_BODY), meta.focusAreas)
    }

    @Test
    fun `parses a full program with reps, seconds and curated wger + exerciseDb ids`() {
        val json = """
            {"id":"program-1","title":"12 Week Full Body","level":"INTERMEDIATE",
             "focusAreas":["FULL_BODY"],"equipment":["HOME"],
             "weeks":[
               {"number":1,"workouts":[
                 {"index":1,"sections":[
                   {"title":"Round 1","exercises":[
                     {"raw":"20 Squats","name":"Squats","reps":20,"seconds":null,"wgerId":"615","exerciseDbId":"exr_41n2hmGR8WuVfe1U"},
                     {"raw":"30s Pause","name":"Pause","reps":null,"seconds":30,"wgerId":null,"exerciseDbId":null}
                   ]}
                 ]}
               ]}
             ]}
        """.trimIndent()

        val program = parseProgram(json)
        assertEquals("program-1", program.meta.id)
        assertEquals(1, program.meta.weekCount)

        val workout = program.weeks[0].workouts[0]
        assertEquals("program-1", workout.programId)
        assertEquals("program-1:w1-o1-s0-i0", workout.keyFor(0, 0))

        val squats = workout.sections[0].exercises[0]
        assertEquals(20, squats.reps)
        assertNull(squats.seconds)
        assertEquals("615", squats.wgerId)
        assertEquals("exr_41n2hmGR8WuVfe1U", squats.exerciseDbId)

        val pause = workout.sections[0].exercises[1]
        assertEquals(30, pause.seconds)
        assertNull(pause.wgerId)
        assertNull(pause.exerciseDbId)
        assertEquals(true, pause.isRest)
    }

    @Test
    fun `parses a real excerpt of the bundled program-1_json asset`() {
        // Verbatim excerpt of app/src/main/assets/programs/program-1.json:
        // top-level metadata plus week 1 / workout 1 / "Warm up" section's
        // first 3 exercises, exactly as authored (the first exercise has
        // real wgerId + exerciseDbId + freeExerciseDbId values).
        val json = """
            {"id": "program-1", "title": "12 Week Full Body", "level": "INTERMEDIATE", "focusAreas": ["FULL_BODY"], "equipment": ["HOME"], "weeks": [{"number": 1, "workouts": [{"index": 1, "sections": [{"title": "Warm up", "exercises": [{"raw": "30s Arm Circle Forward", "name": "Arm Circle Forward", "reps": null, "seconds": 30, "exerciseDbId": "exr_41n2hUBVSgXaKhau", "wgerId": "994", "freeExerciseDbId": "Arm_Circles"}, {"raw": "30s Arm Circle Backward", "name": "Arm Circle Backward", "reps": null, "seconds": 30, "exerciseDbId": "exr_41n2hUBVSgXaKhau", "wgerId": "995", "freeExerciseDbId": "Arm_Circles"}, {"raw": "30 Jumping Jacks", "name": "Jumping Jacks", "reps": 30, "seconds": null, "exerciseDbId": "exr_41n2hupxPcdnktBC", "wgerId": "320", "freeExerciseDbId": null}]}], "estimatedMinutes": 28}]}], "sessionMinutes": 35}
        """.trimIndent()

        val program = parseProgram(json)
        assertEquals("program-1", program.meta.id)
        assertEquals("12 Week Full Body", program.meta.title)
        assertEquals(ProgramLevel.INTERMEDIATE, program.meta.level)
        assertEquals(listOf(FocusArea.FULL_BODY), program.meta.focusAreas)
        assertEquals(listOf(Equipment.HOME), program.meta.equipment)
        assertEquals(35, program.meta.sessionMinutes)
        assertEquals(1, program.weeks.size)

        val workout = program.weeks[0].workouts[0]
        assertEquals(28, workout.estimatedMinutes)
        val warmUp = workout.sections[0]
        assertEquals("Warm up", warmUp.title)
        assertEquals(3, warmUp.exercises.size)

        val armCircleForward = warmUp.exercises[0]
        assertEquals("Arm Circle Forward", armCircleForward.name)
        assertNull(armCircleForward.reps)
        assertEquals(30, armCircleForward.seconds)
        assertEquals("exr_41n2hUBVSgXaKhau", armCircleForward.exerciseDbId)
        assertEquals("994", armCircleForward.wgerId)
        assertEquals("Arm_Circles", armCircleForward.freeExerciseDbId)

        val jumpingJacks = warmUp.exercises[2]
        assertEquals("Jumping Jacks", jumpingJacks.name)
        assertEquals(30, jumpingJacks.reps)
        assertNull(jumpingJacks.seconds)
        assertEquals("320", jumpingJacks.wgerId)
        assertNull(jumpingJacks.freeExerciseDbId)
    }

    @Test
    fun `program json missing the weeks key entirely throws instead of silently defaulting to empty`() {
        val json = """{"id":"x","title":"X"}"""
        assertFailsWith<kotlinx.serialization.SerializationException> { parseProgram(json) }
    }
}
