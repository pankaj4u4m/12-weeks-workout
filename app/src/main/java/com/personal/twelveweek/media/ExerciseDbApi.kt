package com.personal.twelveweek.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

sealed interface ApiResult {
    data class Success(val detail: ExerciseDbDetail) : ApiResult
    data object Unauthorized : ApiResult
    data object NetworkError : ApiResult
}

/**
 * Thin client for the ExerciseDB v2 API (RapidAPI-gated). Host/header names
 * per docs.exercisedb.dev — confirm against live docs if RapidAPI changes them.
 */
class ExerciseDbApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://edb-with-videos-and-images-by-ascendapi.p.rapidapi.com"
) {
    suspend fun fetchExercise(exerciseId: String, apiKey: String): ApiResult =
        withContext(Dispatchers.IO) {
            // Path + "data"-wrapped response shape confirmed against the live API
            // (docs.exercisedb.dev's inline sample omits the {success,data} envelope).
            val request = Request.Builder()
                .url("$baseUrl/api/v1/exercises/$exerciseId")
                .header("X-RapidAPI-Key", apiKey)
                .header("X-RapidAPI-Host", "edb-with-videos-and-images-by-ascendapi.p.rapidapi.com")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 -> ApiResult.Unauthorized
                        !response.isSuccessful -> ApiResult.NetworkError
                        else -> {
                            val body = response.body?.string()
                            if (body.isNullOrBlank()) return@use ApiResult.NetworkError
                            val envelope = JSONObject(body)
                            if (!envelope.optBoolean("success", false) || !envelope.has("data")) {
                                ApiResult.NetworkError
                            } else {
                                ApiResult.Success(parseExerciseDbDetail(envelope.getJSONObject("data").toString()))
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                ApiResult.NetworkError
            } catch (e: JSONException) {
                ApiResult.NetworkError
            }
        }
}
