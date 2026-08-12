// THESIS: Home opens on the next workout, not a progress dashboard; it refuses the archive-first week list and dark scoreboard.
// OWN-WORLD: Daylight mineral surfaces, graphite type, cobalt and coral elastic bands, 14–16 dp shapes, and Barlow Semi Condensed.
// STORY: Know what is next, start in one tap, complete a large one-handed session, then see the route advance.
// FIRST VIEWPORT: The current session owns the upper field, its Start action is thumb-reachable, and the twelve-week band remains visible below.
// FORM: Resistance Band Flow, seventh grounded direction, seed e619557c.
// FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md
package com.personal.twelveweek

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.personal.twelveweek.media.ExerciseMediaCarousel
import com.personal.twelveweek.media.ExerciseMediaRepository
import com.personal.twelveweek.media.MediaPage
import com.personal.twelveweek.media.primaryInstructions
import com.personal.twelveweek.programs.IndexEntry
import com.personal.twelveweek.programs.LibraryProgram
import com.personal.twelveweek.programs.ProgramLibrary
import com.personal.twelveweek.programs.ProgramSyncRepository
import com.personal.twelveweek.security.ApiKeyManager
import com.personal.twelveweek.ui.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

private sealed interface Screen {
    data object Today : Screen
    data object Plan : Screen
    data object Programs : Screen
    data class WeekDetail(val week: Int) : Screen
    data class WorkoutDetail(val week: Int, val workout: Int) : Screen
    data class GuidedSession(val week: Int, val workout: Int) : Screen
}

private enum class OnboardingStep { WELCOME, PICK_PLAN }

private data class MainDestination(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val mainDestinations = listOf(
    MainDestination(Screen.Today, "Today", Icons.Filled.Home),
    MainDestination(Screen.Plan, "Plan", Icons.Filled.CalendarMonth),
    MainDestination(Screen.Programs, "Programs", Icons.Filled.FitnessCenter)
)

private data class WorkoutLocation(
    val week: Week,
    val workout: Workout
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val progress = remember { ProgressStore(context) }
    val selectedProgramStore = remember { SelectedProgramStore(context) }
    val library = remember { ProgramLibrary(context) }
    val syncRepo = remember { ProgramSyncRepository.default(context, library) }

    var screen: Screen by remember { mutableStateOf(Screen.Today) }
    var onboarded by remember { mutableStateOf(selectedProgramStore.hasOnboarded()) }
    var onboardingStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var libraryIndex by remember { mutableStateOf<List<IndexEntry>>(emptyList()) }
    var selectedProgramId by remember { mutableStateOf(selectedProgramStore.get()) }
    var activeProgram by remember { mutableStateOf<LibraryProgram?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importScope = rememberCoroutineScope()

    // Manual "upload a program" flow: pick any .json (e.g. one an LLM chat
    // wrote for you following this repo's schema), validate + save it
    // on-device via ProgramLibrary.importProgram, then refresh the picker.
    // Never touches the GitHub-synced cache — a completely separate source.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importScope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                importError = "Couldn't read that file."
                return@launch
            }
            library.importProgram(text).fold(
                onSuccess = {
                    importError = null
                    libraryIndex = library.index()
                },
                onFailure = { e -> importError = e.message ?: "That file isn't a valid program." }
            )
        }
    }

    LaunchedEffect(Unit) {
        libraryIndex = library.index()
        syncRepo.sync()
        libraryIndex = library.index()
    }

    // Re-keyed on libraryIndex too: without it, activeProgram loads once from
    // whatever the cache/bundled asset had at that instant and never picks up
    // the fresher copy the sync above just wrote — e.g. a data-schema change
    // (like estimatedMinutes) landing after the first load would get stuck
    // showing 0 until the user switched programs, even though the sync
    // succeeded and the file on disk was already correct.
    LaunchedEffect(selectedProgramId, libraryIndex) {
        loadFailed = false
        val loaded = library.load(selectedProgramId)
        when {
            loaded != null -> activeProgram = loaded
            selectedProgramId != SelectedProgramStore.DEFAULT_PROGRAM_ID -> {
                selectedProgramStore.set(SelectedProgramStore.DEFAULT_PROGRAM_ID)
                selectedProgramId = SelectedProgramStore.DEFAULT_PROGRAM_ID
            }
            else -> loadFailed = true
        }
    }

    fun selectProgram(id: String) {
        selectedProgramStore.set(id)
        loadFailed = false
        activeProgram = null
        selectedProgramId = id
        screen = Screen.Today
    }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                !onboarded -> OnboardingFlow(
                    step = onboardingStep,
                    entries = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    onShowPlans = { onboardingStep = OnboardingStep.PICK_PLAN },
                    onBack = { onboardingStep = OnboardingStep.WELCOME },
                    onProgramChosen = { id ->
                        selectedProgramStore.set(id)
                        selectedProgramId = id
                        selectedProgramStore.setOnboarded()
                        onboarded = true
                    }
                )

                activeProgram == null && loadFailed -> ProgramPickerScreen(
                    entries = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    onSelect = ::selectProgram,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                )

                activeProgram == null -> LoadingScreen()

                else -> AppShell(
                    program = activeProgram!!,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    screen = screen,
                    onScreenChange = { screen = it },
                    onSelectProgram = ::selectProgram,
                    onImport = { importLauncher.launch("application/json") },
                    importError = importError,
                    onDismissImportError = { importError = null }
                )
            }
        }
    }
}

@Composable
private fun OnboardingFlow(
    step: OnboardingStep,
    entries: List<IndexEntry>,
    selectedProgramId: String,
    onShowPlans: () -> Unit,
    onBack: () -> Unit,
    onProgramChosen: (String) -> Unit
) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomeScreen(onContinue = onShowPlans)
        OnboardingStep.PICK_PLAN -> {
            BackHandler(onBack = onBack)
            ProgramPickerScreen(
                entries = entries,
                selectedProgramId = selectedProgramId,
                onSelect = onProgramChosen,
                onSkip = { onProgramChosen(selectedProgramId) },
                onBack = onBack,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ResistanceBandMark(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
        item {
            Text(
                "Your workout, ready when you are.",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Choose a plan once. TwelveWeek keeps the next home workout close, guides every movement, and remembers where you stopped.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            WelcomeBenefit(
                icon = Icons.Filled.OfflineBolt,
                title = "Works offline",
                body = "Plans, timers, and progress stay useful without a connection."
            )
            Spacer(Modifier.height(18.dp))
            WelcomeBenefit(
                icon = Icons.Filled.TouchApp,
                title = "Built for the middle of a workout",
                body = "Large controls and one exercise at a time keep attention on training."
            )
            Spacer(Modifier.height(18.dp))
            WelcomeBenefit(
                icon = Icons.Filled.RestartAlt,
                title = "Resume without guessing",
                body = "Open the app and continue the first unfinished session."
            )
        }
        item {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
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
private fun WelcomeBenefit(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
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
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Preparing your plan",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun AppShell(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    onSelectProgram: (String) -> Unit,
    onImport: () -> Unit,
    importError: String?,
    onDismissImportError: () -> Unit
) {
    val showMainNavigation = screen is Screen.Today ||
        screen is Screen.Plan ||
        screen is Screen.Programs

    BoxWithConstraints {
        val expanded = maxWidth >= 720.dp

        if (expanded && showMainNavigation) {
            Row(Modifier.fillMaxSize()) {
                AppNavigationRail(
                    selected = screen,
                    onSelect = onScreenChange
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { inner ->
                    AppScreenContent(
                        program = program,
                        libraryIndex = libraryIndex,
                        selectedProgramId = selectedProgramId,
                        progress = progress,
                        screen = screen,
                        onScreenChange = onScreenChange,
                        onSelectProgram = onSelectProgram,
                        onImport = onImport,
                        importError = importError,
                        onDismissImportError = onDismissImportError,
                        modifier = Modifier.padding(inner)
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (showMainNavigation) {
                        AppNavigationBar(
                            selected = screen,
                            onSelect = onScreenChange
                        )
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { inner ->
                AppScreenContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    screen = screen,
                    onScreenChange = onScreenChange,
                    onSelectProgram = onSelectProgram,
                    onImport = onImport,
                    importError = importError,
                    onDismissImportError = onDismissImportError,
                    modifier = Modifier.padding(inner)
                )
            }
        }
    }
}

@Composable
private fun AppScreenContent(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    onSelectProgram: (String) -> Unit,
    onImport: () -> Unit,
    importError: String?,
    onDismissImportError: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (screen) {
        Screen.Today -> TodayScreen(
            weeks = program.weeks,
            programTitle = program.meta.title,
            progress = progress,
            onPreviewWorkout = { week, workout ->
                onScreenChange(Screen.WorkoutDetail(week, workout))
            },
            onStartWorkout = { week, workout ->
                onScreenChange(Screen.GuidedSession(week, workout))
            },
            onOpenPlan = { onScreenChange(Screen.Plan) },
            modifier = modifier
        )

        Screen.Plan -> PlanScreen(
            weeks = program.weeks,
            programTitle = program.meta.title,
            progress = progress,
            onOpenWeek = { onScreenChange(Screen.WeekDetail(it)) },
            modifier = modifier
        )

        Screen.Programs -> ProgramPickerScreen(
            entries = libraryIndex,
            selectedProgramId = selectedProgramId,
            onSelect = onSelectProgram,
            onImport = onImport,
            importError = importError,
            onDismissImportError = onDismissImportError,
            modifier = modifier
        )

        is Screen.WeekDetail -> {
            BackHandler { onScreenChange(Screen.Plan) }
            val week = program.weeks.first { it.number == screen.week }
            WeekDetailScreen(
                week = week,
                progress = progress,
                onBack = { onScreenChange(Screen.Plan) },
                onOpenWorkout = {
                    onScreenChange(Screen.WorkoutDetail(screen.week, it))
                },
                onStartWorkout = {
                    onScreenChange(Screen.GuidedSession(screen.week, it))
                },
                modifier = modifier
            )
        }

        is Screen.WorkoutDetail -> {
            BackHandler { onScreenChange(Screen.WeekDetail(screen.week)) }
            val workout = program.weeks
                .first { it.number == screen.week }
                .workouts.first { it.index == screen.workout }
            WorkoutDetailScreen(
                workout = workout,
                progress = progress,
                onBack = { onScreenChange(Screen.WeekDetail(screen.week)) },
                onStartGuided = {
                    onScreenChange(Screen.GuidedSession(screen.week, screen.workout))
                },
                modifier = modifier
            )
        }

        is Screen.GuidedSession -> {
            val workout = program.weeks
                .first { it.number == screen.week }
                .workouts.first { it.index == screen.workout }
            Box(modifier = modifier.fillMaxSize()) {
                GuidedSessionScreen(
                    workout = workout,
                    progress = progress,
                    onExit = {
                        onScreenChange(Screen.WorkoutDetail(screen.week, screen.workout))
                    }
                )
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    selected: Screen,
    onSelect: (Screen) -> Unit
) {
    NavigationBar {
        mainDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination.screen,
                onClick = { onSelect(destination.screen) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selected: Screen,
    onSelect: (Screen) -> Unit
) {
    NavigationRail(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
        )
    ) {
        Spacer(Modifier.height(12.dp))
        mainDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination.screen,
                onClick = { onSelect(destination.screen) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
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
            Text("TwelveWeek", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                programTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    Text(
                        "Every workout in this plan is marked done. You can review any week or choose another program."
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onOpenPlan,
                        shape = MaterialTheme.shapes.medium
                    ) { Text("Review plan") }
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
            TrainingCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                WeekBand(
                    weekFractions = weekFractions,
                    currentIndex = currentWeekIndex,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${(overall * 100).toInt()}% complete",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = onOpenPlan,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                ) {
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
                    week.workouts.forEachIndexed { index, workout ->
                        CompactWorkoutRow(
                            workout = workout,
                            progress = progress,
                            onClick = { onPreviewWorkout(week.number, workout.index) }
                        )
                        if (index != week.workouts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextWorkoutPanel(
    location: WorkoutLocation,
    progress: ProgressStore,
    onPreview: () -> Unit,
    onStart: () -> Unit
) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "Week ${location.week.number}, ${workout.title}",
                style = MaterialTheme.typography.headlineMedium
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (started) "Resume workout" else "Start workout")
        }
        TextButton(
            onClick = onPreview,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Preview exercises")
        }
    }
}

@Composable
private fun CompactWorkoutRow(
    workout: Workout,
    progress: ProgressStore,
    onClick: () -> Unit
) {
    val keys = workout.allKeys()
    val done = progress.countDone(keys)
    val complete = keys.isNotEmpty() && done == keys.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (complete) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (complete) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    Text("${workout.index}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(workout.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "~${workout.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$done of ${keys.size} movements",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Open ${workout.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Your 12-week plan", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        programTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Plan options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
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
            TrainingCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "$completedWorkouts of ${allWorkouts.size} workouts complete",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                ProgressBand(fraction(progress.countDone(allKeys), allKeys.size))
            }
        }

        gridItems(weeks, key = { it.number }) { week ->
            WeekPlanCard(
                week = week,
                progress = progress,
                current = week.number == currentWeek,
                onClick = { onOpenWeek(week.number) }
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = {
                Text("Every completed movement in every program will be cleared. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        progress.clearEverything()
                        confirmReset = false
                    },
                    shape = MaterialTheme.shapes.medium
                ) { Text("Reset progress") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WeekPlanCard(
    week: Week,
    progress: ProgressStore,
    current: Boolean,
    onClick: () -> Unit
) {
    val keys = week.workouts.flatMap { it.allKeys() }
    val completed = week.workouts.count {
        val workoutKeys = it.allKeys()
        workoutKeys.isNotEmpty() && progress.countDone(workoutKeys) == workoutKeys.size
    }
    TrainingCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        selected = current,
        containerColor = if (current) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Week ${week.number}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (current) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
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
            color = if (current) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
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
    val nextWorkout = week.workouts.firstOrNull { workout ->
        progress.countDone(workout.allKeys()) < workout.totalItems
    }
    val weekKeys = week.workouts.flatMap { it.allKeys() }
    val done = progress.countDone(weekKeys)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Week ${week.number}", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "${week.workouts.size} workouts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            TrainingCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "$done of ${weekKeys.size} movements complete",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                ProgressBand(fraction(done, weekKeys.size))
                nextWorkout?.let { workout ->
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { onStartWorkout(workout.index) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
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
            WorkoutPlanRow(
                workout = workout,
                progress = progress,
                onClick = { onOpenWorkout(workout.index) }
            )
        }
    }
}

@Composable
private fun WorkoutPlanRow(
    workout: Workout,
    progress: ProgressStore,
    onClick: () -> Unit
) {
    val keys = workout.allKeys()
    val done = progress.countDone(keys)
    val rounds = workout.sections.count { it.title.startsWith("Round", ignoreCase = true) }
    TrainingCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (done == keys.size && keys.isNotEmpty()) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(workout.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "~${workout.estimatedMinutes} min",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "$rounds rounds · $done/${keys.size} movements",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    var detail by remember { mutableStateOf<Exercise?>(null) }
    var timerFor by remember { mutableStateOf<Exercise?>(null) }
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Week ${workout.week} · ${workout.title}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "$done of ${keys.size} movements",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Workout options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mark all complete") },
                            leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                            onClick = {
                                progress.setAll(keys, true)
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset workout") },
                            leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                            onClick = {
                                progress.setAll(keys, false)
                                menuExpanded = false
                            }
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
                    Metric(
                        value = "${workout.sections.size}",
                        label = "sections",
                        modifier = Modifier.weight(1f)
                    )
                    Metric(
                        value = "${workout.totalItems}",
                        label = "movements",
                        modifier = Modifier.weight(1f)
                    )
                    Metric(
                        value = "${(fraction(done, keys.size) * 100).toInt()}%",
                        label = "complete",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                ProgressBand(
                    fraction = fraction(done, keys.size),
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStartGuided,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
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
                        ExerciseRow(
                            exercise = exercise,
                            checked = progress.isDone(key),
                            onToggle = { progress.toggle(key) },
                            onOpenDetail = { detail = exercise },
                            onStartTimer = { timerFor = exercise }
                        )
                        if (itemIndex != section.exercises.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { exercise ->
        ExerciseDetailDialog(
            exercise = exercise,
            onStartTimer = {
                detail = null
                timerFor = exercise
            },
            onDismiss = { detail = null }
        )
    }

    timerFor?.let { exercise ->
        CountdownDialog(
            exercise = exercise,
            onDismiss = { timerFor = null }
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: () -> Unit,
    onStartTimer: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenDetail)
                .padding(vertical = 7.dp)
        ) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                when {
                    exercise.seconds != null -> "${exercise.seconds} seconds"
                    exercise.reps != null -> "${exercise.reps} repetitions"
                    exercise.isRest -> "Recovery"
                    else -> exercise.raw
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (exercise.isTimed) {
            IconButton(onClick = onStartTimer) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = "Start ${exercise.seconds} second timer"
                )
            }
        }
        if (!exercise.isRest) {
            IconButton(
                onClick = { openUrl(context, youTubeSearch(exercise.searchQuery)) }
            ) {
                Icon(
                    Icons.Filled.SmartDisplay,
                    contentDescription = "Search videos for ${exercise.name}"
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailDialog(
    exercise: Exercise,
    onStartTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localImage = rememberAssetImage(exercise.slug)
    val keyManager = remember { ApiKeyManager(context) }
    val repository = remember { ExerciseMediaRepository.default(context, keyManager) }
    // null = still loading, emptyList() = confirmed no media. Name/reps text
    // above never waits on this; only the media area shows a spinner.
    var mediaBundle by remember(exercise) { mutableStateOf<List<MediaPage>?>(null) }

    LaunchedEffect(exercise) {
        mediaBundle = null
        mediaBundle = repository.getBundle(exercise)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    when {
                        exercise.seconds != null -> "Hold for ${exercise.seconds} seconds"
                        exercise.reps != null -> "${exercise.reps} repetitions"
                        else -> exercise.raw
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    localImage != null -> {
                        Spacer(Modifier.height(14.dp))
                        Image(
                            bitmap = localImage,
                            contentDescription = exercise.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                    mediaBundle == null -> {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                    mediaBundle!!.isNotEmpty() -> {
                        val bundle = mediaBundle!!
                        Spacer(Modifier.height(14.dp))
                        ExerciseMediaCarousel(
                            pages = bundle,
                            contentDescription = exercise.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        bundle.primaryInstructions().takeIf { it.isNotEmpty() }?.let { instructions ->
                            Spacer(Modifier.height(14.dp))
                            instructions.forEachIndexed { index, instruction ->
                                Text(
                                    "${index + 1}. $instruction",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 7.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { openUrl(context, youTubeSearch(exercise.searchQuery)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.SmartDisplay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch form videos")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openUrl(context, imageSearch(exercise.searchQuery)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.ImageSearch, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("See form images")
                }
                if (exercise.isTimed) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onStartTimer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open ${exercise.seconds}s timer")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun rememberAssetImage(slug: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(slug) {
        listOf("jpg", "jpeg", "png", "webp").firstNotNullOfOrNull { extension ->
            runCatching {
                context.assets.open("exercises/$slug.$extension").use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}

@Composable
private fun CountdownDialog(
    exercise: Exercise,
    onDismiss: () -> Unit
) {
    val total = exercise.seconds ?: return
    val context = LocalContext.current
    var remaining by remember(exercise) { mutableIntStateOf(total) }
    var running by remember(exercise) { mutableStateOf(false) }

    LaunchedEffect(exercise, running) {
        if (!running) return@LaunchedEffect
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0) {
            buzz(context)
            running = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatClock(remaining),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (remaining == 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(16.dp))
                ProgressBand(fraction(remaining, total))
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { running = !running },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                running -> "Pause"
                                remaining < total -> "Resume"
                                else -> "Start"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            remaining = total
                            running = false
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Restart")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

internal fun formatClock(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) String.format("%d:%02d", minutes, remainder) else "$remainder"
}

internal fun buzz(context: Context) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
        )
    }
}

/** Short confirmation tone on exercise/timer completion — audible on top of
 *  [buzz]'s vibration, not a replacement for it (some phones are on silent
 *  vibrate-only, some are on vibrate-off ring modes; giving both cues covers
 *  more real device states than either alone). Releases the ToneGenerator
 *  shortly after the tone finishes so it's never left resident. */
internal fun playCompletionSound() {
    runCatching {
        val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, android.media.ToneGenerator.MAX_VOLUME)
        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 300)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 400)
    }
}
private fun nextWorkout(
    weeks: List<Week>,
    progress: ProgressStore
): WorkoutLocation? = weeks.asSequence()
    .flatMap { week -> week.workouts.asSequence().map { WorkoutLocation(week, it) } }
    .firstOrNull { location ->
        progress.countDone(location.workout.allKeys()) < location.workout.totalItems
    }

private fun fraction(done: Int, total: Int): Float =
    if (total <= 0) 0f else done.toFloat() / total

private fun encode(query: String): String = URLEncoder.encode(query, "UTF-8")

private fun youTubeSearch(query: String): String =
    "https://www.youtube.com/results?search_query=${encode(query)}"

private fun imageSearch(query: String): String =
    "https://www.google.com/search?tbm=isch&q=${encode(query)}"

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
