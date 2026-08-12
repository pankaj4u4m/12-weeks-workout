package com.personal.twelveweek

import com.personal.twelveweek.storage.RawPreferenceStore

/**
 * Which library program is pinned to the Home screen, plus whether the user
 * has already been through first-run onboarding (Connect key → Pick program).
 * Defaults to the bundled "program-1" so the app is fully usable even if
 * someone never opens the picker — same experience as before the program
 * library existed.
 */
class SelectedProgramStore(private val store: RawPreferenceStore) {

    fun get(): String = store.getString(KEY_PROGRAM_ID) ?: DEFAULT_PROGRAM_ID

    fun set(programId: String) {
        store.putString(KEY_PROGRAM_ID, programId)
    }

    fun hasOnboarded(): Boolean = store.getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded() {
        store.putBoolean(KEY_ONBOARDED, true)
    }

    companion object {
        const val DEFAULT_PROGRAM_ID = "program-1"
        private const val KEY_PROGRAM_ID = "selected_program_id"
        private const val KEY_ONBOARDED = "has_onboarded"
    }
}
