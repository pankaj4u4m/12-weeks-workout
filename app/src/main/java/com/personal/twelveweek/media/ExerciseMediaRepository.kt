package com.personal.twelveweek.media

import android.content.Context
import com.personal.twelveweek.Exercise
import com.personal.twelveweek.security.ApiKeyManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

class ExerciseMediaRepository(
    private val keyManager: ApiKeyManager,
    private val exerciseDbApi: ExerciseDbApi,
    private val wgerApi: WgerApi
) {
    /**
     * Every curated media page available for [exercise], most-preferred
     * first — empty when there's nothing (callers render the existing
     * fallback UI). Fixed priority, but every page that actually exists is
     * included, not just the top pick, so the UI can let the user swipe
     * through the rest:
     *
     * 1. wger *video* only (free, no key) — wger's own image is deliberately
     *    NOT used here; it's demoted to priority 5 so a real video anywhere
     *    always wins over a static photo.
     * 2. [FreeExerciseDb] photo loop (free, no key, public domain) — exactly
     *    that source's own two step photos, never mixed with another
     *    provider's images.
     * 3. [Exercise.externalMediaUrl] — a one-off free hotlink (currently
     *    just "Jumping Jack" → a Wikimedia Commons GIF; see CREDITS.md),
     *    for exercises free-exercise-db has no entry for at all.
     * 4. ExerciseDB (RapidAPI, needs the user's own free key) — whichever it
     *    actually has, video or image.
     * 5. wger's static photo, as a last resort.
     */
    suspend fun getBundle(exercise: Exercise): List<MediaPage> {
        val pages = mutableListOf<MediaPage>()

        val wgerDetail = exercise.wgerId?.let { wgerApi.fetchExercise(it) }
        wgerDetail?.videoUrl?.let { pages += MediaPage.Video(it, wgerDetail.instructions) }

        exercise.freeExerciseDbId?.let { id ->
            val urls = FreeExerciseDb.imageUrls(id)
            if (urls.isNotEmpty()) pages += MediaPage.ImageLoop(urls)
        }

        exercise.externalMediaUrl?.let { url -> pages += MediaPage.Image(url) }

        val apiKey = keyManager.get()
        if (apiKey != null) {
            exercise.exerciseDbId?.let { id ->
                when (val result = exerciseDbApi.fetchExercise(id, apiKey)) {
                    is ApiResult.Success -> {
                        val d = result.detail
                        when {
                            d.videoUrl != null -> pages += MediaPage.Video(d.videoUrl, d.instructions)
                            d.imageUrl != null -> pages += MediaPage.Image(d.imageUrl, d.instructions)
                        }
                    }
                    ApiResult.Unauthorized -> keyManager.clear()
                    ApiResult.NetworkError -> {}
                }
            }
        }

        wgerDetail?.imageUrl?.let { pages += MediaPage.Image(it, wgerDetail.instructions) }

        return pages
    }

    companion object {
        /** Composition-root factory: wires a disk-cached OkHttpClient (shared
         *  by both providers) so repeat views are instant/offline. */
        fun default(context: Context, keyManager: ApiKeyManager): ExerciseMediaRepository {
            val cacheDir = File(context.cacheDir, "exercise_media_http")
            val okHttpClient = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 100L * 1024 * 1024))
                .build()
            val ktorClient = HttpClient(OkHttp) {
                engine { preconfigured = okHttpClient }
            }
            return ExerciseMediaRepository(keyManager, ExerciseDbApi(ktorClient), WgerApi(ktorClient))
        }
    }
}
