package com.personal.twelveweek.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.personal.twelveweek.Milestone
import com.personal.twelveweek.MilestoneKind
import com.personal.twelveweek.MilestoneTracker
import com.personal.twelveweek.ProgressStore
import com.personal.twelveweek.Week
import com.personal.twelveweek.Workout
import com.personal.twelveweek.buzz
import com.personal.twelveweek.festiveBuzz
import com.personal.twelveweek.formatClock
import com.personal.twelveweek.media.ExerciseMediaCarousel
import com.personal.twelveweek.media.ExerciseMediaRepository
import com.personal.twelveweek.media.GuidedStep
import com.personal.twelveweek.media.MediaPage
import com.personal.twelveweek.media.firstIncompleteIndex
import com.personal.twelveweek.media.guidedSteps
import com.personal.twelveweek.security.ApiKeyManager
import com.personal.twelveweek.settings.AppSettings
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Same per-rep pacing the program library's own estimatedMinutes figures
 *  are built from — reused here so a rep set's auto-advance timing matches
 *  what the program picker already promised the user for this workout. */
private const val SECONDS_PER_REP = 2.5

/** Chance a motivational cue is shown for any given step — kept under 1.0
 *  so cues feel like an occasional nudge rather than commentary on every
 *  single movement. */
private const val MOTIVATION_CHANCE = 0.5f

@Composable
fun GuidedSessionScreen(
    workout: Workout,
    weeks: List<Week>,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var voiceEnabled by remember { mutableStateOf(settings.voiceEnabled) }
    val transitionSeconds = remember { settings.transitionSeconds }
    val repPrepSeconds = remember { settings.repPrepSeconds }
    val activity = remember(context) { context.findActivity() }
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Keep the screen awake for the whole session — workouts run hands-off
    // for minutes at a time and the phone shouldn't lock mid-set. Cleared on
    // exit so it doesn't leak the flag onto the rest of the app.
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    var transitionRemaining by remember(workout) { mutableIntStateOf(transitionSeconds) }
    var nextIndex by remember(workout) { mutableIntStateOf(0) }

    val voice = rememberVoiceCoach(isEnabled = { settings.voiceEnabled })

    BackHandler(onBack = onExit)

    // Guards against completing/advancing the same step twice — e.g. its
    // own countdown hits zero the instant a manual "Done" tap is also in
    // flight. Without this a double-fire could skip two steps at once.
    var stepCompleted by remember(index) { mutableStateOf(false) }

    fun completeStepAndAdvance(stepKey: String) {
        if (stepCompleted) return
        stepCompleted = true
        progress.setDone(stepKey, true)
        when {
            index == steps.lastIndex -> finished = true
            transitionSeconds <= 0 -> index += 1 // "time between exercises" turned off in Settings
            else -> {
                nextIndex = index + 1
                transitionRemaining = transitionSeconds
                transitioning = true
            }
        }
    }

    LaunchedEffect(transitioning) {
        if (!transitioning) return@LaunchedEffect
        // The completion buzz/beep fires right as this flips true — give it a
        // beat to finish before the "Up next" voice cue starts, so the two
        // don't talk over each other.
        delay(450)
        val nextStep = steps[nextIndex]
        voice.speak(
            when {
                nextStep.exercise.isRest -> RestCues.start()
                nextStep.sectionTitle.equals("Cool Down", ignoreCase = true) -> StretchCues.start()
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

    val finisherLine = remember(finished) { if (finished) FinisherCues.pick() else "" }
    LaunchedEffect(finished) {
        if (finished) voice.speak(finisherLine)
    }

    val newMilestones = remember(finished) {
        if (!finished) return@remember emptyList<Milestone>()
        val workoutsCompleted = weeks.flatMap { it.workouts }.count { w ->
            val k = w.allKeys()
            k.isNotEmpty() && progress.countDone(k) == k.size
        }
        milestones.checkAndConsume(progress.currentStreak(), workoutsCompleted)
    }

    if (finished) {
        SessionCompleteScreen(
            headline = finisherLine,
            movementCount = steps.size,
            streakDays = progress.currentStreak(),
            newMilestones = newMilestones,
            onExit = onExit
        )
        return
    }

    if (transitioning) {
        val nextStep = steps[nextIndex]
        val nextIsCooldown = nextStep.sectionTitle.equals("Cool Down", ignoreCase = true)
        TransitionScreen(
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
    val repsCount = step.exercise.reps

    // ---- timed step: auto-starting countdown; the button on-screen just
    // lets the user pause/resume, it no longer has to be tapped to begin ----
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
                        step.exercise.isRest -> RestCues.halfway()
                        step.sectionTitle.equals("Cool Down", ignoreCase = true) -> StretchCues.halfway()
                        else -> "Halfway there"
                    }
                )
            }
            if (totalSeconds > 5 && !announcedFiveSecondsLeft && remaining == 5) {
                announcedFiveSecondsLeft = true
                voice.speak(
                    when {
                        step.exercise.isRest -> RestCues.almostDone()
                        step.sectionTitle.equals("Cool Down", ignoreCase = true) -> StretchCues.almostDone()
                        else -> "5 seconds remaining"
                    }
                )
            }
        }
        if (remaining == 0) {
            timerRunning = false
            // Haptic only here — the audible completion tone used to clash
            // with the "Up next"/rest voice cue that fires right after.
            buzz(context)
            completeStepAndAdvance(step.key)
        }
    }

    // ---- rep-based step: brief "get ready" grace, then an auto-advancing
    // count-up paced at the same 2.5s/rep the program's own time estimate
    // uses, so what happens here matches what the picker promised ----
    val repTargetSeconds = remember(step.key) {
        repsCount?.let { (it * SECONDS_PER_REP).roundToInt().coerceAtLeast(1) }
    }
    var repGrace by remember(step.key) { mutableIntStateOf(if (repsCount != null) repPrepSeconds else 0) }
    var repElapsed by remember(step.key) { mutableIntStateOf(0) }
    var repRunning by remember(step.key) { mutableStateOf(repsCount != null) }
    var announcedGo by remember(step.key) { mutableStateOf(false) }

    LaunchedEffect(step.key, repRunning) {
        if (repsCount == null || repTargetSeconds == null || !repRunning) return@LaunchedEffect
        if (repPrepSeconds > 0 && repGrace == repPrepSeconds) voice.speak(RepCues.getReady())
        while (repRunning && repGrace > 0) {
            delay(1000)
            repGrace -= 1
        }
        if (repRunning && !announcedGo) {
            announcedGo = true
            voice.speak(RepCues.go())
        }
        while (repRunning && repElapsed < repTargetSeconds) {
            delay(1000)
            repElapsed += 1
        }
        if (repRunning && repElapsed >= repTargetSeconds) {
            buzz(context)
            completeStepAndAdvance(step.key)
        }
    }

    // ---- unified pause control (tap the big ring) and Done-button progress
    // mirror — whichever timer applies to this step (countdown or rep
    // count-up) drives both ----
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

    // ---- motivational cue: a short, tag-matched line shown/spoken once per
    // step at most, well after the "up next"/"get ready"/"go" cues so it
    // never talks over them (rep steps have their own configurable prep
    // delay, so wait that out plus a beat), and only some of the time so it
    // doesn't nag ----
    LaunchedEffect(step.key) {
        if (Random.nextFloat() > MOTIVATION_CHANCE) return@LaunchedEffect
        delay(if (repsCount != null) (repPrepSeconds + 2) * 1000L else 2500L)
        val tags = when {
            step.exercise.isRest -> setOf("general")
            step.sectionTitle.equals("Cool Down", ignoreCase = true) -> setOf("stretch")
            else -> motivationTagsFor(step.exercise.name, step.sectionTitle)
        }
        voice.speak(MotivationLibrary.pick(tags).text)
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

        ProgressBand(
            fraction = (index + 1).toFloat() / steps.size,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        MovementStage(
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
            ProgressDoneButton(
                label = doneLabel,
                progress = doneProgress,
                onClick = { completeStepAndAdvance(step.key) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )
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
    timerActive: Boolean,
    onToggleTimerActive: () -> Unit,
    repGrace: Int?,
    repPrepTotal: Int,
    repElapsed: Int,
    repTarget: Int?,
    modifier: Modifier = Modifier
) {
    val hasMedia = media.isNotEmpty()
    val isRepStep = repTarget != null
    val inGrace = isRepStep && (repGrace ?: 0) > 0
    val hasTimer = totalSeconds != null || isRepStep
    // A big ring sitting on top of moving video/image is hard to read, so
    // once there's media behind it the ring shrinks to a corner badge —
    // pausing/resuming is then done by tapping anywhere on the stage rather
    // than needing to hit the (now small) ring itself.
    val badgeSize = if (hasMedia) 112.dp else 280.dp
    val badgeAlignment = if (hasMedia) Alignment.TopEnd else Alignment.Center

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (hasTimer) Modifier.clickable(onClick = onToggleTimerActive) else Modifier)
        ) {
            if (hasMedia) {
                ExerciseMediaCarousel(
                    pages = media,
                    contentDescription = step.exercise.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            when {
                totalSeconds != null -> {
                    Column(
                        modifier = Modifier.align(badgeAlignment),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassTimerBadge(
                            overImage = hasMedia,
                            size = badgeSize,
                            modifier = Modifier.padding(if (hasMedia) 20.dp else 0.dp)
                        ) {
                            TimerRing(
                                progressFraction = remaining.toFloat() / totalSeconds,
                                primaryText = formatClock(remaining),
                                paused = !timerActive,
                                compact = hasMedia,
                                modifier = Modifier.fillMaxSize(),
                                ringThickness = if (hasMedia) 6.dp else 12.dp
                            )
                        }
                        if (!hasMedia) {
                            Spacer(Modifier.height(6.dp))
                            Text("SECONDS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                isRepStep -> {
                    Column(
                        modifier = Modifier.align(badgeAlignment),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassTimerBadge(
                            overImage = hasMedia,
                            size = badgeSize,
                            modifier = Modifier.padding(if (hasMedia) 20.dp else 0.dp)
                        ) {
                            if (inGrace) {
                                TimerRing(
                                    progressFraction = (repGrace ?: 0).toFloat() / repPrepTotal.coerceAtLeast(1).toFloat(),
                                    primaryText = "${repGrace}",
                                    paused = !timerActive,
                                    compact = hasMedia,
                                    modifier = Modifier.fillMaxSize(),
                                    ringThickness = if (hasMedia) 6.dp else 12.dp
                                )
                            } else {
                                TimerRing(
                                    progressFraction = 1f - (repElapsed.toFloat() / repTarget.toFloat()),
                                    primaryText = "${repElapsed}s",
                                    paused = !timerActive,
                                    compact = hasMedia,
                                    modifier = Modifier.fillMaxSize(),
                                    ringThickness = if (hasMedia) 6.dp else 12.dp
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (!hasMedia) {
                        ResistanceBandMark(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 28.dp, vertical = 20.dp),
                            muted = true
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "REST",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "RECOVER",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

        }
    }
}

/** No backing panel — the ring and its text sit directly on the video/image,
 *  just dialed down to partial opacity ([overImage]) so they read as an
 *  overlay rather than a flat badge floating on top of it. */
@Composable
private fun GlassTimerBadge(
    overImage: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (overImage) Modifier.graphicsLayer(alpha = 0.82f) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Circular countdown/count-up visualization shared by the in-exercise
 *  timer and the between-exercise transition — [progressFraction] 1f is a
 *  full ring (just started / most time left), 0f is empty (finished/done).
 *  Animates smoothly between the once-a-second integer ticks that drive it
 *  rather than snapping, shifts from primary → tertiary → error as time
 *  runs low so the last few seconds read as urgent at a glance — no scale
 *  animation on the ring itself, since that risked clipping against the
 *  media card's rounded corners in the compact corner-badge layout. */
@Composable
fun TimerRing(
    progressFraction: Float,
    primaryText: String,
    modifier: Modifier = Modifier,
    ringThickness: Dp = 12.dp,
    paused: Boolean = false,
    compact: Boolean = false
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
    Box(
        modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = ringThickness,
            trackColor = ringColor.copy(alpha = 0.16f),
            color = ringColor,
            strokeCap = StrokeCap.Round
        )
        if (paused) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Resume",
                tint = ringColor,
                modifier = Modifier.size(if (compact) 22.dp else 56.dp)
            )
        } else {
            // Padded in from the stroke so the number never crowds the ring's
            // edge — any secondary label ("GET READY", "SECONDS"...) lives
            // outside/below the ring entirely, not squeezed in here too.
            Text(
                primaryText,
                modifier = Modifier.padding(ringThickness + if (compact) 6.dp else 14.dp),
                style = if (compact) {
                    val base = MaterialTheme.typography.headlineSmall
                    base.copy(fontSize = base.fontSize * 2)
                } else MaterialTheme.typography.displayLarge,
                color = ringColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** The bottom "Done"/"Finish workout" button doubles as a live progress bar
 *  — [progress] (0f..1f) fills in behind the label as the same countdown or
 *  rep count-up driving the big ring plays out, so the two stay in sync. */
@Composable
private fun ProgressDoneButton(
    label: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "doneButtonProgress")
    // Full-width wash again, but darkening the primary base instead of
    // lightening it — this theme's primary is already a light lavender, so a
    // white overlay nearly disappeared into it. A dark overlay stays clearly
    // visible as a distinct, deeper shade while keeping onPrimary text
    // legible (darkening a light background only helps dark-on-light
    // contrast, never hurts it).
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(Modifier.fillMaxSize()) {
            if (animated > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated)
                        .background(Color.Black.copy(alpha = 0.22f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
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

/** Brief, skippable "up next" beat between exercises — gives the user a
 *  moment to get into position instead of snapping straight into the next
 *  movement, whether the previous one finished via its own timer or a
 *  manual Done tap. */
@Composable
private fun TransitionScreen(
    nextName: String,
    remaining: Int,
    totalSeconds: Int,
    onSkip: () -> Unit,
    label: String = "UP NEXT"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
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
            progressFraction = remaining.toFloat() / totalSeconds.coerceAtLeast(1),
            primaryText = "$remaining",
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onSkip) { Text("Skip") }
    }
}

@Composable
private fun SessionCompleteScreen(
    movementCount: Int,
    streakDays: Int,
    newMilestones: List<Milestone>,
    onExit: () -> Unit,
    headline: String = "Workout complete"
) {
    val context = LocalContext.current
    LaunchedEffect(newMilestones) {
        if (newMilestones.isNotEmpty()) festiveBuzz(context)
    }
    Box(modifier = Modifier.fillMaxSize()) {
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
                headline.ifEmpty { "Workout complete" },
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            if (streakDays >= 2) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🔥 $streakDays day streak",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "$movementCount movements recorded. Your plan is ready for the next session.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            newMilestones.forEach { milestone ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "🎉 New milestone: ${milestoneLabel(milestone)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }
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
        ConfettiBurst(modifier = Modifier.fillMaxSize())
    }
}

private fun milestoneLabel(milestone: Milestone): String = when (milestone.kind) {
    MilestoneKind.STREAK -> "${milestone.threshold}-day streak!"
    MilestoneKind.WORKOUTS -> "${milestone.threshold} workouts done!"
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
