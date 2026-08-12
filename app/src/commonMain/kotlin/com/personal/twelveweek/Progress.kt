package com.personal.twelveweek

import androidx.compose.runtime.mutableStateMapOf
import com.personal.twelveweek.storage.RawKeyFlagStore

/**
 * Which boxes are ticked. Mirrors the TRUE/FALSE columns in the sheet.
 * Backed by [RawKeyFlagStore] so it survives restarts (Android: included
 * in Android auto-backup; wasmJs: survives a page reload via localStorage).
 *
 * Keys are `"<programId>:w..-o..-s..-i.."` (see [Workout.keyFor]). Before
 * the program library existed there was only ever one program and keys had
 * no prefix (`"w..-o..-s..-i.."`) — those are the built-in program's keys
 * under the hood, so [isDone]/[setDone] transparently fall back to/clean
 * up the legacy unprefixed form for `"program-1:..."` keys. Nobody's
 * existing tick history is lost by the program-library upgrade.
 */
class ProgressStore(private val store: RawKeyFlagStore) {

    private val done = mutableStateMapOf<String, Boolean>()

    init {
        store.allKeys().forEach { key -> done[key] = true }
    }

    private fun legacyKey(key: String): String? =
        if (key.startsWith(LEGACY_PREFIX)) key.removePrefix(LEGACY_PREFIX) else null

    fun isDone(key: String): Boolean {
        if (done[key] == true) return true
        val legacy = legacyKey(key) ?: return false
        return done[legacy] == true
    }

    fun setDone(key: String, value: Boolean) {
        if (value) {
            done[key] = true
            store.setPresent(key)
        } else {
            done.remove(key)
            store.remove(key)
        }
        // Either way, the legacy form is now superseded by the new key — stop
        // carrying it forward so it can't cause a stale "still done" read.
        legacyKey(key)?.let { legacy ->
            done.remove(legacy)
            store.remove(legacy)
        }
    }

    fun toggle(key: String) = setDone(key, !isDone(key))

    fun countDone(keys: List<String>): Int = keys.count { isDone(it) }

    fun setAll(keys: List<String>, value: Boolean) {
        keys.forEach { key -> setDone(key, value) }
    }

    fun clearEverything() {
        done.clear()
        store.clear()
    }

    private companion object {
        const val LEGACY_PREFIX = "program-1:"
    }
}
