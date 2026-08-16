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
