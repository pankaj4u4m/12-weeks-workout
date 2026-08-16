package com.personal.twelveweek.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.personal.twelveweek.Week
import com.personal.twelveweek.programs.Equipment
import com.personal.twelveweek.programs.FocusArea
import com.personal.twelveweek.programs.IndexEntry
import com.personal.twelveweek.programs.LibraryProgram
import com.personal.twelveweek.programs.ProgramLevel
import com.personal.twelveweek.programs.ProgramLibrary
import kotlinx.coroutines.launch

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

@Composable
fun ProgramPickerScreen(
    entries: List<IndexEntry>,
    selectedProgramId: String,
    library: ProgramLibrary,
    onSelect: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    /** Launches a file picker for a hand-authored/LLM-generated program
     *  `.json` — null hides the import entry point entirely (e.g. during
     *  first-run onboarding, where we don't want to introduce this yet). */
    onImport: (() -> Unit)? = null,
    importError: String? = null,
    onDismissImportError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var levelFilter by remember { mutableStateOf<ProgramLevel?>(null) }
    var focusFilter by remember { mutableStateOf<FocusArea?>(null) }
    var equipmentFilter by remember { mutableStateOf<Equipment?>(null) }
    // "Up to N minutes/day" — an independent filter dimension, same pattern as
    // level/focus/space. Any level can be paired with any time budget; picking
    // one narrows which programs show, same as the others (never a separate
    // per-time program set to author — sessionMinutes is precomputed once in
    // the GitHub repo per program, this just filters on it).
    var durationFilter by remember { mutableStateOf<Int?>(null) }
    var previewCache by remember { mutableStateOf<Map<String, LibraryProgram>>(emptyMap()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val previewScope = rememberCoroutineScope()

    fun toggleExpand(id: String) {
        expandedId = if (expandedId == id) null else id
        if (id !in previewCache) {
            previewScope.launch {
                library.load(id)?.let { loaded -> previewCache = previewCache + (id to loaded) }
            }
        }
    }

    val allFocusAreas = remember(entries) { entries.flatMap { it.meta.focusAreas }.distinct() }
    val allEquipment = remember(entries) { entries.flatMap { it.meta.equipment }.distinct() }
    val hasFilters = levelFilter != null || focusFilter != null || equipmentFilter != null || durationFilter != null

    val filtered = entries.filter { entry ->
        (levelFilter == null || entry.meta.level == levelFilter) &&
            (focusFilter == null || focusFilter in entry.meta.focusAreas) &&
            (equipmentFilter == null || equipmentFilter in entry.meta.equipment) &&
            (durationFilter == null || entry.meta.sessionMinutes == 0 || entry.meta.sessionMinutes <= durationFilter!!)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onBack?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Spacer(Modifier.weight(1f))
                onSkip?.let {
                    TextButton(onClick = it) { Text("Use recommended plan") }
                }
            }
            Text("Choose your plan", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a level and setup that work in your space. Switching plans never erases progress.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            onImport?.let {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
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
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissImportError) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                FilterRow(
                    label = "Level",
                    options = ProgramLevel.entries.toList(),
                    selected = levelFilter,
                    text = { it.label() },
                    onToggle = { levelFilter = if (levelFilter == it) null else it }
                )
            }
            if (allFocusAreas.size > 1) {
                item {
                    FilterRow(
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
                    FilterRow(
                        label = "Space",
                        options = allEquipment,
                        selected = equipmentFilter,
                        text = { it.label() },
                        onToggle = { equipmentFilter = if (equipmentFilter == it) null else it }
                    )
                }
            }
            item {
                FilterRow(
                    label = "Time per day",
                    options = listOf(10, 15, 20, 30, 45, 60),
                    selected = durationFilter,
                    text = { "≤ $it min" },
                    onToggle = { durationFilter = if (durationFilter == it) null else it }
                )
            }

            if (hasFilters) {
                item {
                    TextButton(
                        onClick = {
                            levelFilter = null
                            focusFilter = null
                            equipmentFilter = null
                            durationFilter = null
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear filters")
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    TrainingCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text("No plans match those filters", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Clear a filter to see more options.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filtered, key = { it.meta.id }) { entry ->
                    ProgramCard(
                        entry = entry,
                        selected = entry.meta.id == selectedProgramId,
                        expanded = entry.meta.id == expandedId,
                        preview = previewCache[entry.meta.id],
                        onToggleExpand = { toggleExpand(entry.meta.id) },
                        onUsePlan = { onSelect(entry.meta.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> FilterRow(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onToggle: (T) -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onToggle(option) },
                    label = { Text(text(option)) },
                    leadingIcon = if (option == selected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(
    entry: IndexEntry,
    selected: Boolean,
    expanded: Boolean,
    preview: LibraryProgram?,
    onToggleExpand: () -> Unit,
    onUsePlan: () -> Unit
) {
    val meta = entry.meta
    Surface(
        onClick = onToggleExpand,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(meta.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${meta.level.label()} · ${meta.weekCount} weeks · " +
                            meta.equipment.joinToString("/") { it.label() } +
                            if (meta.sessionMinutes > 0) " · ~${meta.sessionMinutes} min/day" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
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
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "Hide workouts" else "See workouts")
                }
                Spacer(Modifier.weight(1f))
                if (!selected) {
                    Button(onClick = onUsePlan) { Text("Use this plan") }
                } else {
                    Text(
                        "Current plan",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (expanded) {
                if (preview == null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    WeekPreviewList(preview.weeks)
                }
            }
        }
    }
}

@Composable
private fun WeekPreviewList(weeks: List<Week>) {
    Column(Modifier.padding(top = 12.dp)) {
        weeks.forEach { week ->
            Text("Week ${week.number}", style = MaterialTheme.typography.labelLarge)
            week.workouts.forEach { workout ->
                val names = workout.sections
                    .flatMap { it.exercises }
                    .filterNot { it.isRest }
                    .joinToString(", ") { it.name }
                Text(
                    "${workout.title}: $names",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
