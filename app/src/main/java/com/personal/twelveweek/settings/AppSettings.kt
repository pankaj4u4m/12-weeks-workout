package com.personal.twelveweek.settings

import android.content.Context

/**
 * Local, unencrypted app preferences — nothing sensitive lives here (that's
 * [com.personal.twelveweek.security.ApiKeyManager]'s job). Plain
 * SharedPreferences, same pattern as `SelectedProgramStore`.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("twelve_week_settings", Context.MODE_PRIVATE)

    /** Whether guided-session voice cues ("Halfway there", "5 seconds
     *  remaining", "Up next: X") are spoken aloud. On by default. */
    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    /** Length, in seconds, of the skippable "Up next" transition shown
     *  between exercises in a guided session. */
    var transitionSeconds: Int
        get() = prefs.getInt(KEY_TRANSITION_SECONDS, DEFAULT_TRANSITION_SECONDS)
        set(value) = prefs.edit().putInt(KEY_TRANSITION_SECONDS, value).apply()

    /** Length, in seconds, of the "get ready" prep countdown shown before a
     *  rep-based exercise becomes interactive (0 disables it — timed
     *  exercises already have their own countdown and never show this). */
    var repPrepSeconds: Int
        get() = prefs.getInt(KEY_REP_PREP_SECONDS, DEFAULT_REP_PREP_SECONDS)
        set(value) = prefs.edit().putInt(KEY_REP_PREP_SECONDS, value).apply()

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
