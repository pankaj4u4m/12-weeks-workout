package com.personal.twelveweek.web

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Workout

/** One step in a linear guided-workout sequence — web-package copy of the
 *  Android app's `media.GuidedStep`/`guidedSteps()` (that file lives in
 *  `androidMain`, unreachable from wasmJsMain). */
data class WebGuidedStep(
    val sectionIndex: Int,
    val sectionTitle: String,
    val itemIndex: Int,
    val exercise: Exercise,
    val key: String
)

fun Workout.webGuidedSteps(): List<WebGuidedStep> = buildList {
    sections.forEachIndexed { s, section ->
        section.exercises.forEachIndexed { i, exercise ->
            add(WebGuidedStep(s, section.title, i, exercise, keyFor(s, i)))
        }
    }
}

fun List<WebGuidedStep>.firstIncompleteIndex(isDone: (String) -> Boolean): Int {
    val idx = indexOfFirst { !isDone(it.key) }
    return if (idx == -1) 0 else idx
}

fun formatClock(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) "$minutes:${remainder.toString().padStart(2, '0')}" else "$remainder"
}
