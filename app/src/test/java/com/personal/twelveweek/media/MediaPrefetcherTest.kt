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
