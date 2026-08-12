package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.security.ApiKeyManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseMediaRepositoryTest {

    private lateinit var wgerServer: MockWebServer
    private lateinit var exerciseDbServer: MockWebServer
    private lateinit var wgerApi: WgerApi
    private lateinit var exerciseDbApi: ExerciseDbApi

    private val noIds = Exercise.parse("30s Child Pose") // all three ids null

    @Before
    fun setUp() {
        wgerServer = MockWebServer()
        wgerServer.start()
        wgerApi = WgerApi(HttpClient(OkHttp), baseUrl = wgerServer.url("/").toString().trimEnd('/'))

        exerciseDbServer = MockWebServer()
        exerciseDbServer.start()
        exerciseDbApi = ExerciseDbApi(HttpClient(OkHttp), baseUrl = exerciseDbServer.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        wgerServer.shutdown()
        exerciseDbServer.shutdown()
    }

    private fun fakeKeyManager(key: String?): ApiKeyManager {
        val m = mockk<ApiKeyManager>(relaxed = true)
        every { m.get() } returns key
        return m
    }

    private fun repo(key: String?) =
        ExerciseMediaRepository(fakeKeyManager(key), exerciseDbApi, wgerApi)

    private fun wgerBody(hasVideo: Boolean, hasImage: Boolean) = """
        {"id":1,"translations":[{"language":2,"name":"X","description":"<p>d</p>"}],
         "images":[${if (hasImage) """{"image":"https://wger.de/i.png"}""" else ""}],
         "videos":[${if (hasVideo) """{"video":"https://wger.de/v.mp4"}""" else ""}]}
    """.trimIndent()

    @Test
    fun `exercise with no ids returns empty bundle, no network calls at all`() = runBlocking {
        assertEquals(emptyList<MediaPage>(), repo("key").getBundle(noIds))
        assertEquals(0, wgerServer.requestCount)
        assertEquals(0, exerciseDbServer.requestCount)
    }

    @Test
    fun `freeExerciseDbId alone produces an image loop with no network call`() = runBlocking {
        val exercise = Exercise.parse("20 Squats").copy(freeExerciseDbId = "Mountain_Climbers")
        val bundle = repo(null).getBundle(exercise)
        assertEquals(1, bundle.size)
        val page = bundle[0] as MediaPage.ImageLoop
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Mountain_Climbers/0.jpg",
                "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Mountain_Climbers/1.jpg"
            ),
            page.urls
        )
        assertEquals(0, wgerServer.requestCount)
        assertEquals(0, exerciseDbServer.requestCount)
    }

    @Test
    fun `full priority order - wger video, free-exercise-db loop, exerciseDb, wger image last`() = runBlocking {
        wgerServer.enqueue(MockResponse().setBody(wgerBody(hasVideo = true, hasImage = true)))
        exerciseDbServer.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{"exerciseId":"exr_x","name":"Squat","videoUrl":"https://edb/v.mp4"}}"""
            )
        )
        val exercise = Exercise.parse("20 Squats").copy(
            wgerId = "615", freeExerciseDbId = "Squats", exerciseDbId = "exr_x"
        )
        val bundle = repo("key").getBundle(exercise)
        assertEquals(4, bundle.size)
        assertEquals("https://wger.de/v.mp4", (bundle[0] as MediaPage.Video).url)
        assertTrue(bundle[1] is MediaPage.ImageLoop)
        assertEquals("https://edb/v.mp4", (bundle[2] as MediaPage.Video).url)
        assertEquals("https://wger.de/i.png", (bundle[3] as MediaPage.Image).url)
    }

    @Test
    fun `wger text-only entry contributes nothing, exerciseDb still tried`() = runBlocking {
        wgerServer.enqueue(MockResponse().setBody(wgerBody(hasVideo = false, hasImage = false)))
        exerciseDbServer.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{"exerciseId":"exr_x","name":"Squat","imageUrl":"https://edb/i.png"}}"""
            )
        )
        val exercise = Exercise.parse("20 Squats").copy(wgerId = "591", exerciseDbId = "exr_x")
        val bundle = repo("key").getBundle(exercise)
        assertEquals(1, bundle.size)
        assertEquals("https://edb/i.png", (bundle[0] as MediaPage.Image).url)
        assertEquals(1, wgerServer.requestCount)
        assertEquals(1, exerciseDbServer.requestCount)
    }

    @Test
    fun `no stored key skips exerciseDb entirely, no network call to it`() = runBlocking {
        wgerServer.enqueue(MockResponse().setBody(wgerBody(hasVideo = true, hasImage = false)))
        val exercise = Exercise.parse("20 Squats").copy(wgerId = "615", exerciseDbId = "exr_x")
        val bundle = repo(null).getBundle(exercise)
        assertEquals(1, bundle.size)
        assertTrue(bundle[0] is MediaPage.Video)
        assertEquals(0, exerciseDbServer.requestCount)
    }
}
