package com.personal.twelveweek

import java.time.LocalDate

// LocalDate.now() already formats as ISO "yyyy-MM-dd" via toString() (zero
// padded). minSdk is 26 (see app/build.gradle.kts), so java.time is always
// available — no desugaring needed.
actual fun todayIso(): String = LocalDate.now().toString()
