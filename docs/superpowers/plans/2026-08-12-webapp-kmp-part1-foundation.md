# TwelveWeek Web App — Part 1: KMP Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the `app` module to Kotlin Multiplatform (Android + wasmJs targets) with a working shared-Compose smoke test, then migrate the zero-Android-dependency program/data layer (`Program.kt`, `ProgramModels.kt`, `ProgramJson.kt`) into `commonMain`, without moving or changing any other existing Android source file.

**Architecture:** Additive-only to the existing Android app. `androidMain`'s Kotlin source set is pointed at the existing `src/main/java` directory via explicit `srcDir()` — no existing file moves in this part. Only new files (KMP skeleton, smoke test, wasmJs entry point) plus the four program-data files actually being migrated are touched.

**Tech Stack:** Kotlin Multiplatform (Kotlin 2.4.10), Compose Multiplatform (wasmJs target), kotlinx.serialization (replacing org.json for the migrated parser), kotlin.test (replacing JUnit4 for the migrated tests).

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36 — unchanged.
- AGP 8.13.2, Kotlin 2.4.10, Gradle wrapper 8.14.5, compose-bom 2026.06.01 — existing Android toolchain versions; do not downgrade to make KMP fit.
- Android release signing (`keystore.properties` → `signingConfigs.release`, falls back to unsigned if absent) must keep working exactly as-is.
- Baseline: 17/17 Android unit tests pass, `bundleRelease` + `assembleRelease` succeed, `apksigner verify` confirms the release keystore's signature. Every task in this plan ends with re-confirming that baseline still holds (for the tasks after which it's meaningful).
- Scope: this is **Part 1 of a multi-part plan** (matches the design spec's 10-step sequence). It covers spec steps 1–2 only (KMP skeleton + program-data migration). Screen migration, networking (Ktor), storage `expect/actual`, wasmJs actuals, PWA shell, and CI deploy are separate follow-up plans, written after this part lands — later parts depend on real interfaces this part produces, and the remaining screen files (`MainActivity.kt`, `GuidedSessionScreen.kt`, `ProgramPickerScreen.kt`, `ConnectMediaScreen.kt`) haven't been fully read yet, so planning them now would mean guessing.

---

### Task 1: Kotlin Multiplatform skeleton (Android + wasmJs) with a shared Compose smoke test

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/kmp/KmpFoundationSmokeTest.kt`
- Create: `app/src/wasmJsMain/kotlin/com/personal/twelveweek/kmp/main.kt`
- Create: `app/src/wasmJsMain/resources/index.html`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `KmpFoundationSmokeTestText(): String` in `com.personal.twelveweek.kmp` — a plain function (not yet a real screen) that later tasks can delete once real shared UI exists. Not consumed by any other task in this plan; exists purely to prove the toolchain end-to-end.

- [ ] **Step 1: Look up current compatible plugin versions**

  This project pairs Kotlin 2.4.10 with AGP 8.13.2. Compose Multiplatform's Gradle plugin (`org.jetbrains.compose`) and kotlinx.serialization's plugin version need to match what's current and Kotlin-2.4.10-compatible as of today, not a guessed number. Check:
  - https://github.com/JetBrains/compose-multiplatform/releases — latest stable release whose changelog/compatibility notes list Kotlin 2.4.10 (or the nearest 2.4.x) as supported.
  - https://github.com/Kotlin/kotlinx.serialization/releases — latest stable release.

  Write down the two version strings you find — they're used in Step 2 in place of the `<COMPOSE_MULTIPLATFORM_VERSION>` / `<KOTLINX_SERIALIZATION_VERSION>` placeholders below.

- [ ] **Step 2: Update root `build.gradle.kts`**

  Replace the whole file:

  ```kotlin
  plugins {
      id("com.android.application") version "8.13.2" apply false
      id("org.jetbrains.kotlin.multiplatform") version "2.4.10" apply false
      id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
      id("org.jetbrains.compose") version "<COMPOSE_MULTIPLATFORM_VERSION>" apply false
      id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
  }
  ```

  (`org.jetbrains.kotlin.android` is removed — `org.jetbrains.kotlin.multiplatform` supersedes it once the module targets more than one platform. `org.jetbrains.kotlin.plugin.serialization` is declared now, used starting Task 4.)

- [ ] **Step 3: Add the JetBrains Compose repository if needed**

  If Step 1's chosen Compose Multiplatform version is a stable release, `google()` + `mavenCentral()` (already in `settings.gradle.kts`) are sufficient — no change needed. Only if dependency resolution fails in Step 7 with an unresolved `org.jetbrains.compose.*` artifact, add to `settings.gradle.kts`'s `dependencyResolutionManagement.repositories` block:

  ```kotlin
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  ```

- [ ] **Step 4: Rewrite `app/build.gradle.kts`**

  Replace the whole file:

  ```kotlin
  import java.util.Properties

  plugins {
      id("com.android.application")
      id("org.jetbrains.kotlin.multiplatform")
      id("org.jetbrains.kotlin.plugin.compose")
      id("org.jetbrains.compose")
      id("org.jetbrains.kotlin.plugin.serialization")
  }

  // Release signing: loaded from rootProject/keystore.properties if present.
  // Falls back to an unsigned release build when absent (fresh clone / CI without secrets).
  val keystorePropertiesFile = rootProject.file("keystore.properties")
  val keystoreProperties = Properties().apply {
      if (keystorePropertiesFile.exists()) {
          keystorePropertiesFile.inputStream().use { load(it) }
      }
  }
  val hasReleaseSigning = keystorePropertiesFile.exists()

  kotlin {
      androidTarget {
          compilerOptions {
              jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
          }
      }

      wasmJs {
          browser()
          binaries.executable()
      }

      sourceSets {
          val commonMain by getting {
              dependencies {
                  implementation(compose.runtime)
                  implementation(compose.foundation)
                  implementation(compose.material3)
                  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<KOTLINX_SERIALIZATION_VERSION>")
              }
          }
          val commonTest by getting {
              dependencies {
                  implementation(kotlin("test"))
              }
          }
          val androidMain by getting {
              kotlin.srcDir("src/main/java")
              dependencies {
                  implementation("androidx.core:core-ktx:1.13.1")
                  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
                  implementation("androidx.activity:activity-compose:1.9.0")
                  implementation("androidx.compose.ui:ui-tooling-preview")
                  implementation("androidx.compose.material:material-icons-extended")
                  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
                  implementation("com.squareup.okhttp3:okhttp:4.12.0")
                  implementation("androidx.media3:media3-exoplayer:1.4.1")
                  implementation("androidx.media3:media3-ui:1.4.1")
                  implementation("io.coil-kt:coil-compose:2.6.0")
                  implementation("androidx.security:security-crypto:1.1.0-alpha06")
              }
          }
          val wasmJsMain by getting {
              dependencies {
                  implementation(compose.html.core)
              }
          }
      }
  }

  android {
      namespace = "com.personal.twelveweek"
      compileSdk = 36

      defaultConfig {
          applicationId = "com.personal.twelveweek"
          minSdk = 26
          targetSdk = 36
          versionCode = 1
          versionName = "1.0"
      }

      signingConfigs {
          if (hasReleaseSigning) {
              create("release") {
                  storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                  storePassword = keystoreProperties.getProperty("storePassword")
                  keyAlias = keystoreProperties.getProperty("keyAlias")
                  keyPassword = keystoreProperties.getProperty("keyPassword")
              }
          }
      }

      buildTypes {
          release {
              isMinifyEnabled = false
              proguardFiles(
                  getDefaultProguardFile("proguard-android-optimize.txt"),
                  "proguard-rules.pro"
              )
              if (hasReleaseSigning) {
                  signingConfig = signingConfigs.getByName("release")
              }
          }
      }

      compileOptions {
          sourceCompatibility = JavaVersion.VERSION_17
          targetCompatibility = JavaVersion.VERSION_17
      }

      buildFeatures {
          compose = true
      }

      packaging {
          resources {
              excludes += "/META-INF/{AL2.0,LGPL2.1}"
          }
      }
  }

  dependencies {
      val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
      add("androidMainImplementation", composeBom)
      testImplementation("junit:junit:4.13.2")
      testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
      testImplementation("io.mockk:mockk:1.13.12")
      testImplementation("org.json:json:20240303")
  }
  ```

  Notes on what changed from the pre-KMP file: `androidx.compose.ui:ui` / `ui-graphics` / `material3` are supplied via the `compose.*` multiplatform aliases in `commonMain` instead of being listed as plain Android artifacts (that's what makes them available to `wasmJsMain` too); the compose-bom still applies to the Android compilation via `androidMainImplementation` so exact Android artifact versions are pinned the same way as before. `debugImplementation("androidx.compose.ui:ui-tooling")` is dropped for now (Android-Studio-only tooling dependency, not required for the build/test/release commands this plan verifies — re-add to `androidMain` dependencies later if Compose Preview stops working in Android Studio).

- [ ] **Step 5: Create the shared smoke-test composable**

  `app/src/commonMain/kotlin/com/personal/twelveweek/kmp/KmpFoundationSmokeTest.kt`:

  ```kotlin
  package com.personal.twelveweek.kmp

  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable

  /** Proves a Composable written once in commonMain renders on every target.
   *  Delete once Part 3 (screen migration) moves real screens into commonMain. */
  @Composable
  fun KmpFoundationSmokeTest() {
      MaterialTheme {
          Text("TwelveWeek KMP foundation OK")
      }
  }
  ```

- [ ] **Step 6: Create the wasmJs entry point**

  `app/src/wasmJsMain/kotlin/com/personal/twelveweek/kmp/main.kt`:

  ```kotlin
  package com.personal.twelveweek.kmp

  import androidx.compose.ui.window.CanvasBasedWindow

  fun main() {
      CanvasBasedWindow(canvasElementId = "ComposeTarget") {
          KmpFoundationSmokeTest()
      }
  }
  ```

  `app/src/wasmJsMain/resources/index.html`:

  ```html
  <!DOCTYPE html>
  <html lang="en">
  <head>
      <meta charset="UTF-8">
      <title>TwelveWeek</title>
      <style>html, body { margin: 0; height: 100%; } canvas { width: 100%; height: 100%; }</style>
  </head>
  <body>
      <canvas id="ComposeTarget"></canvas>
      <script src="app.js"></script>
  </body>
  </html>
  ```

- [ ] **Step 7: Build both targets**

  Run: `./gradlew :app:assembleDebug :app:wasmJsBrowserDevelopmentExecutableDistribution --console=plain`

  Expected: `BUILD SUCCESSFUL`. If the wasmJs task name differs for the Compose Multiplatform version you picked in Step 1, run `./gradlew :app:tasks --group="build" --console=plain` and use whichever task produces the browser distribution (commonly `wasmJsBrowserDevelopmentExecutableDistribution` or `wasmJsBrowserDistribution`).

  If this fails on dependency resolution for `compose.html.core` or `compose.runtime`: confirm the `org.jetbrains.compose` plugin version from Step 1 is actually applied (check `./gradlew :app:dependencies --configuration wasmJsMainCompileClasspath | head -30`), and confirm Step 3's repository was added if needed.

- [ ] **Step 8: Serve and visually confirm the wasmJs build**

  ```bash
  cd app/build/dist/wasmJs/developmentExecutable
  python3 -m http.server 8080
  ```

  Open `http://localhost:8080` in a browser (or fetch it) and confirm the page renders "TwelveWeek KMP foundation OK" — not a blank canvas or a console error. Stop the server after confirming.

- [ ] **Step 9: Confirm Android is unaffected**

  Run: `./gradlew testDebugUnitTest --console=plain`
  Expected: 17/17 tests pass (same baseline as before this task).

  Run: `./gradlew bundleRelease assembleRelease --console=plain`
  Expected: `BUILD SUCCESSFUL`, produces `app/build/outputs/bundle/release/app-release.aab` and `app/build/outputs/apk/release/app-release.apk`.

  Run: `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk`
  Expected: prints the same `CN=TwelveWeek` signer identity as before — confirms the KMP conversion didn't disturb release signing.

- [ ] **Step 10: Commit**

  ```bash
  git add build.gradle.kts settings.gradle.kts app/build.gradle.kts app/src/commonMain app/src/wasmJsMain
  git commit -m "Convert app module to Kotlin Multiplatform (Android + wasmJs), add shared Compose smoke test"
  ```

---

### Task 2: Move domain models (`Program.kt`, `ProgramModels.kt`) into `commonMain`

**Files:**
- Delete: `app/src/main/java/com/personal/twelveweek/Program.kt`
- Delete: `app/src/main/java/com/personal/twelveweek/programs/ProgramModels.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/Program.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/programs/ProgramModels.kt`
- Test: `app/src/commonTest/kotlin/com/personal/twelveweek/ProgramTest.kt`

**Interfaces:**
- Consumes: nothing new (these files have zero Android dependencies today — confirmed by reading their current imports, which are limited to `com.personal.twelveweek.Week` and pure Kotlin stdlib).
- Produces: `Exercise`, `Section`, `Workout`, `Week` (package `com.personal.twelveweek`) and `ProgramLevel`, `FocusArea`, `Equipment`, `ProgramMeta`, `LibraryProgram` (package `com.personal.twelveweek.programs`) — now available from `commonMain`, unchanged in shape from their current definitions. Task 3 and Task 4 depend on these.

- [ ] **Step 1: Write the failing test**

  `app/src/commonTest/kotlin/com/personal/twelveweek/ProgramTest.kt` (new coverage — these computed properties have no existing dedicated test):

  ```kotlin
  package com.personal.twelveweek

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue
  import kotlin.test.assertFalse

  class ProgramTest {

      private fun exercise(name: String, reps: Int? = null, seconds: Int? = null) =
          Exercise(raw = name, name = name, reps = reps, seconds = seconds)

      @Test
      fun `isTimed is true only when seconds is set`() {
          assertTrue(exercise("Plank", seconds = 30).isTimed)
          assertFalse(exercise("Squats", reps = 20).isTimed)
      }

      @Test
      fun `isRest matches Pause case-insensitively`() {
          assertTrue(exercise("Pause").isRest)
          assertTrue(exercise("pause").isRest)
          assertFalse(exercise("Squats").isRest)
      }

      @Test
      fun `workout keyFor namespaces by programId, week, index, section and item`() {
          val workout = Workout(
              programId = "program-1",
              week = 2,
              index = 3,
              sections = listOf(
                  Section("Round 1", listOf(exercise("Squats", reps = 20))),
                  Section("Round 2", listOf(exercise("Push Ups", reps = 10)))
              )
          )
          assertEquals("program-1:w2-o3-s0-i0", workout.keyFor(0, 0))
          assertEquals("program-1:w2-o3-s1-i0", workout.keyFor(1, 0))
      }

      @Test
      fun `workout allKeys covers every exercise across every section`() {
          val workout = Workout(
              programId = "program-1",
              week = 1,
              index = 1,
              sections = listOf(
                  Section("Round 1", listOf(exercise("Squats", reps = 20), exercise("Pause", seconds = 30))),
                  Section("Round 2", listOf(exercise("Push Ups", reps = 10)))
              )
          )
          assertEquals(3, workout.totalItems)
          assertEquals(3, workout.allKeys().size)
          assertEquals(workout.allKeys().toSet().size, workout.allKeys().size) // no duplicate keys
      }

      @Test
      fun `workout title is Day plus index`() {
          val workout = Workout(programId = "p", week = 1, index = 4, sections = emptyList())
          assertEquals("Day 4", workout.title)
      }
  }
  ```

- [ ] **Step 2: Run it to verify it fails**

  Run: `./gradlew :app:commonTest --console=plain` (or, if that task name doesn't exist for your KMP setup, `./gradlew :app:allTests --console=plain` — check with `./gradlew :app:tasks --group=verification`)
  Expected: FAIL with "unresolved reference" — `Program.kt` doesn't exist in `commonMain` yet.

- [ ] **Step 3: Move the files**

  ```bash
  mkdir -p app/src/commonMain/kotlin/com/personal/twelveweek/programs
  git mv app/src/main/java/com/personal/twelveweek/Program.kt app/src/commonMain/kotlin/com/personal/twelveweek/Program.kt
  git mv app/src/main/java/com/personal/twelveweek/programs/ProgramModels.kt app/src/commonMain/kotlin/com/personal/twelveweek/programs/ProgramModels.kt
  ```

  No content changes — both files are already pure Kotlin with no Android imports.

- [ ] **Step 4: Run the test to verify it passes**

  Run: `./gradlew :app:commonTest --console=plain` (or `:app:allTests`)
  Expected: PASS, 5/5 new tests green.

- [ ] **Step 5: Confirm Android still compiles and its existing tests still pass**

  Run: `./gradlew testDebugUnitTest --console=plain`
  Expected: 17/17 pass — `androidMain`'s `kotlin.srcDir("src/main/java")` (Task 1) no longer finds `Program.kt`/`ProgramModels.kt` there since they moved, but `commonMain` is automatically part of the Android compilation, so every existing call site resolves unchanged.

- [ ] **Step 6: Commit**

  ```bash
  git add -A
  git commit -m "Move Program.kt and ProgramModels.kt into commonMain, add ProgramTest"
  ```

---

### Task 3: Move `ProgramJson.kt` into `commonMain`, org.json → kotlinx.serialization

**Files:**
- Delete: `app/src/main/java/com/personal/twelveweek/programs/ProgramJson.kt`
- Delete: `app/src/test/java/com/personal/twelveweek/programs/ProgramJsonTest.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/programs/ProgramJson.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/programs/ProgramJsonTest.kt`

**Interfaces:**
- Consumes: `Exercise`, `Section`, `Workout`, `Week` (Task 2's `Program.kt`), `ProgramMeta`, `ProgramLevel`, `FocusArea`, `Equipment`, `LibraryProgram` (Task 2's `ProgramModels.kt`).
- Produces: `IndexEntry` (unchanged shape: `data class IndexEntry(val meta: ProgramMeta, val file: String)`), `fun parseIndex(json: String): List<IndexEntry>`, `fun parseProgram(json: String): LibraryProgram` — same names and signatures as today, so `ProgramLibrary.kt` (moved in a later part) doesn't need to change its call sites.

- [ ] **Step 1: Write the failing test (ported from the existing JUnit4 test, using kotlin.test)**

  `app/src/commonTest/kotlin/com/personal/twelveweek/programs/ProgramJsonTest.kt`:

  ```kotlin
  package com.personal.twelveweek.programs

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull

  class ProgramJsonTest {

      @Test
      fun `parses index entries`() {
          val json = """
              {"programs": [
                {"id":"program-1","title":"12 Week Full Body","level":"INTERMEDIATE",
                 "focusAreas":["FULL_BODY"],"equipment":["HOME"],"weeks":12,
                 "file":"programs/program-1.json"}
              ]}
          """.trimIndent()

          val entries = parseIndex(json)
          assertEquals(1, entries.size)
          val meta = entries[0].meta
          assertEquals("program-1", meta.id)
          assertEquals(ProgramLevel.INTERMEDIATE, meta.level)
          assertEquals(listOf(FocusArea.FULL_BODY), meta.focusAreas)
          assertEquals(listOf(Equipment.HOME), meta.equipment)
          assertEquals(12, meta.weekCount)
          assertEquals("programs/program-1.json", entries[0].file)
      }

      @Test
      fun `unknown level falls back to intermediate, unknown enum values are dropped`() {
          val json = """
              {"programs": [
                {"id":"x","title":"X","level":"NIGHTMARE",
                 "focusAreas":["FULL_BODY","CARDIO"],"equipment":[],"weeks":1,"file":"programs/x.json"}
              ]}
          """.trimIndent()

          val meta = parseIndex(json)[0].meta
          assertEquals(ProgramLevel.INTERMEDIATE, meta.level)
          assertEquals(listOf(FocusArea.FULL_BODY), meta.focusAreas)
      }

      @Test
      fun `parses a full program with reps, seconds and curated wger + exerciseDb ids`() {
          val json = """
              {"id":"program-1","title":"12 Week Full Body","level":"INTERMEDIATE",
               "focusAreas":["FULL_BODY"],"equipment":["HOME"],
               "weeks":[
                 {"number":1,"workouts":[
                   {"index":1,"sections":[
                     {"title":"Round 1","exercises":[
                       {"raw":"20 Squats","name":"Squats","reps":20,"seconds":null,"wgerId":"615","exerciseDbId":"exr_41n2hmGR8WuVfe1U"},
                       {"raw":"30s Pause","name":"Pause","reps":null,"seconds":30,"wgerId":null,"exerciseDbId":null}
                     ]}
                   ]}
                 ]}
               ]}
          """.trimIndent()

          val program = parseProgram(json)
          assertEquals("program-1", program.meta.id)
          assertEquals(1, program.meta.weekCount)

          val workout = program.weeks[0].workouts[0]
          assertEquals("program-1", workout.programId)
          assertEquals("program-1:w1-o1-s0-i0", workout.keyFor(0, 0))

          val squats = workout.sections[0].exercises[0]
          assertEquals(20, squats.reps)
          assertNull(squats.seconds)
          assertEquals("615", squats.wgerId)
          assertEquals("exr_41n2hmGR8WuVfe1U", squats.exerciseDbId)

          val pause = workout.sections[0].exercises[1]
          assertEquals(30, pause.seconds)
          assertNull(pause.wgerId)
          assertNull(pause.exerciseDbId)
          assertEquals(true, pause.isRest)
      }
  }
  ```

- [ ] **Step 2: Run it to verify it fails**

  Run: `./gradlew :app:commonTest --console=plain` (or `:app:allTests`)
  Expected: FAIL — `ProgramJson.kt` (with `parseIndex`/`parseProgram`) doesn't exist in `commonMain` yet (the old `src/main/java` copy still exists and still compiles against org.json for the Android target only, so this is specifically a `commonTest` resolution failure, not a full build failure, until Step 4 removes the old file).

- [ ] **Step 3: Write the kotlinx.serialization implementation**

  `app/src/commonMain/kotlin/com/personal/twelveweek/programs/ProgramJson.kt`:

  ```kotlin
  package com.personal.twelveweek.programs

  import com.personal.twelveweek.Exercise
  import com.personal.twelveweek.Section
  import com.personal.twelveweek.Week
  import com.personal.twelveweek.Workout
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json

  /**
   * Parses this program-library format (see `programs/index.json` in the synced
   * GitHub repo, and `programs/program-1.json` in this repo as the worked
   * example). Deliberately tolerant of unknown enum values / missing optional
   * fields so a future program that uses a `FocusArea` this build doesn't know
   * about yet degrades to "uncategorized" instead of crashing the sync.
   */

  private val json = Json { ignoreUnknownKeys = true }

  /** One row of `index.json` — metadata plus where to fetch the full program. */
  data class IndexEntry(val meta: ProgramMeta, val file: String)

  @Serializable
  private data class ExerciseDto(
      val raw: String,
      val name: String,
      val reps: Int? = null,
      val seconds: Int? = null,
      val wgerId: String? = null,
      val exerciseDbId: String? = null,
      val freeExerciseDbId: String? = null
  )

  @Serializable
  private data class SectionDto(val title: String, val exercises: List<ExerciseDto> = emptyList())

  @Serializable
  private data class WorkoutDto(val index: Int, val sections: List<SectionDto> = emptyList(), val estimatedMinutes: Int = 0)

  @Serializable
  private data class WeekDto(val number: Int, val workouts: List<WorkoutDto> = emptyList())

  @Serializable
  private data class ProgramDto(
      val id: String,
      val title: String,
      val level: String = "",
      val focusAreas: List<String> = emptyList(),
      val equipment: List<String> = emptyList(),
      val sessionMinutes: Int = 0,
      val weeks: List<WeekDto> = emptyList()
  )

  @Serializable
  private data class IndexEntryDto(
      val id: String,
      val title: String,
      val level: String = "",
      val focusAreas: List<String> = emptyList(),
      val equipment: List<String> = emptyList(),
      val weeks: Int = 0,
      val sessionMinutes: Int = 0,
      val file: String
  )

  @Serializable
  private data class IndexDto(val programs: List<IndexEntryDto> = emptyList())

  private fun parseLevel(raw: String): ProgramLevel =
      raw.takeIf { it.isNotBlank() }
          ?.let { runCatching { ProgramLevel.valueOf(it) }.getOrNull() }
          ?: ProgramLevel.INTERMEDIATE

  private fun parseFocusAreas(values: List<String>): List<FocusArea> =
      values.mapNotNull { runCatching { FocusArea.valueOf(it) }.getOrNull() }

  private fun parseEquipment(values: List<String>): List<Equipment> =
      values.mapNotNull { runCatching { Equipment.valueOf(it) }.getOrNull() }

  private fun ExerciseDto.toDomain() = Exercise(
      raw = raw,
      name = name,
      reps = reps,
      seconds = seconds,
      wgerId = wgerId,
      exerciseDbId = exerciseDbId,
      freeExerciseDbId = freeExerciseDbId
  )

  private fun SectionDto.toDomain() = Section(title = title, exercises = exercises.map { it.toDomain() })

  private fun WorkoutDto.toDomain(programId: String, week: Int) = Workout(
      programId = programId,
      week = week,
      index = index,
      sections = sections.map { it.toDomain() },
      estimatedMinutes = estimatedMinutes
  )

  private fun WeekDto.toDomain(programId: String) = Week(
      number = number,
      workouts = workouts.map { it.toDomain(programId, number) }
  )

  private fun IndexEntryDto.toDomain() = IndexEntry(
      meta = ProgramMeta(
          id = id,
          title = title,
          level = parseLevel(level),
          focusAreas = parseFocusAreas(focusAreas),
          equipment = parseEquipment(equipment),
          weekCount = weeks,
          sessionMinutes = sessionMinutes
      ),
      file = file
  )

  /** Parses `programs/index.json` — the picker's lightweight listing. */
  fun parseIndex(jsonText: String): List<IndexEntry> =
      json.decodeFromString(IndexDto.serializer(), jsonText).programs.map { it.toDomain() }

  /** Parses one full `programs/<id>.json` — metadata plus all 12 weeks. */
  fun parseProgram(jsonText: String): LibraryProgram {
      val dto = json.decodeFromString(ProgramDto.serializer(), jsonText)
      val weeks = dto.weeks.map { it.toDomain(dto.id) }
      val meta = ProgramMeta(
          id = dto.id,
          title = dto.title,
          level = parseLevel(dto.level),
          focusAreas = parseFocusAreas(dto.focusAreas),
          equipment = parseEquipment(dto.equipment),
          weekCount = weeks.size,
          sessionMinutes = dto.sessionMinutes
      )
      return LibraryProgram(meta, weeks)
  }
  ```

  This preserves the exact tolerant-parsing behavior of the original (unknown `level`/enum values degrade gracefully, same as tested) by reusing the same `parseLevel`/`parseFocusAreas`/`parseEquipment` mapping logic — only the JSON-decoding layer underneath changed, from `org.json.JSONObject` to `@Serializable` DTOs.

- [ ] **Step 4: Delete the old file and its old test**

  ```bash
  git rm app/src/main/java/com/personal/twelveweek/programs/ProgramJson.kt
  git rm app/src/test/java/com/personal/twelveweek/programs/ProgramJsonTest.kt
  ```

- [ ] **Step 5: Run the new test to verify it passes**

  Run: `./gradlew :app:commonTest --console=plain` (or `:app:allTests`)
  Expected: PASS, all 3 ported tests green, plus Task 2's 5 tests still green.

- [ ] **Step 6: Confirm Android still builds, tests pass, and release still signs**

  Run: `./gradlew testDebugUnitTest --console=plain`
  Expected: passes. (Count will be lower than 17 now that `ProgramJsonTest.kt`'s 3 JVM tests moved to `commonTest` and no longer run under `testDebugUnitTest` specifically — confirm the remaining Android-only tests, e.g. `ExerciseMediaRepositoryTest`, still all pass; the 3 migrated tests are proven passing via `commonTest`/`allTests` in Step 5 instead.)

  Run: `./gradlew bundleRelease assembleRelease --console=plain`
  Expected: `BUILD SUCCESSFUL`.

  Run: `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk`
  Expected: same `CN=TwelveWeek` signer identity as before.

- [ ] **Step 7: Commit**

  ```bash
  git add -A
  git commit -m "Move ProgramJson.kt into commonMain, org.json -> kotlinx.serialization"
  ```

---

## What's deliberately not in this part

- `ProgramLibrary.kt` stays in `androidMain` untouched — it does file I/O via `android.content.Context`/`java.io.File`, which needs a real storage `expect`/`actual` design (Part 2 of this plan series), not a quick move.
- `ProgramSyncRepository.kt`, `ExerciseDbApi.kt`, `WgerApi.kt`, `ApiKeyManager.kt`, and every Compose screen (`MainActivity.kt`, `ConnectMediaScreen.kt`, `GuidedSessionScreen.kt`, `ProgramPickerScreen.kt`) are untouched — covered by later parts once their full current source has been read and the storage/networking abstractions from Part 2 exist for them to build on.
- No PWA manifest, service worker, or CI deploy workflow yet — those need a real UI to serve first.
