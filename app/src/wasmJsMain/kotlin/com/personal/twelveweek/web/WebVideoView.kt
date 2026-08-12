package com.personal.twelveweek.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.js.ExperimentalWasmJsInterop

private var webVideoViewCounter = 0

/**
 * Real, inline, autoplay-loop-muted video — the wasmJs counterpart to the
 * Android app's `ExerciseVideoPlayer` (Media3 `ExoPlayer` in an
 * `AndroidView`). Compose Multiplatform's wasmJs target draws everything to
 * a single WebGL canvas, so there is no native "embed a video composable"
 * primitive; this creates a real HTML `<video>` element as a sibling of
 * that canvas and keeps it pinned, every frame the layout changes, over the
 * screen rect this composable occupies — the standard "DOM element
 * overlaying a canvas" technique. `pointer-events: none` on the video
 * element lets taps (e.g. the guided session's tap-to-pause-timer gesture)
 * pass straight through to the canvas underneath, exactly like the
 * static-image/photo-loop media pages already do.
 */
@Composable
fun WebVideoView(url: String, modifier: Modifier = Modifier) {
    val id = remember(url) { "tw-video-${webVideoViewCounter++}" }

    DisposableEffect(id, url) {
        jsCreateVideo(id, url)
        onDispose { jsRemoveVideo(id) }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val pos = coordinates.positionInWindow()
            val size = coordinates.size
            jsPositionVideo(id, pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
        }
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(id, url) => { try { var v = document.createElement('video'); v.id = id; v.src = url; v.autoplay = true; v.loop = true; v.muted = true; v.defaultMuted = true; v.playsInline = true; v.setAttribute('webkit-playsinline', 'true'); v.style.position = 'fixed'; v.style.objectFit = 'cover'; v.style.zIndex = '10'; v.style.pointerEvents = 'none'; v.style.borderRadius = '16px'; v.style.background = '#000'; v.style.left = '-9999px'; v.style.top = '-9999px'; v.style.width = '1px'; v.style.height = '1px'; document.body.appendChild(v); var p = v.play(); if (p && p.catch) p.catch(function () {}); } catch (e) {} }"
)
private external fun jsCreateVideo(id: String, url: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(id, x, y, w, h) => { try { var v = document.getElementById(id); if (!v) return; v.style.left = x + 'px'; v.style.top = y + 'px'; v.style.width = w + 'px'; v.style.height = h + 'px'; } catch (e) {} }"
)
private external fun jsPositionVideo(id: String, x: Float, y: Float, w: Float, h: Float)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(id) => { try { var v = document.getElementById(id); if (v) v.remove(); } catch (e) {} }")
private external fun jsRemoveVideo(id: String)
