package com.personal.twelveweek.web

/**
 * One page of exercise-demo media — web-package copy of the Android app's
 * `media.MediaPage` (that file has zero Android dependencies but lives in
 * `androidMain`, unreachable from wasmJs). [Video] opens externally (no
 * inline autoplay video bridge yet); [ImageLoop]/[Image] render inline via
 * [WebAsyncImage].
 */
sealed interface WebMediaPage {
    data class Video(val url: String, val instructions: List<String> = emptyList()) : WebMediaPage
    data class ImageLoop(val urls: List<String>) : WebMediaPage
    data class Image(val url: String, val instructions: List<String> = emptyList()) : WebMediaPage
}

fun List<WebMediaPage>.primaryInstructions(): List<String> = firstNotNullOfOrNull { page ->
    when (page) {
        is WebMediaPage.Video -> page.instructions.takeIf { it.isNotEmpty() }
        is WebMediaPage.Image -> page.instructions.takeIf { it.isNotEmpty() }
        is WebMediaPage.ImageLoop -> null
    }
}.orEmpty()
