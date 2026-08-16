package com.personal.twelveweek

import com.personal.twelveweek.storage.RawPreferenceStore

enum class MilestoneKind { STREAK, WORKOUTS }

data class Milestone(val kind: MilestoneKind, val threshold: Int)

object MilestoneThresholds {
    val STREAK_DAYS = listOf(3, 7, 14, 21, 30, 60, 90)
    val WORKOUTS_DONE = listOf(1, 5, 10, 25, 50)
}

/**
 * Fires each streak/workout-count threshold from [MilestoneThresholds]
 * exactly once, by remembering the highest value already celebrated (a
 * "high-water mark") in [store]. No "before" snapshot is needed: any
 * threshold in `(mark, current]` is newly crossed by this call.
 */
class MilestoneTracker(private val store: RawPreferenceStore) {

    fun checkAndConsume(currentStreak: Int, workoutsCompleted: Int): List<Milestone> {
        val result = mutableListOf<Milestone>()

        val streakMark = store.getString(STREAK_KEY)?.toIntOrNull() ?: 0
        MilestoneThresholds.STREAK_DAYS
            .filter { it > streakMark && it <= currentStreak }
            .forEach { result += Milestone(MilestoneKind.STREAK, it) }
        if (currentStreak > streakMark) store.putString(STREAK_KEY, currentStreak.toString())

        val workoutsMark = store.getString(WORKOUTS_KEY)?.toIntOrNull() ?: 0
        MilestoneThresholds.WORKOUTS_DONE
            .filter { it > workoutsMark && it <= workoutsCompleted }
            .forEach { result += Milestone(MilestoneKind.WORKOUTS, it) }
        if (workoutsCompleted > workoutsMark) store.putString(WORKOUTS_KEY, workoutsCompleted.toString())

        return result
    }

    /** Resets both high-water marks — paired with [ProgressStore.clearEverything]
     *  on "reset all progress" so old streak/workout milestones don't
     *  silently block from ever firing again after a reset. */
    fun resetAll() {
        store.putString(STREAK_KEY, "0")
        store.putString(WORKOUTS_KEY, "0")
    }

    private companion object {
        const val STREAK_KEY = "streak_high_water"
        const val WORKOUTS_KEY = "workouts_high_water"
    }
}
