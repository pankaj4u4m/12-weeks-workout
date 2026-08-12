package com.personal.twelveweek.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Web port of the Android app's `media.ExerciseMediaCarousel` — same
 * horizontal-pager-of-pages shape with dot indicators. [WebMediaPage.Video]
 * plays inline via [WebVideoView] (a real HTML `<video>` overlaid on the
 * canvas), muted/looped/autoplaying, matching the Android app's ExoPlayer
 * behavior.
 */
@Composable
fun WebExerciseMediaCarousel(pages: List<WebMediaPage>, contentDescription: String, modifier: Modifier = Modifier) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (val p = pages[page]) {
                is WebMediaPage.Image -> WebAsyncImage(
                    url = p.url,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                is WebMediaPage.ImageLoop -> WebImageLoopPlayer(
                    urls = p.urls,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
                is WebMediaPage.Video -> WebVideoView(url = p.url, modifier = Modifier.fillMaxSize())
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun WebImageLoopPlayer(urls: List<String>, contentDescription: String, modifier: Modifier = Modifier) {
    var index by remember(urls) { mutableIntStateOf(0) }
    LaunchedEffect(urls) {
        if (urls.size <= 1) return@LaunchedEffect
        while (true) {
            delay(700)
            index = (index + 1) % urls.size
        }
    }
    WebAsyncImage(
        url = urls[index.coerceIn(urls.indices)],
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

