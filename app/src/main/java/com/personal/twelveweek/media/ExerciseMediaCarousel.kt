package com.personal.twelveweek.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Shows [pages] as a horizontally swipeable carousel, opening on the
 * highest-priority page (index 0) — video first, per
 * [ExerciseMediaRepository.getBundle]. Renders nothing for an empty list;
 * callers keep their own fallback UI for that case, same as before this
 * existed. Dot indicators only appear when there's more than one page, so a
 * single-source exercise looks exactly like it always did.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseMediaCarousel(
    pages: List<MediaPage>,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            when (val page = pages[pageIndex]) {
                is MediaPage.Video -> ExerciseVideoPlayer(
                    videoUrl = page.url,
                    imageUrl = null,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
                is MediaPage.ImageLoop -> ImageLoopPlayer(
                    urls = page.urls,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
                is MediaPage.Image -> AsyncImage(
                    model = page.url,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                    .spacedBy(5.dp, Alignment.CenterHorizontally)
            ) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}
