package com.personal.twelveweek.web

import com.personal.twelveweek.storage.RawPreferenceStore

/**
 * Web port of the Android app's `settings.AppSettings` — same keys, same
 * defaults, same option presets, just backed by the shared
 * [RawPreferenceStore] (browser `localStorage`) instead of Android
 * SharedPreferences. Kept as a `web`-package copy rather than sharing the
 * Android class directly for the same classfile-collision reason as
 * [TwelveWeekWebTheme] — see that file's comment.
 */
class WebSettings(private val store: RawPreferenceStore = RawPreferenceStore("twelve_week_settings")) {

    /** Whether guided-session voice cues ("Halfway there", "5 seconds
     *  remaining", "Up next: X") are spoken aloud. On by default. */
    var voiceEnabled: Boolean
        get() = store.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = store.putBoolean(KEY_VOICE_ENABLED, value)

    /** Length, in seconds, of the skippable "Up next" transition shown
     *  between exercises in a guided session. */
    var transitionSeconds: Int
        get() = store.getString(KEY_TRANSITION_SECONDS)?.toIntOrNull() ?: DEFAULT_TRANSITION_SECONDS
        set(value) = store.putString(KEY_TRANSITION_SECONDS, value.toString())

    /** Length, in seconds, of the "get ready" prep countdown shown before a
     *  rep-based exercise becomes interactive (0 disables it). */
    var repPrepSeconds: Int
        get() = store.getString(KEY_REP_PREP_SECONDS)?.toIntOrNull() ?: DEFAULT_REP_PREP_SECONDS
        set(value) = store.putString(KEY_REP_PREP_SECONDS, value.toString())

    companion object {
        const val DEFAULT_TRANSITION_SECONDS = 5
        const val DEFAULT_REP_PREP_SECONDS = 3

        /** Selectable presets shown in Settings — 0 means "off". */
        val TRANSITION_OPTIONS = listOf(0, 3, 5, 8, 10)
        val REP_PREP_OPTIONS = listOf(0, 3, 5, 8)

        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_TRANSITION_SECONDS = "transition_seconds"
        private const val KEY_REP_PREP_SECONDS = "rep_prep_seconds"
    }
}
