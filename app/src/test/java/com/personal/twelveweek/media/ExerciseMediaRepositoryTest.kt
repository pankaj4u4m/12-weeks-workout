package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.security.ApiKeyManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExerciseMediaRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ExerciseDbApi
    private val unmapped = Exercise.parse("30s Child Pose") // no exerciseId set — Exercise.parse never sets one

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ExerciseDbApi(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun fakeKeyManager(key: String?): ApiKeyManager {
        val m = mockk<ApiKeyManager>(relaxed = true)
        every { m.get() } returns key
        return m
    }

    @Test
    fun `no stored key returns null without a network call`() = runBlocking {
        val repo = ExerciseMediaRepository(fakeKeyManager(null), api)
        assertNull(repo.get(unmapped))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `exercise with no curated exerciseId returns null without a network call`() = runBlocking {
        val repo = ExerciseMediaRepository(fakeKeyManager("key"), api)
        assertNull(repo.get(unmapped))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `curated exercise fetches via api and returns detail`() = runBlocking {
        server.enqueue(
            okhttp3.mockwebserver.MockResponse().setBody(
                """{"success":true,"data":{"exerciseId":"exr_41n2hmGR8WuVfe1U","name":"Squat"}}"""
            )
        )
        val repo = ExerciseMediaRepository(fakeKeyManager("key"), api)
        val curated = Exercise.parse("20 Squats").copy(exerciseId = "exr_41n2hmGR8WuVfe1U")
        val detail = repo.get(curated)
        assertEquals("Squat", detail?.name)
        assertEquals(1, server.requestCount)
    }
}
