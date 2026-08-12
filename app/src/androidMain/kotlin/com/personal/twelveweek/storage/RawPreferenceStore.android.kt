package com.personal.twelveweek.storage

import android.content.Context
import android.content.SharedPreferences

actual class RawPreferenceStore actual constructor(private val namespace: String) {

    private val underTest = android.os.Build.FINGERPRINT == null
    private val memoryFallback: MutableMap<String, Any>? = if (underTest) mutableMapOf() else null

    private val realPrefs: SharedPreferences? =
        if (underTest) null
        else requireNotNull(AndroidPlatformContext.appContext) {
            "AndroidPlatformContext.install(context) must run before any RawPreferenceStore is used."
        }.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    actual fun getString(key: String): String? =
        realPrefs?.getString(key, null) ?: memoryFallback?.get(key) as? String

    actual fun putString(key: String, value: String) {
        realPrefs?.edit()?.putString(key, value)?.apply() ?: memoryFallback?.put(key, value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        realPrefs?.getBoolean(key, default) ?: (memoryFallback?.get(key) as? Boolean ?: default)

    actual fun putBoolean(key: String, value: Boolean) {
        realPrefs?.edit()?.putBoolean(key, value)?.apply() ?: memoryFallback?.put(key, value)
    }
}
