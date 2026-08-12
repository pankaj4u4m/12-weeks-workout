package com.personal.twelveweek.storage

import android.content.Context
import android.content.SharedPreferences

/** [namespace] is the SharedPreferences file name, matching today's
 *  hardcoded `"twelve_week_progress"` exactly — same on-disk file,
 *  same format, nobody's saved progress moves or changes shape. */
actual class RawKeyFlagStore actual constructor(private val namespace: String) {

    // Set by AndroidPlatformContext.install() before any RawKeyFlagStore is
    // constructed — see Task 2's AndroidPlatformContext for the composition-
    // root wiring. Kotlin Multiplatform's `expect class` constructors can't
    // take an Android Context parameter directly (the signature must match
    // every actual, and wasmJs has no Context), so Context is threaded in
    // via this small platform-only side channel instead.
    private val prefs: SharedPreferences
        get() = requireNotNull(AndroidPlatformContext.appContext) {
            "AndroidPlatformContext.install(context) must run before any RawKeyFlagStore is used — call it from Application.onCreate() or MainActivity.onCreate()."
        }.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    actual fun allKeys(): Set<String> = prefs.all.keys.toSet()

    actual fun setPresent(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}

/** Tiny composition-root side channel so androidMain code (which has a
 *  real [Context]) can hand it to [RawKeyFlagStore] without threading a
 *  Context parameter through the shared `expect` constructor, which must
 *  have an identical signature on every platform including wasmJs (which
 *  has no Context at all). */
object AndroidPlatformContext {
    internal var appContext: Context? = null
        private set

    fun install(context: Context) {
        appContext = context.applicationContext
    }
}
