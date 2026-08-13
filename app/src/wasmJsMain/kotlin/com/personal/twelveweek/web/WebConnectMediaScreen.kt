package com.personal.twelveweek.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.media.ApiResult
import com.personal.twelveweek.media.ExerciseDbApi
import kotlinx.coroutines.launch

private const val SIGNUP_URL = "https://rapidapi.com/console/"
private const val VALIDATION_EXERCISE_ID = "exr_41n2hxnFMotsXTj3"

/**
 * Web port of the Android app's `ui.ConnectMediaScreen` — same copy, same
 * validation flow (a live lookup of one known exercise id against
 * ExerciseDB, using the already-shared [ExerciseDbApi]). `Intent.ACTION_VIEW`
 * becomes [webOpenUrl] (`window.open`).
 */
@Composable
fun WebConnectMediaScreen(
    keyManager: WebApiKeyManager,
    exerciseDbApi: ExerciseDbApi,
    onConnected: () -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        val trimmed = keyInput.trim()
        if (trimmed.isBlank() || checking) return
        checking = true
        error = null
        scope.launch {
            when (exerciseDbApi.fetchExercise(VALIDATION_EXERCISE_ID, trimmed)) {
                is ApiResult.Success -> {
                    keyManager.set(trimmed) {
                        checking = false
                        onConnected()
                    }
                }
                ApiResult.Unauthorized -> {
                    checking = false
                    error = "That key was rejected. Copy it again from RapidAPI."
                }
                ApiResult.NetworkError -> {
                    checking = false
                    error = "ExerciseDB could not be reached. Check your connection and retry."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise demos") },
        text = {
            Column {
                Text("TwelveWeek works fully without this. Connect a free ExerciseDB key only if you want video and image demonstrations.")
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { webOpenUrl(SIGNUP_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Get a free API key")
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; error = null },
                    label = { Text("RapidAPI key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key"
                            )
                        }
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    error ?: "The key is encrypted on this device and sent only to ExerciseDB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::connect, enabled = keyInput.isNotBlank() && !checking) {
                if (checking) {
                    Row {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking")
                    }
                } else {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
