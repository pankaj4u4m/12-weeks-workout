package com.personal.twelveweek.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.storage.RawPreferenceStore

/**
 * One-time, dismissible tip shown after first-run onboarding, pointing new
 * web users at ways to run TwelveWeek without browser chrome — there's no
 * app-store install here, so this is the web app's equivalent of "looks
 * like a real app" once launched from the home screen. Shown at most once
 * per browser profile (tracked in the same [RawPreferenceStore] as the
 * rest of the app's local preferences).
 */
class WebInstallTipState(private val store: RawPreferenceStore = RawPreferenceStore("twelve_week_ui")) {
    fun hasSeenTip(): Boolean = store.getBoolean(KEY, false)
    fun markSeen() = store.putBoolean(KEY, true)

    private companion object {
        const val KEY = "seen_install_tip"
    }
}

private data class InstallStep(val heading: String, val body: String)

private val INSTALL_STEPS = listOf(
    InstallStep(
        "Add to Home Screen (recommended)",
        "Chrome on Android: tap ⋮ (top right) → \"Install app\" or \"Add to Home screen\". " +
            "Safari on iPhone: tap Share → \"Add to Home Screen\". " +
            "Opening the icon this creates runs TwelveWeek full-screen, with no address bar or browser frame."
    ),
    InstallStep(
        "Or: force any site into a borderless app (Hermit)",
        "If your browser doesn't offer an \"Install app\" option, a Lite Apps browser like Hermit can force it:\n" +
            "1. Install Hermit (Lite Apps Browser) from the Google Play Store.\n" +
            "2. Open Hermit, paste this page's web address.\n" +
            "3. Tap Create Lite App.\n" +
            "4. Grant permission to place the icon on your home screen.\n" +
            "Opening the icon Hermit creates runs the site as a completely standalone app — no URL bar, top header, or browser frame."
    )
)

@Composable
fun WebInstallTipDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Get the full-screen app feel") },
        text = {
            LazyColumn {
                items(INSTALL_STEPS) { step ->
                    Column {
                        Text(step.heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(step.body, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}
