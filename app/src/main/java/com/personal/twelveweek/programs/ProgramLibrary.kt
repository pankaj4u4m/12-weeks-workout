package com.personal.twelveweek.programs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the program library: a synced-from-GitHub cache if present (fresher),
 * else the bundled assets (under `assets/programs/`) that ship in the APK so
 * the app works fully offline on first launch, before any sync ever runs.
 */
class ProgramLibrary(private val context: Context) {

    private val cacheDir = File(context.filesDir, "programs_cache")

    private fun readCacheOrAsset(cacheName: String, assetPath: String): String? {
        val cached = File(cacheDir, cacheName)
        if (cached.exists()) {
            runCatching { cached.readText() }.getOrNull()?.let { return it }
        }
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    suspend fun index(): List<IndexEntry> = withContext(Dispatchers.IO) {
        val json = readCacheOrAsset("index.json", "programs/index.json") ?: return@withContext emptyList()
        runCatching { parseIndex(json) }.getOrDefault(emptyList())
    }

    suspend fun load(id: String): LibraryProgram? = withContext(Dispatchers.IO) {
        val json = readCacheOrAsset("$id.json", "programs/$id.json") ?: return@withContext null
        runCatching { parseProgram(json) }.getOrNull()
    }

    /** Used by [ProgramSyncRepository] to drop freshly-fetched files in place. */
    fun writeCache(name: String, content: String) {
        runCatching {
            cacheDir.mkdirs()
            File(cacheDir, name).writeText(content)
        }
    }
}
