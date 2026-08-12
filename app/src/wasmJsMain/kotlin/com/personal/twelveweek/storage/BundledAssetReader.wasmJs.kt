package com.personal.twelveweek.storage

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

actual class BundledAssetReader actual constructor() {
    private val client = HttpClient()

    actual suspend fun read(path: String): String? = runCatching {
        val response = client.get(path)
        if (!response.status.isSuccess()) return@runCatching null
        response.bodyAsText()
    }.getOrNull()
}
