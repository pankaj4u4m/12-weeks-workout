package com.personal.twelveweek.media

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseDbApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ExerciseDbApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ExerciseDbApi(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `success response parses to Success`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"data":{"exerciseId":"e1","name":"Squat"}}""")
        )
        val result = api.fetchExercise("e1", "fake-key")
        assertTrue(result is ApiResult.Success)
        assertEquals("Squat", (result as ApiResult.Success).detail.name)
    }

    @Test
    fun `success=false envelope maps to NetworkError`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":false}"""))
        val result = api.fetchExercise("e1", "fake-key")
        assertEquals(ApiResult.NetworkError, result)
    }

    @Test
    fun `401 maps to Unauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.fetchExercise("e1", "bad-key")
        assertEquals(ApiResult.Unauthorized, result)
    }

    @Test
    fun `500 maps to NetworkError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = api.fetchExercise("e1", "fake-key")
        assertEquals(ApiResult.NetworkError, result)
    }
}
