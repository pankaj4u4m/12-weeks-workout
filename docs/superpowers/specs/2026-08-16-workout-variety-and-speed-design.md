# Workout variety, faster plan switching, media coverage — design spec

Date: 2026-08-16
Status: approved (brainstorming), pending implementation plan

## Problem

Five related complaints about the current app:

1. **Repetitive content.** Every program repeats the exact same warm-up, cool-down,
   and Round exercise *names* in every one of its 12 weeks. Verified on
   `beginner-full-body-home.json`: warm-up section is byte-identical across all
   12 weeks; Round 1 exercise names are identical across all 12 weeks too — only
   `reps`/`seconds` values change, in three 4-week blocks (e.g. Squats: 10 →
   12 → 15 reps). This holds for other programs by construction (same authoring
   pattern).
2. **Plan list doesn't show workouts.** `ProgramPickerScreen`'s `ProgramCard`
   shows only metadata (level, weeks, equipment, minutes, focus tags) — no way
   to see what the actual workouts look like before switching to a plan.
3. **Switching plans feels slow / unclear what it's doing.** `AppRoot.selectProgram()`
   sets `activeProgram = null`, which forces the `activeProgram == null` branch
   to render a full-screen `LoadingScreen()` until the new program re-parses.
   In practice this is *not* a slow operation — `ProgramSyncRepository.sync()`
   already downloads every program file to local disk on every app launch, so
   `ProgramLibrary.load(id)` on switch is a local file read + JSON parse, not a
   network call. The full-screen loader is pure unnecessary UI friction.
4. **No background media prefetch.** `ExerciseMediaRepository.getBundle()` is
   only ever called on-demand when a user opens an exercise's detail sheet or
   guided session step. Nothing warms the cache ahead of time.
5. **Media coverage has real gaps.** Audited every exercise row across all 35
   bundled program JSON files (52,469 exercise rows, 230 distinct exercise
   names): 6,000 rows are `Pause` (rest slots, correctly and intentionally
   uncovered — not a real exercise). Exactly **7 other exercise names** have
   zero media match (no `wgerId`, `exerciseDbId`, `freeExerciseDbId`, or
   `externalMediaUrl`): `Broad Jump`, `Curtsy Lunge`, `Side Plank with Rotation L`,
   `Side Plank with Rotation R`, `Single-Arm Plank L`, `Single-Arm Plank R`,
   `Skater Jumps`.

## Key invariant that de-risks the content-variety work

`Workout.keyFor(sectionIndex, itemIndex)` (see `Program.kt`) produces progress
keys like `"program-1:w1-o1-s0-i0"` — **keyed by position, not by exercise
identity**. Changing *which* exercise occupies a given week/section/slot never
touches progress tracking; a user's ticks stay tied to the slot regardless of
what exercise fills it. This means rotating exercise content week-to-week is
safe with zero migration concern.

## Scope decisions (from brainstorming)

- Warm-up/cool-down variety: **rotating pool**, not a one-time hand rewrite —
  stays fresh automatically, no recurring manual authoring.
- "Work on all exercises much more": **both** real media coverage *and*
  exercise variety in the Round sections (not just warm-up/cool-down).
- Plan list preview: **inline expand** on the picker card, not a separate screen.
- Background media prefetch: **whole active program**, not just the next workout.

## Design

### A. Exercise pool + rotation (content, not app code)

The 35 programs already use 230 distinct exercise names between them, and only
7 of those lack media (see above) — so the pool for rotation is built from
**exercises that already exist in the program library today**, not new,
un-vetted names. A program currently only samples ~15-20 of the 230 names
in any given slot type; opening each slot to the full applicable pool is the
variety win, with no new media-curation dependency.

**`programs/_pools/exercise-catalog.json`** (new, source-of-truth, hand-reviewed):
one entry per distinct exercise name (deduplicated across all 35 programs,
keeping its existing `wgerId`/`exerciseDbId`/`freeExerciseDbId`/`externalMediaUrl`),
tagged with:
- `kind`: `REPS` or `SECONDS` — derived automatically from existing data
  (whether `reps` or `seconds` is non-null wherever the name appears).
- `roles`: subset of `WARMUP`, `COOLDOWN`, `ROUND` — which section types this
  exercise is appropriate for (an exercise can serve more than one role, e.g.
  "Jumping Jacks" fits `WARMUP` and `ROUND`). Seeded from which section titles
  the name currently appears under, then reviewed for sanity by whoever builds
  the catalog — e.g. don't let a max-effort move default into `WARMUP` just
  because of how one program happened to use it.
- `equipment`: `HOME` and/or `GYM` — seeded from which programs currently
  contain the name (a `-home.json` vs `-gym.json` file), reviewed for sanity.
- `focus`: subset of `FULL_BODY`, `LEGS`, `ABS`, `CORE`, `UPPER_BODY`, `STRENGTH`
  — seeded from which programs' `focusAreas` currently contain the name,
  reviewed for sanity. Only meaningful for `ROUND`-role entries; warm-up/
  cool-down entries are generic mobility/stretch moves independent of focus.

**Rotation script** (new, `scripts/rotate_program_content.py`, committed for
reuse — e.g. next time a program is added): for every slot (program × week ×
workout × section × exercise index) across all 35 programs:
1. Determine the slot's `kind` from its current `reps`/`seconds` (non-null one).
2. Determine the slot's `role` from its section title (`Warm up` → `WARMUP`,
   `Cool down` → `COOLDOWN`, `Round N` → `ROUND`); rows named `Pause` are left
   untouched (rest slots, not rotated).
3. Build the eligible candidate pool: catalog entries matching `kind`, `role`,
   the program's `equipment`, and (for `ROUND` only) intersecting the program's
   `focusAreas`.
4. Deterministically pick one candidate per `(programId, week, section index,
   item index)` — a stable hash seed, not `random`, so re-runs are reproducible
   — excluding whichever candidate was picked for the same slot in the
   previous or next week (avoids adjacent-week repeats). Falls back to keeping
   the existing exercise if the eligible pool has only one candidate (some
   niche program/slot combinations may not have enough variety yet — logged,
   not silently forced).
5. Replace `name`, `raw`, `wgerId`, `exerciseDbId`, `freeExerciseDbId`,
   `externalMediaUrl` on that row. **`reps`/`seconds` values are left exactly
   as authored** — the existing per-block progression (10 → 12 → 15, etc.) is
   untouched; only *which* exercise fills the slot changes, never the
   difficulty curve.
6. Writes output to all three synced locations (`programs/`,
   `app/src/main/assets/programs/`, `app/src/wasmJsMain/resources/programs/`),
   matching the existing manual-sync convention.

This is a one-time content regeneration (script re-run whenever pools grow).
No Kotlin parsing changes needed — `ProgramJson.kt`/`Exercise` already handle
arbitrary exercise names per slot.

### B. Fix the 7 real media gaps

Same curation discipline as `CREDITS.md`'s existing one-off entries: for each
of `Broad Jump`, `Curtsy Lunge`, `Side Plank with Rotation L/R`, `Single-Arm
Plank L/R`, `Skater Jumps`, search RapidAPI ExerciseDB / wger / free-exercise-db
for a real match first; only fall back to a verified individually-checked free
hotlink (`externalMediaUrl`, credited in `CREDITS.md`, same pattern as the
existing Jumping Jack/Diamond Push-up/Downward Facing Dog/Crunch Floor rows) if
none of the id-based providers has one. Never a forced/fuzzy match — a gap that
stays a gap is acceptable and must be left alone, exactly like the existing
policy. Update `exercise-catalog.json` (and thus every program row using that
name, including newly-rotated-in ones) once matched.

### C. Catalog completeness test

Extend `commonTest` (pattern already used by `ProgramJsonTest.kt`) with a new
test: every distinct exercise name across the pool catalog + all 35 programs
has at least one of the four media identifiers, **or** is in an explicit
`Pause`-only allowlist. Catches future silent gaps (e.g. someone adds a new
program/pool entry without media) instead of relying on a manual audit.

### D. Plan list: inline expand

`ProgramCard` (`ProgramPickerScreen.kt`) gains a "See workouts" toggle. On
expand, it fetches the full program via `ProgramLibrary.load(entry.meta.id)`
(same call `AppRoot` already uses to load the *active* program — cheap local
read per §Problem-3, not a network call) and shows a compact week-by-week
summary (e.g. per week: workout count + first few exercise names per
workout), cached in the picker's own state so re-expanding doesn't reload.
Card tap toggles expand/collapse; **only the explicit "Use this plan" button
switches the program** — browsing/previewing never accidentally selects.
Mirrored in `WebApp.kt`'s picker for parity (per `docs/webapp-android-parity.md`
convention already established in this repo).

### E. Instant plan switching

`AppRoot.selectProgram()` currently does:
```kotlin
fun selectProgram(id: String) {
    selectedProgramStore.set(id)
    loadFailed = false
    activeProgram = null   // <- forces the full-screen LoadingScreen branch
    selectedProgramId = id
    screen = Screen.Today
}
```
Change: don't null `activeProgram` on switch. Keep rendering the current
`AppShell` with the outgoing program while the new one loads in the
background (the existing `LaunchedEffect(selectedProgramId, libraryIndex)`
already does the load); swap `activeProgram` in place once ready, with a brief
crossfade on whichever screen is currently showing so the change reads as
intentional rather than jarring. The `activeProgram == null -> LoadingScreen()`
branch stays, but now only fires on genuine first-launch cold start (nothing
loaded yet at all), never on a switch. Same treatment applied to `WebApp.kt`'s
equivalent.

### F. Background whole-program media prefetch

New: after a program finishes loading (both initial load and post-switch),
kick off a low-priority background coroutine that walks every exercise across
all of that program's weeks/workouts and calls the existing
`ExerciseMediaRepository.getBundle()` once per **distinct exercise name**
(dedup — no point re-fetching the same exercise 200 times across weeks) to
warm the OkHttp disk cache. Throttled (small delay between requests, not a
burst) to stay well inside RapidAPI's free-tier rate limit; skips anything
already cache-hit; cancelled if the program changes again mid-prefetch
(`selectedProgramId` changes → cancel + restart for the new one). Fully
silent — no UI, no error surfacing; a miss during prefetch just means that
exercise falls back to on-demand fetch (today's behavior) when actually
viewed, exactly as if prefetch didn't run at all.

### G. Smoothness pass

After A–F land: one audit-and-fix pass over navigation transitions (crossfades
where screens swap, per §E), list recomposition (stable `key`s on `LazyColumn`/
`LazyRow` items — already used in `ProgramPickerScreen` via `key = { it.meta.id
}`, verify elsewhere), and any other synchronous/blocking main-thread work
found, across both Android and web targets. Fix what's found; no rewrites.

## Data flow (plan switch + prefetch)

```
ProgramPickerScreen ──[Use this plan]──▶ selectProgram(id)
                                             │
                          selectedProgramId = id (screen stays on current content)
                                             │
                     LaunchedEffect(selectedProgramId) ──▶ ProgramLibrary.load(id)
                                             │                  (local disk read+parse)
                                             ▼
                          activeProgram swapped in place (crossfade)
                                             │
                          background prefetch coroutine started
                                             │
                for each distinct exercise ──▶ ExerciseMediaRepository.getBundle()
                                             │        (throttled, cancellable, silent)
                                             ▼
                                    OkHttp disk cache warmed
```

## Error handling

| Condition | Behavior |
|---|---|
| New program fails to load on switch | Existing `loadFailed` path already handles this (falls back to `ProgramPickerScreen`); unchanged — just no longer preceded by a spurious full-screen loader for the success case |
| Rotation script slot has < 2 eligible pool candidates | Keeps the existing exercise for that slot, logs it — never forces a bad substitution |
| Media gap-fill finds no real match for one of the 7 | Stays unmatched, same as today — falls back to external search buttons |
| Background prefetch hits a network/rate-limit error | Silently stops/skips that exercise; no UI impact, on-demand fetch still works later |
| Program switches again mid-prefetch | In-flight prefetch for the old program is cancelled, a new one starts for the new program |

## Testing

- New unit test (§C): catalog + program completeness (media or documented
  allowlist), extending the `commonTest` pattern already used by
  `ProgramJsonTest.kt`.
- New Python test alongside the rotation script: given a fixed seed, same
  inputs always produce the same picks (reproducibility), and `reps`/`seconds`
  are never altered by rotation (only name/raw/media ids are).
- Manual/device verification (same method used for prior features in this
  repo): gradle build + `installDebug`, switch programs and confirm no
  full-screen loader + progress ticks survive the switch untouched, expand a
  plan card and confirm the workout preview, background-prefetch a program and
  confirm subsequent exercise views are cache-instant/offline, spot-check a
  handful of rotated programs across two weeks to confirm variety without
  broken JSON. No new instrumented/UI test framework — consistent with this
  project's existing testing stance (personal single-user app).

## Explicitly out of scope

- Adding brand-new exercise names beyond the existing 230 (+7 gap-fills) — the
  variety win comes from wider reuse of the existing well-covered library, not
  from sourcing novel moves. A future spec can revisit this once the pool
  approach is in place and its ceiling is felt.
- Per-user customization of pool rotation (e.g. "never give me Burpees") — no
  settings/preferences system is being introduced for this.
- Any change to the reps/seconds progression curve itself — untouched by this
  work; only *which* exercise fills a slot changes.
- Wi-Fi-only gating for background prefetch — not requested; can be a later
  follow-up if data usage becomes a concern.
