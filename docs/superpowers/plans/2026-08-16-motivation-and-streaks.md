# Streaks, Milestones & Celebration Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reward consistency (streaks + milestones) and turn workout completion into a small celebration (confetti + haptic + streak/milestone callout), on both Android and Web.

**Architecture:** Two new small commonMain types (`StreakTracker`, `MilestoneTracker`) reuse the existing `RawKeyFlagStore`/`RawPreferenceStore` platform storage. `ProgressStore.setDone(key, true)` — the single chokepoint every completion path already calls — marks today active, so no new call sites need to be found. UI changes are mirrored per-platform (Android `MainActivity.kt`/`GuidedSessionScreen.kt`/`TrainingComponents.kt`, Web `WebApp.kt`/`WebGuidedSession.kt`/`WebTrainingComponents.kt`), matching this codebase's existing no-shared-UI convention.

**Tech Stack:** Kotlin Multiplatform (Android + wasmJs), Compose Multiplatform. No new dependencies.

## Global Constraints

- Streak rule: any day with ≥1 set logged (`ProgressStore.setDone(key, true)`) keeps the streak alive. A day doesn't have to be marked yet for "today" to still count as part of an ongoing streak.
- Celebration intensity: playful but light — confetti/haptic/one line, never blocking, never a separate full-screen ceremony.
- Streak-day storage namespace: `"twelve_week_streak_days"` (new `RawKeyFlagStore`). Milestone storage namespace: `"twelve_week_milestones"` (new `RawPreferenceStore`).
- Milestone thresholds: streak days `[3, 7, 14, 21, 30, 60, 90]`; workouts completed in the active program `[1, 5, 10, 25, 50]`. Each threshold fires exactly once (tracked via a persisted high-water mark), never refires unless `MilestoneTracker.resetAll()` runs (paired with "reset all progress").
- No new dependency (no `kotlinx-datetime`) — date-one-day-back arithmetic is hand-rolled in `Streak.kt`.
- No new stats/dashboard screen, no server sync of streak data, no cross-program lifetime workout counter, no changes to the existing `MotivationCoach.kt`/`WebMotivationCues.kt` system.
- Every commonMain addition needs Android + wasmJs UI wiring — this codebase has no shared Compose UI layer between the two targets (see `WebApp.kt`'s own file-header comment).
- Verification commands (this project's known-working task names): `./gradlew :app:compileDebugKotlin`, `./gradlew :app:compileKotlinWasmJs`, `./gradlew :app:testDebugUnitTest --tests "*<Name>*"`.

---

### Task 1: `todayIso()` — local calendar date expect/actual

**Files:**
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/TodayDate.kt`
- Create: `app/src/androidMain/kotlin/com/personal/twelveweek/TodayDate.android.kt`
- Create: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/TodayDate.wasmJs.kt`

**Interfaces:**
- Produces: `expect fun todayIso(): String` — device-local calendar date as `"yyyy-MM-dd"`. Used by Task 2's `StreakTracker`.

- [ ] **Step 1: Write the commonMain `expect` declaration**

`app/src/commonMain/kotlin/com/personal/twelveweek/TodayDate.kt`:

```kotlin
package com.personal.twelveweek

/** The device's local calendar date as "yyyy-MM-dd" — always local time,
 *  never UTC (a session finished at 11pm local must count for that local
 *  day, not roll to UTC's next day). Used to key [StreakTracker]'s active
 *  days. */
expect fun todayIso(): String
```

- [ ] **Step 2: Write the Android actual**

`app/src/androidMain/kotlin/com/personal/twelveweek/TodayDate.android.kt`:

```kotlin
package com.personal.twelveweek

import java.time.LocalDate

// LocalDate.now() already formats as ISO "yyyy-MM-dd" via toString() (zero
// padded). minSdk is 26 (see app/build.gradle.kts), so java.time is always
// available — no desugaring needed.
actual fun todayIso(): String = LocalDate.now().toString()
```

- [ ] **Step 3: Write the wasmJs actual**

Kotlin/Wasm's `wasmJs` target does **not** ship `kotlin.js.Date` (verified
empirically against `kotlin-stdlib-wasm-js-2.4.10.klib` — no `Date` symbol
in the `kotlin.js` package). Use the project's existing `@JsFun` interop
pattern instead (same pattern as `WebHaptics.kt`'s `jsVibrate()`):

`app/src/wasmJsMain/kotlin/com/personal/twelveweek/TodayDate.wasmJs.kt`:

```kotlin
package com.personal.twelveweek

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
actual fun todayIso(): String = jsTodayIso()

// Built from local-time getters (getFullYear/getMonth/getDate), NOT
// toISOString() — toISOString() is UTC and would misdate a session run
// late at night in most timezones.
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "() => { var d = new Date(); var m = String(d.getMonth() + 1).padStart(2, '0'); var day = String(d.getDate()).padStart(2, '0'); return d.getFullYear() + '-' + m + '-' + day; }"
)
private external fun jsTodayIso(): String
```

- [ ] **Step 4: Verify both targets compile**

Run: `./gradlew :app:compileDebugKotlin :app:compileKotlinWasmJs`
Expected: `BUILD SUCCESSFUL` for both tasks. This is the one genuinely novel
platform-interop piece in the whole plan — if `@JsFun` string return
doesn't marshal as expected, this is where it will surface, before any
other task depends on it.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/com/personal/twelveweek/TodayDate.kt \
        app/src/androidMain/kotlin/com/personal/twelveweek/TodayDate.android.kt \
        app/src/wasmJsMain/kotlin/com/personal/twelveweek/TodayDate.wasmJs.kt
git commit -m "feat: todayIso() expect/actual for streak tracking"
```

---

### Task 2: `StreakTracker` — commonMain streak logic + tests

**Files:**
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/Streak.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/StreakTest.kt`

**Interfaces:**
- Consumes: `todayIso()` (Task 1), `RawKeyFlagStore` (existing — `allKeys()`, `setPresent(key)`, `clear()`).
- Produces: `class StreakTracker(store: RawKeyFlagStore)` with `markActive(dateIso: String = todayIso())`, `currentStreak(today: String = todayIso()): Int`, `totalActiveDays(): Int`, `clear()`. Also `internal fun previousIsoDate(iso: String): String` (package `com.personal.twelveweek`) — used only by `StreakTracker` itself but tested directly. Task 4 (`ProgressStore`) consumes `StreakTracker.markActive()`/`.clear()`/`.currentStreak()`.

- [ ] **Step 1: Write `Streak.kt`**

`app/src/commonMain/kotlin/com/personal/twelveweek/Streak.kt`:

```kotlin
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
```

- [ ] **Step 2: Write `StreakTest.kt`**

`app/src/commonTest/kotlin/com/personal/twelveweek/StreakTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*StreakTest*"`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/com/personal/twelveweek/Streak.kt \
        app/src/commonTest/kotlin/com/personal/twelveweek/StreakTest.kt
git commit -m "feat: StreakTracker — consecutive-day streak tracking"
```

---

### Task 3: `MilestoneTracker` — commonMain milestone logic + tests

**Files:**
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/Milestones.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/MilestonesTest.kt`

**Interfaces:**
- Consumes: `RawPreferenceStore` (existing — `getString(key)`, `putString(key, value)`).
- Produces: `enum class MilestoneKind { STREAK, WORKOUTS }`, `data class Milestone(val kind: MilestoneKind, val threshold: Int)`, `object MilestoneThresholds { val STREAK_DAYS; val WORKOUTS_DONE }`, `class MilestoneTracker(store: RawPreferenceStore)` with `checkAndConsume(currentStreak: Int, workoutsCompleted: Int): List<Milestone>` and `resetAll()`. Tasks 5/6 (Android/Web UI) call `checkAndConsume` on workout completion and `resetAll()` on "reset all progress".

- [ ] **Step 1: Write `Milestones.kt`**

`app/src/commonMain/kotlin/com/personal/twelveweek/Milestones.kt`:

```kotlin
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
```

- [ ] **Step 2: Write `MilestonesTest.kt`**

`app/src/commonTest/kotlin/com/personal/twelveweek/MilestonesTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*MilestonesTest*"`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/com/personal/twelveweek/Milestones.kt \
        app/src/commonTest/kotlin/com/personal/twelveweek/MilestonesTest.kt
git commit -m "feat: MilestoneTracker — fire-once streak/workout thresholds"
```

---

### Task 4: Wire `ProgressStore` to `StreakTracker`

**Files:**
- Modify: `app/src/commonMain/kotlin/com/personal/twelveweek/Progress.kt`
- Modify: `app/src/commonTest/kotlin/com/personal/twelveweek/ProgressTest.kt`

**Interfaces:**
- Consumes: `StreakTracker` (Task 2).
- Produces: `ProgressStore(store: RawKeyFlagStore, streaks: StreakTracker? = null)` (new optional 2nd constructor param — every existing 1-arg call site keeps compiling unchanged), `ProgressStore.currentStreak(): Int`. Tasks 5/6 pass a real `StreakTracker` in and call `progress.currentStreak()`.

- [ ] **Step 1: Modify `Progress.kt`**

Current full file:

```kotlin
package com.personal.twelveweek

import androidx.compose.runtime.mutableStateMapOf
import com.personal.twelveweek.storage.RawKeyFlagStore

/**
 * Which boxes are ticked. Mirrors the TRUE/FALSE columns in the sheet.
 * Backed by [RawKeyFlagStore] so it survives restarts (Android: included
 * in Android auto-backup; wasmJs: survives a page reload via localStorage).
 *
 * Keys are `"<programId>:w..-o..-s..-i.."` (see [Workout.keyFor]). Before
 * the program library existed there was only ever one program and keys had
 * no prefix (`"w..-o..-s..-i.."`) — those are the built-in program's keys
 * under the hood, so [isDone]/[setDone] transparently fall back to/clean
 * up the legacy unprefixed form for `"program-1:..."` keys. Nobody's
 * existing tick history is lost by the program-library upgrade.
 */
class ProgressStore(private val store: RawKeyFlagStore) {

    private val done = mutableStateMapOf<String, Boolean>()

    init {
        store.allKeys().forEach { key -> done[key] = true }
    }

    private fun legacyKey(key: String): String? =
        if (key.startsWith(LEGACY_PREFIX)) key.removePrefix(LEGACY_PREFIX) else null

    fun isDone(key: String): Boolean {
        if (done[key] == true) return true
        val legacy = legacyKey(key) ?: return false
        return done[legacy] == true
    }

    fun setDone(key: String, value: Boolean) {
        if (value) {
            done[key] = true
            store.setPresent(key)
        } else {
            done.remove(key)
            store.remove(key)
        }
        // Either way, the legacy form is now superseded by the new key — stop
        // carrying it forward so it can't cause a stale "still done" read.
        legacyKey(key)?.let { legacy ->
            done.remove(legacy)
            store.remove(legacy)
        }
    }

    fun toggle(key: String) = setDone(key, !isDone(key))

    fun countDone(keys: List<String>): Int = keys.count { isDone(it) }

    fun setAll(keys: List<String>, value: Boolean) {
        keys.forEach { key -> setDone(key, value) }
    }

    fun clearEverything() {
        done.clear()
        store.clear()
    }

    private companion object {
        const val LEGACY_PREFIX = "program-1:"
    }
}
```

Replace with:

```kotlin
package com.personal.twelveweek

import androidx.compose.runtime.mutableStateMapOf
import com.personal.twelveweek.storage.RawKeyFlagStore

/**
 * Which boxes are ticked. Mirrors the TRUE/FALSE columns in the sheet.
 * Backed by [RawKeyFlagStore] so it survives restarts (Android: included
 * in Android auto-backup; wasmJs: survives a page reload via localStorage).
 *
 * Keys are `"<programId>:w..-o..-s..-i.."` (see [Workout.keyFor]). Before
 * the program library existed there was only ever one program and keys had
 * no prefix (`"w..-o..-s..-i.."`) — those are the built-in program's keys
 * under the hood, so [isDone]/[setDone] transparently fall back to/clean
 * up the legacy unprefixed form for `"program-1:..."` keys. Nobody's
 * existing tick history is lost by the program-library upgrade.
 *
 * [streaks], when provided, is marked active every time an item is newly
 * completed — every completion path in the app already funnels through
 * [setDone], so this is the one place streak tracking needs to be wired in.
 */
class ProgressStore(
    private val store: RawKeyFlagStore,
    private val streaks: StreakTracker? = null
) {

    private val done = mutableStateMapOf<String, Boolean>()

    init {
        store.allKeys().forEach { key -> done[key] = true }
    }

    private fun legacyKey(key: String): String? =
        if (key.startsWith(LEGACY_PREFIX)) key.removePrefix(LEGACY_PREFIX) else null

    fun isDone(key: String): Boolean {
        if (done[key] == true) return true
        val legacy = legacyKey(key) ?: return false
        return done[legacy] == true
    }

    fun setDone(key: String, value: Boolean) {
        if (value) {
            done[key] = true
            store.setPresent(key)
            streaks?.markActive()
        } else {
            done.remove(key)
            store.remove(key)
        }
        // Either way, the legacy form is now superseded by the new key — stop
        // carrying it forward so it can't cause a stale "still done" read.
        legacyKey(key)?.let { legacy ->
            done.remove(legacy)
            store.remove(legacy)
        }
    }

    fun toggle(key: String) = setDone(key, !isDone(key))

    fun countDone(keys: List<String>): Int = keys.count { isDone(it) }

    fun setAll(keys: List<String>, value: Boolean) {
        keys.forEach { key -> setDone(key, value) }
    }

    fun clearEverything() {
        done.clear()
        store.clear()
        streaks?.clear()
    }

    /** Current consecutive-day streak (see [StreakTracker.currentStreak]) —
     *  0 when no [streaks] collaborator was provided. */
    fun currentStreak(): Int = streaks?.currentStreak() ?: 0

    private companion object {
        const val LEGACY_PREFIX = "program-1:"
    }
}
```

- [ ] **Step 2: Add tests to `ProgressTest.kt`**

Append these test functions inside the existing `ProgressTest` class (the file already has a `freshStore()` helper — reuse it unchanged):

```kotlin
    @Test
    fun `setDone true marks today active for streak tracking`() {
        val streaks = StreakTracker(RawKeyFlagStore("progress_test_streaks_a").also { it.clear() })
        val progress = ProgressStore(freshStore(), streaks)
        progress.setDone("program-1:w1-o1-s0-i0", true)
        assertEquals(1, progress.currentStreak())
    }

    @Test
    fun `currentStreak is zero with no streak tracker wired in`() {
        val progress = ProgressStore(freshStore())
        assertEquals(0, progress.currentStreak())
    }

    @Test
    fun `clearEverything also clears streak days`() {
        val streaks = StreakTracker(RawKeyFlagStore("progress_test_streaks_b").also { it.clear() })
        val progress = ProgressStore(freshStore(), streaks)
        progress.setDone("program-1:w1-o1-s0-i0", true)
        assertEquals(1, progress.currentStreak())
        progress.clearEverything()
        assertEquals(0, progress.currentStreak())
    }
```

`assertEquals` is already imported in this file; no new imports needed (`StreakTracker` is in the same `com.personal.twelveweek` package).

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProgressTest*"`
Expected: all tests (existing + new) pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/com/personal/twelveweek/Progress.kt \
        app/src/commonTest/kotlin/com/personal/twelveweek/ProgressTest.kt
git commit -m "feat: ProgressStore marks streak days on completion"
```

---

### Task 5: Android — wire streaks/milestones into the app, add completion celebration

**Files:**
- Modify: `app/src/main/java/com/personal/twelveweek/MainActivity.kt`
- Modify: `app/src/main/java/com/personal/twelveweek/ui/TrainingComponents.kt`
- Modify: `app/src/main/java/com/personal/twelveweek/ui/GuidedSessionScreen.kt`

**Interfaces:**
- Consumes: `StreakTracker`, `MilestoneTracker`, `Milestone`, `MilestoneKind` (Tasks 2–4), `ProgressStore.currentStreak()` (Task 4).
- Produces: nothing consumed by later tasks (this is the Android leaf). Task 6 mirrors this task's shape on Web independently.

This task threads two new collaborators through the existing Android call chain (`AppRoot` → `AppShell` → `AppScreenContent` → `PlanScreen` / `GuidedSessionScreen`) and adds the celebration UI. Apply the edits below in order — each one is a small, uniquely-matchable old→new text replacement; line numbers are from the pre-edit file and drift after earlier edits in the same file land, so match on the text, not the numbers.

- [ ] **Step 1: `MainActivity.kt` — construct the trackers in `AppRoot`**

Around line 109. Old:

```kotlin
    val progress = remember { ProgressStore(RawKeyFlagStore("twelve_week_progress")) }
```

New:

```kotlin
    val streaks = remember { StreakTracker(RawKeyFlagStore("twelve_week_streak_days")) }
    val progress = remember { ProgressStore(RawKeyFlagStore("twelve_week_progress"), streaks) }
    val milestones = remember { MilestoneTracker(RawPreferenceStore("twelve_week_milestones")) }
```

- [ ] **Step 2: `MainActivity.kt` — pass `milestones` into the `AppShell` call site**

Around line 229 (inside the `else -> Crossfade(...) { program -> AppShell(...) }` branch). Old:

```kotlin
                    AppShell(
                        program = program,
                        libraryIndex = libraryIndex,
                        selectedProgramId = selectedProgramId,
                        library = library,
                        progress = progress,
                        screen = screen,
```

New:

```kotlin
                    AppShell(
                        program = program,
                        libraryIndex = libraryIndex,
                        selectedProgramId = selectedProgramId,
                        library = library,
                        progress = progress,
                        milestones = milestones,
                        screen = screen,
```

- [ ] **Step 3: `MainActivity.kt` — `AppShell` signature + both internal `AppScreenContent` calls**

`AppShell`'s signature. Old:

```kotlin
private fun AppShell(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    library: ProgramLibrary,
    progress: ProgressStore,
    screen: Screen,
```

New:

```kotlin
private fun AppShell(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    library: ProgramLibrary,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    screen: Screen,
```

`AppShell` contains **two** `AppScreenContent(...)` calls, at **different indentation levels** (the expanded-layout branch nests it one level deeper inside `Row { ... Scaffold { inner -> ... } }`; the bottom-nav branch's `Scaffold` has no such wrapping `Row`) — apply both edits below.

Occurrence A (expanded-layout branch, inside `Row(Modifier.fillMaxSize()) { AppNavigationRail(...); Scaffold(...) { inner -> ... } }`). Old:

```kotlin
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { inner ->
                    AppScreenContent(
                        program = program,
                        libraryIndex = libraryIndex,
                        selectedProgramId = selectedProgramId,
                        library = library,
                        progress = progress,
                        screen = screen,
```

New:

```kotlin
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { inner ->
                    AppScreenContent(
                        program = program,
                        libraryIndex = libraryIndex,
                        selectedProgramId = selectedProgramId,
                        library = library,
                        progress = progress,
                        milestones = milestones,
                        screen = screen,
```

Occurrence B (bottom-nav branch, directly inside `Scaffold(bottomBar = { ... }, ...) { inner -> ... }`, one indent level shallower than Occurrence A). Old:

```kotlin
                contentWindowInsets = WindowInsets.safeDrawing
            ) { inner ->
                AppScreenContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    library = library,
                    progress = progress,
                    screen = screen,
```

New:

```kotlin
                contentWindowInsets = WindowInsets.safeDrawing
            ) { inner ->
                AppScreenContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    library = library,
                    progress = progress,
                    milestones = milestones,
                    screen = screen,
```
                        screen = screen,
```

- [ ] **Step 4: `MainActivity.kt` — `AppScreenContent` signature**

Old:

```kotlin
private fun AppScreenContent(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    library: ProgramLibrary,
    progress: ProgressStore,
    screen: Screen,
```

New:

```kotlin
private fun AppScreenContent(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    library: ProgramLibrary,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    screen: Screen,
```

- [ ] **Step 5: `MainActivity.kt` — `Screen.Plan` branch passes `milestones`**

Old:

```kotlin
        Screen.Plan -> PlanScreen(
            weeks = program.weeks,
            programTitle = program.meta.title,
            progress = progress,
            onOpenWeek = { onScreenChange(Screen.WeekDetail(it)) },
            modifier = modifier
        )
```

New:

```kotlin
        Screen.Plan -> PlanScreen(
            weeks = program.weeks,
            programTitle = program.meta.title,
            progress = progress,
            milestones = milestones,
            onOpenWeek = { onScreenChange(Screen.WeekDetail(it)) },
            modifier = modifier
        )
```

- [ ] **Step 6: `MainActivity.kt` — `Screen.GuidedSession` branch passes `weeks` + `milestones`**

Old:

```kotlin
        is Screen.GuidedSession -> {
            val workout = program.weeks
                .first { it.number == screen.week }
                .workouts.first { it.index == screen.workout }
            Box(modifier = modifier.fillMaxSize()) {
                GuidedSessionScreen(
                    workout = workout,
                    progress = progress,
                    onExit = {
                        onScreenChange(Screen.WorkoutDetail(screen.week, screen.workout))
                    }
                )
            }
        }
```

New:

```kotlin
        is Screen.GuidedSession -> {
            val workout = program.weeks
                .first { it.number == screen.week }
                .workouts.first { it.index == screen.workout }
            Box(modifier = modifier.fillMaxSize()) {
                GuidedSessionScreen(
                    workout = workout,
                    weeks = program.weeks,
                    progress = progress,
                    milestones = milestones,
                    onExit = {
                        onScreenChange(Screen.WorkoutDetail(screen.week, screen.workout))
                    }
                )
            }
        }
```

- [ ] **Step 7: `MainActivity.kt` — `TodayScreen` header gets a streak chip**

(`TodayScreen` already receives `progress` — no new param needed, `progress.currentStreak()` is called directly.) Old:

```kotlin
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
```

(this is the tail of the `TodayScreen` header `item { Row(...) { Column {...}; IconButton(...) } }` block, immediately after the `Column` that shows "TwelveWeek" / `programTitle`). New:

```kotlin
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val streakDays = progress.currentStreak()
                    if (streakDays >= 1) {
                        StreakChip(streakDays = streakDays)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        }
```

If this exact tail text also matches inside `PlanScreen`'s header (it doesn't — `PlanScreen`'s header ends with a `DropdownMenu`, not this `IconButton`), double check by confirming the match is inside `TodayScreen`'s function body before applying.

- [ ] **Step 8: `MainActivity.kt` — add the `StreakChip` composable**

Add this new private composable near `TodayScreen` (e.g. directly above it, or anywhere else at file scope in `MainActivity.kt`):

```kotlin
/** Small flame+count badge shown once a streak exists — the one
 *  continuously-animating element added by this feature (a slow pulse);
 *  everything else here is one-shot. */
@Composable
private fun StreakChip(streakDays: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "streakPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakPulseScale"
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = "$streakDays day streak",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$streakDays",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
```

- [ ] **Step 9: `MainActivity.kt` — `PlanScreen` gets `milestones` param + reset cascade**

Signature. Old:

```kotlin
private fun PlanScreen(
    weeks: List<Week>,
    programTitle: String,
    progress: ProgressStore,
    onOpenWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
```

New:

```kotlin
private fun PlanScreen(
    weeks: List<Week>,
    programTitle: String,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    onOpenWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
```

Reset button. Old:

```kotlin
                Button(
                    onClick = {
                        progress.clearEverything()
                        confirmReset = false
                    },
                    shape = MaterialTheme.shapes.medium
                ) { Text("Reset progress") }
```

New:

```kotlin
                Button(
                    onClick = {
                        progress.clearEverything()
                        milestones.resetAll()
                        confirmReset = false
                    },
                    shape = MaterialTheme.shapes.medium
                ) { Text("Reset progress") }
```

- [ ] **Step 10: `MainActivity.kt` — add `festiveBuzz()` next to the existing `buzz()`**

Old:

```kotlin
internal fun buzz(context: Context) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
        )
    }
}
```

New:

```kotlin
internal fun buzz(context: Context) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
        )
    }
}

/** Slightly longer/bouncier celebratory pattern for milestone unlocks —
 *  distinct from [buzz]'s per-step completion pulse so a milestone reads
 *  as a bigger moment. */
internal fun festiveBuzz(context: Context) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120, 90, 220), -1)
        )
    }
}
```

- [ ] **Step 11: `MainActivity.kt` — add the new imports**

Add these to the existing import block (alongside the other `androidx.compose.animation.*`/`androidx.compose.material.icons.*` imports — `Icons.Filled.*` is already wildcard-imported so `LocalFireDepartment` needs no separate import):

```kotlin
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
```

- [ ] **Step 12: `TrainingComponents.kt` — add the `ConfettiBurst` composable**

Append to `app/src/main/java/com/personal/twelveweek/ui/TrainingComponents.kt` (same file as `ResistanceBandMark`):

```kotlin
/** Short, non-blocking confetti burst for workout-completion celebrations —
 *  purely decorative, plays once over ~1.2s and stops; never gates the
 *  screen's exit action. Colors pulled from the app's own Band palette so
 *  it matches the existing visual language instead of generic confetti. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, particleCount: Int = 28) {
    val burstColors = listOf(
        BandBlue, BandCoral, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary
    )
    val particles = remember {
        List(particleCount) { i ->
            ConfettiParticle(
                angle = Random.nextFloat() * (kotlin.math.PI.toFloat() * 2f),
                speed = 0.25f + Random.nextFloat() * 0.35f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 540f,
                size = (6 + Random.nextInt(6)).dp,
                color = burstColors[i % burstColors.size]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1200, easing = LinearEasing))
    }
    if (progress.value >= 1f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val fade = (1f - t).coerceIn(0f, 1f)
        val reach = minOf(size.width, size.height)
        val originX = size.width / 2f
        val originY = size.height * 0.18f
        particles.forEach { p ->
            val outward = reach * p.speed * t
            val x = originX + cos(p.angle) * outward
            val fall = size.height * 0.55f * t * t
            val y = originY + sin(p.angle) * outward * 0.4f + fall
            val half = p.size.toPx() / 2f
            rotate(p.rotationSpeed * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - half, y - half * 0.6f),
                    size = Size(p.size.toPx(), p.size.toPx() * 0.6f)
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val rotationSpeed: Float,
    val size: Dp,
    val color: Color
)
```

Add these imports to `TrainingComponents.kt`:

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
```

(`Offset`, `Size`, `Color`, `Dp`, `dp` are already imported in this file.)

- [ ] **Step 13: `GuidedSessionScreen.kt` — accept `weeks` + `milestones`, compute newly-crossed milestones**

Signature. Old:

```kotlin
@Composable
fun GuidedSessionScreen(
    workout: Workout,
    progress: ProgressStore,
    onExit: () -> Unit
) {
```

New:

```kotlin
@Composable
fun GuidedSessionScreen(
    workout: Workout,
    weeks: List<Week>,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    onExit: () -> Unit
) {
```

Right after the existing `val finisherLine = remember(finished) { ... }` / `LaunchedEffect(finished) { if (finished) voice.speak(finisherLine) }` block, and before `if (finished) { SessionCompleteScreen(...) ... }`, insert:

```kotlin
    val newMilestones = remember(finished) {
        if (!finished) return@remember emptyList<Milestone>()
        val workoutsCompleted = weeks.flatMap { it.workouts }.count { w ->
            val k = w.allKeys()
            k.isNotEmpty() && progress.countDone(k) == k.size
        }
        milestones.checkAndConsume(progress.currentStreak(), workoutsCompleted)
    }
```

Then update the `SessionCompleteScreen(...)` call itself. Old:

```kotlin
    if (finished) {
        SessionCompleteScreen(
            headline = finisherLine,
            movementCount = steps.size,
            onExit = onExit
        )
        return
    }
```

New:

```kotlin
    if (finished) {
        SessionCompleteScreen(
            headline = finisherLine,
            movementCount = steps.size,
            streakDays = progress.currentStreak(),
            newMilestones = newMilestones,
            onExit = onExit
        )
        return
    }
```

- [ ] **Step 14: `GuidedSessionScreen.kt` — redesign `SessionCompleteScreen`**

Old:

```kotlin
@Composable
private fun SessionCompleteScreen(
    movementCount: Int,
    onExit: () -> Unit,
    headline: String = "Workout complete"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ResistanceBandMark(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            headline.ifEmpty { "Workout complete" },
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "$movementCount movements recorded. Your plan is ready for the next session.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Return to workout")
        }
    }
}
```

New:

```kotlin
@Composable
private fun SessionCompleteScreen(
    movementCount: Int,
    streakDays: Int,
    newMilestones: List<Milestone>,
    onExit: () -> Unit,
    headline: String = "Workout complete"
) {
    val context = LocalContext.current
    LaunchedEffect(newMilestones) {
        if (newMilestones.isNotEmpty()) festiveBuzz(context)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ResistanceBandMark(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                headline.ifEmpty { "Workout complete" },
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            if (streakDays >= 2) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🔥 $streakDays day streak",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "$movementCount movements recorded. Your plan is ready for the next session.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            newMilestones.forEach { milestone ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "🎉 New milestone: ${milestoneLabel(milestone)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Return to workout")
            }
        }
        ConfettiBurst(modifier = Modifier.fillMaxSize())
    }
}

private fun milestoneLabel(milestone: Milestone): String = when (milestone.kind) {
    MilestoneKind.STREAK -> "${milestone.threshold}-day streak!"
    MilestoneKind.WORKOUTS -> "${milestone.threshold} workouts done!"
}
```

- [ ] **Step 15: `GuidedSessionScreen.kt` — add the new imports**

Add:

```kotlin
import com.personal.twelveweek.Milestone
import com.personal.twelveweek.MilestoneKind
import com.personal.twelveweek.MilestoneTracker
import com.personal.twelveweek.Week
import com.personal.twelveweek.festiveBuzz
```

(`com.personal.twelveweek.buzz` is already imported in this file — add `festiveBuzz` alongside it. `androidx.compose.foundation.layout.Box` is already imported.)

- [ ] **Step 16: Verify**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass. Manually smoke-test if a device/emulator is available: complete a workout, confirm confetti plays once and the "Return to workout" button is never blocked by it; check the Today screen shows no streak chip on day 1 (< 1 day isn't possible — confirm it appears after the first completion, i.e. `currentStreak() == 1`, per the design `>= 1` threshold) and that repeated completions the same day don't change the streak count.

- [ ] **Step 17: Commit**

```bash
git add app/src/main/java/com/personal/twelveweek/MainActivity.kt \
        app/src/main/java/com/personal/twelveweek/ui/TrainingComponents.kt \
        app/src/main/java/com/personal/twelveweek/ui/GuidedSessionScreen.kt
git commit -m "feat(android): streak chip, milestones, confetti completion celebration"
```

---

### Task 6: Web — wire streaks/milestones into the app, add completion celebration

**Files:**
- Modify: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt`
- Modify: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebTrainingComponents.kt`
- Modify: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebGuidedSession.kt`

**Interfaces:**
- Consumes: same commonMain types as Task 5 (`StreakTracker`, `MilestoneTracker`, `Milestone`, `MilestoneKind`, `ProgressStore.currentStreak()`), plus the existing `webBuzz()` (from `WebHaptics.kt`, same package — no import needed).
- Produces: nothing consumed by later tasks (Web leaf, mirrors Task 5 independently — no shared UI code between the two platforms, per this codebase's existing convention).

Same approach as Task 5: small, uniquely-matchable old→new text replacements, applied in order.

- [ ] **Step 1: `WebApp.kt` — construct the trackers in `WebApp()`**

Old:

```kotlin
    val progress = remember { ProgressStore(RawKeyFlagStore("twelve_week_progress")) }
```

New:

```kotlin
    val streaks = remember { StreakTracker(RawKeyFlagStore("twelve_week_streak_days")) }
    val progress = remember { ProgressStore(RawKeyFlagStore("twelve_week_progress"), streaks) }
    val milestones = remember { MilestoneTracker(RawPreferenceStore("twelve_week_milestones")) }
```

- [ ] **Step 2: `WebApp.kt` — pass `milestones` into the `WebAppShell` call site**

Old:

```kotlin
                else -> Crossfade(targetState = program, label = "activeProgram") { current ->
                    WebAppShell(
                        program = current,
                        libraryIndex = entries,
                        selectedProgramId = selectedProgramId,
                        progress = progress,
                        settings = settings,
```

New:

```kotlin
                else -> Crossfade(targetState = program, label = "activeProgram") { current ->
                    WebAppShell(
                        program = current,
                        libraryIndex = entries,
                        selectedProgramId = selectedProgramId,
                        progress = progress,
                        milestones = milestones,
                        settings = settings,
```

- [ ] **Step 3: `WebApp.kt` — `WebAppShell` signature + both internal `WebAppContent` calls**

Signature. Old:

```kotlin
private fun WebAppShell(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    settings: WebSettings,
```

New:

```kotlin
private fun WebAppShell(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    settings: WebSettings,
```

Both `WebAppContent(...)` calls inside `WebAppShell` (expanded and non-expanded branches) currently look like this — apply to both occurrences. Old:

```kotlin
                WebAppContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    settings = settings,
```

New:

```kotlin
                WebAppContent(
                    program = program,
                    libraryIndex = libraryIndex,
                    selectedProgramId = selectedProgramId,
                    progress = progress,
                    milestones = milestones,
                    settings = settings,
```

- [ ] **Step 4: `WebApp.kt` — `WebAppContent` signature**

Old:

```kotlin
private fun WebAppContent(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    settings: WebSettings,
```

New:

```kotlin
private fun WebAppContent(
    program: LibraryProgram,
    libraryIndex: List<IndexEntry>,
    selectedProgramId: String,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    settings: WebSettings,
```

- [ ] **Step 5: `WebApp.kt` — `WebScreen.Plan` branch passes `milestones`**

Old:

```kotlin
            WebScreen.Plan -> PlanScreen(
                weeks = program.weeks,
                programTitle = program.meta.title,
                progress = progress,
                onOpenWeek = { onScreenChange(WebScreen.WeekDetail(it)) },
                modifier = content
            )
```

New:

```kotlin
            WebScreen.Plan -> PlanScreen(
                weeks = program.weeks,
                programTitle = program.meta.title,
                progress = progress,
                milestones = milestones,
                onOpenWeek = { onScreenChange(WebScreen.WeekDetail(it)) },
                modifier = content
            )
```

- [ ] **Step 6: `WebApp.kt` — `WebScreen.GuidedSession` branch passes `weeks` + `milestones`**

Old:

```kotlin
            is WebScreen.GuidedSession -> {
                val workout = program.weeks.first { it.number == screen.week }
                    .workouts.first { it.index == screen.workout }
                Box(modifier = content.fillMaxSize()) {
                    WebGuidedSessionScreen(
                        workout = workout,
                        progress = progress,
                        settings = settings,
                        onExit = { onScreenChange(WebScreen.WorkoutDetail(screen.week, screen.workout)) }
                    )
                }
            }
```

New:

```kotlin
            is WebScreen.GuidedSession -> {
                val workout = program.weeks.first { it.number == screen.week }
                    .workouts.first { it.index == screen.workout }
                Box(modifier = content.fillMaxSize()) {
                    WebGuidedSessionScreen(
                        workout = workout,
                        weeks = program.weeks,
                        progress = progress,
                        milestones = milestones,
                        settings = settings,
                        onExit = { onScreenChange(WebScreen.WorkoutDetail(screen.week, screen.workout)) }
                    )
                }
            }
```

- [ ] **Step 7: `WebApp.kt` — `TodayScreen` header gets a streak chip**

Old:

```kotlin
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
```

(this is the tail of `TodayScreen`'s header `item { Row(...) { Column {...}; IconButton(...) } }`.) New:

```kotlin
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val streakDays = progress.currentStreak()
                    if (streakDays >= 1) {
                        WebStreakChip(streakDays = streakDays)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
```

Confirm the match is inside `TodayScreen` (not `PlanScreen`, whose header ends with a `DropdownMenu`, not this `IconButton`) before applying.

- [ ] **Step 8: `WebApp.kt` — add the `WebStreakChip` composable**

Add near `TodayScreen`:

```kotlin
/** Web port of the Android app's `StreakChip` — same shape, same pulse. */
@Composable
private fun WebStreakChip(streakDays: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "streakPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakPulseScale"
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = "$streakDays day streak",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$streakDays",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
```

- [ ] **Step 9: `WebApp.kt` — `PlanScreen` gets `milestones` param + reset cascade**

Signature. Old:

```kotlin
private fun PlanScreen(
    weeks: List<Week>,
    programTitle: String,
    progress: ProgressStore,
    onOpenWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
```

New:

```kotlin
private fun PlanScreen(
    weeks: List<Week>,
    programTitle: String,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    onOpenWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
```

Reset button. Old:

```kotlin
                Button(onClick = { progress.clearEverything(); confirmReset = false }, shape = MaterialTheme.shapes.medium) {
                    Text("Reset progress")
                }
```

New:

```kotlin
                Button(
                    onClick = {
                        progress.clearEverything()
                        milestones.resetAll()
                        confirmReset = false
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Reset progress")
                }
```

- [ ] **Step 10: `WebApp.kt` — add the new imports**

```kotlin
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.graphics.graphicsLayer
import com.personal.twelveweek.MilestoneTracker
import com.personal.twelveweek.StreakTracker
```

(`Milestone`/`MilestoneKind` aren't referenced directly in `WebApp.kt` — only in `WebGuidedSession.kt`, Step 14 below. `RawKeyFlagStore`/`RawPreferenceStore`/`Week` are already imported.)

- [ ] **Step 11: `WebTrainingComponents.kt` — add the `WebConfettiBurst` composable**

Append to `app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebTrainingComponents.kt`:

```kotlin
/** Web port of the Android app's `ConfettiBurst` — same shape, same
 *  ~1.2s one-shot burst, using the Web Band palette. */
@Composable
fun WebConfettiBurst(modifier: Modifier = Modifier, particleCount: Int = 28) {
    val burstColors = listOf(
        WebBandBlue, WebBandCoral, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary
    )
    val particles = remember {
        List(particleCount) { i ->
            WebConfettiParticle(
                angle = Random.nextFloat() * (kotlin.math.PI.toFloat() * 2f),
                speed = 0.25f + Random.nextFloat() * 0.35f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 540f,
                size = (6 + Random.nextInt(6)).dp,
                color = burstColors[i % burstColors.size]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1200, easing = LinearEasing))
    }
    if (progress.value >= 1f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val fade = (1f - t).coerceIn(0f, 1f)
        val reach = minOf(size.width, size.height)
        val originX = size.width / 2f
        val originY = size.height * 0.18f
        particles.forEach { p ->
            val outward = reach * p.speed * t
            val x = originX + cos(p.angle) * outward
            val fall = size.height * 0.55f * t * t
            val y = originY + sin(p.angle) * outward * 0.4f + fall
            val half = p.size.toPx() / 2f
            rotate(p.rotationSpeed * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - half, y - half * 0.6f),
                    size = Size(p.size.toPx(), p.size.toPx() * 0.6f)
                )
            }
        }
    }
}

private data class WebConfettiParticle(
    val angle: Float,
    val speed: Float,
    val rotationSpeed: Float,
    val size: Dp,
    val color: Color
)
```

Add these imports to `WebTrainingComponents.kt`:

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
```

(`animateFloatAsState` is already imported but unused by this new function — leave it, it's still used by `ProgressBand`. `Offset`, `Size`, `Color`, `Dp`, `dp` are already imported.)

- [ ] **Step 12: `WebGuidedSession.kt` — accept `weeks` + `milestones`, compute newly-crossed milestones**

Signature. Old:

```kotlin
@Composable
fun WebGuidedSessionScreen(
    workout: Workout,
    progress: ProgressStore,
    settings: WebSettings,
    onExit: () -> Unit
) {
```

New:

```kotlin
@Composable
fun WebGuidedSessionScreen(
    workout: Workout,
    weeks: List<Week>,
    progress: ProgressStore,
    milestones: MilestoneTracker,
    settings: WebSettings,
    onExit: () -> Unit
) {
```

Right after the existing `val finisherLine = remember(finished) { ... }` / `LaunchedEffect(finished) { if (finished) voice.speak(finisherLine) }` block, and before `if (finished) { WebSessionCompleteScreen(...) ... }`, insert:

```kotlin
    val newMilestones = remember(finished) {
        if (!finished) return@remember emptyList<Milestone>()
        val workoutsCompleted = weeks.flatMap { it.workouts }.count { w ->
            val k = w.allKeys()
            k.isNotEmpty() && progress.countDone(k) == k.size
        }
        milestones.checkAndConsume(progress.currentStreak(), workoutsCompleted)
    }
```

Then update the call. Old:

```kotlin
    if (finished) {
        WebSessionCompleteScreen(headline = finisherLine, movementCount = steps.size, onExit = onExit)
        return
    }
```

New:

```kotlin
    if (finished) {
        WebSessionCompleteScreen(
            headline = finisherLine,
            movementCount = steps.size,
            streakDays = progress.currentStreak(),
            newMilestones = newMilestones,
            onExit = onExit
        )
        return
    }
```

- [ ] **Step 13: `WebGuidedSession.kt` — redesign `WebSessionCompleteScreen`**

Old:

```kotlin
@Composable
private fun WebSessionCompleteScreen(movementCount: Int, onExit: () -> Unit, headline: String = "Workout complete") {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ResistanceBandMark(modifier = Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(24.dp))
        Text(headline.ifEmpty { "Workout complete" }, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "$movementCount movements recorded. Your plan is ready for the next session.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) { Text("Return to workout") }
    }
}
```

New:

```kotlin
@Composable
private fun WebSessionCompleteScreen(
    movementCount: Int,
    streakDays: Int,
    newMilestones: List<Milestone>,
    onExit: () -> Unit,
    headline: String = "Workout complete"
) {
    LaunchedEffect(newMilestones) {
        if (newMilestones.isNotEmpty()) webBuzz()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ResistanceBandMark(modifier = Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(24.dp))
            Text(headline.ifEmpty { "Workout complete" }, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            if (streakDays >= 2) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🔥 $streakDays day streak",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "$movementCount movements recorded. Your plan is ready for the next session.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            newMilestones.forEach { milestone ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "🎉 New milestone: ${webMilestoneLabel(milestone)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Return to workout") }
        }
        WebConfettiBurst(modifier = Modifier.fillMaxSize())
    }
}

private fun webMilestoneLabel(milestone: Milestone): String = when (milestone.kind) {
    MilestoneKind.STREAK -> "${milestone.threshold}-day streak!"
    MilestoneKind.WORKOUTS -> "${milestone.threshold} workouts done!"
}
```

- [ ] **Step 14: `WebGuidedSession.kt` — add the new imports**

```kotlin
import com.personal.twelveweek.Milestone
import com.personal.twelveweek.MilestoneKind
import com.personal.twelveweek.MilestoneTracker
import com.personal.twelveweek.Week
```

(`Box` and `LaunchedEffect` are already imported. `webBuzz` is in the same package, `com.personal.twelveweek.web` — no import needed.)

- [ ] **Step 15: Verify**

Run: `./gradlew :app:compileKotlinWasmJs`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 16: Commit**

```bash
git add app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebApp.kt \
        app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebTrainingComponents.kt \
        app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/WebGuidedSession.kt
git commit -m "feat(web): streak chip, milestones, confetti completion celebration"
```

---

## Final Verification (after all 6 tasks)

Run: `./gradlew :app:compileDebugKotlin :app:compileKotlinWasmJs :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (existing + new `StreakTest`, `MilestonesTest`, plus the 3 new `ProgressTest` cases).
