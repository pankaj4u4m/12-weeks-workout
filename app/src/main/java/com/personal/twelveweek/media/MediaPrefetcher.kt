package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.programs.LibraryProgram
import kotlinx.coroutines.delay

/** Every distinct, non-rest exercise across [this] program's full set of
 *  weeks — the set worth warming the media cache for. Deduplicated by
 *  [Exercise.slug] so a name reused across dozens of weeks/workouts is
 *  only fetched once. */
fun LibraryProgram.distinctPrefetchableExercises(): List<Exercise> =
    weeks
        .flatMap { week -> week.workouts.flatMap { it.sections.flatMap { s -> s.exercises } } }
        .filterNot { it.isRest }
        .distinctBy { it.slug }

/**
 * Silently warms [repository]'s disk cache for every distinct exercise in
 * [program], throttled by [throttleMillis] between requests to stay well
 * inside RapidAPI's free-tier rate limit. A plain suspend function —
 * callers run it inside `LaunchedEffect(program.meta.id)` so switching
 * programs cancels an in-flight prefetch and starts a fresh one for free
 * (Compose cancels the old coroutine when the effect's key changes). Never
 * throws: a failed fetch for one exercise just leaves that one to fall
 * back to the existing on-demand fetch when actually viewed.
 */
suspend fun prefetchProgramMedia(
    program: LibraryProgram,
    repository: ExerciseMediaRepository,
    throttleMillis: Long = 400L
) {
    for (exercise in program.distinctPrefetchableExercises()) {
        runCatching { repository.getBundle(exercise) }
        delay(throttleMillis)
    }
}
