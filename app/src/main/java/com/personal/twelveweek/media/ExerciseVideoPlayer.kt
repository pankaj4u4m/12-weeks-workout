package com.personal.twelveweek.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * Ambient exercise demo: plays a muted, looping video if [videoUrl] is given,
 * otherwise falls back to a static [imageUrl]. Renders nothing if both are
 * null — callers render their own fallback UI in that case.
 */
@Composable
fun ExerciseVideoPlayer(
    videoUrl: String?,
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    when {
        videoUrl != null -> {
            val context = LocalContext.current
            val player = remember(videoUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        this.player = player
                    }
                }
            )
        }
        imageUrl != null -> AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    }
}
