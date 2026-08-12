package com.personal.twelveweek.ui

/**
 * Short motivational cues shown/spoken during a guided workout, tagged by
 * muscle group / movement style so the line said during a leg-heavy set
 * feels different from one said during a plank hold. [tags] is intentionally
 * a small open vocabulary ("legs", "abs", "core", "arms", "upper_body",
 * "cardio", "general") rather than [com.personal.twelveweek.programs.FocusArea]
 * — cues are picked per *exercise*, not per *program*, and a single full-body
 * program still moves through leg, core and arm movements in one session.
 */
data class MotivationalLine(val text: String, val tags: Set<String>)

private const val GENERAL = "general"

object MotivationLibrary {

    val lines: List<MotivationalLine> = listOf(
        // General — always eligible, used whenever no more specific tag matches.
        MotivationalLine("You're doing great — keep pushing!", setOf(GENERAL)),
        MotivationalLine("Every rep counts. Stay with it!", setOf(GENERAL)),
        MotivationalLine("Strong work. Keep that form tight.", setOf(GENERAL)),
        MotivationalLine("This is the part that makes you stronger.", setOf(GENERAL)),
        MotivationalLine("Breathe, stay steady, keep going.", setOf(GENERAL)),
        MotivationalLine("You showed up — now finish strong.", setOf(GENERAL)),
        MotivationalLine("Nice pace, keep it up!", setOf(GENERAL)),
        MotivationalLine("Almost there — don't slow down now.", setOf(GENERAL)),
        MotivationalLine("One more push. You've got this.", setOf(GENERAL)),
        MotivationalLine("Future you says thank you.", setOf(GENERAL)),

        // Legs
        MotivationalLine("Feel those legs working — that's real progress.", setOf("legs")),
        MotivationalLine("Strong legs, strong you. Drive through it!", setOf("legs")),
        MotivationalLine("Push through your heels, keep those legs burning.", setOf("legs")),
        MotivationalLine("Every squat and lunge is building real strength.", setOf("legs")),

        // Abs
        MotivationalLine("Squeeze those abs tight — you've got this.", setOf("abs")),
        MotivationalLine("Keep your core engaged, every rep matters.", setOf("abs")),
        MotivationalLine("That burn means it's working — stay with it.", setOf("abs")),

        // Core
        MotivationalLine("Core strength is total-body strength. Push on!", setOf("core")),
        MotivationalLine("Stay tight, stay steady — hold that line.", setOf("core")),
        MotivationalLine("A strong core carries you through everything else.", setOf("core")),

        // Arms / upper body
        MotivationalLine("Pump those arms — almost there!", setOf("arms", "upper_body")),
        MotivationalLine("Strong chest and shoulders, keep the tempo up.", setOf("upper_body")),
        MotivationalLine("Every push and pull is building real upper-body strength.", setOf("arms", "upper_body")),

        // Cardio / high-intensity
        MotivationalLine("Get that heart rate up — you're crushing this!", setOf("cardio")),
        MotivationalLine("Keep the energy high, you're almost through it.", setOf("cardio")),
        MotivationalLine("This is where the fitness gets built — keep moving!", setOf("cardio")),

        // Stretch / cool-down — deliberately calmer tone, never the
        // push-harder language used for active sets.
        MotivationalLine("Nice, easy stretch — hold it steady.", setOf("stretch")),
        MotivationalLine("Let your muscles relax into this one.", setOf("stretch")),
        MotivationalLine("Breathe deep, let the tension go.", setOf("stretch")),
        MotivationalLine("Good work today — ease into this one.", setOf("stretch")),
        MotivationalLine("No rush here, just a nice steady hold.", setOf("stretch")),
    )

    /** Picks a random line matching any of [tags], falling back to the
     *  general pool if nothing tag-specific is found. */
    fun pick(tags: Set<String>): MotivationalLine {
        val matches = lines.filter { line -> line.tags.any { it in tags } }
        val pool = matches.ifEmpty { lines.filter { GENERAL in it.tags } }
        return pool.random()
    }
}

/** Varied phrasing for the rest/"Pause" rows between rounds — kept
 *  separate from [MotivationLibrary] since these are tied to specific
 *  moments in a rest period (it starting, halfway through, wrapping up)
 *  rather than a random nudge during a movement. */
object RestCues {
    private val START = listOf(
        "Take a rest.",
        "Catch your breath.",
        "Drink some water.",
        "Shake it out.",
        "Nice work — breathe for a bit.",
        "Stretch it out a little.",
        "Rest up, you earned it."
    )
    private val HALFWAY = listOf(
        "Halfway through your rest.",
        "Keep breathing, halfway there.",
        "Almost recovered — halfway through."
    )
    private val ALMOST_DONE = listOf(
        "5 seconds, get ready to go again.",
        "Back at it in 5.",
        "5 seconds — shake out your arms."
    )

    fun start(): String = START.random()
    fun halfway(): String = HALFWAY.random()
    fun almostDone(): String = ALMOST_DONE.random()
}

/** Varied phrasing for Cool Down / stretch steps — same shape as
 *  [RestCues], but for the "up next"/halfway/almost-done moments of a
 *  stretch hold, which should read calm rather than push-harder. */
object StretchCues {
    private val START = listOf(
        "Next up, a stretch — no rush.",
        "Time to stretch it out.",
        "Ease into the next stretch.",
        "Let's cool down a bit."
    )
    private val HALFWAY = listOf(
        "Halfway through the stretch, keep holding.",
        "Nice and steady, halfway there.",
        "Keep breathing, you're halfway through this hold."
    )
    private val ALMOST_DONE = listOf(
        "5 seconds, then ease out of it.",
        "5 more seconds of this stretch.",
        "Almost there, hold a little longer."
    )

    fun start(): String = START.random()
    fun halfway(): String = HALFWAY.random()
    fun almostDone(): String = ALMOST_DONE.random()
}

/** Varied "you finished the whole day's workout" lines — said once (via
 *  voice) and shown once (as the [SessionCompleteScreen] headline) per
 *  completed session, so finishing doesn't say the exact same sentence
 *  every single day. */
object FinisherCues {
    private val LINES = listOf(
        "Workout complete, nice work!",
        "That's a wrap — great session!",
        "Day done. Solid effort!",
        "You crushed that one.",
        "All done for today — well earned.",
        "Nice work — day complete!",
        "That's it for today, awesome job.",
        "Session complete — be proud of that one."
    )

    fun pick(): String = LINES.random()
}

/** Tiny variety pool for the very short rep-set cues — kept separate and
 *  minimal since these are functional beats ("go now"), not personality
 *  lines; even a couple of alternates stops them feeling like a stuck
 *  recording on a long workout. */
object RepCues {
    private val GET_READY = listOf("Get ready", "Get set", "Here we go", "Ready up")
    private val GO = listOf("Go", "Go go go", "Now", "Let's go")

    fun getReady(): String = GET_READY.random()
    fun go(): String = GO.random()
}

/** Cheap keyword classifier from an exercise's name/section title to a
 *  small set of motivational tags — lets cues feel contextual without
 *  requiring every program author to hand-tag every exercise row. */
fun motivationTagsFor(exerciseName: String, sectionTitle: String): Set<String> {
    val text = "$exerciseName $sectionTitle".lowercase()
    val tags = mutableSetOf<String>()
    if (listOf("squat", "lunge", "leg", "calf", "glute", "step-up", "step up", "donkey kick", "fire hydrant").any { it in text }) {
        tags += "legs"
    }
    if (listOf("sit-up", "situp", "crunch", "v-up", "leg raise", "toe touch", "flutter kick", "scissor kick").any { it in text }) {
        tags += "abs"
    }
    if (listOf("plank", "dead bug", "bird dog", "pallof", "hollow", "woodchopper", "russian twist", "anti-rotation").any { it in text }) {
        tags += "core"
    }
    if (listOf("push-up", "pushup", "press", "curl", "tricep", "bicep", "dip", "row", "pull-up", "pullup", "shoulder", "chest", "lat pulldown").any { it in text }) {
        tags += "arms"
        tags += "upper_body"
    }
    if (listOf("burpee", "jump", "jack", "mountain climber", "sprint", "quick feet", "high knee").any { it in text }) {
        tags += "cardio"
    }
    if (tags.isEmpty()) tags += GENERAL
    return tags
}
