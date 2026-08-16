package com.personal.twelveweek

import com.personal.twelveweek.storage.RawPreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MilestonesTest {

    private fun freshTracker(namespace: String): MilestoneTracker {
        val store = RawPreferenceStore(namespace)
        store.putString("streak_high_water", "0")
        store.putString("workouts_high_water", "0")
        return MilestoneTracker(store)
    }

    @Test
    fun `no milestones below the first threshold`() {
        val tracker = freshTracker("milestone_test_below")
        assertEquals(emptyList(), tracker.checkAndConsume(currentStreak = 2, workoutsCompleted = 0))
    }

    @Test
    fun `hitting the exact first streak threshold fires it once`() {
        val tracker = freshTracker("milestone_test_exact")
        val result = tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 0)
        assertEquals(listOf(Milestone(MilestoneKind.STREAK, 3)), result)
    }

    @Test
    fun `an already-celebrated threshold does not refire`() {
        val tracker = freshTracker("milestone_test_norefire")
        tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 0)
        val second = tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 0)
        assertEquals(emptyList(), second)
    }

    @Test
    fun `jumping past multiple thresholds in one call fires all of them`() {
        val tracker = freshTracker("milestone_test_jump")
        val result = tracker.checkAndConsume(currentStreak = 10, workoutsCompleted = 0)
        assertEquals(
            listOf(Milestone(MilestoneKind.STREAK, 3), Milestone(MilestoneKind.STREAK, 7)),
            result
        )
    }

    @Test
    fun `streak and workout milestones can both fire on the same call`() {
        val tracker = freshTracker("milestone_test_both")
        val result = tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 1)
        assertEquals(
            listOf(Milestone(MilestoneKind.STREAK, 3), Milestone(MilestoneKind.WORKOUTS, 1)),
            result
        )
    }

    @Test
    fun `a regression in streak after reset does not refire a lower threshold`() {
        val tracker = freshTracker("milestone_test_regress")
        tracker.checkAndConsume(currentStreak = 7, workoutsCompleted = 0)
        // Streak broke and restarted — high-water mark stays at 7, so 3 must not refire.
        val result = tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resetAll clears both high-water marks so thresholds can refire`() {
        val tracker = freshTracker("milestone_test_reset")
        tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 0)
        tracker.resetAll()
        val result = tracker.checkAndConsume(currentStreak = 3, workoutsCompleted = 0)
        assertEquals(listOf(Milestone(MilestoneKind.STREAK, 3)), result)
    }
}
