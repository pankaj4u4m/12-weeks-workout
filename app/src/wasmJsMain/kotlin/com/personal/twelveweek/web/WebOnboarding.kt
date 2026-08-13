package com.personal.twelveweek.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.media.ExerciseDbApi
import com.personal.twelveweek.programs.IndexEntry
import io.ktor.client.HttpClient

/**
 * Web port of the Android app's first-run onboarding (`MainActivity.kt`'s
 * `OnboardingFlow`/`WelcomeScreen`) — welcome, then pick a plan (reusing
 * [ProgramsScreen] itself), then an optional "connect exercise demos" step
 * ([WebConnectMediaScreen]) so a first-time web user can add their
 * ExerciseDB key up front instead of having to discover the same prompt
 * buried in Settings or mid-workout later. Skippable like every media
 * feature in this app — "Not now" finishes onboarding exactly like
 * connecting does. Gated the same way as before on
 * [com.personal.twelveweek.SelectedProgramStore.hasOnboarded]/`setOnboarded`.
 */
enum class WebOnboardingStep { WELCOME, PICK_PLAN, CONNECT_MEDIA }

@Composable
fun WebOnboardingFlow(
    step: WebOnboardingStep,
    entries: List<IndexEntry>,
    selectedProgramId: String,
    onShowPlans: () -> Unit,
    onBack: () -> Unit,
    onProgramChosen: (String) -> Unit,
    onFinish: () -> Unit
) {
    when (step) {
        WebOnboardingStep.WELCOME -> WebWelcomeScreen(onContinue = onShowPlans)
        WebOnboardingStep.PICK_PLAN -> ProgramsScreen(
            entries = entries,
            selectedProgramId = selectedProgramId,
            onSelect = onProgramChosen,
            library = null,
            onImported = {},
            onSkip = { onProgramChosen(selectedProgramId) },
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        )
        WebOnboardingStep.CONNECT_MEDIA -> {
            val keyManager = remember { WebApiKeyManager() }
            val exerciseDbApi = remember { ExerciseDbApi(HttpClient()) }
            WebConnectMediaScreen(
                keyManager = keyManager,
                exerciseDbApi = exerciseDbApi,
                onConnected = onFinish,
                onDismiss = onFinish
            )
        }
    }
}

@Composable
private fun WebWelcomeScreen(onContinue: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ResistanceBandMark(modifier = Modifier.fillMaxWidth().height(220.dp))
        }
        item {
            Text("Your workout, ready when you are.", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Choose a plan once. TwelveWeek keeps the next home workout close, guides every movement, and remembers where you stopped.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            WebWelcomeBenefit(
                icon = Icons.Filled.OfflineBolt,
                title = "Works offline",
                body = "Plans, timers, and progress stay useful without a connection."
            )
            Spacer(Modifier.height(18.dp))
            WebWelcomeBenefit(
                icon = Icons.Filled.TouchApp,
                title = "Built for the middle of a workout",
                body = "Large controls and one exercise at a time keep attention on training."
            )
            Spacer(Modifier.height(18.dp))
            WebWelcomeBenefit(
                icon = Icons.Filled.RestartAlt,
                title = "Resume without guessing",
                body = "Open the app and continue the first unfinished session."
            )
        }
        item {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Choose my plan")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun WebWelcomeBenefit(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp).size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
