package com.personal.twelveweek.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.media.ApiResult
import com.personal.twelveweek.media.ExerciseDbApi
import com.personal.twelveweek.security.ApiKeyManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.launch

private const val SIGNUP_URL =
    "https://rapidapi.com/auth/login?referral=%2Fascendapi%2Fapi%2Fedb-with-videos-and-images-by-ascendapi%2Fpricing"
private const val VALIDATION_EXERCISE_ID = "exr_41n2hxnFMotsXTj3"

@Composable
fun ConnectMediaScreen(
    keyManager: ApiKeyManager,
    onConnected: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    val api = remember { ExerciseDbApi(HttpClient(OkHttp)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise demos") },
        text = {
            Column {
                Text(
                    "TwelveWeek works fully without this. Connect a free ExerciseDB key only if you want video and image demonstrations.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SIGNUP_URL)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Get a free API key")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        error = null
                    },
                    label = { Text("RapidAPI key") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key"
                            )
                        }
                    },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The key is encrypted on this device and sent only to ExerciseDB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = keyInput.isNotBlank() && !checking,
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    checking = true
                    scope.launch {
                        when (api.fetchExercise(VALIDATION_EXERCISE_ID, keyInput.trim())) {
                            is ApiResult.Success -> {
                                keyManager.set(keyInput.trim())
                                checking = false
                                onConnected()
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
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (checking) "Checking" else "Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
