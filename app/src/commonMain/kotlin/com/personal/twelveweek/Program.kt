package com.personal.twelveweek

/**
 * One line of a workout, e.g. "20 Jumping Jacks" or "45s Low Plank".
 */
data class Exercise(
    val raw: String,
    val name: String,
    val reps: Int?,
    val seconds: Int?,
    /** Curated media ids, embedded per-exercise by whoever authored the
     *  program JSON (see `programs/` in the GitHub program-library repo) —
     *  null means no good match was found for that provider. [wgerId] is
     *  tried first (wger.de, free/keyless, real photo/video for common
     *  bodyweight moves but thin coverage overall); [exerciseDbId] is the
     *  fallback (ExerciseDB via RapidAPI, near-total coverage but needs the
     *  user's own free key). Both null always falls back to the external
     *  search buttons — never a guessed match. */
    val wgerId: String? = null,
    val exerciseDbId: String? = null,
    /** Curated [free-exercise-db](https://github.com/yuhonas/free-exercise-db)
     *  exercise id (its `id`/folder slug, e.g. "Mountain_Climbers") — that
     *  repo is public-domain and every entry ships exactly two step photos
     *  (`0.jpg`/`1.jpg`), which the app loops as a lightweight flipbook.
     *  Only ever this source for the loop — never mixed with wger's or
     *  ExerciseDB's own images. */
    val freeExerciseDbId: String? = null
) {
    val isTimed: Boolean get() = seconds != null

    /** "Pause" rows are rest, not a movement — no demo lookup for them. */
    val isRest: Boolean get() = name.equals("Pause", ignoreCase = true)

    /** Key used for the asset filename if you drop in your own picture. */
    val slug: String
        get() = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    val searchQuery: String get() = "$name exercise proper form"

    companion object {
        private val PATTERN = Regex("^(\\d+)(s?)\\s+(.+)$")

        fun parse(raw: String): Exercise {
            val trimmed = raw.trim()
            val m = PATTERN.find(trimmed)
                ?: return Exercise(trimmed, trimmed, null, null)
            val value = m.groupValues[1].toInt()
            val isSeconds = m.groupValues[2] == "s"
            val name = m.groupValues[3].trim()
            return Exercise(
                raw = trimmed,
                name = name,
                reps = if (isSeconds) null else value,
                seconds = if (isSeconds) value else null
            )
        }
    }
}

/** A titled block of exercises: "Warm up", "Round 3", "Cool Down". */
data class Section(
    val title: String,
    val exercises: List<Exercise>
)

data class Workout(
    /** Which library program this workout belongs to — namespaces progress
     *  keys so two different programs' "Week 1 Day 1" never collide. */
    val programId: String,
    val week: Int,
    val index: Int,
    val sections: List<Section>,
    /** Precomputed at authoring time (2.5s/rep + an 8s transition per row),
     *  not recomputed on-device — same "curate once in the JSON, don't
     *  guess live" pattern as the media ids. 0 if the program JSON predates
     *  this field. */
    val estimatedMinutes: Int = 0
) {
    val title: String get() = "Day $index"

    val totalItems: Int get() = sections.sumOf { it.exercises.size }

    /** Stable id for saving tick state. Survives app restarts. */
    fun keyFor(sectionIndex: Int, itemIndex: Int) =
        "$programId:w$week-o$index-s$sectionIndex-i$itemIndex"

    fun allKeys(): List<String> = buildList {
        sections.forEachIndexed { s, section ->
            section.exercises.indices.forEach { i -> add(keyFor(s, i)) }
        }
    }
}

data class Week(
    val number: Int,
    val workouts: List<Workout>
)
