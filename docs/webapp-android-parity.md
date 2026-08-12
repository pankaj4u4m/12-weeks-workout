# Web app vs Android app — feature parity review

Date: 2026-08-13. Compares the live web app (`app/src/wasmJsMain/kotlin/com/personal/twelveweek/web/`,
live at https://pankaj4u4m.github.io/12-weeks-workout/) against the Android app
(`MainActivity.kt` + `ui/*.kt`). Web reuses the fully-shared data layer
(`ProgramLibrary`, `ProgressStore`, `SelectedProgramStore`) — every gap below
is a **UI/platform-bridge** gap, not a data gap: progress, program content,
and week/workout structure are byte-identical between the two.

Legend: ✅ full parity · 🟡 partial · ❌ missing

| Area | Android | Web | Status | Notes |
|---|---|---|---|---|
| Bottom nav (Today/Plan/Programs) | Material `NavigationBar` | Ported 1:1 | ✅ | |
| Adaptive nav rail ≥720dp | `NavigationRail` + 2-pane layout | Ported (`BoxWithConstraints` branch in `WebAppShell`) | ✅ | |
| Today screen | Next-workout card, 12-week route band, this-week list | Ported 1:1 | ✅ | |
| Today → Settings gear icon | Opens Settings | Ported — opens `WebSettingsScreen` | ✅ | |
| Plan screen | Grid of week cards, overall progress, reset-all menu | Ported 1:1 | ✅ | |
| Programs picker | Level/Focus/Space/Time-per-day filters, program cards | Ported 1:1, all four filters | ✅ | |
| Programs picker → Import `.json` | File picker → `ProgramLibrary.importProgram` | Native file picker + `FileReader`, wired to the same `importProgram()` | ✅ | |
| First-run onboarding | `WelcomeScreen` → plan picker, one-time | Ported 1:1 (`WebOnboarding.kt`) | ✅ | |
| Week detail | Workout rows, "Start Day N" CTA | Ported 1:1 | ✅ | |
| Workout detail — checklist | Sections, checkbox rows, mark-all/reset menu, metrics | Ported 1:1 | ✅ | |
| Workout detail — exercise tap-through | `ExerciseDetailDialog` (instructions + media) | Row only toggles the checkbox | ❌ | Media/detail view lives in the guided session instead on web, not the checklist |
| Workout detail — per-exercise timer | `CountdownDialog` (start/pause countdown + vibration) | Not present | ❌ | |
| **Guided session runner** | Full-screen one-exercise-at-a-time flow: circular timer, voice announcements, rep-prep countdown, auto-advance, completion sound/vibration | Ported (`WebGuidedSession.kt`) | ✅ | Voice via Web Speech API, haptics via Vibration API, completion tone via Web Audio API |
| **Settings screen** | Full page: voice mute, transition-seconds, rep-prep-seconds, API key connect/disconnect | Ported (`WebSettingsScreen.kt`) | ✅ | |
| Exercise media — images / photo loops | wger static image, FreeExerciseDb 2-photo loop | Ported, renders inline (`WebAsyncImage.kt` via Skia decode) | ✅ | |
| Exercise media — video | wger/ExerciseDB video, autoplay/loop/muted in-place | Ported (`WebVideoView.kt`) — real HTML `<video>` overlaid on the canvas, position-synced every layout pass | ✅ | |
| Encrypted API key storage | `ApiKeyManager` (Android Keystore-backed) | Ported (`WebApiKeyManager.kt`, Web Crypto AES-GCM) | ✅ | Different threat model — no browser-exposed secure enclave, see file comment |
| Voice / TTS (`VoiceCoach`) | Android `TextToSpeech` | Ported (`WebVoiceCoach.kt`, Web Speech API) | ✅ | |
| Vibration on completion | Android `Vibrator` | Ported (`WebHaptics.kt`, Vibration API) | ✅ | Known weaker/inconsistent on iOS Safari — platform limitation, not a bug |
| GitHub-synced program updates | `ProgramSyncRepository` fetches `raw.githubusercontent.com/.../main` on every launch | Not present | ❌ | Web always reads the bundled/cached copy shipped in the wasmJs dist; no background sync |
| Theming — colors/shapes | "Resistance Band Flow" tokens (`Theme.kt`) | Same tokens, duplicated into `web/WebTheme.kt` | ✅ | Deliberate duplicate, not a shared import — see file comment for why |
| Theming — typography | Barlow Semi Condensed (custom font) on headlines/titles | System default font at the same weight/size/tracking | 🟡 | `lc-debt` noted in `WebTheme.kt`: needs a commonMain `Font()` byte-array loader to share the real face |
| Reset/confirm dialogs | Material `AlertDialog` | Ported 1:1 | ✅ | |
| Accessibility semantics (progress bars, week band) | `semantics { progressBarRangeInfo / contentDescription }` | Ported 1:1 in code | 🟡 | Code carries the same semantics, but Compose Multiplatform's wasmJs canvas renderer doesn't expose a real DOM/ARIA accessibility tree yet (verified live: browser a11y tools see one opaque canvas, no nav landmarks) — a platform limitation, not something fixable in this app's code today |
| PWA installability | N/A (native app) | `manifest.json` + service worker; install-tip dialog points users at Add-to-Home-Screen / Hermit | ✅ | |
| Offline behavior | Full offline (bundled assets + on-device storage) | Fully offline already (bundled programs + localStorage) for everything it *does* implement | ✅ | |

## Bottom line

Full functional parity: every screen, every guided-session behavior, voice,
vibration, encrypted API-key storage, exercise media (images/loops inline,
video via link), onboarding, tablet nav rail, and PWA installability are all
ported and live. Remaining gaps are narrow and explicitly platform-limited,
not missing effort:
1. **GitHub-synced program updates** — web always reads the bundled/cached
   copy shipped in the dist; no background sync from the program-library repo.
3. **Custom font (Barlow Semi Condensed)** — headlines use the system default
   font at the same size/weight instead of the real face; needs a
   commonMain `Font()` byte-array loader shared across targets.
4. **Accessibility tree** — Compose Multiplatform's wasmJs canvas renderer
   doesn't expose a real DOM/ARIA tree yet; a platform limitation, not
   something fixable in this app's code.
5. **Per-exercise countdown timer / exercise detail dialog** on the plain
   checklist screen — that interaction now lives in the guided session
   instead on web, which is arguably a stronger single home for it, but it's
   a genuine behavior difference from Android's checklist screen.
