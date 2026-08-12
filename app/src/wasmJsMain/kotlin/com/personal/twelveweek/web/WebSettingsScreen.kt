package com.personal.twelveweek.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.media.ExerciseDbApi
import io.ktor.client.HttpClient

/**
 * Full-page Settings — web port of the Android app's `ui.SettingsScreen`.
 * Same guided-session controls (voice cues, time between exercises, rep
 * prep countdown) and the same "Exercise demos" API-key connect/disconnect
 * flow, backed by [WebApiKeyManager] (Web Crypto) instead of Android
 * Keystore.
 */
@Composable
fun WebSettingsScreen(settings: WebSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var voiceEnabled by remember { mutableStateOf(settings.voiceEnabled) }
    var transitionSeconds by remember { mutableStateOf(settings.transitionSeconds) }
    var repPrepSeconds by remember { mutableStateOf(settings.repPrepSeconds) }

    val keyManager = remember { WebApiKeyManager() }
    val exerciseDbApi = remember { ExerciseDbApi(HttpClient()) }
    var keyVersion by remember { mutableIntStateOf(0) }
    var hasKey by remember { mutableStateOf(false) }
    var showConnect by remember { mutableStateOf(false) }
    LaunchedEffect(keyVersion) { keyManager.get { hasKey = it != null } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                SettingsSection("Guided session") {
                    SwitchRow(
                        title = "Voice cues",
                        subtitle = "Spoken \"halfway there\", \"5 seconds remaining\", \"up next\"",
                        checked = voiceEnabled,
                        onCheckedChange = {
                            voiceEnabled = it
                            settings.voiceEnabled = it
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    OptionPickerRow(
                        title = "Time between exercises",
                        subtitle = "Skippable \"up next\" pause after each movement",
                        options = WebSettings.TRANSITION_OPTIONS,
                        selected = transitionSeconds,
                        label = { if (it == 0) "Off" else "${it}s" },
                        onSelect = {
                            transitionSeconds = it
                            settings.transitionSeconds = it
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    OptionPickerRow(
                        title = "Prep time before rep exercises",
                        subtitle = "\"Get ready\" countdown before a reps-based movement starts",
                        options = WebSettings.REP_PREP_OPTIONS,
                        selected = repPrepSeconds,
                        label = { if (it == 0) "Off" else "${it}s" },
                        onSelect = {
                            repPrepSeconds = it
                            settings.repPrepSeconds = it
                        }
                    )
                }
            }

            item {
                SettingsSection("Exercise demos") {
                    if (hasKey) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("ExerciseDB key connected")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The key never leaves this device except in calls to ExerciseDB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { keyManager.clear(); keyVersion += 1 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("Disconnect") }
                    } else {
                        Text("Not connected — exercise demos fall back to search links.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showConnect = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("Connect") }
                    }
                }
            }
        }
    }

    if (showConnect) {
        WebConnectMediaScreen(
            keyManager = keyManager,
            exerciseDbApi = exerciseDbApi,
            onConnected = {
                showConnect = false
                keyVersion += 1
            },
            onDismiss = { showConnect = false }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionPickerRow(
    title: String,
    subtitle: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { value ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
            }
        }
    }
}
