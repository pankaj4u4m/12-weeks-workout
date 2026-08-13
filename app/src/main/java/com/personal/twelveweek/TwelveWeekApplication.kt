package com.personal.twelveweek

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * Registers GIF decoding (the platform `ImageDecoder` on API 28+, the
 * legacy `Movie`-based decoder below that) on the app's default Coil
 * [ImageLoader]. Coil's core artifact only decodes static images out of
 * the box; without this, an animated exercise-demo GIF (e.g. the
 * Wikimedia Commons one wired up via [Exercise.externalMediaUrl]) would
 * just freeze on its first frame in [coil.compose.AsyncImage].
 */
class TwelveWeekApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
