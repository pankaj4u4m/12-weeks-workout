package com.personal.twelveweek.media

import android.content.Context
import com.personal.twelveweek.Exercise
import com.personal.twelveweek.security.ApiKeyManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

class ExerciseMediaRepository(
    private val keyManager: ApiKeyManager,
    private val api: ExerciseDbApi
) {
    /**
     * Returns curated media/instructions for [exercise], or null when there's
     * no stored key, no curated `exerciseId` for this exercise, or the fetch
     * failed — callers render the existing fallback UI in every null case,
     * never crash.
     */
    suspend fun get(exercise: Exercise): ExerciseDbDetail? {
        val apiKey = keyManager.get() ?: return null
        val exerciseId = exercise.exerciseId ?: return null
        return when (val result = api.fetchExercise(exerciseId, apiKey)) {
            is ApiResult.Success -> result.detail
            ApiResult.Unauthorized -> {
                keyManager.clear()
                null
            }
            ApiResult.NetworkError -> null
        }
    }

    companion object {
        /** Composition-root factory: wires a disk-cached OkHttpClient so repeat
         *  views (across the 60 workouts sharing ~65 exercises) are instant/offline. */
        fun default(context: Context, keyManager: ApiKeyManager): ExerciseMediaRepository {
            val cacheDir = File(context.cacheDir, "exercise_media_http")
            val client = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 100L * 1024 * 1024))
                .build()
            return ExerciseMediaRepository(keyManager, ExerciseDbApi(client))
        }
    }
}
