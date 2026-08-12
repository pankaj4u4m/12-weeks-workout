package com.personal.twelveweek.programs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Pulls the program library from the public GitHub repo
 * (`pankaj4u4m/12-weeks-exercise-app`) over plain HTTPS via
 * raw.githubusercontent.com — the repo is public, so no auth token is needed.
 * Runs once on every app launch (see AppRoot); best-effort and silent on
 * failure — offline or a dead network just means the last-synced (or
 * bundled-asset) copy keeps being used, never a crash or a blocking spinner.
 */
class ProgramSyncRepository(
    private val client: OkHttpClient,
    private val library: ProgramLibrary,
    private val baseRawUrl: String =
        "https://raw.githubusercontent.com/pankaj4u4m/12-weeks-exercise-app/main"
) {
    suspend fun sync() = withContext(Dispatchers.IO) {
        val indexJson = fetch("$baseRawUrl/programs/index.json") ?: return@withContext
        val entries = runCatching { parseIndex(indexJson) }.getOrDefault(emptyList())
        if (entries.isEmpty()) return@withContext
        // Only replace the cached index once every program in it fetched fine —
        // otherwise a picker refresh could list a program whose file 404s.
        var allFilesOk = true
        val fetchedFiles = mutableMapOf<String, String>()
        entries.forEach { entry ->
            val programJson = fetch("$baseRawUrl/${entry.file}")
            if (programJson == null) {
                allFilesOk = false
            } else {
                fetchedFiles["${entry.meta.id}.json"] = programJson
            }
        }
        fetchedFiles.forEach { (name, content) -> library.writeCache(name, content) }
        if (allFilesOk) library.writeCache("index.json", indexJson)
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    companion object {
        /** Composition-root factory, same pattern as ExerciseMediaRepository.default(). */
        fun default(context: Context, library: ProgramLibrary): ProgramSyncRepository {
            val cacheDir = File(context.cacheDir, "program_sync_http")
            val client = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 10L * 1024 * 1024))
                .build()
            return ProgramSyncRepository(client, library)
        }
    }
}
