package com.personal.twelveweek.web

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.media.ApiResult
import com.personal.twelveweek.media.ExerciseDbApi
import com.personal.twelveweek.media.WgerApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Web port of the Android app's `media.ExerciseMediaRepository` — identical
 * priority order and provider logic, reusing the already-shared commonMain
 * [WgerApi]/[ExerciseDbApi] networking (Part 2 of the KMP migration) as-is.
 * Only the API-key lookup and the free-exercise-db URL builder are
 * web-package copies (the former because [WebApiKeyManager] is
 * callback-based, the latter for the same file-location reason as
 * [WebMediaPage]).
 */
class WebExerciseMediaRepository(
    private val keyManager: WebApiKeyManager,
    private val exerciseDbApi: ExerciseDbApi,
    private val wgerApi: WgerApi
) {
    suspend fun getBundle(exercise: Exercise): List<WebMediaPage> {
        val pages = mutableListOf<WebMediaPage>()

        val wgerDetail = exercise.wgerId?.let { wgerApi.fetchExercise(it) }
        wgerDetail?.videoUrl?.let { pages += WebMediaPage.Video(it, wgerDetail.instructions) }

        exercise.freeExerciseDbId?.let { id ->
            val urls = webFreeExerciseDbImageUrls(id)
            if (urls.isNotEmpty()) pages += WebMediaPage.ImageLoop(urls)
        }

        val apiKey = keyManager.getSuspend()
        if (apiKey != null) {
            exercise.exerciseDbId?.let { id ->
                when (val result = exerciseDbApi.fetchExercise(id, apiKey)) {
                    is ApiResult.Success -> {
                        val d = result.detail
                        when {
                            d.videoUrl != null -> pages += WebMediaPage.Video(d.videoUrl, d.instructions)
                            d.imageUrl != null -> pages += WebMediaPage.Image(d.imageUrl, d.instructions)
                        }
                    }
                    ApiResult.Unauthorized -> keyManager.clear()
                    ApiResult.NetworkError -> {}
                }
            }
        }

        wgerDetail?.imageUrl?.let { pages += WebMediaPage.Image(it, wgerDetail.instructions) }

        return pages
    }

    companion object {
        fun default(keyManager: WebApiKeyManager): WebExerciseMediaRepository {
            val client = HttpClient()
            return WebExerciseMediaRepository(keyManager, ExerciseDbApi(client), WgerApi(client))
        }
    }
}

private fun webFreeExerciseDbImageUrls(id: String): List<String> {
    val base = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises"
    return listOf("$base/$id/0.jpg", "$base/$id/1.jpg")
}

suspend fun WebApiKeyManager.getSuspend(): String? = suspendCancellableCoroutine { cont ->
    get { cont.resume(it) { _, _, _ -> } }
}
