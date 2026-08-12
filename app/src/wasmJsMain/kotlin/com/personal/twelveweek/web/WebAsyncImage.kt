package com.personal.twelveweek.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.jetbrains.skia.Image as SkiaImage

private val sharedImageClient by lazy { HttpClient() }

/**
 * Fetches [url] and decodes it as a bitmap — the wasmJs equivalent of the
 * Android app's Coil `AsyncImage` (Coil has no Compose Multiplatform/wasmJs
 * artifact). Decoding goes through Skia, which already ships with Compose
 * Multiplatform's wasmJs target, so no new dependency is needed. Cached per
 * composition only (no disk/memory cache across page loads — acceptable
 * for exercise-demo images, which are viewed once per session).
 */
@Composable
fun WebAsyncImage(url: String, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        bitmap = null
        failed = false
        runCatching {
            val response = sharedImageClient.get(url)
            if (!response.status.isSuccess()) {
                failed = true
                return@runCatching
            }
            val bytes = response.bodyAsChannel().readRemaining().readByteArray()
            bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.onFailure { failed = true }
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        failed -> Box(modifier) {}
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier, color = MaterialTheme.colorScheme.primary)
        }
    }
}
