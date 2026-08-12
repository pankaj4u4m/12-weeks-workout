package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Section
import com.personal.twelveweek.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedStepsTest {

    private fun workout() = Workout(
        programId = "program-1", week = 1, index = 1,
        sections = listOf(
            Section("Warm up", listOf(Exercise.parse("30 Jumping Jacks"))),
            Section("Round 1", listOf(Exercise.parse("20 Squats"), Exercise.parse("45s Wall Sit"))),
            Section("Cool Down", listOf(Exercise.parse("30s Cat Cow")))
        )
    )

    @Test
    fun `flattens all sections in order`() {
        val steps = workout().guidedSteps()
        assertEquals(4, steps.size)
        assertEquals("Warm up", steps[0].sectionTitle)
        assertEquals("Round 1", steps[1].sectionTitle)
        assertEquals("Squats", steps[1].exercise.name)
        assertEquals("Cool Down", steps[3].sectionTitle)
    }

    @Test
    fun `resumes at first incomplete step`() {
        val steps = workout().guidedSteps()
        val done = setOf(steps[0].key, steps[1].key)
        val idx = steps.firstIncompleteIndex { it in done }
        assertEquals(2, idx)
    }

    @Test
    fun `resumes at 0 when everything is done`() {
        val steps = workout().guidedSteps()
        val idx = steps.firstIncompleteIndex { true }
        assertEquals(0, idx)
    }
}
