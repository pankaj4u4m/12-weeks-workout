package com.personal.twelveweek.storage

/**
 * Raw, platform-specific "is this key present" flag storage — the only
 * thing that crosses the expect/actual boundary for progress tracking.
 * [namespace] scopes the storage (Android: SharedPreferences file name;
 * wasmJs: a localStorage key prefix) so this type can be reused for
 * anything else that needs the same shape later without collisions.
 *
 * A key's presence means "true"; there is no other value — this matches
 * exactly how [com.personal.twelveweek.Program]'s progress keys are used
 * today (SharedPreferences.putBoolean(key, true) / .remove(key), never
 * putBoolean(key, false)).
 */
expect class RawKeyFlagStore(namespace: String) {
    fun allKeys(): Set<String>
    fun setPresent(key: String)
    fun remove(key: String)
    fun clear()
}
