package com.personal.twelveweek.media

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface ApiResult {
    data class Success(val detail: ExerciseDbDetail) : ApiResult
    data object Unauthorized : ApiResult
    data object NetworkError : ApiResult
}

@Serializable
private data class ExerciseDbEnvelope(
    val success: Boolean = false,
    val data: JsonObject? = null
)

private val envelopeJson = Json { ignoreUnknownKeys = true }

/**
 * Thin client for the ExerciseDB v2 API (RapidAPI-gated). Host/header names
 * per docs.exercisedb.dev — confirm against live docs if RapidAPI changes them.
 */
class ExerciseDbApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://edb-with-videos-and-images-by-ascendapi.p.rapidapi.com"
) {
    suspend fun fetchExercise(exerciseId: String, apiKey: String): ApiResult =
        withContext(Dispatchers.Default) {
            runCatching {
                val response = client.get("$baseUrl/api/v1/exercises/$exerciseId") {
                    header("X-RapidAPI-Key", apiKey)
                    header("X-RapidAPI-Host", "edb-with-videos-and-images-by-ascendapi.p.rapidapi.com")
                }
                when {
                    response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                        ApiResult.Unauthorized
                    !response.status.isSuccess() -> ApiResult.NetworkError
                    else -> {
                        val body = response.bodyAsText()
                        if (body.isBlank()) return@runCatching ApiResult.NetworkError
                        // Path + "data"-wrapped response shape confirmed against the live API
                        // (docs.exercisedb.dev's inline sample omits the {success,data} envelope).
                        val envelope = envelopeJson.decodeFromString(ExerciseDbEnvelope.serializer(), body)
                        val data = envelope.data
                        if (!envelope.success || data == null) {
                            ApiResult.NetworkError
                        } else {
                            ApiResult.Success(parseExerciseDbDetail(data.toString()))
                        }
                    }
                }
            }.getOrDefault(ApiResult.NetworkError)
        }
}
