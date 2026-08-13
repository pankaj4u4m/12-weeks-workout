package com.personal.twelveweek.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.personal.twelveweek.ProgressStore
import com.personal.twelveweek.SelectedProgramStore
import com.personal.twelveweek.Week
import com.personal.twelveweek.Workout
import com.personal.twelveweek.programs.Equipment
import com.personal.twelveweek.programs.FocusArea
import com.personal.twelveweek.programs.IndexEntry
import com.personal.twelveweek.programs.LibraryProgram
import com.personal.twelveweek.programs.ProgramLevel
import com.personal.twelveweek.programs.ProgramLibrary
import com.personal.twelveweek.storage.RawKeyFlagStore
import com.personal.twelveweek.storage.RawPreferenceStore
import kotlinx.coroutines.launch

/**
 * Web entry screen — a from-scratch port of the Android app's Today / Plan /
 * Programs / week / workout screens (see MainActivity.kt's AppRoot/AppShell
 * family and ui/ProgramPickerScreen.kt) into commonMain, sharing the same
 * design system (WebTheme.kt, WebTrainingComponents.kt — copies of
 * DESIGN.md's tokens) and the same fully-shared data layer (ProgramLibrary,
 * ProgressStore, SelectedProgramStore). Lives in its own `web` package
 * (rather than `com.personal.twelveweek`, where MainActivity.kt's
 * near-identical private screen/helper names already live) purely to avoid
 * classfile-name collisions on the Android target, which compiles
 * commonMain and androidMain together (the whole `web` package now lives in
 * wasmJsMain instead, for the same collision-avoidance reason, plus the
 * freedom to call Web Speech/Vibration/Audio APIs directly). Guided session
 * and Settings ARE ported (WebGuidedSession.kt, WebSettingsScreen.kt) —
 * voice cues via the Web Speech API, haptics via the Vibration API,
 * completion tone via the Web Audio API. Not ported: exercise media and the
 * ExerciseDB API-key flow (no wasmJs image/video/Web-Crypto bridge yet —
 * see docs/webapp-android-parity.md).
 */
@Composable
fun WebApp() {
    val library = remember { ProgramLibrary() }
    val progress = remember { ProgressStore(RawKeyFlagStore("twelve_week_progress")) }
    val selectedProgramStore = remember { SelectedProgramStore(RawPreferenceStore("twelve_week_selected_program")) }
    val settings = remember { WebSettings() }
    val installTipState = remember { WebInstallTipState() }
    var showInstallTip by remember { mutableStateOf(false) }

    var index by remember { mutableStateOf<List<IndexEntry>?>(null) }
    var selectedProgramId by remember { mutableStateOf(selectedProgramStore.get()) }
    var activeProgram by remember { mutableStateOf<LibraryProgram?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var screen: WebScreen by remember { mutableStateOf(WebScreen.Today) }
    var onboarded by remember { mutableStateOf(selectedProgramStore.hasOnboarded()) }
    var onboardingStep by remember { mutableStateOf(WebOnboardingStep.WELCOME) }
    val appScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { index = library.index() }

    LaunchedEffect(onboarded) {
        if (onboarded && !installTipState.hasSeenTip()) showInstallTip = true
    }

    LaunchedEffect(selectedProgramId) {
        loadFailed = false
        activeProgram = null
        val loaded = library.load(selectedProgramId)
        if (loaded != null) {
            activeProgram = loaded
        } else if (selectedProgramId != SelectedProgramStore.DEFAULT_PROGRAM_ID) {
            selectedProgramStore.set(SelectedProgramStore.DEFAULT_PROGRAM_ID)
            selectedProgramId = SelectedProgramStore.DEFAULT_PROGRAM_ID
        } else {
            loadFailed = true
        }
    }

    TwelveWeekWebTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val entries = index
            val program = activeProgram
            when {
                !onboarded -> WebOnboardingFlow(
                    step = onboardingStep,
                    entries = entries.orEmpty(),
                    selectedProgramId = selectedProgramId,
                    onShowPlans = { onboardingStep = WebOnboardingStep.PICK_PLAN },
                    onBack = { onboardingStep = WebOnboardingStep.WELCOME },
                    onProgramChosen = { id ->
                        selectedProgramStore.set(id)
                        selectedProgramId = id
                        onboardingStep = WebOnboardingStep.CONNECT_MEDIA
                    },
                    onFinish = {
                        selectedProgramStore.setOnboarded()
                        onboarded = true
                    }
                )

                loadFailed || (entries != null && program == null && entries.isEmpty()) -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Couldn't load a program.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                entries == null || program == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> WebAppShell(
                    program = program,
                    libraryIndex = entries,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    settings = settings,
                    library = library,
                    screen = screen,
                    onScreenChange = { screen = it },
                    onSelectProgram = { id ->
                        selectedProgramStore.set(id)
                        selectedProgramId = id
                        screen = WebScreen.Today
                    },
                    onProgramImported = { appScope.launch { index = library.index() } }
                )
            }

            if (showInstallTip) {
                WebInstallTipDialog(onDismiss = {
                    showInstallTip = false
                    installTipState.markSeen()
                })
            }
        }
    }
}

private sealed interface WebScreen {
    data object Today : WebScreen
    data object Plan : WebScreen
    data object Programs : WebScreen
    data object Settings : WebScreen
    data class WeekDetail(val week: Int) : WebScreen
    data class WorkoutDetail(val week: Int, val workout: Int) : WebScreen
    data class GuidedSession(val week: Int, val workout: Int) : WebScreen
}

private data class WebDestination(val screen: WebScreen, val label: String, val icon: ImageVector)

private val webDestinations = listOf(
    WebDestination(WebScreen.Today, "Today", Icons.Filled.Home),
    WebDestination(WebScreen.Plan, "Plan", Icons.Filled.CalendarMonth),
    WebDestination(WebScreen.Programs, "Programs", Icons.Filled.FitnessCenter)
)

private data class WebWorkoutLocation(val week: Week, val workout: Workout)

private fun nextWorkout(weeks: List<Week>, progress: ProgressStore): WebWorkoutLocation? =
    weeks.asSequence()
        .flatMap { week -> week.workouts.asSequence().map { WebWorkoutLocation(week, it) } }
        .firstOrNull { location -> progress.countDone(location.workout.allKeys()) < location.workout.totalItems }

private fun fraction(done: Int, total: Int): Float = if (total <= 0) 0f else done.toFloat() / total

@Composable
private fun WebAppShell(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    settings: WebSettings,
    library: ProgramLibrary,
    screen: WebScreen,
    onScreenChange: (WebScreen) -> Unit,
    onSelectProgram: (String) -> Unit,
    onProgramImported: () -> Unit
) {
    val showMainNavigation = screen is WebScreen.Today || screen is WebScreen.Plan || screen is WebScreen.Programs

    // BoxWithConstraints branches the same way Android's AppShell does:
    // a NavigationRail beside the content at tablet widths (>=720dp), a
    // bottom NavigationBar below it otherwise. Plain Column/Row instead of
    // Scaffold(bottomBar = ...): on the wasmJs target, Scaffold's bottomBar
    // slot combined with default WindowInsets handling rendered a
    // zero-height bar (see the web build's dev notes) — a weighted content
    // area plus a directly-placed NavigationBar/NavigationRail sidesteps
    // that inset plumbing entirely.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        if (expanded && showMainNavigation) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    webDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = screen == destination.screen,
                            onClick = { onScreenChange(destination.screen) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
                WebAppContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    settings = settings,
                    library = library,
                    screen = screen,
                    onScreenChange = onScreenChange,
                    onSelectProgram = onSelectProgram,
                    onProgramImported = onProgramImported,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                WebAppContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    settings = settings,
                    library = library,
                    screen = screen,
                    onScreenChange = onScreenChange,
                    onSelectProgram = onSelectProgram,
                    onProgramImported = onProgramImported,
                    modifier = Modifier.weight(1f)
                )
                if (showMainNavigation) {
                    NavigationBar {
                        webDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = screen == destination.screen,
                                onClick = { onScreenChange(destination.screen) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebAppContent(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    settings: WebSettings,
    library: ProgramLibrary,
    screen: WebScreen,
    onScreenChange: (WebScreen) -> Unit,
    onSelectProgram: (String) -> Unit,
    onProgramImported: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = modifier
    run {
        when (screen) {
            WebScreen.Today -> TodayScreen(
                weeks = program.weeks,
                programTitle = program.meta.title,
                progress = progress,
                onPreviewWorkout = { week, workout -> onScreenChange(WebScreen.WorkoutDetail(week, workout)) },
                onStartWorkout = { week, workout -> onScreenChange(WebScreen.GuidedSession(week, workout)) },
                onOpenPlan = { onScreenChange(WebScreen.Plan) },
                onOpenSettings = { onScreenChange(WebScreen.Settings) },
                modifier = content
            )

            WebScreen.Plan -> PlanScreen(
                weeks = program.weeks,
                programTitle = program.meta.title,
                progress = progress,
                onOpenWeek = { onScreenChange(WebScreen.WeekDetail(it)) },
                modifier = content
            )

            WebScreen.Programs -> ProgramsScreen(
                entries = libraryIndex,
                selectedProgramId = selectedProgramId,
                onSelect = onSelectProgram,
                library = library,
                onImported = onProgramImported,
                modifier = content
            )

            WebScreen.Settings -> WebSettingsScreen(
                settings = settings,
                onBack = { onScreenChange(WebScreen.Today) },
                modifier = content
            )

            is WebScreen.WeekDetail -> {
                val week = program.weeks.first { it.number == screen.week }
                WeekDetailScreen(
                    week = week,
                    progress = progress,
                    onBack = { onScreenChange(WebScreen.Plan) },
                    onOpenWorkout = { onScreenChange(WebScreen.WorkoutDetail(screen.week, it)) },
                    onStartWorkout = { onScreenChange(WebScreen.GuidedSession(screen.week, it)) },
                    modifier = content
                )
            }

            is WebScreen.WorkoutDetail -> {
                val workout = program.weeks.first { it.number == screen.week }
                    .workouts.first { it.index == screen.workout }
                WorkoutDetailScreen(
                    workout = workout,
                    progress = progress,
                    onBack = { onScreenChange(WebScreen.WeekDetail(screen.week)) },
                    onStartGuided = { onScreenChange(WebScreen.GuidedSession(screen.week, screen.workout)) },
                    modifier = content
                )
            }

            is WebScreen.GuidedSession -> {
                val workout = program.weeks.first { it.number == screen.week }
                    .workouts.first { it.index == screen.workout }
                Box(modifier = content.fillMaxSize()) {
                    WebGuidedSessionScreen(
                        workout = workout,
                        progress = progress,
                        settings = settings,
                        onExit = { onScreenChange(WebScreen.WorkoutDetail(screen.week, screen.workout)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(
    weeks: List<Week>,
    programTitle: String,
    progress: ProgressStore,
    onPreviewWorkout: (Int, Int) -> Unit,
    onStartWorkout: (Int, Int) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val next = nextWorkout(weeks, progress)
    val currentWeek = next?.week ?: weeks.lastOrNull()
    val allKeys = remember(weeks) { weeks.flatMap { it.workouts }.flatMap { it.allKeys() } }
    val overall = fraction(progress.countDone(allKeys), allKeys.size)
    val weekFractions = weeks.map { week ->
        val keys = week.workouts.flatMap { it.allKeys() }
        fraction(progress.countDone(keys), keys.size)
    }
    val currentWeekIndex = weeks.indexOfFirst { it.number == currentWeek?.number }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("TwelveWeek", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(3.dp))
                    Text(programTitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        item {
            if (next == null) {
                TrainingCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text("All 12 weeks complete", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Every workout in this plan is marked done. You can review any week or choose another program.")
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onOpenPlan, shape = MaterialTheme.shapes.medium) { Text("Review plan") }
                }
            } else {
                NextWorkoutPanel(
                    location = next,
                    progress = progress,
                    onPreview = { onPreviewWorkout(next.week.number, next.workout.index) },
                    onStart = { onStartWorkout(next.week.number, next.workout.index) }
                )
            }
        }

        item {
            Text("Your 12-week route", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            TrainingCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                WeekBand(weekFractions = weekFractions, currentIndex = currentWeekIndex, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("${(overall * 100).toInt()}% complete", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenPlan, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                    Text("See the full plan")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }

        currentWeek?.let { week ->
            item {
                Text("This week", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                TrainingCard(modifier = Modifier.fillMaxWidth()) {
                    week.workouts.forEachIndexed { i, workout ->
                        CompactWorkoutRow(workout = workout, progress = progress, onClick = { onPreviewWorkout(week.number, workout.index) })
                        if (i != week.workouts.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextWorkoutPanel(location: WebWorkoutLocation, progress: ProgressStore, onPreview: () -> Unit, onStart: () -> Unit) {
    val workout = location.workout
    val keys = workout.allKeys()
    val done = progress.countDone(keys)
    val started = done > 0
    val rounds = workout.sections.count { it.title.startsWith("Round", ignoreCase = true) }

    TrainingCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        contentPadding = 20.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Week ${location.week.number}, ${workout.title}", style = MaterialTheme.typography.headlineMedium)
            Text(
                "~${workout.estimatedMinutes} min",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "$rounds rounds · ${workout.totalItems} movements",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
        )
        Spacer(Modifier.height(18.dp))
        ProgressBand(
            fraction = fraction(done, keys.size),
            activeColor = MaterialTheme.colorScheme.primary,
            markerColor = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (started) "$done movements complete" else "Ready to start",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (started) "Resume workout" else "Start workout")
        }
        TextButton(onClick = onPreview, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Preview exercises")
        }
    }
}

@Composable
private fun CompactWorkoutRow(workout: Workout, progress: ProgressStore, onClick: () -> Unit) {
    val keys = workout.allKeys()
    val done = progress.countDone(keys)
    val complete = keys.isNotEmpty() && done == keys.size
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (complete) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (complete) {
                    Icon(Icons.Filled.Check, contentDescription = "Complete", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                } else {
                    Text("${workout.index}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(workout.title, style = MaterialTheme.typography.titleMedium)
                Text("~${workout.estimatedMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$done of ${keys.size} movements", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open ${workout.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanScreen(
    weeks: List<Week>,
    programTitle: String,
    progress: ProgressStore,
    onOpenWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val allWorkouts = weeks.flatMap { it.workouts }
    val allKeys = remember(weeks) { allWorkouts.flatMap { it.allKeys() } }
    val completedWorkouts = allWorkouts.count { workout ->
        val keys = workout.allKeys()
        keys.isNotEmpty() && progress.countDone(keys) == keys.size
    }
    val currentWeek = nextWorkout(weeks, progress)?.week?.number

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Your 12-week plan", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(3.dp))
                    Text(programTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Plan options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Reset all progress") },
                            leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                confirmReset = true
                            }
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            TrainingCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text("$completedWorkouts of ${allWorkouts.size} workouts complete", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ProgressBand(fraction(progress.countDone(allKeys), allKeys.size))
            }
        }

        gridItems(weeks, key = { it.number }) { week ->
            WeekPlanCard(week = week, progress = progress, current = week.number == currentWeek, onClick = { onOpenWeek(week.number) })
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("Every completed movement in every program will be cleared. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { progress.clearEverything(); confirmReset = false }, shape = MaterialTheme.shapes.medium) {
                    Text("Reset progress")
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun WeekPlanCard(week: Week, progress: ProgressStore, current: Boolean, onClick: () -> Unit) {
    val keys = week.workouts.flatMap { it.allKeys() }
    val completed = week.workouts.count {
        val workoutKeys = it.allKeys()
        workoutKeys.isNotEmpty() && progress.countDone(workoutKeys) == workoutKeys.size
    }
    TrainingCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        selected = current,
        containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Week ${week.number}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (current) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "Current",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ProgressBand(fraction(progress.countDone(keys), keys.size))
        Spacer(Modifier.height(8.dp))
        Text(
            "$completed of ${week.workouts.size} workouts",
            style = MaterialTheme.typography.bodySmall,
            color = if (current) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeekDetailScreen(
    week: Week,
    progress: ProgressStore,
    onBack: () -> Unit,
    onOpenWorkout: (Int) -> Unit,
    onStartWorkout: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextIncomplete = week.workouts.firstOrNull { workout -> progress.countDone(workout.allKeys()) < workout.totalItems }
    val weekKeys = week.workouts.flatMap { it.allKeys() }
    val done = progress.countDone(weekKeys)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Week ${week.number}", style = MaterialTheme.typography.headlineLarge)
                    Text("${week.workouts.size} workouts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            TrainingCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text("$done of ${weekKeys.size} movements complete", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ProgressBand(fraction(done, weekKeys.size))
                nextIncomplete?.let { workout ->
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { onStartWorkout(workout.index) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start ${workout.title}")
                    }
                }
            }
        }

        items(week.workouts, key = { it.index }) { workout ->
            WorkoutPlanRow(workout = workout, progress = progress, onClick = { onOpenWorkout(workout.index) })
        }
    }
}

@Composable
private fun WorkoutPlanRow(workout: Workout, progress: ProgressStore, onClick: () -> Unit) {
    val keys = workout.allKeys()
    val done = progress.countDone(keys)
    val rounds = workout.sections.count { it.title.startsWith("Round", ignoreCase = true) }
    TrainingCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (done == keys.size && keys.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (done == keys.size && keys.isNotEmpty()) {
                        Icon(Icons.Filled.Check, contentDescription = "Complete")
                    } else {
                        Text("${workout.index}", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(workout.title, style = MaterialTheme.typography.titleLarge)
                    Text("~${workout.estimatedMinutes} min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(3.dp))
                Text("$rounds rounds · $done/${keys.size} movements", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open workout")
        }
        Spacer(Modifier.height(12.dp))
        ProgressBand(fraction(done, keys.size))
    }
}

@Composable
private fun WorkoutDetailScreen(
    workout: Workout,
    progress: ProgressStore,
    onBack: () -> Unit,
    onStartGuided: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val keys = remember(workout) { workout.allKeys() }
    val done = progress.countDone(keys)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Week ${workout.week} · ${workout.title}", style = MaterialTheme.typography.headlineMedium)
                    Text("$done of ${keys.size} movements", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Workout options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Mark all complete") },
                            leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                            onClick = { progress.setAll(keys, true); menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset workout") },
                            leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                            onClick = { progress.setAll(keys, false); menuExpanded = false }
                        )
                    }
                }
            }
        }

        item {
            TrainingCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row {
                    Metric(value = "${workout.sections.size}", label = "sections", modifier = Modifier.weight(1f))
                    Metric(value = "${workout.totalItems}", label = "movements", modifier = Modifier.weight(1f))
                    Metric(value = "${(fraction(done, keys.size) * 100).toInt()}%", label = "complete", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                ProgressBand(fraction = fraction(done, keys.size), trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f))
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStartGuided,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (done > 0) "Resume guided workout" else "Start guided workout")
                }
            }
        }

        workout.sections.forEachIndexed { sectionIndex, section ->
            item(key = "section-$sectionIndex") {
                TrainingCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    section.exercises.forEachIndexed { itemIndex, exercise ->
                        val key = workout.keyFor(sectionIndex, itemIndex)
                        val checked = progress.isDone(key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { progress.toggle(key) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (checked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                    if (checked) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                exercise.raw,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (itemIndex != section.exercises.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgramsScreen(
    entries: List<IndexEntry>,
    selectedProgramId: String,
    onSelect: (String) -> Unit,
    library: ProgramLibrary?,
    onImported: () -> Unit,
    onSkip: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var levelFilter by remember { mutableStateOf<ProgramLevel?>(null) }
    var focusFilter by remember { mutableStateOf<FocusArea?>(null) }
    var equipmentFilter by remember { mutableStateOf<Equipment?>(null) }
    var durationFilter by remember { mutableStateOf<Int?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importScope = rememberCoroutineScope()

    val allFocusAreas = remember(entries) { entries.flatMap { it.meta.focusAreas }.distinct() }
    val allEquipment = remember(entries) { entries.flatMap { it.meta.equipment }.distinct() }
    val hasFilters = levelFilter != null || focusFilter != null || equipmentFilter != null || durationFilter != null

    val filtered = entries.filter { entry ->
        (levelFilter == null || entry.meta.level == levelFilter) &&
            (focusFilter == null || focusFilter in entry.meta.focusAreas) &&
            (equipmentFilter == null || equipmentFilter in entry.meta.equipment) &&
            (durationFilter == null || entry.meta.sessionMinutes == 0 || entry.meta.sessionMinutes <= durationFilter!!)
    }

    fun startImport() {
        val lib = library ?: return
        pickJsonFile { text ->
            if (text.isBlank()) return@pickJsonFile
            importScope.launch {
                lib.importProgram(text).fold(
                    onSuccess = {
                        importError = null
                        onImported()
                    },
                    onFailure = { e -> importError = e.message ?: "That file isn't a valid program." }
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            if (onBack != null || onSkip != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    onBack?.let {
                        IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                    Spacer(Modifier.weight(1f))
                    onSkip?.let {
                        TextButton(onClick = it) { Text("Use recommended plan") }
                    }
                }
            }
            Text("Choose your plan", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a level and setup that work in your space. Switching plans never erases progress.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (library != null) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = ::startImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import a program (.json)")
                }
            }
            importError?.let { message ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { importError = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                ProgramFilterRow(
                    label = "Level",
                    options = ProgramLevel.entries.toList(),
                    selected = levelFilter,
                    text = { it.label() },
                    onToggle = { levelFilter = if (levelFilter == it) null else it }
                )
            }
            if (allFocusAreas.size > 1) {
                item {
                    ProgramFilterRow(
                        label = "Focus",
                        options = allFocusAreas,
                        selected = focusFilter,
                        text = { it.label() },
                        onToggle = { focusFilter = if (focusFilter == it) null else it }
                    )
                }
            }
            if (allEquipment.size > 1) {
                item {
                    ProgramFilterRow(
                        label = "Space",
                        options = allEquipment,
                        selected = equipmentFilter,
                        text = { it.label() },
                        onToggle = { equipmentFilter = if (equipmentFilter == it) null else it }
                    )
                }
            }
            item {
                ProgramFilterRow(
                    label = "Time per day",
                    options = listOf(10, 15, 20, 30, 45, 60),
                    selected = durationFilter,
                    text = { "≤ $it min" },
                    onToggle = { durationFilter = if (durationFilter == it) null else it }
                )
            }
            if (hasFilters) {
                item {
                    TextButton(onClick = { levelFilter = null; focusFilter = null; equipmentFilter = null; durationFilter = null }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear filters")
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    TrainingCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("No plans match those filters", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Clear a filter to see more options.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filtered, key = { it.meta.id }) { entry ->
                    ProgramCard(entry = entry, selected = entry.meta.id == selectedProgramId, onClick = { onSelect(entry.meta.id) })
                }
            }
        }
    }
}

@Composable
private fun <T> ProgramFilterRow(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onToggle: (T) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onToggle(option) },
                    label = { Text(text(option)) },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(entry: IndexEntry, selected: Boolean, onClick: () -> Unit) {
    val meta = entry.meta
    TrainingCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, selected = selected, contentPadding = 18.dp,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(meta.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${meta.level.label()} · ${meta.weekCount} weeks · " +
                        meta.equipment.joinToString("/") { it.label() } +
                        if (meta.sessionMinutes > 0) " · ~${meta.sessionMinutes} min/day" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Current plan",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }
            }
        }
        if (meta.focusAreas.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                meta.focusAreas.joinToString(" · ") { it.label() },
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (selected) "Current plan" else "Use this plan",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
        )
    }
}

private fun ProgramLevel.label() = when (this) {
    ProgramLevel.BEGINNER -> "Beginner"
    ProgramLevel.INTERMEDIATE -> "Intermediate"
    ProgramLevel.ADVANCED -> "Advanced"
}

private fun FocusArea.label() = when (this) {
    FocusArea.FULL_BODY -> "Full body"
    FocusArea.LEGS -> "Legs"
    FocusArea.ABS -> "Abs"
    FocusArea.CORE -> "Core"
    FocusArea.UPPER_BODY -> "Upper body"
    FocusArea.STRENGTH -> "Strength"
}

private fun Equipment.label() = when (this) {
    Equipment.HOME -> "At home"
    Equipment.GYM -> "Gym"
}
