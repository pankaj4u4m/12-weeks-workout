package com.personal.twelveweek.storage

import kotlinx.browser.localStorage

/** [namespace] prefixes every localStorage key so this store never
 *  collides with anything else using localStorage on the same origin. */
actual class RawKeyFlagStore actual constructor(private val namespace: String) {

    private fun prefixed(key: String) = "$namespace:$key"

    actual fun allKeys(): Set<String> {
        val prefix = "$namespace:"
        val keys = mutableSetOf<String>()
        for (i in 0 until localStorage.length) {
            val k = localStorage.key(i) ?: continue
            if (k.startsWith(prefix)) keys.add(k.removePrefix(prefix))
        }
        return keys
    }

    actual fun setPresent(key: String) {
        localStorage.setItem(prefixed(key), "1")
    }

    actual fun remove(key: String) {
        localStorage.removeItem(prefixed(key))
    }

    actual fun clear() {
        allKeys().forEach { remove(it) }
    }
}
