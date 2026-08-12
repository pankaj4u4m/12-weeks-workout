package com.personal.twelveweek.kmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

// lc-debt: brief specified CanvasBasedWindow, which no longer exists in Compose Multiplatform
// 1.11.1 (org.jetbrains.compose.ui:ui-wasm-js) -- it was replaced by the (still experimental)
// ComposeViewport entry point. Upgrade path: drop @OptIn once ComposeViewport stabilizes.
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "ComposeTarget") {
        KmpFoundationSmokeTest()
    }
}
