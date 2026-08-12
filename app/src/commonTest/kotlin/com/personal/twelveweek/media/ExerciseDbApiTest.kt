package com.personal.twelveweek.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExerciseDbApiTest {

    private fun clientReturning(status: HttpStatusCode, body: String) = HttpClient(
        MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    )

    @Test
    fun `success response parses to Success`() = runTest {
        val api = ExerciseDbApi(
            clientReturning(HttpStatusCode.OK, """{"success":true,"data":{"exerciseId":"e1","name":"Squat"}}""")
        )
        val result = api.fetchExercise("e1", "fake-key")
        assertTrue(result is ApiResult.Success)
        assertEquals("Squat", (result as ApiResult.Success).detail.name)
    }

    @Test
    fun `success=false envelope maps to NetworkError`() = runTest {
        val api = ExerciseDbApi(clientReturning(HttpStatusCode.OK, """{"success":false}"""))
        val result = api.fetchExercise("e1", "fake-key")
        assertEquals(ApiResult.NetworkError, result)
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val api = ExerciseDbApi(clientReturning(HttpStatusCode.Unauthorized, ""))
        val result = api.fetchExercise("e1", "bad-key")
        assertEquals(ApiResult.Unauthorized, result)
    }

    @Test
    fun `500 maps to NetworkError`() = runTest {
        val api = ExerciseDbApi(clientReturning(HttpStatusCode.InternalServerError, ""))
        val result = api.fetchExercise("e1", "fake-key")
        assertEquals(ApiResult.NetworkError, result)
    }

    @Test
    fun `request targets the exercise path and carries RapidAPI auth headers`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = HttpClient(
            MockEngine { request ->
                capturedRequest = request
                respond(
                    content = """{"success":true,"data":{"exerciseId":"e1","name":"Squat"}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        val api = ExerciseDbApi(client)
        api.fetchExercise("e1", "fake-key")

        val request = assertNotNull(capturedRequest)
        assertEquals("/api/v1/exercises/e1", request.url.encodedPath)
        assertEquals("fake-key", request.headers["X-RapidAPI-Key"])
        assertEquals(
            "edb-with-videos-and-images-by-ascendapi.p.rapidapi.com",
            request.headers["X-RapidAPI-Host"]
        )
    }
}
