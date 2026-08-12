package com.personal.twelveweek.storage

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.browser.window

actual class BundledAssetReader actual constructor() {
    private val client = HttpClient()

    // Ktor's URL parser resolves a bare relative path ("programs/index.json")
    // against the origin ROOT, not the current page's own path — unlike a
    // native browser fetch(), which resolves relative URLs against the
    // document's own location. GitHub Pages serves this app from a subpath
    // (`/12-weeks-workout/`), so without this the request 404s against
    // the origin root instead of hitting the real asset next to index.html.
    private val baseHref: String get() = window.location.href.substringBeforeLast('/') + "/"

    actual suspend fun read(path: String): String? = runCatching {
        val response = client.get(baseHref + path)
        if (!response.status.isSuccess()) return@runCatching null
        response.bodyAsText()
    }.getOrNull()
}
