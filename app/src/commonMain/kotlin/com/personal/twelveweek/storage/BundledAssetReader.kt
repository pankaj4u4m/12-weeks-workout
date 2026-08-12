package com.personal.twelveweek.storage

/** Reads a file bundled with the app itself (Android: APK assets; wasmJs:
 *  static files served alongside the app). Returns null if not found. */
expect class BundledAssetReader() {
    suspend fun read(path: String): String?
}
