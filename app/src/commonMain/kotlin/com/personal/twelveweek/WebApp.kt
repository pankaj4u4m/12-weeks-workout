package com.personal.twelveweek

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.programs.IndexEntry
import com.personal.twelveweek.programs.LibraryProgram
import com.personal.twelveweek.programs.ProgramLibrary
import com.personal.twelveweek.storage.RawKeyFlagStore
import com.personal.twelveweek.storage.RawPreferenceStore

/**
 * Web entry screen — a from-scratch, purpose-built UI for the browser
 * target (not a port of [MainActivity]'s screens, which are Android-specific
 * and stay that way for now). Reuses the fully-shared data layer (program
 * parsing, [ProgressStore], [SelectedProgramStore], [ProgramLibrary]) so
 * progress and program selection are the same persistence model as Android,
 * just a simpler, single-page read/tick UI: pick a program, pick a week,
 * tick exercises. No guided session, no media, no settings yet on web —
 * those need their own platform bridges (video, TTS, encrypted key storage)
 * not built yet.
 */
@Composable
fun WebApp() {
    val library = remember { ProgramLibrary() }
    val progress = remember { ProgressStore(RawKeyFlagStore("twelve_week_progress")) }
    val selectedProgramStore = remember { SelectedProgramStore(RawPreferenceStore("twelve_week_selected_program")) }

    var index by remember { mutableStateOf<List<IndexEntry>?>(null) }
    var selectedProgramId by remember { mutableStateOf(selectedProgramStore.get()) }
    var activeProgram by remember { mutableStateOf<LibraryProgram?>(null) }
    var selectedWeekNumber by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) { index = library.index() }
    LaunchedEffect(selectedProgramId) {
        activeProgram = null
        activeProgram = library.load(selectedProgramId)
        selectedWeekNumber = 1
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text("TwelveWeek", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Web preview — pick a program, track a week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                val entries = index
                if (entries == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                Text("Program", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { entry ->
                        FilterChip(
                            selected = entry.meta.id == selectedProgramId,
                            onClick = {
                                selectedProgramId = entry.meta.id
                                selectedProgramStore.set(entry.meta.id)
                            },
                            label = { Text(entry.meta.title) }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                val program = activeProgram
                if (program == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                Text("Week", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(program.weeks) { week ->
                        FilterChip(
                            selected = week.number == selectedWeekNumber,
                            onClick = { selectedWeekNumber = week.number },
                            label = { Text("W${week.number}") }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                val week = program.weeks.firstOrNull { it.number == selectedWeekNumber }
                if (week == null) {
                    Text("No data for this week.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(week.workouts) { workout ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    val doneCount = progress.countDone(workout.allKeys())
                                    Text(workout.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "$doneCount of ${workout.totalItems} done",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    workout.sections.forEachIndexed { sectionIndex, section ->
                                        Text(
                                            section.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                        )
                                        section.exercises.forEachIndexed { itemIndex, exercise ->
                                            val key = workout.keyFor(sectionIndex, itemIndex)
                                            val done = progress.isDone(key)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { progress.setDone(key, !done) }
                                                    .padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = done,
                                                    onCheckedChange = { progress.setDone(key, it) }
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    exercise.raw,
                                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
