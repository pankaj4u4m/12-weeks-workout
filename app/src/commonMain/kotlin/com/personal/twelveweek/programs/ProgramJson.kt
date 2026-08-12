package com.personal.twelveweek.programs

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Section
import com.personal.twelveweek.Week
import com.personal.twelveweek.Workout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses this program-library format (see `programs/index.json` in the synced
 * GitHub repo, and `programs/program-1.json` in this repo as the worked
 * example). Deliberately tolerant of unknown enum values / missing optional
 * fields so a future program that uses a `FocusArea` this build doesn't know
 * about yet degrades to "uncategorized" instead of crashing the sync.
 */

private val json = Json { ignoreUnknownKeys = true }

/** One row of `index.json` — metadata plus where to fetch the full program. */
data class IndexEntry(val meta: ProgramMeta, val file: String)

@Serializable
private data class ExerciseDto(
    val raw: String,
    val name: String,
    val reps: Int? = null,
    val seconds: Int? = null,
    val wgerId: String? = null,
    val exerciseDbId: String? = null,
    val freeExerciseDbId: String? = null
)

@Serializable
private data class SectionDto(val title: String, val exercises: List<ExerciseDto> = emptyList())

@Serializable
private data class WorkoutDto(val index: Int, val sections: List<SectionDto> = emptyList(), val estimatedMinutes: Int = 0)

@Serializable
private data class WeekDto(val number: Int, val workouts: List<WorkoutDto> = emptyList())

@Serializable
private data class ProgramDto(
    val id: String,
    val title: String,
    val level: String = "",
    val focusAreas: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val sessionMinutes: Int = 0,
    val weeks: List<WeekDto>
)

@Serializable
private data class IndexEntryDto(
    val id: String,
    val title: String,
    val level: String = "",
    val focusAreas: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val weeks: Int = 0,
    val sessionMinutes: Int = 0,
    val file: String
)

@Serializable
private data class IndexDto(val programs: List<IndexEntryDto> = emptyList())

internal fun parseLevel(raw: String): ProgramLevel =
    raw.takeIf { it.isNotBlank() }
        ?.let { runCatching { ProgramLevel.valueOf(it) }.getOrNull() }
        ?: ProgramLevel.INTERMEDIATE

internal fun parseFocusAreas(values: List<String>): List<FocusArea> =
    values.mapNotNull { runCatching { FocusArea.valueOf(it) }.getOrNull() }

internal fun parseEquipment(values: List<String>): List<Equipment> =
    values.mapNotNull { runCatching { Equipment.valueOf(it) }.getOrNull() }

private fun ExerciseDto.toDomain() = Exercise(
    raw = raw,
    name = name,
    reps = reps,
    seconds = seconds,
    wgerId = wgerId,
    exerciseDbId = exerciseDbId,
    freeExerciseDbId = freeExerciseDbId
)

private fun SectionDto.toDomain() = Section(title = title, exercises = exercises.map { it.toDomain() })

private fun WorkoutDto.toDomain(programId: String, week: Int) = Workout(
    programId = programId,
    week = week,
    index = index,
    sections = sections.map { it.toDomain() },
    estimatedMinutes = estimatedMinutes
)

private fun WeekDto.toDomain(programId: String) = Week(
    number = number,
    workouts = workouts.map { it.toDomain(programId, number) }
)

private fun IndexEntryDto.toDomain() = IndexEntry(
    meta = ProgramMeta(
        id = id,
        title = title,
        level = parseLevel(level),
        focusAreas = parseFocusAreas(focusAreas),
        equipment = parseEquipment(equipment),
        weekCount = weeks,
        sessionMinutes = sessionMinutes
    ),
    file = file
)

/** Parses `programs/index.json` — the picker's lightweight listing. */
fun parseIndex(jsonText: String): List<IndexEntry> =
    json.decodeFromString(IndexDto.serializer(), jsonText).programs.map { it.toDomain() }

/** Parses one full `programs/<id>.json` — metadata plus all 12 weeks. */
fun parseProgram(jsonText: String): LibraryProgram {
    val dto = json.decodeFromString(ProgramDto.serializer(), jsonText)
    val weeks = dto.weeks.map { it.toDomain(dto.id) }
    val meta = ProgramMeta(
        id = dto.id,
        title = dto.title,
        level = parseLevel(dto.level),
        focusAreas = parseFocusAreas(dto.focusAreas),
        equipment = parseEquipment(dto.equipment),
        weekCount = weeks.size,
        sessionMinutes = dto.sessionMinutes
    )
    return LibraryProgram(meta, weeks)
}
