package com.personal.twelveweek.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Cycles through [urls] as a manual low-fps "loop" — a flipbook, not a real
 * video — for sources (free-exercise-db) that only ship a couple of static
 * step photos per exercise. Fits the whole frame rather than cropping
 * (unlike the video player) so a step photo never loses its edges.
 */
@Composable
fun ImageLoopPlayer(
    urls: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    frameDelayMs: Long = 700
) {
    var index by remember(urls) { mutableIntStateOf(0) }
    LaunchedEffect(urls) {
        if (urls.size <= 1) return@LaunchedEffect
        while (true) {
            delay(frameDelayMs)
            index = (index + 1) % urls.size
        }
    }
    if (urls.isEmpty()) return
    AsyncImage(
        model = urls[index],
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize()
    )
}
