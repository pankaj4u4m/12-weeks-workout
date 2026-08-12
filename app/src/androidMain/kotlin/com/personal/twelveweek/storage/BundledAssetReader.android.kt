package com.personal.twelveweek.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class BundledAssetReader actual constructor() {
    actual suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            requireNotNull(AndroidPlatformContext.appContext) {
                "AndroidPlatformContext.install(context) must run before any BundledAssetReader is used."
            }.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}
