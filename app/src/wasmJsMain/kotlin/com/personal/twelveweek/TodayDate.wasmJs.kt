package com.personal.twelveweek

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
actual fun todayIso(): String = jsTodayIso()

// Built from local-time getters (getFullYear/getMonth/getDate), NOT
// toISOString() — toISOString() is UTC and would misdate a session run
// late at night in most timezones.
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "() => { var d = new Date(); var m = String(d.getMonth() + 1).padStart(2, '0'); var day = String(d.getDate()).padStart(2, '0'); return d.getFullYear() + '-' + m + '-' + day; }"
)
private external fun jsTodayIso(): String
