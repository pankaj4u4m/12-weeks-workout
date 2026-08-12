package com.personal.twelveweek.web

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.ProgressStore
import com.personal.twelveweek.Workout
import com.personal.twelveweek.media.ExerciseDbApi
import io.ktor.client.HttpClient
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Same per-rep pacing the program library's own estimatedMinutes figures
 *  are built from — matches the Android app's guided session exactly. */
private const val SECONDS_PER_REP = 2.5
private const val MOTIVATION_CHANCE = 0.5f

/**
 * Web port of the Android app's `ui.GuidedSessionScreen` — same state
 * machine (timed countdown / rep count-up / rep-prep grace / skippable
 * "up next" transition / voice cues / motivational lines / completion),
 * same visuals (circular timer ring, progress-filling Done button). Not
 * ported: exercise media (no wasmJs image/video bridge yet — the movement
 * stage always shows the plain timer/rest card), "Add demos" / API key
 * connect (same reason), and keep-screen-awake (no Screen Wake Lock API
 * bridge yet). None of those are required for a workout to run correctly
 * per this app's own design rules (media is enhancement, never required).
 */
@Composable
fun WebGuidedSessionScreen(
    workout: Workout,
    progress: ProgressStore,
    settings: WebSettings,
    onExit: () -> Unit
) {
    var voiceEnabled by remember { mutableStateOf(settings.voiceEnabled) }
    val transitionSeconds = remember { settings.transitionSeconds }
    val repPrepSeconds = remember { settings.repPrepSeconds }
    val voice = remember { WebVoiceCoach(isEnabled = { settings.voiceEnabled }) }

    val steps = remember(workout) { workout.webGuidedSteps() }

    if (steps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This workout has no exercises.")
        }
        return
    }

    val firstIndex = remember(workout) {
        steps.firstIncompleteIndex { progress.isDone(it) }.coerceIn(0, steps.lastIndex)
    }

    var index by remember(workout) { mutableIntStateOf(firstIndex) }
    var finished by remember(workout) { mutableStateOf(steps.all { progress.isDone(it.key) }) }

    var transitioning by remember(workout) { mutableStateOf(false) }
    var transitionRemaining by remember(workout) { mutableIntStateOf(transitionSeconds) }
    var nextIndex by remember(workout) { mutableIntStateOf(0) }

    var stepCompleted by remember(index) { mutableStateOf(false) }

    fun completeStepAndAdvance(stepKey: String) {
        if (stepCompleted) return
        stepCompleted = true
        progress.setDone(stepKey, true)
        when {
            index == steps.lastIndex -> finished = true
            transitionSeconds <= 0 -> index += 1
            else -> {
                nextIndex = index + 1
                transitionRemaining = transitionSeconds
                transitioning = true
            }
        }
    }

    LaunchedEffect(transitioning) {
        if (!transitioning) return@LaunchedEffect
        delay(450)
        val nextStep = steps[nextIndex]
        voice.speak(
            when {
                nextStep.exercise.isRest -> WebRestCues.start()
                nextStep.sectionTitle.equals("Cool Down", ignoreCase = true) -> WebStretchCues.start()
                else -> "Up next: ${nextStep.exercise.name}"
            }
        )
        while (transitioning && transitionRemaining > 0) {
            delay(1000)
            transitionRemaining -= 1
        }
        if (transitioning) {
            transitioning = false
            index = nextIndex
        }
    }

    val finisherLine = remember(finished) { if (finished) WebFinisherCues.pick() else "" }
    LaunchedEffect(finished) {
        if (finished) voice.speak(finisherLine)
    }

    if (finished) {
        WebSessionCompleteScreen(headline = finisherLine, movementCount = steps.size, onExit = onExit)
        return
    }

    if (transitioning) {
        val nextStep = steps[nextIndex]
        val nextIsCooldown = nextStep.sectionTitle.equals("Cool Down", ignoreCase = true)
        WebTransitionScreen(
            label = when {
                nextStep.exercise.isRest -> "REST UP"
                nextIsCooldown -> "COOL DOWN"
                else -> "UP NEXT"
            },
            nextName = if (nextStep.exercise.isRest) "Rest" else nextStep.exercise.name,
            remaining = transitionRemaining,
            totalSeconds = transitionSeconds,
            onSkip = {
                transitioning = false
                index = nextIndex
            }
        )
        return
    }

    val keyManager = remember { WebApiKeyManager() }
    val mediaRepository = remember { WebExerciseMediaRepository.default(keyManager) }
    val exerciseDbApi = remember { ExerciseDbApi(HttpClient()) }
    var showConnect by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableIntStateOf(0) }
    var hasKey by remember { mutableStateOf(false) }
    LaunchedEffect(keyVersion) { keyManager.get { hasKey = it != null } }

    val step = steps[index]
    val totalSeconds = step.exercise.seconds
    val repsCount = step.exercise.reps

    var mediaBundle by remember(step.key, keyVersion) { mutableStateOf<List<WebMediaPage>>(emptyList()) }
    LaunchedEffect(step.key, keyVersion) {
        mediaBundle = emptyList()
        if (!step.exercise.isRest) mediaBundle = mediaRepository.getBundle(step.exercise)
    }

    var remaining by remember(step.key) { mutableIntStateOf(totalSeconds ?: 0) }
    var timerRunning by remember(step.key) { mutableStateOf(totalSeconds != null) }
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
                voice.speak(
                    when {
                        step.exercise.isRest -> WebRestCues.halfway()
                        step.sectionTitle.equals("Cool Down", ignoreCase = true) -> WebStretchCues.halfway()
                        else -> "Halfway there"
                    }
                )
            }
            if (totalSeconds > 5 && !announcedFiveSecondsLeft && remaining == 5) {
                announcedFiveSecondsLeft = true
                voice.speak(
                    when {
                        step.exercise.isRest -> WebRestCues.almostDone()
                        step.sectionTitle.equals("Cool Down", ignoreCase = true) -> WebStretchCues.almostDone()
                        else -> "5 seconds remaining"
                    }
                )
            }
        }
        if (remaining == 0) {
            timerRunning = false
            webBuzz()
            completeStepAndAdvance(step.key)
        }
    }

    val repTargetSeconds = remember(step.key) {
        repsCount?.let { (it * SECONDS_PER_REP).roundToInt().coerceAtLeast(1) }
    }
    var repGrace by remember(step.key) { mutableIntStateOf(if (repsCount != null) repPrepSeconds else 0) }
    var repElapsed by remember(step.key) { mutableIntStateOf(0) }
    var repRunning by remember(step.key) { mutableStateOf(repsCount != null) }
    var announcedGo by remember(step.key) { mutableStateOf(false) }

    LaunchedEffect(step.key, repRunning) {
        if (repsCount == null || repTargetSeconds == null || !repRunning) return@LaunchedEffect
        if (repPrepSeconds > 0 && repGrace == repPrepSeconds) voice.speak(WebRepCues.getReady())
        while (repRunning && repGrace > 0) {
            delay(1000)
            repGrace -= 1
        }
        if (repRunning && !announcedGo) {
            announcedGo = true
            voice.speak(WebRepCues.go())
        }
        while (repRunning && repElapsed < repTargetSeconds) {
            delay(1000)
            repElapsed += 1
        }
        if (repRunning && repElapsed >= repTargetSeconds) {
            webBuzz()
            completeStepAndAdvance(step.key)
        }
    }

    val inGrace = repsCount != null && repGrace > 0
    val timerActive = when {
        totalSeconds != null -> timerRunning
        repTargetSeconds != null -> repRunning
        else -> true
    }
    fun toggleTimerActive() {
        when {
            totalSeconds != null -> timerRunning = !timerRunning
            repTargetSeconds != null -> repRunning = !repRunning
        }
    }
    val doneVerb = if (index == steps.lastIndex) "Finish workout" else "Done"
    val doneLabel = when {
        totalSeconds != null -> "$doneVerb · ${formatClock(remaining)}"
        repTargetSeconds != null -> if (inGrace) "$doneVerb · Get ready" else "$doneVerb · ${repElapsed}s"
        else -> doneVerb
    }
    val doneProgress = when {
        totalSeconds != null -> 1f - (remaining.toFloat() / totalSeconds.toFloat())
        repTargetSeconds != null && !inGrace -> repElapsed.toFloat() / repTargetSeconds.toFloat()
        else -> 0f
    }

    LaunchedEffect(step.key) {
        if (Random.nextFloat() > MOTIVATION_CHANCE) return@LaunchedEffect
        delay(if (repsCount != null) (repPrepSeconds + 2) * 1000L else 2500L)
        val tags = when {
            step.exercise.isRest -> setOf("general")
            step.sectionTitle.equals("Cool Down", ignoreCase = true) -> setOf("stretch")
            else -> webMotivationTagsFor(step.exercise.name, step.sectionTitle)
        }
        voice.speak(WebMotivationLibrary.pick(tags).text)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Exit workout")
            }
            Column(Modifier.weight(1f)) {
                Text("Week ${workout.week} · ${workout.title}", style = MaterialTheme.typography.titleMedium)
                Text("${index + 1} of ${steps.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                voiceEnabled = !voiceEnabled
                settings.voiceEnabled = voiceEnabled
                if (!voiceEnabled) voice.stop()
            }) {
                Icon(
                    if (voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (voiceEnabled) "Mute voice cues" else "Unmute voice cues"
                )
            }
            if (!hasKey && !step.exercise.isRest) {
                TextButton(onClick = { showConnect = true }) {
                    Text("Add demos")
                }
            }
        }

        ProgressBand(fraction = (index + 1).toFloat() / steps.size, modifier = Modifier.padding(vertical = 10.dp))

        WebMovementStage(
            step = step,
            media = mediaBundle,
            remaining = remaining,
            totalSeconds = totalSeconds,
            timerActive = timerActive,
            onToggleTimerActive = ::toggleTimerActive,
            repGrace = if (repsCount != null) repGrace else null,
            repPrepTotal = repPrepSeconds,
            repElapsed = repElapsed,
            repTarget = repTargetSeconds,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Spacer(Modifier.height(12.dp))
        Text(step.sectionTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(step.exercise.name, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            webPrescription(step, remaining),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(enabled = index > 0, onClick = { index -= 1 }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous exercise")
            }
            WebProgressDoneButton(
                label = doneLabel,
                progress = doneProgress,
                onClick = { completeStepAndAdvance(step.key) },
                modifier = Modifier.weight(1f).height(56.dp)
            )
            FilledTonalIconButton(enabled = index < steps.lastIndex, onClick = { index += 1 }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next exercise")
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
private fun WebMovementStage(
    step: WebGuidedStep,
    media: List<WebMediaPage>,
    remaining: Int,
    totalSeconds: Int?,
    timerActive: Boolean,
    onToggleTimerActive: () -> Unit,
    repGrace: Int?,
    repPrepTotal: Int,
    repElapsed: Int,
    repTarget: Int?,
    modifier: Modifier = Modifier
) {
    val isRepStep = repTarget != null
    val inGrace = isRepStep && (repGrace ?: 0) > 0
    val hasTimer = totalSeconds != null || isRepStep
    val hasMedia = media.isNotEmpty()
    // A big ring sitting on top of moving video/image is hard to read, so
    // once there's media behind it the ring shrinks to a corner badge.
    val badgeSize = if (hasMedia) 112.dp else 280.dp
    val badgeAlignment = if (hasMedia) Alignment.TopEnd else Alignment.Center

    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(
            Modifier.fillMaxSize().let { if (hasTimer) it.clickable(onClick = onToggleTimerActive) else it }
        ) {
            if (hasMedia) {
                WebExerciseMediaCarousel(
                    pages = media,
                    contentDescription = step.exercise.name,
                    modifier = Modifier.fillMaxSize()
                )
            }

            when {
                totalSeconds != null -> {
                    Column(modifier = Modifier.align(badgeAlignment).padding(if (hasMedia) 12.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        WebTimerRing(
                            progressFraction = remaining.toFloat() / totalSeconds,
                            primaryText = formatClock(remaining),
                            paused = !timerActive,
                            ringThickness = if (hasMedia) 6.dp else 12.dp,
                            modifier = Modifier.size(badgeSize)
                        )
                        if (!hasMedia) {
                            Spacer(Modifier.height(6.dp))
                            Text("SECONDS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                isRepStep -> {
                    Column(modifier = Modifier.align(badgeAlignment).padding(if (hasMedia) 12.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (inGrace) {
                            WebTimerRing(
                                progressFraction = (repGrace ?: 0).toFloat() / repPrepTotal.coerceAtLeast(1).toFloat(),
                                primaryText = "${repGrace}",
                                paused = !timerActive,
                                ringThickness = if (hasMedia) 6.dp else 12.dp,
                                modifier = Modifier.size(badgeSize)
                            )
                        } else {
                            WebTimerRing(
                                progressFraction = 1f - (repElapsed.toFloat() / repTarget.toFloat()),
                                primaryText = "${repElapsed}s",
                                paused = !timerActive,
                                ringThickness = if (hasMedia) 6.dp else 12.dp,
                                modifier = Modifier.size(badgeSize)
                            )
                        }
                        if (inGrace && !hasMedia) {
                            Spacer(Modifier.height(6.dp))
                            Text("GET READY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    if (!hasMedia) {
                        ResistanceBandMark(
                            modifier = Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter).padding(horizontal = 28.dp, vertical = 20.dp),
                            muted = true
                        )
                    }
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REST", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        Text("RECOVER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun WebTimerRing(
    progressFraction: Float,
    primaryText: String,
    modifier: Modifier = Modifier,
    ringThickness: Dp = 12.dp,
    paused: Boolean = false
) {
    val clamped = progressFraction.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = clamped, label = "timerRingProgress")
    val urgent = clamped <= 0.2f
    val ringColor by animateColorAsState(
        targetValue = when {
            urgent -> MaterialTheme.colorScheme.error
            clamped <= 0.5f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "timerRingColor"
    )
    val pulse by animateFloatAsState(
        targetValue = if (urgent) 1.05f else 1f,
        animationSpec = tween(500),
        label = "timerRingPulse"
    )

    Box(modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = ringThickness,
            trackColor = ringColor.copy(alpha = 0.16f),
            color = ringColor,
            strokeCap = StrokeCap.Round
        )
        if (paused) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = ringColor, modifier = Modifier.size(56.dp))
        } else {
            Text(
                primaryText,
                modifier = Modifier.padding(ringThickness + 14.dp),
                style = MaterialTheme.typography.displayLarge,
                color = ringColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WebProgressDoneButton(label: String, progress: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "doneButtonProgress")
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
        Box(Modifier.fillMaxSize()) {
            if (animated > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(animated).background(Color.Black.copy(alpha = 0.22f)))
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun WebTransitionScreen(
    nextName: String,
    remaining: Int,
    totalSeconds: Int,
    onSkip: () -> Unit,
    label: String = "UP NEXT"
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(nextName, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(28.dp))
        WebTimerRing(
            progressFraction = remaining.toFloat() / totalSeconds.coerceAtLeast(1),
            primaryText = "$remaining",
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text("GET READY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSkip) { Text("Skip") }
    }
}

@Composable
private fun WebSessionCompleteScreen(movementCount: Int, onExit: () -> Unit, headline: String = "Workout complete") {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ResistanceBandMark(modifier = Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(24.dp))
        Text(headline.ifEmpty { "Workout complete" }, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) { Text("Return to workout") }
    }
}

private fun webPrescription(step: WebGuidedStep, remaining: Int): String = when {
    step.exercise.seconds != null -> "${formatClock(remaining)} remaining"
    step.exercise.reps != null -> "${step.exercise.reps} repetitions"
    step.exercise.isRest -> "Recover, then continue when ready"
    else -> step.exercise.raw
}
