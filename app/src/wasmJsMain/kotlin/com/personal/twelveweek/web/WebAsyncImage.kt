package com.personal.twelveweek.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.js.ExperimentalWasmJsInterop

private var webAsyncImageCounter = 0

/**
 * Renders [url] as a real HTML `<img>` element pinned over the wasmJs
 * canvas — the same DOM-overlay technique [WebVideoView] uses for video,
 * and for the same reason: fetching exercise-demo images through Ktor
 * (`HttpClient.get`, as this used to) enforces the browser's CORS policy,
 * and ExerciseDB's image CDN (`cdn.exercisedb.dev`) sends no
 * `Access-Control-Allow-Origin` header, so every programmatic fetch of
 * those images was silently rejected by the browser — the image just
 * never appeared. free-exercise-db's images (raw.githubusercontent.com)
 * happened to work because that host *does* send the header. An
 * `<img src>` load, unlike `fetch`, is never subject to CORS for plain
 * display, so this sidesteps the problem entirely instead of depending on
 * every media host cooperating. [jsSetImgSrc] preloads off-DOM before
 * swapping the visible `src` so cycling through free-exercise-db's photo
 * loop repaints instantly instead of flashing blank between frames.
 */
@Composable
fun WebAsyncImage(url: String, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val id = remember { "tw-img-${webAsyncImageCounter++}" }
    val objectFit = if (contentScale === ContentScale.Crop) "cover" else "contain"

    DisposableEffect(id, objectFit) {
        jsCreateImg(id, objectFit)
        onDispose { jsRemoveImg(id) }
    }

    LaunchedEffect(id, url, contentDescription) { jsSetImgSrc(id, url, contentDescription ?: "") }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val pos = coordinates.positionInWindow()
            val size = coordinates.size
            jsPositionImg(id, pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
        }
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(id, fit) => { try { var img = document.createElement('img'); img.id = id; img.style.objectFit = fit; img.style.position = 'fixed'; img.style.zIndex = '10'; img.style.pointerEvents = 'none'; img.style.borderRadius = '16px'; img.style.left = '-9999px'; img.style.top = '-9999px'; img.style.width = '1px'; img.style.height = '1px'; img.onerror = function () { img.style.visibility = 'hidden'; }; img.onload = function () { img.style.visibility = 'visible'; }; document.body.appendChild(img); } catch (e) {} }"
)
private external fun jsCreateImg(id: String, fit: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(id, url, alt) => { try { var img = document.getElementById(id); if (!img) return; if (img.src === url) return; var pre = new Image(); var swap = function () { var live = document.getElementById(id); if (!live) return; live.src = url; live.alt = alt; }; pre.onload = swap; pre.onerror = swap; pre.src = url; } catch (e) {} }"
)
private external fun jsSetImgSrc(id: String, url: String, alt: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(id, x, y, w, h) => { try { var img = document.getElementById(id); if (!img) return; var dpr = window.devicePixelRatio || 1; img.style.left = (x / dpr) + 'px'; img.style.top = (y / dpr) + 'px'; img.style.width = (w / dpr) + 'px'; img.style.height = (h / dpr) + 'px'; } catch (e) {} }"
)
private external fun jsPositionImg(id: String, x: Float, y: Float, w: Float, h: Float)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(id) => { try { var img = document.getElementById(id); if (img) img.remove(); } catch (e) {} }")
private external fun jsRemoveImg(id: String)
