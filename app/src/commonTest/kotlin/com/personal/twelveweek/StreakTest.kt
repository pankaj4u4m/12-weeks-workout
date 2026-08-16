package com.personal.twelveweek

import com.personal.twelveweek.storage.RawKeyFlagStore
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {

    private fun freshStore(namespace: String): RawKeyFlagStore {
        val store = RawKeyFlagStore(namespace)
        store.clear()
        return store
    }

    // --- previousIsoDate ---

    @Test
    fun `previousIsoDate steps back a day within a month`() {
        assertEquals("2026-08-15", previousIsoDate("2026-08-16"))
    }

    @Test
    fun `previousIsoDate rolls over a month boundary`() {
        assertEquals("2026-08-31", previousIsoDate("2026-09-01"))
    }

    @Test
    fun `previousIsoDate rolls over a year boundary`() {
        assertEquals("2025-12-31", previousIsoDate("2026-01-01"))
    }

    @Test
    fun `previousIsoDate handles leap day`() {
        assertEquals("2024-02-29", previousIsoDate("2024-03-01"))
    }

    @Test
    fun `previousIsoDate skips Feb 29 in a non-leap year`() {
        assertEquals("2025-02-28", previousIsoDate("2025-03-01"))
    }

    // --- StreakTracker.currentStreak ---

    @Test
    fun `currentStreak is zero with no active days`() {
        val tracker = StreakTracker(freshStore("streak_test_empty"))
        assertEquals(0, tracker.currentStreak(today = "2026-08-16"))
    }

    @Test
    fun `currentStreak counts a single day marked today`() {
        val tracker = StreakTracker(freshStore("streak_test_single"))
        tracker.markActive("2026-08-16")
        assertEquals(1, tracker.currentStreak(today = "2026-08-16"))
    }

    @Test
    fun `currentStreak counts consecutive days`() {
        val tracker = StreakTracker(freshStore("streak_test_consecutive"))
        tracker.markActive("2026-08-14")
        tracker.markActive("2026-08-15")
        tracker.markActive("2026-08-16")
        assertEquals(3, tracker.currentStreak(today = "2026-08-16"))
    }

    @Test
    fun `currentStreak still counts through today when today isn't marked yet`() {
        val tracker = StreakTracker(freshStore("streak_test_today_unmarked"))
        tracker.markActive("2026-08-14")
        tracker.markActive("2026-08-15")
        assertEquals(2, tracker.currentStreak(today = "2026-08-16"))
    }

    @Test
    fun `a gap breaks the streak`() {
        val tracker = StreakTracker(freshStore("streak_test_gap"))
        tracker.markActive("2026-08-10")
        tracker.markActive("2026-08-15")
        tracker.markActive("2026-08-16")
        assertEquals(2, tracker.currentStreak(today = "2026-08-16"))
    }

    @Test
    fun `totalActiveDays counts every marked day regardless of gaps`() {
        val tracker = StreakTracker(freshStore("streak_test_total"))
        tracker.markActive("2026-08-01")
        tracker.markActive("2026-08-10")
        tracker.markActive("2026-08-16")
        assertEquals(3, tracker.totalActiveDays())
    }

    @Test
    fun `clear removes all active days`() {
        val tracker = StreakTracker(freshStore("streak_test_clear"))
        tracker.markActive("2026-08-16")
        tracker.clear()
        assertEquals(0, tracker.totalActiveDays())
    }
}
