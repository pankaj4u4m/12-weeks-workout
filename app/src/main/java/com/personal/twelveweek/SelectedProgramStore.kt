package com.personal.twelveweek

import android.content.Context

/**
 * Which library program is pinned to the Home screen, plus whether the user
 * has already been through first-run onboarding (Connect key → Pick program).
 * Defaults to the bundled "program-1" so the app is fully usable even if
 * someone never opens the picker — same experience as before the program
 * library existed.
 */
class SelectedProgramStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("twelve_week_selected_program", Context.MODE_PRIVATE)

    fun get(): String = prefs.getString(KEY_PROGRAM_ID, DEFAULT_PROGRAM_ID) ?: DEFAULT_PROGRAM_ID

    fun set(programId: String) {
        prefs.edit().putString(KEY_PROGRAM_ID, programId).apply()
    }

    fun hasOnboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    companion object {
        const val DEFAULT_PROGRAM_ID = "program-1"
        private const val KEY_PROGRAM_ID = "selected_program_id"
        private const val KEY_ONBOARDED = "has_onboarded"
    }
}
