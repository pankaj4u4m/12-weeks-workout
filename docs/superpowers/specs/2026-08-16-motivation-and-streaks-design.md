# Streaks, milestones & celebration polish — design spec

Date: 2026-08-16
Status: approved (brainstorming), pending implementation plan

## Problem

User feedback: the app should be "more interesting and motivational." Auditing
what already exists vs. what's missing:

**Already built** (do not duplicate):
- In-workout motivational cues (`MotivationCoach.kt`: `MotivationLibrary`,
  `RestCues`, `StretchCues`, `FinisherCues`, `RepCues`), voice coach, and a
  live `TimerRing` / `ProgressDoneButton` with animated progress.
- Per-workout and per-week progress bars (`NextWorkoutPanel`, `WeekBand`,
  `PlanScreen`).
- Haptic (`buzz()`) and audible completion cues.

**Missing** — nothing rewards *consistency* over time, and the one moment that
should feel like a payoff (`SessionCompleteScreen`) is a plain headline with no
animation, no streak information, and no sense of progress toward anything
bigger than "today's workout."

## Scope decisions (from brainstorming)

- Streak rule: **any day with ≥1 set logged** keeps the streak alive (forgiving
  — partial effort still counts, lower pressure to quit after one bad day).
- Celebration intensity: **playful but light** — a short confetti burst +
  haptic + one line, never a blocking full-screen ceremony.
- Areas covered: gamification (streaks/milestones), progress visualization
  (streak chip), visual polish (confetti, pulse animation). In-workout
  motivation is already covered by existing code — out of scope here.

## Key existing invariant this design relies on

All completions already funnel through one chokepoint: `ProgressStore.setDone(key,
value)` (`app/src/commonMain/kotlin/com/personal/twelveweek/Progress.kt`),
called from the guided session, workout-detail checkboxes, and week-detail
screens alike. Hooking streak tracking into `setDone(key, true)` means every
existing completion path feeds streaks for free — no new call sites to find
or thread through.

## Design

### A. `todayIso()` — new expect/actual (commonMain)

```kotlin
// commonMain
expect fun todayIso(): String // "2026-08-16", local device date
```

- Android actual: `java.time.LocalDate.now().toString()` (already ISO format).
- wasmJs actual: `kotlinx.js.Date().let { "${it.getFullYear()}-..." }` (pad
  month/day to 2 digits — JS `Date` has no built-in ISO-date-only formatter
  that respects local time; `toISOString()` is UTC and would misdate sessions
  done late at night in most timezones, so this must build the string from the
  local-time getters, not `toISOString()`).

### B. `StreakTracker` (commonMain, new file `Streak.kt`)

Reuses the existing `RawKeyFlagStore` pattern (same shape `ProgressStore`
already uses) — one key per active ISO date, presence = "did ≥1 set that day":

```kotlin
class StreakTracker(private val store: RawKeyFlagStore) {
    fun markActive(dateIso: String = todayIso()) = store.setPresent(dateIso)

    fun currentStreak(today: String = todayIso()): Int {
        val days = store.allKeys()
        if (days.isEmpty()) return 0
        var cursor = LocalDate.parse(today) // commonMain-safe manual parse, see below
        // today counts as still "alive" even if not yet marked (streak doesn't
        // break until a full day passes with nothing logged); walk backward
        // from today, allow today itself to be absent.
        var count = 0
        var d = cursor
        var first = true
        while (days.contains(d.isoString()) || first) {
            if (days.contains(d.isoString())) count++
            else if (!first) break
            d = d.minusDays(1)
            first = false
        }
        return count
    }

    fun totalActiveDays(): Int = store.allKeys().size
}
```

(Exact recurrence handled with a small dependency-free date-math helper — see
Task breakdown; no `kotlinx-datetime` dependency added, since the only
operations needed are "parse an ISO date" and "subtract one day," both doable
with plain integer year/month/day arithmetic in ~20 lines, avoiding a new
multiplatform dependency for one function.)

Namespace: `RawKeyFlagStore("twelve_week_streak_days")`.

### C. `MilestoneTracker` (commonMain, new file in `Streak.kt`)

Fires each threshold exactly once, backed by two ints in a
`RawPreferenceStore("twelve_week_milestones")`:

```kotlin
enum class MilestoneKind { STREAK, WORKOUTS }
data class Milestone(val kind: MilestoneKind, val threshold: Int)

object MilestoneThresholds {
    val STREAK_DAYS = listOf(3, 7, 14, 21, 30, 60, 90)
    val WORKOUTS_DONE = listOf(1, 5, 10, 25, 50)
}

class MilestoneTracker(private val store: RawPreferenceStore) {
    // Returns newly-crossed milestones (0, 1, or 2 — a streak and a
    // workout-count milestone could both land on the same completion) and
    // persists the new high-water marks so each fires exactly once.
    fun checkAndConsume(currentStreak: Int, workoutsCompleted: Int): List<Milestone> { ... }
}
```

No "before" snapshot needed: compare the current value against the stored
high-water mark, celebrate any threshold in `(mark, current]`, then bump the
mark to `current`.

`workoutsCompleted` = count of fully-completed workouts in the *active
program* (same computation `PlanScreen` already does: `allWorkouts.count {
keys.isNotEmpty() && progress.countDone(keys) == keys.size }`) — deliberately
scoped per-program, matching the existing "% complete" stat, not a cross-program
lifetime counter (no such concept exists anywhere else in the app today).

### D. Wiring `ProgressStore` → `StreakTracker`

`ProgressStore` gains an optional collaborator so existing call sites
(`ProgressStore(RawKeyFlagStore(...))`) don't break:

```kotlin
class ProgressStore(
    private val store: RawKeyFlagStore,
    private val streaks: StreakTracker? = null
) {
    fun setDone(key: String, value: Boolean) {
        // existing body unchanged
        if (value) streaks?.markActive()
    }
}
```

`AppRoot()` (Android) / its Web equivalent constructs the real
`StreakTracker` and passes it in — one line at each of the two existing
`ProgressStore(...)` construction sites.

### E. `SessionCompleteScreen` celebration (Android + Web mirror)

Current screen: `ResistanceBandMark` image + plain headline + "movements
recorded" line + "Return to workout" button. Additions:

1. **Confetti burst** — new small reusable composable `ConfettiBurst` (Compose
   `Canvas`, ~24 particles, radial burst + gravity fall, fades out over
   ~1.2s via `LaunchedEffect(Unit)` + `animateFloatAsState`/manual frame
   clock). Colors pulled from `MaterialTheme.colorScheme` (primary/secondary/
   tertiary) so it matches the app's existing cobalt/coral palette instead of
   hardcoded colors. Purely decorative — never blocks the exit button, never
   loops.
2. **Streak line** — "🔥 X day streak" shown under the headline whenever
   `currentStreak() ≥ 2` (a 1-day streak isn't a streak yet).
3. **Milestone banner** — when `MilestoneTracker.checkAndConsume(...)`
   returns ≥1 result, an extra highlighted line: "🎉 New milestone: 7-day
   streak!" or "🎉 10 workouts done!" (multiple milestones → multiple lines,
   rare but handled). Triggers a second, more festive haptic pattern distinct
   from the per-step `buzz()` (reuse `VibrationEffect.createWaveform` with a
   different, slightly longer pattern — same mechanism, new constant).
4. Confetti still plays on an ordinary (non-milestone) completion, just
   without the banner/second haptic — "playful but light" applies to every
   completion, milestones are the extra layer on top.

### F. Today screen streak chip (Android + Web mirror)

A small chip next to the "TwelveWeek / {programTitle}" header (same row as
the existing `IconButton(Settings)`): flame icon + `currentStreak()`, shown
only when `currentStreak() ≥ 1`. Subtle pulse: `infiniteTransition` scaling
the flame icon between 1.0–1.08x on a slow loop — the one genuinely-recurring
("alive") animation in the design, everything else is one-shot.

### G. Error handling / edge cases

- Fresh install, no data yet: `currentStreak() == 0`, chip hidden, no
  milestone spam on first-ever completion beyond the legitimate "1 workout
  done" / possibly "3-day streak" milestones if genuinely earned.
- Clock rollback / bad device date: `StreakTracker` only ever compares
  string-sortable ISO dates it wrote itself — a backward jump can under- or
  over-count by a few days at worst, never crash. No attempt to detect clock
  tampering (not worth the complexity for a personal fitness app).
- `ProgressStore.clearEverything()` (existing "reset progress" menu action in
  `PlanScreen`) must also clear `StreakTracker`'s store and
  `MilestoneTracker`'s high-water marks — otherwise a reset plan still shows
  an old streak/blocks milestones from re-firing. Add this call at the same
  site.

## Testing

- `commonTest`: `StreakTracker` — empty store, single day, consecutive days,
  a gap breaks the streak, streak counted through "today not yet marked"
  case, `totalActiveDays()`.
- `commonTest`: `MilestoneTracker.checkAndConsume` — no milestones below
  first threshold, exact threshold hit, skipping multiple thresholds in one
  jump (e.g. streak goes 0→7 directly), already-celebrated threshold doesn't
  refire, streak and workout milestones both firing on the same call.
- `commonTest`: `todayIso()` date-math helper (day rollover, month rollover,
  year rollover, leap day) — the hand-rolled subtract-one-day logic is the
  one genuinely new piece of arithmetic in this design and needs direct
  coverage.
- Manual/device: confetti renders and fades without jank; streak chip
  appears/disappears correctly across a simulated multi-day sequence (can be
  tested by directly manipulating the `RawKeyFlagStore` namespace during
  development, same technique used to verify the earlier program-cache fix).

## Out of scope (explicitly)

- No new stats/dashboard screen.
- No server-side sync of streak data (matches existing progress data, which
  is also local-only).
- No cross-program lifetime workout counter.
- No changes to the existing motivational-cue system (`MotivationCoach.kt`) —
  already covers in-workout motivation.
