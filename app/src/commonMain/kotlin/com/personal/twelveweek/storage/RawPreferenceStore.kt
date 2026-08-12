package com.personal.twelveweek.storage

/**
 * Raw, platform-specific typed key-value storage — the general-purpose
 * sibling of [RawKeyFlagStore] (which only supports presence/absence).
 * [namespace] scopes the storage (Android: SharedPreferences file name;
 * wasmJs: a localStorage key prefix).
 */
expect class RawPreferenceStore(namespace: String) {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}
