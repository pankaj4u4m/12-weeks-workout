# TwelveWeek Web App (Compose Multiplatform / Wasm) — Design

_2026-08-12_

## Goals

- Ship a browser-usable version of TwelveWeek (installable as a PWA, works on iOS via "Add to Home Screen") with full feature parity with the Android app: program picker, 12-week plan grid, guided session runner with timers, exercise media, optional ExerciseDB API key connect, program import, offline-first operation.
- Reuse real Kotlin/Compose source between platforms rather than maintaining two separate UI implementations.
- Do not regress the existing signed Android release pipeline (compileSdk/targetSdk 36, keystore-based signing, `bundleRelease`) while doing this.

## Non-goals (v1)

- Push notifications.
- Wrapping the web app for app-store distribution (TWA/Capacitor) — a possible future step, not part of this spec.
- Automated browser test coverage for the wasmJs target — v1 relies on manual smoke testing there; shared logic keeps automated coverage via `commonTest`.

## Why Compose Multiplatform over a plain-JS PWA rewrite

A plain JS/TS rewrite (React or vanilla + Vite) was the lower-risk default recommendation, but it means two independently maintained UI implementations of the same app. Compose Multiplatform lets the existing Compose UI, program models, and session logic compile to both Android and `wasmJs` from one `commonMain` source set. Trade-off accepted: the web Compose ecosystem is less mature than Android's, so media playback and some platform APIs need per-platform `expect/actual` implementations (see below) rather than existing off-the-shelf.

## Architecture

`app/` becomes a Kotlin Multiplatform module (migrated in place, not a new project):

```
app/
  src/
    commonMain/    Compose UI (all screens), program models, session/guided-
                    workout logic, repository layer, kotlinx.serialization
                    JSON parsing, Ktor-based network calls (wger, ExerciseDB,
                    GitHub program sync)
    commonTest/     shared unit tests (program parsing, session logic) —
                    ported from the existing JVM test suite, target-agnostic
    androidMain/    Android entry point (MainActivity), ExoPlayer (media3)
                    video playback, Android Keystore-backed API key storage,
                    DataStore/SharedPrefs-backed progress storage. Existing
                    signing/release config in app/build.gradle.kts is
                    unaffected by the migration itself.
    wasmJsMain/     Browser entry point, <video>/<img> media via expect/
                    actual (Coil3 for images), Web Crypto (SubtleCrypto)
                    API key storage, IndexedDB progress storage, PWA glue
```

**Library swaps required for shared `commonMain` code** (Android/JVM-only libraries have no `wasmJs` target):

| Today (Android-only) | Becomes (commonMain) |
|---|---|
| OkHttp | Ktor client (`wasmJs` engine on web, Android engine on Android) |
| org.json | kotlinx.serialization |

Everything else currently in the app (program picker, plan grid, guided session UI, timer logic) is not platform-specific today and moves into `commonMain` as-is, recompiled for both targets.

## Platform-specific implementations (`expect`/`actual`)

| Concern | `androidMain` (unchanged behavior) | `wasmJsMain` (new) |
|---|---|---|
| Video/image media | ExoPlayer (media3) + Coil | Coil3 (multiplatform, has a `wasmJs` target) for images; video via an `expect/actual` wrapper around a plain HTML `<video>` element — no multiplatform ExoPlayer equivalent exists |
| Progress/program storage | DataStore/SharedPrefs | IndexedDB, behind a shared `expect/actual` storage interface that `commonMain` code calls |
| Optional ExerciseDB API key storage | Android Keystore (hardware-backed encryption) | Web Crypto `SubtleCrypto`, non-extractable key — encrypted at rest, not hardware-backed |
| Timer-complete alert | `Vibrator` API | Feature-detected `navigator.vibrate()` (present on Android Chrome, absent on iOS Safari) **plus** a short audio chime and a visual pulse on the timer card on every platform, so iOS users always get a clear signal even without vibration |
| Program JSON + GitHub sync | Bundled assets + Ktor fetch | Same JSON files bundled as static web assets + Ktor fetch, cached for offline-first parity |

## Offline / PWA

- `manifest.json`: name, icons (512×512 icon — shared asset, still owed for the Play listing too), `start_url: "/"`, `display: "standalone"`.
- Service worker: cache-first for the app shell (wasmJs bundle + bundled program JSON), network-first-falling-back-to-cache for the GitHub program sync — matches the Android app's "stale copy beats a blocked screen" behavior.
- IndexedDB (via the storage interface above) holds progress + selected program, making the app usable offline after first load, same guarantee as the Android app.

## Hosting

- `gh-pages` branch, served via GitHub Pages (already enabled on `pankaj4u4m/12-weeks-exercise-app`).
- Site root (`https://pankaj4u4m.github.io/12-weeks-exercise-app/`) becomes the app itself (`wasmJsBrowserDistribution` output).
- Privacy policy moves from root to `/privacy/` — one link/reference update, nothing else currently points at the old root URL.

## Deployment

- New workflow `.github/workflows/deploy-web.yml`, separate from the existing `release.yml` (Android signing). Triggers on push to `main`. Steps: checkout → JDK 17 → Gradle → `./gradlew wasmJsBrowserDistribution` → publish output to `gh-pages` (root, alongside `/privacy/`, without clobbering it). No secrets required (no signing for a web build).
- Held back from wiring into `main` until the existing local/origin git-history divergence (see prior conversation — `git merge-base` returns none between local `main` and `origin/main`) is resolved; dry-run against a side branch first.

## Migration sequence

Each step is a checkpoint; Android is re-verified before moving to the next step. Step 5 (moving UI into `commonMain`) is the highest-risk step since it's the first one that changes Android's compiled source — done one screen at a time rather than in one pass.

1. Add the Kotlin Multiplatform plugin + `wasmJs` target alongside the existing `android` target; empty `commonMain`/`androidMain`/`wasmJsMain` skeleton.
   **Checkpoint:** Android still builds and signs unchanged (`./gradlew testDebugUnitTest bundleRelease`, `apksigner verify`).
2. Extract program models + JSON parsing into `commonMain` (org.json → kotlinx.serialization).
   **Checkpoint:** Android unit tests pass.
3. Extract the network layer (OkHttp → Ktor) for wger/ExerciseDB/GitHub sync.
   **Checkpoint:** Android tests + device smoke test.
4. Add the storage `expect`/`actual` interface; Android side wired to the existing DataStore/SharedPrefs implementation (no behavior change); web side stubbed.
5. Move Compose screens into `commonMain` one at a time: Today → Programs picker → Plan grid → Guided session. Re-verify on the physical Android device after each screen.
6. Fill in `wasmJs` actuals: IndexedDB storage, Coil3 + `<video>` media wrapper, Web Crypto key storage, vibrate+chime+pulse timer signal.
7. Manual browser smoke test (local dev server) before touching PWA/CI.
8. PWA shell: manifest + service worker + offline-reload test.
9. Add `deploy-web.yml`; dry-run against a side branch (not `main`, per the hosting note above).
10. Final regression: Android `testDebugUnitTest` + `bundleRelease` + `apksigner verify` must still pass exactly as today; manual iOS Safari pass (add-to-home-screen, offline reload, chime fallback, key storage).

## Testing strategy

- Shared program/session-logic tests move to `commonTest`, ported from the existing JVM test suite, now target-agnostic.
- Android-only code (Keystore wrapper) keeps its existing tests.
- Web (`wasmJsMain`) gets manual smoke coverage for v1 — no automated browser test runner yet. This is a deliberate v1 scope cut, not an oversight; revisit if the web app grows past what manual smoke testing can reasonably cover.

## Risk summary

- Steps 1–4 are additive to Android (new source sets, swapped libraries behind the same interfaces) — low risk to the existing release pipeline.
- Step 5 is the real risk point: moving compiled UI source into `commonMain`. Mitigated by one-screen-at-a-time migration with an Android checkpoint after each.
- Web-side platform gaps (video playback maturity, Web Crypto edge cases, iOS Safari storage eviction) are new-territory risk with no direct Android precedent — addressed via the `expect/actual` boundaries above so a gap on one platform doesn't block the other.
