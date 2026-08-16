package com.personal.twelveweek

import com.personal.twelveweek.storage.RawKeyFlagStore

/**
 * Tracks which calendar days (device-local, "yyyy-MM-dd") had at least one
 * exercise marked done — the "any day with 1+ set logged keeps the streak
 * alive" rule from the design spec. Backed by [RawKeyFlagStore] using the
 * same presence-only shape [ProgressStore] already uses, just a different
 * namespace/key domain (dates instead of exercise-slot keys).
 */
class StreakTracker(private val store: RawKeyFlagStore) {

    /** Marks [dateIso] (default: today) as an active day. Idempotent —
     *  calling it more than once for the same day is harmless. */
    fun markActive(dateIso: String = todayIso()) = store.setPresent(dateIso)

    /** Consecutive active days ending at [today] (default: the real
     *  today). Today itself doesn't need to be marked yet for the streak
     *  to still read as alive — a user who trained every day through
     *  yesterday but hasn't opened the app yet today still has that
     *  streak; it only breaks once a full day passes with nothing logged. */
    fun currentStreak(today: String = todayIso()): Int {
        val activeDays = store.allKeys()
        if (activeDays.isEmpty()) return 0
        var count = 0
        var cursor = today
        if (activeDays.contains(cursor)) count++
        cursor = previousIsoDate(cursor)
        while (activeDays.contains(cursor)) {
            count++
            cursor = previousIsoDate(cursor)
        }
        return count
    }

    fun totalActiveDays(): Int = store.allKeys().size

    fun clear() = store.clear()
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> error("invalid month $month")
}

private fun isoOf(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** [iso] ("yyyy-MM-dd") minus one calendar day, handling month/year
 *  rollover and leap years. Hand-rolled instead of pulling in
 *  kotlinx-datetime for this one operation — see the design spec. */
internal fun previousIsoDate(iso: String): String {
    val year = iso.substring(0, 4).toInt()
    val month = iso.substring(5, 7).toInt()
    val day = iso.substring(8, 10).toInt()
    return when {
        day > 1 -> isoOf(year, month, day - 1)
        month > 1 -> isoOf(year, month - 1, daysInMonth(year, month - 1))
        else -> isoOf(year - 1, 12, 31)
    }
}
