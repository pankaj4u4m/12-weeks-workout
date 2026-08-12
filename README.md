# 12 Week Program — Android app

A personal, offline-first workout tracker. Ships with three 12-week programs
(Beginner / Intermediate / Advanced, Full Body, home equipment) and syncs
more from a public GitHub program library — pick one from a filterable
picker, it's pinned to Home, and every exercise's real demo video/image comes
from ExerciseDB.

## Web app

TwelveWeek also runs in the browser (Kotlin/Wasm, Compose Multiplatform) at
**https://pankaj4u4m.github.io/12-weeks-workout/** — same programs, same
progress tracking, same guided sessions, no install required.

### Running it without the browser chrome

The web app isn't in an app store, but you can still make it look and feel
like a standalone app instead of a browser tab:

**Add to Home Screen (recommended).** Chrome on Android: tap ⋮ (top right) →
"Install app" or "Add to Home screen". Safari on iPhone: tap Share → "Add to
Home Screen". Opening the icon this creates runs TwelveWeek full-screen, with
no address bar or browser frame — the web app ships a `manifest.json` so this
works as a real installable PWA.

**Or: force any site into a borderless app (Hermit).** If your browser
doesn't offer an "Install app" option, a Lite Apps browser like
[Hermit](https://play.google.com/store/apps/details?id=com.craxiom.hermit)
can force it:
1. Install Hermit (Lite Apps Browser) from the Google Play Store.
2. Open Hermit, paste the web app's address above.
3. Tap **Create Lite App**.
4. Grant permission to place the icon on your home screen.

Opening the icon Hermit creates runs the site as a completely standalone
app — no URL bar, top header, or browser frame.

The web app itself shows this same tip once, right after you pick a plan for
the first time.

## Build it

1. Install [Android Studio](https://developer.android.com/studio) (any recent version).
2. **File → Open** → pick this folder. Let Gradle sync (first run downloads dependencies).
3. Plug in your phone with USB debugging on, or start an emulator.
4. Press **Run** (green ▶).

That's it — no signing key, no Play Store, no accounts.

If Android Studio offers to upgrade AGP or Gradle, accept it; nothing here depends
on the exact versions. The Gradle wrapper JAR isn't included (binaries don't
travel well in a zip) — Android Studio regenerates it on first sync, or run
`gradle wrapper` once if you have Gradle installed.

**Requirements:** Android 8.0 (API 26) or newer. JDK 17 ships with Android Studio.

## What it does

- **First run** → Connect exercise videos (skippable) → pick a program from
  the library (skippable, defaults to "12 Week Full Body") → Home. Returning
  users skip straight to Home.
- **Program picker** (⇄ icon, top bar) → browse/filter by level, focus area,
  and equipment; switch programs any time. Your progress is tracked per
  program, so switching never overwrites another program's ticks.
- **Week list** → overall % done, plus per-week progress.
- **Week screen** → the 5 workouts, each with a round count and progress.
- **Workout screen** → Warm up, each Round, Cool Down. Tap a row to tick it off.
  Ticks persist across restarts and are included in Android auto-backup.
- **Start Workout** (▶ icon, top bar) → full-screen guided mode, one exercise at
  a time. Timed exercises count down and auto-advance; rep exercises need a tap
  on **Done**. Ticking here is the same tick as the checklist — they always agree.
- **Timer** (⏱ icon) on every timed hold — 45s Low Plank, 60s Wall Sit, etc.
  Pause, resume, restart. Buzzes when it's done.
- **Demo lookup** (▶ icon on a row) opens a sheet with the exercise's target,
  video/instructions if available, plus buttons for demo videos and form images.
- **Reset** buttons: per-workout (top bar) or the whole program (home screen).

## The program library

Programs are no longer hardcoded — they're JSON, bundled in the app for
instant offline use and refreshed from the public
[`pankaj4u4m/12-weeks-workout`](https://github.com/pankaj4u4m/12-weeks-workout)
repo on every launch (plain HTTPS, no account/token needed since the repo is
public). Offline or a dead network just means the last-known copy keeps being
used — never a blocked screen. See that repo's README for the JSON schema if
you want to author your own program and add it to `index.json`.

## Exercise videos

Most exercises across the three bundled programs (1,709/2,405 rows in the
Intermediate program; similar coverage in Beginner/Advanced — mostly the
calisthenics/plyo movements; static warm-up/cool-down stretches are the main
gap since ExerciseDB doesn't have all of them) have a real demo video or image
pulled from
[ExerciseDB](https://rapidapi.com/ascendapi/api/edb-with-videos-and-images-by-ascendapi),
played **natively in the app** (via ExoPlayer/Coil — not a link that opens your
browser) in both **Start Workout** mode and the per-exercise ▶ detail sheet.
An exercise with no curated match always falls back to the search buttons
below, by design — never a guessed/wrong video. Every `exerciseId` is baked
into the program's JSON at authoring time (hand-verified against a live
ExerciseDB response), not looked up by a runtime fuzzy search.

**Turning it on** (one-time, ~30 seconds, free):

1. In the app, tap **Start Workout** on any workout, or open an exercise's ▶
   detail sheet — you'll see a **"Connect exercise videos"** prompt.
2. Tap it → **Get free API key** opens RapidAPI's signup/subscribe page in your
   browser. Sign up (free tier), subscribe to the plan, then copy your
   `X-RapidAPI-Key` from your RapidAPI dashboard.
3. Back in the app, paste the key into the field and tap **Save**. The app does
   one test call to confirm it works, then stores it (encrypted, on-device only
   — never leaves your phone, never sent anywhere but ExerciseDB's API).

That's it — every curated exercise now plays for real, from then on. Videos/
images are cached to disk after first view, so repeat views (and workouts that
reuse the same movement) are instant and mostly work offline afterward.

**Free tier note:** ExerciseDB's free tier watermarks its videos/images (a
small logo overlay). Paid tiers remove it — not required, purely cosmetic.

**Want your own offline pictures instead (or on top)?** Drop image files into:

```
app/src/main/assets/exercises/<slug>.jpg
```

The slug is the exercise name lowercased with non-alphanumerics turned into
hyphens. Examples:

| Exercise | Filename |
|---|---|
| Jumping Jacks | `jumping-jacks.jpg` |
| High Plank Knee-to-Elbow | `high-plank-knee-to-elbow.jpg` |
| 4-Count Burpees | `4-count-burpees.jpg` |
| Side Plank Oblique Crunch L | `side-plank-oblique-crunch-l.jpg` |

`.jpg`, `.jpeg`, `.png` and `.webp` all work. A local file always wins over the
curated ExerciseDB media if both exist. Rebuild after adding files.

### Adding more curated matches

`exerciseId` lives inline on each exercise in its program's JSON file, hand-
curated (ExerciseDB's search is unreliable for multi-word phrases, so matches
were picked by eyeballing/verifying candidate lists, not automated). To add
more: find the right `exerciseId` against a live ExerciseDB response, edit the
exercise's entry in the relevant `programs/<id>.json`, push it to the GitHub
repo. Leave exercises with no good match as `exerciseId: null` — the fallback
UI is intentional, not a placeholder.

## Importing your own program

Drop a `programs/<id>.json` file (schema in the
[program library repo's README](https://github.com/pankaj4u4m/12-weeks-workout))
into that repo and add it to `index.json` — it shows up in the in-app picker
on the next sync, no rebuild needed. There's still no in-app "upload a
spreadsheet and auto-generate JSON" flow — that's a deliberate scope cut, a
bigger separate feature.

## Notes on the built-in "12 Week Full Body" program's transcription

Two things in the original sheet were cleaned up when it became `program-1.json`:

- Week 11, Workout 4 lists `605 Wall Sit`. Read as `60s Wall Sit`.
- Typos `Butterly Stretch` and `Wall Pectorcal Stretch` corrected.

Week 6 Workout 2 labels its columns "Round 1, 2, 3, 5" — treated as four
consecutive rounds.

## Credits

- Exercise videos/images/instructions: [ExerciseDB](https://ascendapi.com) (your own API key, see above).
- Display font: [Oswald](https://github.com/googlefonts/OswaldFont) by Vernon
  Adams et al., SIL Open Font License 1.1 (`app/src/main/assets/licenses/oswald-OFL.txt`).

## Not medical advice

This is a tracker for a program you already have, not a training or health
recommendation. Warm up properly, stop if something hurts, and talk to a doctor
or a qualified coach before starting a new program — especially with the volume
in the later weeks.
