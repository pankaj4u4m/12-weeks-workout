package com.personal.twelveweek.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's own RapidAPI key for ExerciseDB. Nothing else lives here —
 * this app has no accounts, no other settings.
 */
class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "exercise_media_key",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun set(key: String) {
        prefs.edit().putString(KEY, key.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "rapidapi_key"
    }
}
