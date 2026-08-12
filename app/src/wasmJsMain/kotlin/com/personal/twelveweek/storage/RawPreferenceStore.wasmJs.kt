package com.personal.twelveweek.storage

import kotlinx.browser.localStorage

actual class RawPreferenceStore actual constructor(private val namespace: String) {

    private fun prefixed(key: String) = "$namespace:$key"

    actual fun getString(key: String): String? = localStorage.getItem(prefixed(key))

    actual fun putString(key: String, value: String) {
        localStorage.setItem(prefixed(key), value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        when (localStorage.getItem(prefixed(key))) {
            "1" -> true
            "0" -> false
            else -> default
        }

    actual fun putBoolean(key: String, value: Boolean) {
        localStorage.setItem(prefixed(key), if (value) "1" else "0")
    }
}
