# TwelveWeek — Play Store Launch Status

_2026-08-12_

## Blockers only you can clear

1. **No Play Console developer account exists yet** for pankaj4u4m@gmail.com — navigating to Play Console redirected straight to the account-creation signup flow. Creating one needs your Google identity, a $25 one-time fee, and accepting the Developer Distribution Agreement personally — I can't do any of that for you (payment + identity verification are off-limits for me). Once you've created it, tell me and I'll pick the listing back up.
2. **This local repo's git history is disconnected from `origin/main`** (`git merge-base` returns nothing — "unrelated histories"). Local has a single squashed "First commit"; origin/main has real prior history (media-provider work, program library commits, etc.) that isn't in your local log, even though the working-tree files look equivalent. I did **not** touch `main` — merging or force-pushing here risks destroying real commits on the public repo. Left as-is; worth sorting out (probably: re-clone from origin and replay your uncommitted local changes on top) before you push anything else to `main`.

## Done

- **Release keystore generated**: `release-keystore.jks` + `keystore.properties` at repo root (both gitignored, `chmod 600`). **Back these up somewhere safe right now** (password manager, encrypted cloud folder) — if this file is lost, you can never publish an update to this app listing again, ever. SHA256 cert fingerprint: `C3:48:45:4B:2F:86:AB:1E:F9:00:CF:0E:1A:4C:C8:00:FD:A1:3E:0A:FE:54:AE:68:C9:29:FD:09:CC:1F:98:85`
- **4 real device screenshots** captured from your connected SM-S928B, saved to `store-assets/screenshots/` (Today/home, program picker, 12-week plan grid, guided session with real exercise photo).
- **Privacy policy** drafted and published: https://pankaj4u4m.github.io/12-weeks-exercise-app/ (may take a few minutes to finish propagating). Published via a clean new `gh-pages` branch, not `main`, to avoid the history problem above. Source at `docs/privacy.md`.
- Store listing copy (title/short/full description/ASO keywords) — from earlier in this conversation, still valid.

## Done (cont.)

- **Toolchain bumped**: compileSdk/targetSdk 34→36, AGP 8.13.2, Gradle 8.14.5, Kotlin 2.4.10 (+ `org.jetbrains.kotlin.plugin.compose`), compose-bom 2026.06.01. Meets Google Play's Aug 31 2026 targetSdk-36 requirement.
- **Release signing wired**: `app/build.gradle.kts` reads `keystore.properties`, falls back to unsigned if absent.
- **Unit tests**: 17/17 pass.
- **Signed release build produced and signature-verified**:
  - `app/build/outputs/bundle/release/app-release.aab` (16M) — upload this to Play Console
  - `app/build/outputs/apk/release/app-release.apk` (17M) — sideload testing

## Not started

- **App icon (512×512) + feature graphic (1024×500)**: only a vector adaptive icon exists in-app, no exported raster art. Needs an actual design pass, not something I can competently generate blind — flag when ready to tackle it.
- **Data Safety form** answers (Play Console questionnaire) — straightforward given the privacy policy above (no PII collected, on-device-only API key, direct-to-third-party network calls), but only fillable once the account/app entry exists.
- **Content rating questionnaire**, **final Play Console listing entry** — blocked on account creation.
