# Guided workout media — design spec

Date: 2026-08-12
Status: approved (brainstorming), pending implementation plan

## Problem

Today, tapping the ▶ icon on an exercise opens an external browser (YouTube/Google
Image search). There is no in-app playable demonstration, and workouts are done
entirely from the static checklist — no guided, one-exercise-at-a-time session.
Goal: bring real video/step-by-step guidance in-app, and add a full-screen
"guided workout" mode, without regressing the existing checklist/progress flow
or introducing a paid/legally-risky dependency the user didn't knowingly opt into.

This spec covers **sub-project 1 of 3** identified during brainstorming:
1. **In-app media + guided workout session UX** — this document.
2. RapidAPI onboarding UX — folded into this spec (§5) since sub-project 1 is
   non-functional without it; kept minimal (one screen).
3. AI-generated / uploaded custom programs + program picker — explicitly out of
   scope here; separate future brainstorm.

## Media source

**ExerciseDB** (`edb-with-videos-and-images-by-ascendapi` on RapidAPI), chosen over
the free/public-domain `free-exercise-db` because the latter has no match for
several exercises central to this program (Jumping Jacks, Burpees, Wall Sit).
ExerciseDB has ~11,000 exercises with real video clips, images, step
instructions, and target muscles — good coverage for this program's ~47 unique
exercise names. Trade-off accepted by the user: requires a free RapidAPI
account/key (§5) and a live network dependency for first-time fetches.

Curation is a **one-time, build-time activity**, not a runtime search: during
implementation, each of the program's ~47 exercise names is matched by hand to
the best ExerciseDB `exerciseId`, and the mapping is hardcoded into the app.
Exercises with no good match (expected: a handful of program-specific bodyweight
moves) are deliberately left unmapped and always show the existing external
search fallback — never a forced bad match.

## Components

### `ExerciseMediaCatalog` (new, `app/.../media/ExerciseMediaCatalog.kt`)
Static `Map<String, String>` of `Exercise.slug → exerciseDbId`, hand-curated.
Exercises absent from this map are documented as intentionally unmatched.

### `ExerciseDbDetail` (new data class)
`exerciseId, name, videoUrl, imageUrl, instructions: List<String>,
targetMuscles: List<String>, secondaryMuscles: List<String>, equipments: List<String>`
— mapped from the ExerciseDB v2 JSON response shape.

### `ExerciseMediaRepository` (new)
`suspend fun get(slug: String): ExerciseDbDetail?`
- No stored API key → returns `null` immediately, no network call.
- No catalog entry for `slug` → returns `null` immediately, no network call.
- Otherwise: fetch exercise detail by id from ExerciseDB via Retrofit/OkHttp,
  through an OkHttp disk cache (~100MB) so repeat views (and typical reuse
  across the 60 workouts sharing ~47 exercises) are instant/offline after the
  first fetch.
- Network failure with no cached response → returns `null` (fallback UI),
  never crashes.
- HTTP 401/403 (invalid/revoked key) → treated the same as "no key": clears
  the stored key state so the UI prompts reconnect rather than silently
  failing forever.

### `ApiKeyManager` (new)
Wraps `androidx.security.crypto.EncryptedSharedPreferences`
(`androidx.security:security-crypto` dependency added). `get()/set()/clear()`
for the RapidAPI key. Nothing else stored here — no broader settings system.

### `ConnectMediaScreen` (new, single small screen/sheet)
Shown wherever media is needed and no valid key is stored (inline banner in the
guided session / detail sheet, or reachable via one minimal settings entry
point). One explanatory line, a "Get free API key" button that opens
`https://rapidapi.com/auth/login?referral=%2Fascendapi%2Fapi%2Fedb-with-videos-and-images-by-ascendapi%2Fpricing`
in a Custom Tab, a text field + "Save" button below it. Save triggers one
validation request (fetch a single known exercise); success stores the key and
dismisses with confirmation, failure shows an inline error and stores nothing.
User interaction required: sign up on RapidAPI (their side, ~30s, free tier),
copy the key, paste once. No further steps ever again.

### `GuidedSessionScreen` + `GuidedSessionViewModel` (new)
Layout: full-bleed looping video/image background (autoplay, muted, no
scrubber), bottom gradient scrim, overlaid section label
(`ROUND 2 · EXERCISE 3/5` / `WARM UP` / `COOL DOWN`), exercise name, reps or
seconds, muscle tag chips, a thin progress bar, prev/next controls, and a
primary "Done ✓" action.

- Sequencing: flattens the workout's sections (Warm up → Round 1..N → Cool
  down) into an ordered step list using the existing `Workout.allKeys()`.
- Resume: on entry, jumps to the first incomplete step per the existing
  `Progress` store; no separate "current guided index" is persisted — the
  screen is stateless beyond in-memory position, so process death just
  re-resumes from first-incomplete, same as the checklist already implies.
- Timed exercises: inline countdown (same timer/vibrate logic the existing
  `CountdownDialog` uses, extracted so both share it), auto-marks done and
  auto-advances a beat after it buzzes.
- Rep exercises: user taps "Done" manually to mark complete and advance.
- Prev/Next navigate without mutating progress; only "Done" writes through to
  the same `Progress` store the checklist reads — ticking in guided mode and
  ticking in the checklist are the same action on the same data.
- No media for this exercise (no catalog entry, or fetch failed/offline) →
  same slot shows a static fallback (icon + exercise name + the existing
  "Watch demo videos" / "See form images" external-search buttons) so the
  session flow never breaks.

### Entry point
A "Start Workout" button added to the existing Workout (checklist) screen
launches `GuidedSessionScreen`. The checklist screen itself is unchanged —
still the way to browse, manually tick items, and see overall progress.

### `ExerciseDetailDialog` (existing, upgraded)
Media priority order: user-dropped local asset (`assets/exercises/<slug>.*`,
existing behavior, unchanged) → curated ExerciseDB media → external search
buttons (existing, unchanged, always present as the final fallback regardless
of the above). When curated data is available, instructions and target-muscle
tags are shown below the media.

## Data flow

```
Workout screen ──[Start Workout]──▶ GuidedSessionViewModel
                                        │
                         reads Workout.allKeys() + Progress
                                        │
                         for current step ──▶ ExerciseMediaRepository.get(slug)
                                        │            │
                                        │   catalog miss / no key / offline
                                        │            │──▶ null ──▶ fallback UI
                                        │            │
                                        │   catalog hit ──▶ OkHttp (cached) ──▶ ExerciseDbDetail
                                        │
                         [Done] ──▶ Progress store (same as checklist ticks)
```

## Error handling

| Condition | Behavior |
|---|---|
| No API key stored | Media slots show connect prompt / fallback; no network calls attempted |
| Key invalid/revoked (401/403) | Treated as "no key"; prompts reconnect |
| Network error, no cache | Fallback UI (icon + external search buttons), no crash |
| Exercise has no catalog entry | Always fallback UI, by design — never a fuzzy/forced match |
| Local asset present (existing feature) | Takes priority over curated media, unchanged |

## Testing

Project currently has no test suite. Adding two focused unit tests (pure logic,
no device needed):
- `ExerciseMediaCatalog` completeness: every `Exercise.slug` produced by
  `ProgramData` is either in the catalog map or in a documented
  "intentionally unmatched" allowlist — catches silent gaps if `ProgramData`
  changes later.
- Guided-session sequencing/resume: given a `Workout` and a fake `Progress`,
  the flattened step order and "resume at first incomplete" logic are correct.

Manual/device verification (same method used earlier this session): gradle
build + `installDebug` on the physical device, launch guided session, confirm
video plays, confirm a "Done" tap is reflected back on the checklist screen,
confirm the connect-key flow and the no-match fallback UI, with screenshots
for each state. No broader instrumented/UI test framework is being introduced
— YAGNI for a personal single-user app.

## Explicitly out of scope (this spec)

- Automated RapidAPI account creation or key extraction — not feasible or
  appropriate (requires human signup/verification on their side); rejected
  during brainstorming in favor of the one-time manual paste in §
  `ConnectMediaScreen`.
- AI-generated or uploaded custom programs, and any program picker — separate
  sub-project, separate future spec.
- Rest timers/transitions between exercises beyond what `ProgramData` already
  encodes (e.g. explicit "Pause" rows) — no new rest-period concept is being
  added.
