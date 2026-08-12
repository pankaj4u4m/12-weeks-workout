package com.personal.twelveweek.programs

import com.personal.twelveweek.Week

enum class ProgramLevel { BEGINNER, INTERMEDIATE, ADVANCED }

enum class FocusArea { FULL_BODY, LEGS, ABS, CORE, UPPER_BODY, STRENGTH }

enum class Equipment { HOME, GYM }

/**
 * Lightweight listing entry for the program picker — everything needed to
 * render a card and filter chips without downloading the full 12-week body.
 */
data class ProgramMeta(
    val id: String,
    val title: String,
    val level: ProgramLevel,
    val focusAreas: List<FocusArea>,
    val equipment: List<Equipment>,
    val weekCount: Int
)

/** A fully-loaded program: metadata plus its 12 weeks of workouts. */
data class LibraryProgram(
    val meta: ProgramMeta,
    val weeks: List<Week>
)
