package com.personal.twelveweek.kmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Proves a Composable written once in commonMain renders on every target.
 *  Delete once Part 3 (screen migration) moves real screens into commonMain. */
@Composable
fun KmpFoundationSmokeTest() {
    MaterialTheme {
        Text("TwelveWeek KMP foundation OK")
    }
}
