package com.personal.twelveweek.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.security.ApiKeyManager
import com.personal.twelveweek.settings.AppSettings

/**
 * Full settings page — guided-session behavior (voice cues, inter-exercise
 * timer, rep-exercise prep countdown) plus the ExerciseDB connection
 * ([ConnectMediaScreen] only ever adds a key; this is where it's reviewed
 * and removed).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyManager = remember { ApiKeyManager(context) }
    val settings = remember { AppSettings(context) }

    var keyVersion by remember { mutableIntStateOf(0) }
    val hasKey = remember(keyVersion) { keyManager.get() != null }
    var showConnect by remember { mutableStateOf(false) }

    var voiceEnabled by remember { mutableStateOf(settings.voiceEnabled) }
    var transitionSeconds by remember { mutableIntStateOf(settings.transitionSeconds) }
    var repPrepSeconds by remember { mutableIntStateOf(settings.repPrepSeconds) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
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
                        options = AppSettings.TRANSITION_OPTIONS,
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
                        options = AppSettings.REP_PREP_OPTIONS,
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
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                            onClick = {
                                keyManager.clear()
                                keyVersion += 1
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Disconnect")
                        }
                    } else {
                        Text(
                            "Not connected — exercise demos fall back to search links.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showConnect = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }

    if (showConnect) {
        ConnectMediaScreen(
            keyManager = keyManager,
            onConnected = {
                showConnect = false
                keyVersion += 1
            },
            onDismiss = { showConnect = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label(value)) }
                )
            }
        }
    }
}
