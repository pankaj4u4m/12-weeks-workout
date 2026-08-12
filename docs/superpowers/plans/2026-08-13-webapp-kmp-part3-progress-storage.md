# TwelveWeek Web App — Part 3: Progress Storage (expect/actual) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split `ProgressStore` (currently Android SharedPreferences-only) into a pure-Kotlin, fully-shared `commonMain` reactive layer (the exact `isDone`/`setDone`/`toggle`/`countDone`/`setAll`/`clearEverything` API + legacy-key migration logic, unchanged) sitting on top of a tiny `expect`/`actual` raw persistence boundary, so progress tracking works identically on Android (SharedPreferences, unchanged on-disk format) and wasmJs (browser `localStorage`).

**Architecture:** Raw I/O only crosses the `expect`/`actual` boundary (`RawKeyFlagStore`: load all present keys, set a key present, remove a key, clear all). All business logic — reactive Compose state, the legacy `program-1:` prefix migration, the public `ProgressStore` API — moves to `commonMain` untouched in behavior, calling the injected raw store instead of `SharedPreferences` directly.

**Tech Stack:** `androidx.compose.runtime`'s `mutableStateMapOf` (already a commonMain dependency since Part 1 — Compose's snapshot-state system is multiplatform, not Android-only). Android's actual wraps the existing `SharedPreferences` mechanism (same file name, same on-disk format — nobody's saved progress is lost or migrated). wasmJs's actual uses browser `localStorage` (exact API surface confirmed in Task 1, same discipline as every prior part's version/API lookups).

## Global Constraints

- Same toolchain as Parts 1-2: AGP 8.13.2, Kotlin 2.4.10, Gradle wrapper 8.14.5, compose-bom 2026.06.01, compileSdk/targetSdk 36, minSdk 26, Ktor 3.5.2.
- **On-disk compatibility is non-negotiable**: Android's actual must read/write the exact same SharedPreferences file (`"twelve_week_progress"`) and key format as today. Nobody's existing workout progress may be lost or altered by this migration. Every existing behavior in `Progress.kt` — including the `program-1:` legacy-prefix fallback/cleanup — must be preserved exactly, just relocated.
- Confirm current baseline (`./gradlew :app:allTests testDebugUnitTest`) before starting and after every task — note the exact pass count, don't assume a stale number.
- Commit only the files each task's Files section lists — explicit `git add <path>...`, never `git add -A`. `ExerciseMediaRepository.kt`, `ConnectMediaScreen.kt`, `ExerciseMediaRepositoryTest.kt`, and any other file with pre-existing unrelated uncommitted content stay untouched unless a task's brief explicitly says otherwise — if a dependency-signature change forces a mechanical, uncommitted, out-of-scope fix to keep the ambient build green, apply it directly rather than committing it (same pattern already established in Part 2), and say so in the report.
- `keystore.properties`/`*.jks` stay gitignored — never staged.
- `bundleRelease`/`assembleRelease`/`apksigner verify` (`CN=TwelveWeek`) must keep passing after every task.

---

### Task 1: `RawKeyFlagStore` expect/actual — raw persistence boundary

**Files:**
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.kt` (the `expect` declaration)
- Create: `app/src/androidMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.android.kt` (Android `actual`)
- Create: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.wasmJs.kt` (wasmJs `actual`)

**Interfaces:**
- Consumes: nothing (foundational task).
- Produces: `expect class RawKeyFlagStore(namespace: String)` with `fun allKeys(): Set<String>`, `fun setPresent(key: String)`, `fun remove(key: String)`, `fun clear()` — a flag store where a key's mere presence means "true"/"done"; there is no stored value, only presence/absence. Task 2 depends on this exact API.

- [ ] **Step 1: Look up the current Kotlin/Wasm browser-storage API**

  Kotlin/Wasm accesses browser APIs (like `localStorage`) either via JetBrains' multiplatform `kotlinx-browser` bindings (artifact `org.jetbrains.kotlinx:kotlinx-browser`) or via `external`/`@JsFun` interop declared directly in `wasmJsMain`. Check current guidance at https://kotlinlang.org/docs/wasm-js-interop.html and https://github.com/Kotlin/kotlinx-browser for the current-recommended approach and, if using `kotlinx-browser`, its current version compatible with Kotlin 2.4.10. Write down which approach and (if applicable) version you're using — it replaces the placeholder library call in Step 4 below.

- [ ] **Step 2: Confirm current baseline**

  Run: `./gradlew :app:allTests testDebugUnitTest --console=plain`
  Record the exact pass count.

- [ ] **Step 3: Declare the `expect` class**

  `app/src/commonMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.kt`:

  ```kotlin
  package com.personal.twelveweek.storage

  /**
   * Raw, platform-specific "is this key present" flag storage — the only
   * thing that crosses the expect/actual boundary for progress tracking.
   * [namespace] scopes the storage (Android: SharedPreferences file name;
   * wasmJs: a localStorage key prefix) so this type can be reused for
   * anything else that needs the same shape later without collisions.
   *
   * A key's presence means "true"; there is no other value — this matches
   * exactly how [com.personal.twelveweek.Program]'s progress keys are used
   * today (SharedPreferences.putBoolean(key, true) / .remove(key), never
   * putBoolean(key, false)).
   */
  expect class RawKeyFlagStore(namespace: String) {
      fun allKeys(): Set<String>
      fun setPresent(key: String)
      fun remove(key: String)
      fun clear()
  }
  ```

- [ ] **Step 4: Android `actual` — wraps the existing SharedPreferences mechanism exactly**

  `app/src/androidMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.android.kt`:

  ```kotlin
  package com.personal.twelveweek.storage

  import android.content.Context
  import android.content.SharedPreferences

  /** [namespace] is the SharedPreferences file name, matching today's
   *  hardcoded `"twelve_week_progress"` exactly — same on-disk file,
   *  same format, nobody's saved progress moves or changes shape. */
  actual class RawKeyFlagStore actual constructor(private val namespace: String) {

      // Set by AndroidPlatformContext.install() before any RawKeyFlagStore is
      // constructed — see Task 2's AndroidPlatformContext for the composition-
      // root wiring. Kotlin Multiplatform's `expect class` constructors can't
      // take an Android Context parameter directly (the signature must match
      // every actual, and wasmJs has no Context), so Context is threaded in
      // via this small platform-only side channel instead.
      private val prefs: SharedPreferences
          get() = requireNotNull(AndroidPlatformContext.appContext) {
              "AndroidPlatformContext.install(context) must run before any RawKeyFlagStore is used — call it from Application.onCreate() or MainActivity.onCreate()."
          }.getSharedPreferences(namespace, Context.MODE_PRIVATE)

      actual fun allKeys(): Set<String> = prefs.all.keys.toSet()

      actual fun setPresent(key: String) {
          prefs.edit().putBoolean(key, true).apply()
      }

      actual fun remove(key: String) {
          prefs.edit().remove(key).apply()
      }

      actual fun clear() {
          prefs.edit().clear().apply()
      }
  }

  /** Tiny composition-root side channel so androidMain code (which has a
   *  real [Context]) can hand it to [RawKeyFlagStore] without threading a
   *  Context parameter through the shared `expect` constructor, which must
   *  have an identical signature on every platform including wasmJs (which
   *  has no Context at all). */
  object AndroidPlatformContext {
      internal var appContext: Context? = null
          private set

      fun install(context: Context) {
          appContext = context.applicationContext
      }
  }
  ```

- [ ] **Step 5: wasmJs `actual` — browser `localStorage`**

  `app/src/wasmJsMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.wasmJs.kt` — write this using whatever API Step 1 confirmed (`kotlinx-browser`'s `kotlinx.browser.localStorage` object, or direct `external`/`@JsFun` interop). Sketch using the `kotlinx-browser` approach (adjust import/API calls if Step 1 found a different current-recommended path):

  ```kotlin
  package com.personal.twelveweek.storage

  import kotlinx.browser.localStorage

  /** [namespace] prefixes every localStorage key so this store never
   *  collides with anything else using localStorage on the same origin. */
  actual class RawKeyFlagStore actual constructor(private val namespace: String) {

      private fun prefixed(key: String) = "$namespace:$key"

      actual fun allKeys(): Set<String> {
          val prefix = "$namespace:"
          val keys = mutableSetOf<String>()
          for (i in 0 until localStorage.length) {
              val k = localStorage.key(i) ?: continue
              if (k.startsWith(prefix)) keys.add(k.removePrefix(prefix))
          }
          return keys
      }

      actual fun setPresent(key: String) {
          localStorage.setItem(prefixed(key), "1")
      }

      actual fun remove(key: String) {
          localStorage.removeItem(prefixed(key))
      }

      actual fun clear() {
          allKeys().forEach { remove(it) }
      }
  }
  ```

  If Step 1 found `kotlinx-browser` isn't the current recommended path for Kotlin/Wasm (e.g. if it requires `external`/`@JsFun` interop instead), write the equivalent using that mechanism — same public shape (`allKeys`/`setPresent`/`remove`/`clear`), same prefixing behavior. Add whatever dependency Step 1 identified to `wasmJsMain`'s `dependencies { }` block in `app/build.gradle.kts` if using `kotlinx-browser`.

- [ ] **Step 6: Wire `AndroidPlatformContext.install()` into app startup**

  Read `app/src/main/java/com/personal/twelveweek/MainActivity.kt`'s `onCreate()` (do not change anything else in that huge file). Add one line near the top of `onCreate()`, before any code that might construct a `RawKeyFlagStore` (directly or transitively):

  ```kotlin
  com.personal.twelveweek.storage.AndroidPlatformContext.install(this)
  ```

  This is the ONLY change to `MainActivity.kt` in this task — one line, in `onCreate()`, nothing else in that file.

- [ ] **Step 7: Build both targets, confirm baseline unchanged**

  Run: `./gradlew :app:assembleDebug :app:wasmJsBrowserDevelopmentExecutableDistribution --console=plain` — expected `BUILD SUCCESSFUL` (nothing consumes `RawKeyFlagStore` yet, so this only proves it compiles on both targets).
  Run: `./gradlew :app:allTests testDebugUnitTest --console=plain` — expected same pass count as Step 2 (no behavior changed yet).

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/commonMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.kt app/src/androidMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.android.kt app/src/wasmJsMain/kotlin/com/personal/twelveweek/storage/RawKeyFlagStore.wasmJs.kt app/src/main/java/com/personal/twelveweek/MainActivity.kt app/build.gradle.kts
  git commit -m "Add RawKeyFlagStore expect/actual (Android SharedPreferences / wasmJs localStorage)"
  ```

---

### Task 2: Move `ProgressStore`'s reactive logic into `commonMain`

**Files:**
- Delete: `app/src/main/java/com/personal/twelveweek/Progress.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/Progress.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/ProgressTest.kt` (new — no test existed for this file before)

**Interfaces:**
- Consumes: `RawKeyFlagStore` (Task 1).
- Produces: `class ProgressStore(store: RawKeyFlagStore)` with the exact same public API as today — `isDone(key): Boolean`, `setDone(key, value: Boolean)`, `toggle(key)`, `countDone(keys): Int`, `setAll(keys, value: Boolean)`, `clearEverything()`. **Constructor signature changes** from `ProgressStore(context: Context)` to `ProgressStore(store: RawKeyFlagStore)` — every call site that constructs `ProgressStore` directly needs updating. Checked: `MainActivity.kt`'s `AppRoot()` composable is the only production call site (`remember { ProgressStore(context) }`-shaped); no other file in the currently-committed codebase constructs it. `GuidedSessionScreen.kt` and other screens only ever *receive* a `ProgressStore` as a parameter, never construct one — they need no changes.

- [ ] **Step 1: Write the failing test (new — exercises the legacy-key migration, which has no existing test)**

  `app/src/commonTest/kotlin/com/personal/twelveweek/ProgressTest.kt`:

  ```kotlin
  package com.personal.twelveweek

  import com.personal.twelveweek.storage.RawKeyFlagStore
  import kotlin.test.Test
  import kotlin.test.AfterTest
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  class ProgressTest {

      // Same namespace every test uses a fresh RawKeyFlagStore instance for,
      // but the underlying platform storage (SharedPreferences file /
      // localStorage prefix) is real and persistent within a test process —
      // clear it before each store is built so tests don't leak into each other.
      private fun freshStore(): RawKeyFlagStore {
          val store = RawKeyFlagStore("progress_test")
          store.clear()
          return store
      }

      @Test
      fun `setDone then isDone round-trips true`() {
          val progress = ProgressStore(freshStore())
          assertFalse(progress.isDone("program-1:w1-o1-s0-i0"))
          progress.setDone("program-1:w1-o1-s0-i0", true)
          assertTrue(progress.isDone("program-1:w1-o1-s0-i0"))
      }

      @Test
      fun `setDone false clears a previously-done key`() {
          val progress = ProgressStore(freshStore())
          progress.setDone("program-1:w1-o1-s0-i0", true)
          progress.setDone("program-1:w1-o1-s0-i0", false)
          assertFalse(progress.isDone("program-1:w1-o1-s0-i0"))
      }

      @Test
      fun `toggle flips state`() {
          val progress = ProgressStore(freshStore())
          progress.toggle("k")
          assertTrue(progress.isDone("k"))
          progress.toggle("k")
          assertFalse(progress.isDone("k"))
      }

      @Test
      fun `countDone counts only the done keys among those given`() {
          val progress = ProgressStore(freshStore())
          progress.setDone("a", true)
          progress.setDone("b", true)
          assertEquals(2, progress.countDone(listOf("a", "b", "c")))
      }

      @Test
      fun `setAll marks every key done, then setAll false clears them all`() {
          val progress = ProgressStore(freshStore())
          progress.setAll(listOf("a", "b", "c"), true)
          assertEquals(3, progress.countDone(listOf("a", "b", "c")))
          progress.setAll(listOf("a", "b", "c"), false)
          assertEquals(0, progress.countDone(listOf("a", "b", "c")))
      }

      @Test
      fun `clearEverything wipes all done keys`() {
          val progress = ProgressStore(freshStore())
          progress.setDone("a", true)
          progress.clearEverything()
          assertFalse(progress.isDone("a"))
      }

      @Test
      fun `legacy unprefixed key is honored by the program-1 prefixed lookup`() {
          val store = freshStore()
          store.setPresent("w1-o1-s0-i0") // legacy, pre-program-library key shape
          val progress = ProgressStore(store)
          assertTrue(progress.isDone("program-1:w1-o1-s0-i0"))
      }

      @Test
      fun `writing the new-shape key retires the legacy key so it can't cause a stale read`() {
          val store = freshStore()
          store.setPresent("w1-o1-s0-i0") // legacy
          val progress = ProgressStore(store)
          progress.setDone("program-1:w1-o1-s0-i0", false) // explicitly un-done via the new key
          assertFalse(progress.isDone("program-1:w1-o1-s0-i0"))
          // re-construct a fresh ProgressStore over the same underlying store to
          // prove the legacy key was actually retired in the raw store, not just
          // masked in this instance's in-memory state
          assertFalse(ProgressStore(store).isDone("program-1:w1-o1-s0-i0"))
      }
  }
  ```

- [ ] **Step 2: Run it to verify it fails**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: FAIL — `ProgressStore(RawKeyFlagStore)` doesn't exist yet (the old file still has `ProgressStore(Context)`).

- [ ] **Step 3: Write the commonMain implementation**

  `app/src/commonMain/kotlin/com/personal/twelveweek/Progress.kt`:

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

  Note the one intentional micro-behavior-change from the original: `setAll` now calls `setDone` per key (which also handles each key's own legacy-form cleanup) instead of hand-rolling the same loop twice — same net effect, less duplicated logic. Everything else is a direct, unchanged port.

- [ ] **Step 4: Update `MainActivity.kt`'s `ProgressStore` construction**

  Find where `ProgressStore(context)` (or equivalent) is constructed in `AppRoot()` — change it to construct a `RawKeyFlagStore("twelve_week_progress")` and pass that:

  ```kotlin
  remember { ProgressStore(RawKeyFlagStore("twelve_week_progress")) }
  ```

  (exact surrounding code may differ slightly — match the existing `remember { ... }` pattern already there, just change what's constructed inside it). Add the import `com.personal.twelveweek.storage.RawKeyFlagStore` if not already present from Task 1. This is the ONLY change to `MainActivity.kt` in this task.

- [ ] **Step 5: Delete the old file**

  ```bash
  git rm app/src/main/java/com/personal/twelveweek/Progress.kt
  ```

- [ ] **Step 6: Run the new test to verify it passes**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: PASS, all 8 new `ProgressTest` cases green on both JVM and wasmJs.

- [ ] **Step 7: Confirm Android still builds, tests pass, release still signs**

  Run: `./gradlew testDebugUnitTest :app:assembleDebug --console=plain` — expected PASS.
  Run: `./gradlew bundleRelease assembleRelease --console=plain` then `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk` — expected same `CN=TwelveWeek`.

- [ ] **Step 8: Manual on-device sanity check** (this task changes real persistence behavior, not just structure — worth a real device check, not only unit tests)

  If a device is connected (`adb devices`): `./gradlew :app:installDebug`, launch the app, tick a couple of exercises done, force-stop and relaunch the app, confirm the same exercises are still shown done. This proves the SharedPreferences file/format round-trip survived the refactor on a real device, not just in a JVM unit test.

- [ ] **Step 9: Commit**

  ```bash
  git add app/src/commonMain/kotlin/com/personal/twelveweek/Progress.kt app/src/commonTest/kotlin/com/personal/twelveweek/ProgressTest.kt app/src/main/java/com/personal/twelveweek/Progress.kt app/src/main/java/com/personal/twelveweek/MainActivity.kt
  git commit -m "Move ProgressStore's reactive logic into commonMain over RawKeyFlagStore"
  ```

---

## What's deliberately not in this part

- `ApiKeyManager.kt` (Android Keystore-encrypted RapidAPI key storage) — needs its own `expect`/`actual` design (Web Crypto on wasmJs, per the original spec decision), a materially different problem from plain flag storage. Own future task.
- `ProgramLibrary.kt` (file-based program cache + custom-import) — needs its own `expect`/`actual` design (larger JSON blobs, custom program import flow). Own future task.
- `ExerciseMediaRepository.kt`'s `WgerApi` wiring — still the task deferred at the end of Part 2, untouched here.
- No Compose screens move into `commonMain` in this part.
