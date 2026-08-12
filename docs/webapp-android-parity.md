# Web app vs Android app — feature parity review

Date: 2026-08-13. Compares the live web app (`app/src/commonMain/kotlin/com/personal/twelveweek/web/WebApp.kt`,
live at https://pankaj4u4m.github.io/12-weeks-workout/) against the Android app
(`MainActivity.kt` + `ui/*.kt`). Web reuses the fully-shared data layer
(`ProgramLibrary`, `ProgressStore`, `SelectedProgramStore`) — every gap below
is a **UI/platform-bridge** gap, not a data gap: progress, program content,
and week/workout structure are byte-identical between the two.

Legend: ✅ full parity · 🟡 partial · ❌ missing

| Area | Android | Web | Status | Notes |
|---|---|---|---|---|
| Bottom nav (Today/Plan/Programs) | Material `NavigationBar` | Ported 1:1 | ✅ | |
| Adaptive nav rail ≥720dp | `NavigationRail` + 2-pane layout | Always bottom bar | ❌ | `BoxWithConstraints` expanded-width branch not ported |
| Today screen | Next-workout card, 12-week route band, this-week list | Ported 1:1 | ✅ | |
| Today → Settings gear icon | Opens Settings | Not present | ❌ | No Settings screen to open (see below) |
| Plan screen | Grid of week cards, overall progress, reset-all menu | Ported 1:1 | ✅ | |
| Programs picker | Level/Focus/Space/**Time-per-day** filters, program cards | Level/Focus/Space filters, program cards | 🟡 | **Time-per-day filter dropped** (time pressure, not a data gap — `sessionMinutes` is on every `ProgramMeta`) |
| Programs picker → Import `.json` | File picker → `ProgramLibrary.importProgram` | Not wired | ❌ | Shared `importProgram()` already exists; web just has no file-input UI for it |
| First-run onboarding | `WelcomeScreen` → plan picker, one-time | Skipped — opens straight to Today with default program | ❌ | Web has no "first launch" concept yet |
| Week detail | Workout rows, "Start Day N" CTA | Ported 1:1 | ✅ | |
| Workout detail — checklist | Sections, checkbox rows, mark-all/reset menu, metrics | Ported 1:1 | ✅ | |
| Workout detail — exercise tap-through | `ExerciseDetailDialog` (instructions + media) | Row only toggles the checkbox | ❌ | No detail/media view on web |
| Workout detail — per-exercise timer | `CountdownDialog` (start/pause countdown + vibration) | Not present | ❌ | |
| **Guided session runner** | Full-screen one-exercise-at-a-time flow: circular timer, voice announcements ("5 sec remaining", "halfway there"), rep-prep countdown, 5s auto-advance, completion sound/vibration | **Does not exist on web** | ❌ | Biggest single gap. "Start workout" on web just opens the same tap-to-check Workout Detail screen instead |
| **Settings screen** | Full page: voice mute, transition-seconds, rep-prep-seconds, API key connect/disconnect | **Does not exist on web** | ❌ | `AppSettings` is plain Android `SharedPreferences`, not yet on `RawPreferenceStore` |
| Exercise media (wger/ExerciseDB/free-exercise-db images+video) | `ExerciseMediaCarousel`, `ExerciseMediaRepository` | Not present | ❌ | No image/video bridge on wasmJs |
| Encrypted API key storage | `ApiKeyManager` (Android Keystore-backed) | Not present | ❌ | Design called for Web Crypto-backed storage on wasmJs; not started |
| Voice / TTS (`VoiceCoach`) | Android `TextToSpeech` | Not present | ❌ | No Web Speech API bridge |
| Vibration on completion | Android `Vibrator` | Not present | ❌ | No timers exist on web yet to vibrate for; also a known weaker/inconsistent API on iOS Safari |
| GitHub-synced program updates | `ProgramSyncRepository` fetches `raw.githubusercontent.com/.../main` on every launch | Not present | ❌ | Web always reads the bundled/cached copy shipped in the wasmJs dist; no background sync |
| Theming — colors/shapes | "Resistance Band Flow" tokens (`Theme.kt`) | Same tokens, duplicated into `web/WebTheme.kt` | ✅ | Deliberate duplicate, not a shared import — see file comment for why |
| Theming — typography | Barlow Semi Condensed (custom font) on headlines/titles | System default font at the same weight/size/tracking | 🟡 | `lc-debt` noted in `WebTheme.kt`: needs a commonMain `Font()` byte-array loader to share the real face |
| Reset/confirm dialogs | Material `AlertDialog` | Ported 1:1 | ✅ | |
| Accessibility semantics (progress bars, week band) | `semantics { progressBarRangeInfo / contentDescription }` | Ported 1:1 in code | 🟡 | Code carries the same semantics, but Compose Multiplatform's wasmJs canvas renderer doesn't expose a real DOM/ARIA accessibility tree yet (verified live: browser a11y tools see one opaque canvas, no nav landmarks) — a platform limitation, not something fixable in this app's code today |
| PWA installability | N/A (native app) | No `manifest.json` / service worker yet | ❌ | Still on the original "do all these" backlog |
| Offline behavior | Full offline (bundled assets + on-device storage) | Fully offline already (bundled programs + localStorage) for everything it *does* implement | ✅ | |

## Bottom line

Everything **read-and-check** (browse programs, drill into a week/workout,
tick exercises, track progress, switch programs, filter by level/focus/space)
now has real Android-app-looking parity: same cards, same colors, same
navigation shape, same progress bands/route visualization.

Everything that's missing clusters into two buckets that both need a new
platform bridge, not just more UI code:
1. **Guided session + Settings + voice + media + API keys** — all gated on
   wasmJs equivalents of Android-only APIs (TextToSpeech, Vibrator,
   EncryptedSharedPreferences, image/video loading). None of these have any
   wasmJs actual yet.
2. **Time filter, import flow, onboarding, sync, nav rail** — smaller, pure-UI
   gaps with no platform blocker; each is a focused follow-up task against
   already-shared data/logic.
