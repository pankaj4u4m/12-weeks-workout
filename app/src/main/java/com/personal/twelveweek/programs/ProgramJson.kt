package com.personal.twelveweek.programs

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Section
import com.personal.twelveweek.Week
import com.personal.twelveweek.Workout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses this program-library format (see `programs/index.json` in the synced
 * GitHub repo, and `programs/program-1.json` in this repo as the worked
 * example). Deliberately tolerant of unknown enum values / missing optional
 * fields so a future program that uses a `FocusArea` this build doesn't know
 * about yet degrades to "uncategorized" instead of crashing the sync.
 */

/** One row of `index.json` — metadata plus where to fetch the full program. */
data class IndexEntry(val meta: ProgramMeta, val file: String)

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

private fun parseLevel(raw: String): ProgramLevel =
    raw.takeIf { it.isNotBlank() }
        ?.let { runCatching { ProgramLevel.valueOf(it) }.getOrNull() }
        ?: ProgramLevel.INTERMEDIATE

private fun parseFocusAreas(arr: JSONArray?): List<FocusArea> =
    arr?.strings().orEmpty().mapNotNull { runCatching { FocusArea.valueOf(it) }.getOrNull() }

private fun parseEquipment(arr: JSONArray?): List<Equipment> =
    arr?.strings().orEmpty().mapNotNull { runCatching { Equipment.valueOf(it) }.getOrNull() }

private fun parseExercise(o: JSONObject): Exercise = Exercise(
    raw = o.getString("raw"),
    name = o.getString("name"),
    reps = if (o.isNull("reps") || !o.has("reps")) null else o.getInt("reps"),
    seconds = if (o.isNull("seconds") || !o.has("seconds")) null else o.getInt("seconds"),
    exerciseId = if (o.isNull("exerciseId") || !o.has("exerciseId")) null else o.getString("exerciseId")
)

private fun parseSection(o: JSONObject): Section {
    val arr = o.getJSONArray("exercises")
    return Section(
        title = o.getString("title"),
        exercises = (0 until arr.length()).map { parseExercise(arr.getJSONObject(it)) }
    )
}

private fun parseWorkout(programId: String, week: Int, o: JSONObject): Workout {
    val arr = o.getJSONArray("sections")
    return Workout(
        programId = programId,
        week = week,
        index = o.getInt("index"),
        sections = (0 until arr.length()).map { parseSection(arr.getJSONObject(it)) }
    )
}

private fun parseWeek(programId: String, o: JSONObject): Week {
    val number = o.getInt("number")
    val arr = o.getJSONArray("workouts")
    return Week(
        number = number,
        workouts = (0 until arr.length()).map { parseWorkout(programId, number, arr.getJSONObject(it)) }
    )
}

/** Parses `programs/index.json` — the picker's lightweight listing. */
fun parseIndex(json: String): List<IndexEntry> {
    val arr = JSONObject(json).getJSONArray("programs")
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        IndexEntry(
            meta = ProgramMeta(
                id = o.getString("id"),
                title = o.getString("title"),
                level = parseLevel(o.optString("level", "")),
                focusAreas = parseFocusAreas(o.optJSONArray("focusAreas")),
                equipment = parseEquipment(o.optJSONArray("equipment")),
                weekCount = o.optInt("weeks", 0)
            ),
            file = o.getString("file")
        )
    }
}

/** Parses one full `programs/<id>.json` — metadata plus all 12 weeks. */
fun parseProgram(json: String): LibraryProgram {
    val root = JSONObject(json)
    val id = root.getString("id")
    val weeksArr = root.getJSONArray("weeks")
    val weeks = (0 until weeksArr.length()).map { parseWeek(id, weeksArr.getJSONObject(it)) }
    val meta = ProgramMeta(
        id = id,
        title = root.getString("title"),
        level = parseLevel(root.optString("level", "")),
        focusAreas = parseFocusAreas(root.optJSONArray("focusAreas")),
        equipment = parseEquipment(root.optJSONArray("equipment")),
        weekCount = weeks.size
    )
    return LibraryProgram(meta, weeks)
}
