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
    // via this small platform-only side channel instead. Captured once at
    // construction — the real app always calls install() in
    // MainActivity.onCreate() before constructing any store, so this is
    // never null for a real instance.
    private val realPrefs: SharedPreferences? =
        AndroidPlatformContext.appContext?.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    // Only engaged when constructed before install() has run AND we're
    // running under a plain JVM unit test (no Robolectric in this project,
    // so the android.jar stub jar leaves Build.FINGERPRINT null — a real
    // device, real emulator, or Robolectric always populates it). On a real
    // device this same condition instead throws below, so a future entry
    // point that forgets to call install() first fails loudly rather than
    // silently losing writes.
    private val underUnitTestJvm = android.os.Build.FINGERPRINT == null

    private val memoryFallback: MutableSet<String>? = when {
        realPrefs != null -> null
        underUnitTestJvm -> mutableSetOf()
        else -> throw IllegalStateException(
            "AndroidPlatformContext.install(context) must run before any RawKeyFlagStore is used — call it from Application.onCreate() or MainActivity.onCreate()."
        )
    }

    actual fun allKeys(): Set<String> = realPrefs?.all?.keys?.toSet() ?: memoryFallback.orEmpty().toSet()

    actual fun setPresent(key: String) {
        realPrefs?.edit()?.putBoolean(key, true)?.apply() ?: memoryFallback?.add(key)
    }

    actual fun remove(key: String) {
        realPrefs?.edit()?.remove(key)?.apply() ?: memoryFallback?.remove(key)
    }

    actual fun clear() {
        realPrefs?.edit()?.clear()?.apply() ?: memoryFallback?.clear()
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
