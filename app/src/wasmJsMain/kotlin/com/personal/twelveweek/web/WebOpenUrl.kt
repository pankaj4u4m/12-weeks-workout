package com.personal.twelveweek.web

import kotlin.js.ExperimentalWasmJsInterop

/** Opens [url] in a new browser tab — the wasmJs counterpart to the
 *  Android app's `Intent.ACTION_VIEW` (used for "Get a free API key" and
 *  external exercise search links). */
@OptIn(ExperimentalWasmJsInterop::class)
fun webOpenUrl(url: String) {
    runCatching { jsOpenUrl(url) }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(url) => { try { window.open(url, '_blank', 'noopener,noreferrer'); } catch (e) {} }")
private external fun jsOpenUrl(url: String)
