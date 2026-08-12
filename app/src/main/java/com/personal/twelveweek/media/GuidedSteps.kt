package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Workout

/** One step in a linear guided-workout sequence. */
data class GuidedStep(
    val sectionIndex: Int,
    val sectionTitle: String,
    val itemIndex: Int,
    val exercise: Exercise,
    val key: String
)

/** Flattens Warm up → Round 1..N → Cool down into one ordered list. */
fun Workout.guidedSteps(): List<GuidedStep> = buildList {
    sections.forEachIndexed { s, section ->
        section.exercises.forEachIndexed { i, exercise ->
            add(GuidedStep(s, section.title, i, exercise, keyFor(s, i)))
        }
    }
}

/** Index of the first not-yet-done step, or 0 if everything is done. */
fun List<GuidedStep>.firstIncompleteIndex(isDone: (String) -> Boolean): Int {
    val idx = indexOfFirst { !isDone(it.key) }
    return if (idx == -1) 0 else idx
}
