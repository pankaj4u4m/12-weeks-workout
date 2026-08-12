package com.personal.twelveweek.media

/**
 * One swipeable page of an exercise's media carousel. Order is fixed by
 * [ExerciseMediaRepository.getBundle] to the app's priority — real video
 * first, then the free-exercise-db photo loop, then ExerciseDB (video or
 * image, whichever it actually has), then wger's own static photo last —
 * but the user can swipe sideways through whichever pages exist for a given
 * exercise; this list is exactly "whichever pages exist," nothing hidden.
 */
sealed interface MediaPage {
    data class Video(val url: String, val instructions: List<String> = emptyList()) : MediaPage
    data class ImageLoop(val urls: List<String>) : MediaPage
    data class Image(val url: String, val instructions: List<String> = emptyList()) : MediaPage
}

/** First non-empty instruction list across the bundle — used by callers that
 *  show one "how to" block below the carousel rather than per-page. */
fun List<MediaPage>.primaryInstructions(): List<String> =
    firstNotNullOfOrNull { page ->
        when (page) {
            is MediaPage.Video -> page.instructions.takeIf { it.isNotEmpty() }
            is MediaPage.Image -> page.instructions.takeIf { it.isNotEmpty() }
            is MediaPage.ImageLoop -> null
        }
    } ?: emptyList()
