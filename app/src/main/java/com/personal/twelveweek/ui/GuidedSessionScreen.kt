package com.personal.twelveweek.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.personal.twelveweek.ProgressStore
import com.personal.twelveweek.Workout
import com.personal.twelveweek.buzz
import com.personal.twelveweek.formatClock
import com.personal.twelveweek.media.ExerciseMediaCarousel
import com.personal.twelveweek.media.ExerciseMediaRepository
import com.personal.twelveweek.media.GuidedStep
import com.personal.twelveweek.media.MediaPage
import com.personal.twelveweek.media.firstIncompleteIndex
import com.personal.twelveweek.media.guidedSteps
import com.personal.twelveweek.playCompletionSound
import com.personal.twelveweek.security.ApiKeyManager
import kotlinx.coroutines.delay

/** How long the "Up next" transition between exercises runs before
 *  auto-advancing, in seconds. */
private const val TRANSITION_SECONDS = 5

@Composable
fun GuidedSessionScreen(
    workout: Workout,
    progress: ProgressStore,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
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

    // "Up next" transition state: shown between one exercise finishing and
    // the next one starting, whether the exercise finished by its own timer
    // running out or by a manual Done tap on a rep/rest step. Skippable.
    var transitioning by remember(workout) { mutableStateOf(false) }
    var transitionRemaining by remember(workout) { mutableIntStateOf(TRANSITION_SECONDS) }
    var nextIndex by remember(workout) { mutableIntStateOf(0) }

    val voice = rememberVoiceCoach()

    BackHandler(onBack = onExit)

    fun completeStepAndAdvance(stepKey: String) {
        progress.setDone(stepKey, true)
        if (index == steps.lastIndex) {
            finished = true
        } else {
            nextIndex = index + 1
            transitionRemaining = TRANSITION_SECONDS
            transitioning = true
        }
    }

    LaunchedEffect(transitioning) {
        if (!transitioning) return@LaunchedEffect
        voice.speak("Up next: ${steps[nextIndex].exercise.name}")
        while (transitioning && transitionRemaining > 0) {
            delay(1000)
            transitionRemaining -= 1
        }
        if (transitioning) {
            transitioning = false
            index = nextIndex
        }
    }

    LaunchedEffect(finished) {
        if (finished) voice.speak("Workout complete, nice work")
    }

    if (finished) {
        SessionCompleteScreen(
            movementCount = steps.size,
            onExit = onExit
        )
        return
    }

    if (transitioning) {
        TransitionScreen(
            nextName = steps[nextIndex].exercise.name,
            remaining = transitionRemaining,
            onSkip = {
                transitioning = false
                index = nextIndex
            }
        )
        return
    }

    val step = steps[index]
    var showConnect by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableIntStateOf(0) }
    val hasKey = remember(keyVersion) { keyManager.get() != null }

    var mediaBundle by remember(step.key, keyVersion) { mutableStateOf<List<MediaPage>>(emptyList()) }
    LaunchedEffect(step.key, keyVersion) {
        mediaBundle = emptyList()
        if (!step.exercise.isRest) mediaBundle = repository.getBundle(step.exercise)
    }

    val totalSeconds = step.exercise.seconds
    var remaining by remember(step.key) { mutableIntStateOf(totalSeconds ?: 0) }
    var timerRunning by remember(step.key) { mutableStateOf(false) }
    var announcedHalfway by remember(step.key) { mutableStateOf(false) }
    var announcedFiveSecondsLeft by remember(step.key) { mutableStateOf(false) }

    LaunchedEffect(step.key, timerRunning) {
        if (!timerRunning || totalSeconds == null) return@LaunchedEffect
        val halfway = totalSeconds / 2
        while (timerRunning && remaining > 0) {
            delay(1000)
            remaining -= 1
            if (totalSeconds >= 10 && !announcedHalfway && remaining == halfway) {
                announcedHalfway = true
                voice.speak("Halfway there")
            }
            if (totalSeconds > 5 && !announcedFiveSecondsLeft && remaining == 5) {
                announcedFiveSecondsLeft = true
                voice.speak("5 seconds remaining")
            }
        }
        if (remaining == 0) {
            timerRunning = false
            buzz(context)
            playCompletionSound()
            completeStepAndAdvance(step.key)
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
            media = mediaBundle,
            remaining = remaining,
            totalSeconds = totalSeconds,
            timerRunning = timerRunning,
            onToggleTimer = { timerRunning = !timerRunning },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                enabled = index > 0,
                onClick = { index -= 1 },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous exercise")
            }
            Button(
                onClick = { completeStepAndAdvance(step.key) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (index == steps.lastIndex) "Finish workout" else "Done")
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
    media: List<MediaPage>,
    remaining: Int,
    totalSeconds: Int?,
    timerRunning: Boolean,
    onToggleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasMedia = media.isNotEmpty()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(Modifier.fillMaxSize()) {
            if (hasMedia) {
                ExerciseMediaCarousel(
                    pages = media,
                    contentDescription = step.exercise.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
                if (totalSeconds != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(76.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shadowElevation = 4.dp
                    ) {
                        TimerRing(
                            progressFraction = remaining.toFloat() / totalSeconds,
                            primaryText = formatClock(remaining),
                            secondaryText = "",
                            ringThickness = 5.dp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            } else if (totalSeconds != null) {
                TimerRing(
                    progressFraction = remaining.toFloat() / totalSeconds,
                    primaryText = formatClock(remaining),
                    secondaryText = "SECONDS",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(220.dp)
                )
            } else {
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
                        if (step.exercise.reps != null) "${step.exercise.reps}" else "REST",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (step.exercise.reps != null) "REPS" else "RECOVER",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (totalSeconds != null) {
                FilledTonalButton(
                    onClick = onToggleTimer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            timerRunning -> "Pause · ${formatClock(remaining)}"
                            remaining < totalSeconds -> "Resume · ${formatClock(remaining)}"
                            else -> "Start ${totalSeconds}s timer"
                        }
                    )
                }
            }
        }
    }
}

/** Circular countdown visualization shared by the in-exercise timer and the
 *  between-exercise transition — [progressFraction] 1f is a full ring
 *  (just started / most time left), 0f is empty (finished). Animates
 *  smoothly between the once-a-second integer ticks that drive it rather
 *  than snapping. */
@Composable
fun TimerRing(
    progressFraction: Float,
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    ringThickness: androidx.compose.ui.unit.Dp = 12.dp
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        label = "timerRingProgress"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = ringThickness,
            trackColor = MaterialTheme.colorScheme.surface,
            color = MaterialTheme.colorScheme.primary,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                primaryText,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            if (secondaryText.isNotEmpty()) {
                Text(
                    secondaryText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Brief, skippable "up next" beat between exercises — gives the user a
 *  moment to get into position instead of snapping straight into the next
 *  movement, whether the previous one finished via its own timer or a
 *  manual Done tap. */
@Composable
private fun TransitionScreen(
    nextName: String,
    remaining: Int,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "UP NEXT",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            nextName,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(28.dp))
        TimerRing(
            progressFraction = remaining.toFloat() / TRANSITION_SECONDS,
            primaryText = "$remaining",
            secondaryText = "GET READY",
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onSkip) { Text("Skip") }
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
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Return to workout")
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun prescription(step: GuidedStep, remaining: Int): String = when {
    step.exercise.seconds != null -> "${formatClock(remaining)} remaining"
    step.exercise.reps != null -> "${step.exercise.reps} repetitions"
    step.exercise.isRest -> "Recover, then continue when ready"
    else -> step.exercise.raw
}
