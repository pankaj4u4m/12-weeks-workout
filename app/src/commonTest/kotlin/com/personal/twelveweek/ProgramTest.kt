package com.personal.twelveweek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ProgramTest {

    private fun exercise(name: String, reps: Int? = null, seconds: Int? = null) =
        Exercise(raw = name, name = name, reps = reps, seconds = seconds)

    @Test
    fun `isTimed is true only when seconds is set`() {
        assertTrue(exercise("Plank", seconds = 30).isTimed)
        assertFalse(exercise("Squats", reps = 20).isTimed)
    }

    @Test
    fun `isRest matches Pause case-insensitively`() {
        assertTrue(exercise("Pause").isRest)
        assertTrue(exercise("pause").isRest)
        assertFalse(exercise("Squats").isRest)
    }

    @Test
    fun `workout keyFor namespaces by programId, week, index, section and item`() {
        val workout = Workout(
            programId = "program-1",
            week = 2,
            index = 3,
            sections = listOf(
                Section("Round 1", listOf(exercise("Squats", reps = 20))),
                Section("Round 2", listOf(exercise("Push Ups", reps = 10)))
            )
        )
        assertEquals("program-1:w2-o3-s0-i0", workout.keyFor(0, 0))
        assertEquals("program-1:w2-o3-s1-i0", workout.keyFor(1, 0))
    }

    @Test
    fun `workout allKeys covers every exercise across every section`() {
        val workout = Workout(
            programId = "program-1",
            week = 1,
            index = 1,
            sections = listOf(
                Section("Round 1", listOf(exercise("Squats", reps = 20), exercise("Pause", seconds = 30))),
                Section("Round 2", listOf(exercise("Push Ups", reps = 10)))
            )
        )
        assertEquals(3, workout.totalItems)
        assertEquals(3, workout.allKeys().size)
        assertEquals(workout.allKeys().toSet().size, workout.allKeys().size) // no duplicate keys
    }

    @Test
    fun `workout title is Day plus index`() {
        val workout = Workout(programId = "p", week = 1, index = 4, sections = emptyList())
        assertEquals("Day 4", workout.title)
    }
}
