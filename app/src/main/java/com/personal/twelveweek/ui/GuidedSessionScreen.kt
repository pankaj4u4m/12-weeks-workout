package com.personal.twelveweek.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.ProgressStore
import com.personal.twelveweek.Workout
import com.personal.twelveweek.buzz
import com.personal.twelveweek.formatClock
import com.personal.twelveweek.media.ExerciseDbDetail
import com.personal.twelveweek.media.ExerciseMediaRepository
import com.personal.twelveweek.media.ExerciseVideoPlayer
import com.personal.twelveweek.media.GuidedStep
import com.personal.twelveweek.media.firstIncompleteIndex
import com.personal.twelveweek.media.guidedSteps
import com.personal.twelveweek.security.ApiKeyManager
import kotlinx.coroutines.delay

@Composable
fun GuidedSessionScreen(
    workout: Workout,
    progress: ProgressStore,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val steps = remember(workout) { workout.guidedSteps() }

    if (steps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This workout has no exercises.")
        }
        return
    }

    val keyManager = remember { ApiKeyManager(context) }
    val repository = remember { ExerciseMediaRepository.default(context, keyManager) }
    val firstIndex = remember(workout) {
        steps.firstIncompleteIndex { progress.isDone(it) }.coerceIn(0, steps.lastIndex)
    }

    var index by remember(workout) { mutableIntStateOf(firstIndex) }
    var finished by remember(workout) {
        mutableStateOf(steps.all { progress.isDone(it.key) })
    }

    BackHandler(onBack = onExit)

    if (finished) {
        SessionCompleteScreen(
            movementCount = steps.size,
            onExit = onExit
        )
        return
    }

    val step = steps[index]
    var showConnect by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableIntStateOf(0) }
    val hasKey = remember(keyVersion) { keyManager.get() != null }

    var media by remember(step.key, keyVersion) { mutableStateOf<ExerciseDbDetail?>(null) }
    LaunchedEffect(step.key, keyVersion) {
        media = null
        if (!step.exercise.isRest) media = repository.get(step.exercise)
    }

    val totalSeconds = step.exercise.seconds
    var remaining by remember(step.key) { mutableIntStateOf(totalSeconds ?: 0) }
    var timerRunning by remember(step.key) { mutableStateOf(false) }

    fun markDoneAndAdvance() {
        progress.setDone(step.key, true)
        if (index == steps.lastIndex) {
            finished = true
        } else {
            index += 1
        }
    }

    LaunchedEffect(step.key, timerRunning) {
        if (!timerRunning || totalSeconds == null) return@LaunchedEffect
        while (timerRunning && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0) {
            timerRunning = false
            progress.setDone(step.key, true)
            buzz(context)
            delay(500)
            if (index == steps.lastIndex) finished = true else index += 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Exit workout")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Week ${workout.week} · ${workout.title}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${index + 1} of ${steps.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasKey && !step.exercise.isRest) {
                TextButton(onClick = { showConnect = true }) {
                    Text("Add demos")
                }
            }
        }

        ProgressBand(
            fraction = (index + 1).toFloat() / steps.size,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        MovementStage(
            step = step,
            media = media,
            remaining = remaining,
            totalSeconds = totalSeconds,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(18.dp))
        Text(
            step.sectionTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            step.exercise.name,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            prescription(step, remaining),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        when {
            totalSeconds != null -> {
                Button(
                    onClick = { timerRunning = !timerRunning },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Icon(
                        if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            timerRunning -> "Pause timer"
                            remaining < totalSeconds -> "Resume timer"
                            else -> "Start ${totalSeconds}s timer"
                        }
                    )
                }
                TextButton(
                    onClick = ::markDoneAndAdvance,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Mark done without timer")
                }
            }
            else -> {
                Button(
                    onClick = ::markDoneAndAdvance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (index == steps.lastIndex) "Finish workout" else "Done · Next")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                enabled = index > 0,
                onClick = { index -= 1 },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous exercise")
            }
            FilledTonalIconButton(
                enabled = index < steps.lastIndex,
                onClick = { index += 1 },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next exercise")
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
private fun MovementStage(
    step: GuidedStep,
    media: ExerciseDbDetail?,
    remaining: Int,
    totalSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val hasMedia = media?.videoUrl != null || media?.imageUrl != null
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (hasMedia) {
            ExerciseVideoPlayer(
                videoUrl = media?.videoUrl,
                imageUrl = media?.imageUrl,
                contentDescription = step.exercise.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                ResistanceBandMark(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    muted = true
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            totalSeconds != null -> formatClock(remaining)
                            step.exercise.reps != null -> "${step.exercise.reps}"
                            else -> "REST"
                        },
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        when {
                            totalSeconds != null -> "SECONDS"
                            step.exercise.reps != null -> "REPS"
                            else -> "RECOVER"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCompleteScreen(
    movementCount: Int,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ResistanceBandMark(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Workout complete",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "$movementCount movements recorded. Your plan is ready for the next session.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Return to workout")
        }
    }
}

private fun prescription(step: GuidedStep, remaining: Int): String = when {
    step.exercise.seconds != null -> "${formatClock(remaining)} remaining"
    step.exercise.reps != null -> "${step.exercise.reps} repetitions"
    step.exercise.isRest -> "Recover, then continue when ready"
    else -> step.exercise.raw
}
