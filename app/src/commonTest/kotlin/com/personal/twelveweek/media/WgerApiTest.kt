package com.personal.twelveweek.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WgerApiTest {

    private fun clientReturning(status: HttpStatusCode, body: String) = HttpClient(
        MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    )

    private fun wgerBody(hasVideo: Boolean, hasImage: Boolean) = """
        {"translations":[{"language":2,"name":"X","description":"<p>d</p>"}],
         "images":[${if (hasImage) """{"image":"https://wger.de/i.png"}""" else ""}],
         "videos":[${if (hasVideo) """{"video":"https://wger.de/v.mp4"}""" else ""}]}
    """.trimIndent()

    @Test
    fun `parses video and image, strips html from description`() = runTest {
        val api = WgerApi(clientReturning(HttpStatusCode.OK, wgerBody(hasVideo = true, hasImage = true)))
        val detail = api.fetchExercise("615")
        assertEquals("615", detail?.exerciseId)
        assertEquals("X", detail?.name)
        assertEquals("https://wger.de/v.mp4", detail?.videoUrl)
        assertEquals("https://wger.de/i.png", detail?.imageUrl)
        assertEquals(listOf("d"), detail?.instructions)
    }

    @Test
    fun `text-only entry (no images or videos) still parses name and instructions`() = runTest {
        val api = WgerApi(clientReturning(HttpStatusCode.OK, wgerBody(hasVideo = false, hasImage = false)))
        val detail = api.fetchExercise("591")
        assertEquals("X", detail?.name)
        assertNull(detail?.videoUrl)
        assertNull(detail?.imageUrl)
    }

    @Test
    fun `non-200 response returns null`() = runTest {
        val api = WgerApi(clientReturning(HttpStatusCode.NotFound, ""))
        assertNull(api.fetchExercise("999"))
    }

    @Test
    fun `structurally missing translations returns null instead of a blank result`() = runTest {
        val api = WgerApi(clientReturning(HttpStatusCode.OK, """{"images":[],"videos":[]}"""))
        assertNull(api.fetchExercise("1"))
    }
}
