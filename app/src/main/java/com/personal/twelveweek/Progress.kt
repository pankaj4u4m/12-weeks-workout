package com.personal.twelveweek

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Which boxes are ticked. Mirrors the TRUE/FALSE columns in the sheet.
 * Backed by SharedPreferences so it survives restarts, and included in
 * Android auto-backup so it survives a new phone.
 *
 * Keys are `"<programId>:w..-o..-s..-i.."` (see [Workout.keyFor]). Before the
 * program library existed there was only ever one program and keys had no
 * prefix (`"w..-o..-s..-i.."`) — those are the built-in program's keys under
 * the hood, so [isDone]/[setDone] transparently fall back to/clean up the
 * legacy unprefixed form for `"program-1:..."` keys. Nobody's existing tick
 * history is lost by the program-library upgrade.
 */
class ProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("twelve_week_progress", Context.MODE_PRIVATE)

    private val done = mutableStateMapOf<String, Boolean>()

    init {
        prefs.all.forEach { (key, value) ->
            if (value == true) done[key] = true
        }
    }

    private fun legacyKey(key: String): String? =
        if (key.startsWith(LEGACY_PREFIX)) key.removePrefix(LEGACY_PREFIX) else null

    fun isDone(key: String): Boolean {
        if (done[key] == true) return true
        val legacy = legacyKey(key) ?: return false
        return done[legacy] == true
    }

    fun setDone(key: String, value: Boolean) {
        val editor = prefs.edit()
        if (value) {
            done[key] = true
            editor.putBoolean(key, true)
        } else {
            done.remove(key)
            editor.remove(key)
        }
        // Either way, the legacy form is now superseded by the new key — stop
        // carrying it forward so it can't cause a stale "still done" read.
        legacyKey(key)?.let { legacy ->
            done.remove(legacy)
            editor.remove(legacy)
        }
        editor.apply()
    }

    fun toggle(key: String) = setDone(key, !isDone(key))

    fun countDone(keys: List<String>): Int = keys.count { isDone(it) }

    fun setAll(keys: List<String>, value: Boolean) {
        val editor = prefs.edit()
        keys.forEach { key ->
            if (value) {
                done[key] = true
                editor.putBoolean(key, true)
            } else {
                done.remove(key)
                editor.remove(key)
            }
            legacyKey(key)?.let { legacy ->
                done.remove(legacy)
                editor.remove(legacy)
            }
        }
        editor.apply()
    }

    fun clearEverything() {
        done.clear()
        prefs.edit().clear().apply()
    }

    private companion object {
        const val LEGACY_PREFIX = "program-1:"
    }
}
