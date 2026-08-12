# TwelveWeek Web App — Part 2: Networking (OkHttp → Ktor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the three network-client files with zero storage dependencies — `ExerciseDbModels.kt`, `ExerciseDbApi.kt`, `WgerApi.kt` — into `commonMain`, replacing OkHttp with Ktor's multiplatform HTTP client and org.json with kotlinx.serialization, so the exercise-media network layer works on both Android and wasmJs.

**Architecture:** Same incremental, additive-first approach as Part 1. `ProgramSyncRepository.kt` and `ExerciseMediaRepository.kt` are NOT moved in this part — both depend on Android-only storage (`ProgramLibrary`, `ApiKeyManager`) and will move together with the storage `expect`/`actual` layer in Part 3. `ExerciseMediaRepository.kt`'s `default()` factory gets a narrow, scoped edit in this part (just the client-construction lines) so it keeps compiling against the new `ExerciseDbApi`/`WgerApi` constructor signatures — nothing else in that file changes.

**Tech Stack:** Ktor multiplatform HTTP client (exact version looked up in Task 1, compatible with Kotlin 2.4.10), kotlinx.serialization (already in the project since Part 1), kotlin.test + Ktor's `MockEngine` for cross-platform network tests (replacing JVM-only OkHttp `MockWebServer`).

## Global Constraints

- Same toolchain as Part 1: AGP 8.13.2, Kotlin 2.4.10, Gradle wrapper 8.14.5, compose-bom 2026.06.01, compileSdk/targetSdk 36, minSdk 26.
- Baseline before this part: `./gradlew :app:allTests` and `testDebugUnitTest` both green (exact count: re-confirm in Task 1 before making changes — do not assume a stale number). `bundleRelease`/`assembleRelease`/`apksigner verify` (`CN=TwelveWeek`) must keep passing after every task.
- **Type-strictness discipline** (lesson from Part 1's final review): kotlinx.serialization does NOT coerce JSON number↔string the way org.json did. Every DTO field in this part must be checked against what the live APIs actually return, and every field that was REQUIRED in the org.json code (a bare `getJSONArray`/`getString` call that threw if missing) must stay REQUIRED (no default) in its DTO — do not add a default "to make it compile" without checking whether the original threw on that field's absence. Getting this wrong reintroduces exactly the silent-failure bug Part 1's final review caught in `ProgramJson.kt`.
- `keystore.properties`/`*.jks` must stay gitignored (already committed as of Part 1) — never stage them.
- Every task commits only the files its own "Files" section lists — no `git add -A`. (Part 1's final review flagged `git add -A` as the root mechanism behind two separate incidents; every task in this plan uses explicit `git add <path>...` instead.)

---

### Task 1: Add Ktor multiplatform dependencies

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing (infra-only task).
- Produces: `io.ktor.client.HttpClient` and friends available to `commonMain`/`commonTest`; `io.ktor.client.engine.okhttp.OkHttp` engine available to `androidMain`; a wasmJs-compatible Ktor engine available to `wasmJsMain`; `io.ktor.client.engine.mock.MockEngine` available to `commonTest`. Tasks 2-4 depend on these being resolvable.

- [ ] **Step 1: Look up the current Ktor version compatible with Kotlin 2.4.10**

  Check https://github.com/ktorio/ktor/releases (or https://ktor.io/docs/releases.html) for the latest stable release whose changelog/compatibility notes list Kotlin 2.4.10 (or the nearest 2.4.x) as supported — same discipline as Part 1's Compose Multiplatform lookup. Write down the version string; it replaces `<KTOR_VERSION>` in every step below. Also confirm the exact wasmJs-target artifact name for Ktor's JS/Wasm client engine at that version (historically `io.ktor:ktor-client-js`, but verify — Ktor's wasmJs support has moved around across releases; check the "wasmJs" or "js" target rows in that version's client-engines documentation page, or `https://mvnrepository.com/artifact/io.ktor` for which engine artifacts publish a `wasm-js` variant).

- [ ] **Step 2: Confirm current baseline before touching anything**

  Run: `./gradlew :app:allTests testDebugUnitTest --console=plain`
  Record the exact pass count reported (do not just check "BUILD SUCCESSFUL" — note the number so later tasks can compare). This is Part 2's actual starting baseline, whatever it is.

- [ ] **Step 3: Add dependencies to `app/build.gradle.kts`**

  In the `commonMain` source set's `dependencies { }` block (alongside the existing `compose.runtime`/`kotlinx-serialization-json` lines), add:

  ```kotlin
  implementation("io.ktor:ktor-client-core:<KTOR_VERSION>")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  ```

  (1.8.1 matches the version `androidMain`'s existing `kotlinx-coroutines-android:1.8.1` already pulls in transitively — pin explicitly rather than leaving it to resolve implicitly, and do not bump it as part of this task.)

  In the `commonTest` source set's `dependencies { }` block (alongside `kotlin("test")`), add:

  ```kotlin
  implementation("io.ktor:ktor-client-mock:<KTOR_VERSION>")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  ```

  In the `androidMain` source set's `dependencies { }` block (alongside the existing `okhttp3:okhttp` line — leave that line in place, `ProgramSyncRepository.kt`/`ExerciseMediaRepository.kt` still use it directly and are out of scope for this part), add:

  ```kotlin
  implementation("io.ktor:ktor-client-okhttp:<KTOR_VERSION>")
  ```

  In the `wasmJsMain` source set's `dependencies { }` block (alongside the existing `compose.ui` line), add the wasmJs engine artifact you confirmed in Step 1, e.g.:

  ```kotlin
  implementation("io.ktor:ktor-client-js:<KTOR_VERSION>")
  ```

  (Use whatever artifact name Step 1 actually confirmed — this is a placeholder for that confirmed value, not a guess to leave unverified.)

- [ ] **Step 4: Verify it resolves and nothing else broke**

  Run: `./gradlew :app:assembleDebug --console=plain`
  Expected: `BUILD SUCCESSFUL`. If dependency resolution fails for any Ktor artifact, re-check Step 1's version/artifact-name lookup — don't guess a second version, look it up again against the same sources.

  Run: `./gradlew :app:allTests testDebugUnitTest --console=plain`
  Expected: same pass count as Step 2 (nothing should change yet — this task adds unused dependencies only, no code changes).

- [ ] **Step 5: Commit**

  ```bash
  git add app/build.gradle.kts
  git commit -m "Add Ktor multiplatform HTTP client dependencies"
  ```

---

### Task 2: Move `ExerciseDbModels.kt` into `commonMain`

**Files:**
- Delete: `app/src/main/java/com/personal/twelveweek/media/ExerciseDbModels.kt`
- Delete: `app/src/test/java/com/personal/twelveweek/media/ExerciseDbModelsTest.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/media/ExerciseDbModels.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/media/ExerciseDbModelsTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `data class ExerciseDbDetail(exerciseId: String, name: String, videoUrl: String?, imageUrl: String?, instructions: List<String>, targetMuscles: List<String>, secondaryMuscles: List<String>, equipments: List<String>)` and `fun parseExerciseDbDetail(jsonText: String): ExerciseDbDetail` — same name/shape as today. Tasks 3 and 4 both consume `ExerciseDbDetail` and (Task 3 only) `parseExerciseDbDetail`.

- [ ] **Step 1: Write the failing test (ported from the existing JUnit4 test)**

  `app/src/commonTest/kotlin/com/personal/twelveweek/media/ExerciseDbModelsTest.kt`:

  ```kotlin
  package com.personal.twelveweek.media

  import kotlin.test.Test
  import kotlin.test.assertEquals

  class ExerciseDbModelsTest {

      private val sample = """
      {
        "exerciseId": "exr_41n2hxnFMotsXTj3",
        "name": "Bench Press",
        "imageUrl": "https://cdn.exercisedb.dev/media/images/CNKJtB2O5Y.webp",
        "equipments": ["BARBELL"],
        "bodyParts": ["CHEST"],
        "targetMuscles": ["PECTORALIS MAJOR STERNAL HEAD"],
        "secondaryMuscles": ["ANTERIOR DELTOID", "TRICEPS BRACHII"],
        "videoUrl": "https://cdn.exercisedb.dev/videos/Trn4QDW/bench.mp4",
        "instructions": ["Grip the barbell.", "Lower to your chest."]
      }
      """.trimIndent()

      @Test
      fun `parses a full exercise object`() {
          val detail = parseExerciseDbDetail(sample)
          assertEquals("exr_41n2hxnFMotsXTj3", detail.exerciseId)
          assertEquals("Bench Press", detail.name)
          assertEquals("https://cdn.exercisedb.dev/videos/Trn4QDW/bench.mp4", detail.videoUrl)
          assertEquals(2, detail.instructions.size)
          assertEquals(listOf("ANTERIOR DELTOID", "TRICEPS BRACHII"), detail.secondaryMuscles)
      }

      @Test
      fun `missing optional fields default to empty-safe values`() {
          val detail = parseExerciseDbDetail(
              """{"exerciseId":"x","name":"Squat"}"""
          )
          assertEquals(null, detail.videoUrl)
          assertEquals(emptyList<String>(), detail.instructions)
      }
  }
  ```

  Note: `bodyParts` appears in the sample but is not read by `ExerciseDbDetail` today — keep it that way (it's genuinely unused; do not add it to the DTO).

- [ ] **Step 2: Run it to verify it fails**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: FAIL — `ExerciseDbModels.kt` doesn't exist in `commonMain` yet.

- [ ] **Step 3: Write the kotlinx.serialization implementation**

  `app/src/commonMain/kotlin/com/personal/twelveweek/media/ExerciseDbModels.kt`:

  ```kotlin
  package com.personal.twelveweek.media

  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json

  data class ExerciseDbDetail(
      val exerciseId: String,
      val name: String,
      val videoUrl: String?,
      val imageUrl: String?,
      val instructions: List<String>,
      val targetMuscles: List<String>,
      val secondaryMuscles: List<String>,
      val equipments: List<String>
  )

  @Serializable
  private data class ExerciseDbDetailDto(
      val exerciseId: String,
      val name: String,
      val videoUrl: String? = null,
      val imageUrl: String? = null,
      val instructions: List<String> = emptyList(),
      val targetMuscles: List<String> = emptyList(),
      val secondaryMuscles: List<String> = emptyList(),
      val equipments: List<String> = emptyList()
  )

  private val exerciseDbJson = Json { ignoreUnknownKeys = true }

  /** Parses one ExerciseDB v2 exercise object (see docs.exercisedb.dev schema). */
  fun parseExerciseDbDetail(jsonText: String): ExerciseDbDetail {
      val dto = exerciseDbJson.decodeFromString(ExerciseDbDetailDto.serializer(), jsonText)
      return ExerciseDbDetail(
          exerciseId = dto.exerciseId,
          name = dto.name,
          videoUrl = dto.videoUrl?.takeIf { it.isNotBlank() },
          imageUrl = dto.imageUrl?.takeIf { it.isNotBlank() },
          instructions = dto.instructions,
          targetMuscles = dto.targetMuscles,
          secondaryMuscles = dto.secondaryMuscles,
          equipments = dto.equipments
      )
  }
  ```

  `exerciseId` and `name` have no default — the original code used `o.getString(...)` for both (required, threw if missing). Preserve that: no default on either field.

- [ ] **Step 4: Delete the old file and its old test**

  ```bash
  git rm app/src/main/java/com/personal/twelveweek/media/ExerciseDbModels.kt
  git rm app/src/test/java/com/personal/twelveweek/media/ExerciseDbModelsTest.kt
  ```

- [ ] **Step 5: Run the new test to verify it passes**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: PASS, both `ExerciseDbModelsTest` cases green.

- [ ] **Step 6: Confirm Android still builds and release still signs**

  Run: `./gradlew testDebugUnitTest :app:assembleDebug --console=plain` — expected PASS (note: `ExerciseDbApi.kt`, not yet migrated, still imports `ExerciseDbDetail`/`parseExerciseDbDetail` from their new commonMain location by package name alone, since both files share package `com.personal.twelveweek.media` — no import statement changes needed on the androidMain side).
  Run: `./gradlew bundleRelease assembleRelease --console=plain` then `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk` — expected same `CN=TwelveWeek`.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/commonMain/kotlin/com/personal/twelveweek/media/ExerciseDbModels.kt app/src/commonTest/kotlin/com/personal/twelveweek/media/ExerciseDbModelsTest.kt app/src/main/java/com/personal/twelveweek/media/ExerciseDbModels.kt app/src/test/java/com/personal/twelveweek/media/ExerciseDbModelsTest.kt
  git commit -m "Move ExerciseDbModels.kt into commonMain, org.json -> kotlinx.serialization"
  ```

---

### Task 3: Move `ExerciseDbApi.kt` into `commonMain`, OkHttp → Ktor

**Files:**
- Delete: `app/src/main/java/com/personal/twelveweek/media/ExerciseDbApi.kt`
- Delete: `app/src/test/java/com/personal/twelveweek/media/ExerciseDbApiTest.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/media/ExerciseDbApi.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/media/ExerciseDbApiTest.kt`
- Modify: `app/src/main/java/com/personal/twelveweek/media/ExerciseMediaRepository.kt` (ONLY the `default()` factory function body and its imports — nothing else in this file)

**Interfaces:**
- Consumes: `ExerciseDbDetail`, `parseExerciseDbDetail` (Task 2).
- Produces: `sealed interface ApiResult` (`Success(detail)`, `Unauthorized`, `NetworkError`, unchanged shape), `class ExerciseDbApi(client: HttpClient, baseUrl: String = ...)` with `suspend fun fetchExercise(exerciseId: String, apiKey: String): ApiResult` — same method name/signature, but the constructor's first parameter type changes from `OkHttpClient` to `io.ktor.client.HttpClient`. Task 4 and `ExerciseMediaRepository.kt` both consume this new constructor signature.

- [ ] **Step 1: Write the failing test (ported to Ktor's `MockEngine`)**

  `app/src/commonTest/kotlin/com/personal/twelveweek/media/ExerciseDbApiTest.kt`:

  ```kotlin
  package com.personal.twelveweek.media

  import io.ktor.client.HttpClient
  import io.ktor.client.engine.mock.MockEngine
  import io.ktor.client.engine.mock.respond
  import io.ktor.http.HttpHeaders
  import io.ktor.http.HttpStatusCode
  import io.ktor.http.headersOf
  import kotlinx.coroutines.test.runTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class ExerciseDbApiTest {

      private fun clientReturning(status: HttpStatusCode, body: String) = HttpClient(
          MockEngine { _ ->
              respond(
                  content = body,
                  status = status,
                  headers = headersOf(HttpHeaders.ContentType, "application/json")
              )
          }
      )

      @Test
      fun `success response parses to Success`() = runTest {
          val api = ExerciseDbApi(
              clientReturning(HttpStatusCode.OK, """{"success":true,"data":{"exerciseId":"e1","name":"Squat"}}""")
          )
          val result = api.fetchExercise("e1", "fake-key")
          assertTrue(result is ApiResult.Success)
          assertEquals("Squat", (result as ApiResult.Success).detail.name)
      }

      @Test
      fun `success=false envelope maps to NetworkError`() = runTest {
          val api = ExerciseDbApi(clientReturning(HttpStatusCode.OK, """{"success":false}"""))
          val result = api.fetchExercise("e1", "fake-key")
          assertEquals(ApiResult.NetworkError, result)
      }

      @Test
      fun `401 maps to Unauthorized`() = runTest {
          val api = ExerciseDbApi(clientReturning(HttpStatusCode.Unauthorized, ""))
          val result = api.fetchExercise("e1", "bad-key")
          assertEquals(ApiResult.Unauthorized, result)
      }

      @Test
      fun `500 maps to NetworkError`() = runTest {
          val api = ExerciseDbApi(clientReturning(HttpStatusCode.InternalServerError, ""))
          val result = api.fetchExercise("e1", "fake-key")
          assertEquals(ApiResult.NetworkError, result)
      }
  }
  ```

- [ ] **Step 2: Run it to verify it fails**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: FAIL — `ExerciseDbApi.kt` doesn't exist in `commonMain` yet (the old file at the androidMain-scanned path still has the old `OkHttpClient`-based constructor).

- [ ] **Step 3: Write the Ktor implementation**

  `app/src/commonMain/kotlin/com/personal/twelveweek/media/ExerciseDbApi.kt`:

  ```kotlin
  package com.personal.twelveweek.media

  import io.ktor.client.HttpClient
  import io.ktor.client.request.get
  import io.ktor.client.request.header
  import io.ktor.client.statement.bodyAsText
  import io.ktor.http.HttpStatusCode
  import io.ktor.http.isSuccess
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json
  import kotlinx.serialization.json.JsonObject

  sealed interface ApiResult {
      data class Success(val detail: ExerciseDbDetail) : ApiResult
      data object Unauthorized : ApiResult
      data object NetworkError : ApiResult
  }

  @Serializable
  private data class ExerciseDbEnvelope(
      val success: Boolean = false,
      val data: JsonObject? = null
  )

  private val envelopeJson = Json { ignoreUnknownKeys = true }

  /**
   * Thin client for the ExerciseDB v2 API (RapidAPI-gated). Host/header names
   * per docs.exercisedb.dev — confirm against live docs if RapidAPI changes them.
   */
  class ExerciseDbApi(
      private val client: HttpClient,
      private val baseUrl: String = "https://edb-with-videos-and-images-by-ascendapi.p.rapidapi.com"
  ) {
      suspend fun fetchExercise(exerciseId: String, apiKey: String): ApiResult =
          withContext(Dispatchers.Default) {
              runCatching {
                  val response = client.get("$baseUrl/api/v1/exercises/$exerciseId") {
                      header("X-RapidAPI-Key", apiKey)
                      header("X-RapidAPI-Host", "edb-with-videos-and-images-by-ascendapi.p.rapidapi.com")
                  }
                  when {
                      response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                          ApiResult.Unauthorized
                      !response.status.isSuccess() -> ApiResult.NetworkError
                      else -> {
                          val body = response.bodyAsText()
                          if (body.isBlank()) return@runCatching ApiResult.NetworkError
                          // Path + "data"-wrapped response shape confirmed against the live API
                          // (docs.exercisedb.dev's inline sample omits the {success,data} envelope).
                          val envelope = envelopeJson.decodeFromString(ExerciseDbEnvelope.serializer(), body)
                          val data = envelope.data
                          if (!envelope.success || data == null) {
                              ApiResult.NetworkError
                          } else {
                              ApiResult.Success(parseExerciseDbDetail(data.toString()))
                          }
                      }
                  }
              }.getOrDefault(ApiResult.NetworkError)
          }
  }
  ```

- [ ] **Step 4: Delete the old file and its old test**

  ```bash
  git rm app/src/main/java/com/personal/twelveweek/media/ExerciseDbApi.kt
  git rm app/src/test/java/com/personal/twelveweek/media/ExerciseDbApiTest.kt
  ```

- [ ] **Step 5: Run the new test to verify it passes**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: PASS, all 4 `ExerciseDbApiTest` cases green.

- [ ] **Step 6: Update `ExerciseMediaRepository.kt`'s `default()` factory**

  Read the current file first (`app/src/main/java/com/personal/twelveweek/media/ExerciseMediaRepository.kt`) — change ONLY the `companion object`'s `default()` function and its imports. Add these imports:

  ```kotlin
  import io.ktor.client.HttpClient
  import io.ktor.client.engine.okhttp.OkHttp
  ```

  Change `default()` from constructing `ExerciseDbApi(client)` with the raw `OkHttpClient` to wrapping it for Ktor, while leaving `WgerApi(client)` on the raw `OkHttpClient` for now (Task 4 migrates `WgerApi`):

  ```kotlin
  fun default(context: Context, keyManager: ApiKeyManager): ExerciseMediaRepository {
      val cacheDir = File(context.cacheDir, "exercise_media_http")
      val okHttpClient = OkHttpClient.Builder()
          .cache(Cache(cacheDir, 100L * 1024 * 1024))
          .build()
      val ktorClient = HttpClient(OkHttp) {
          engine { preconfigured = okHttpClient }
      }
      return ExerciseMediaRepository(keyManager, ExerciseDbApi(ktorClient), WgerApi(okHttpClient))
  }
  ```

  If `engine { preconfigured = okHttpClient }` doesn't match the Ktor OkHttp engine's actual current API (check `io.ktor.client.engine.okhttp.OkHttpConfig` if it fails to compile), find the correct current way to inject a preconfigured `OkHttpClient` into Ktor's OkHttp engine for the version resolved in Task 1, and use that instead — don't drop the disk cache to work around a compile error.

- [ ] **Step 7: Confirm Android still builds, tests pass, release still signs**

  Run: `./gradlew testDebugUnitTest :app:assembleDebug --console=plain` — expected PASS.
  Run: `./gradlew bundleRelease assembleRelease --console=plain` then `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk` — expected same `CN=TwelveWeek`.

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/commonMain/kotlin/com/personal/twelveweek/media/ExerciseDbApi.kt app/src/commonTest/kotlin/com/personal/twelveweek/media/ExerciseDbApiTest.kt app/src/main/java/com/personal/twelveweek/media/ExerciseDbApi.kt app/src/test/java/com/personal/twelveweek/media/ExerciseDbApiTest.kt app/src/main/java/com/personal/twelveweek/media/ExerciseMediaRepository.kt
  git commit -m "Move ExerciseDbApi.kt into commonMain, OkHttp -> Ktor"
  ```

---

### Task 4: Move `WgerApi.kt` into `commonMain`, OkHttp → Ktor

**Files:**
- Delete: `app/src/main/java/com/personal/twelveweek/media/WgerApi.kt`
- Create: `app/src/commonMain/kotlin/com/personal/twelveweek/media/WgerApi.kt`
- Create: `app/src/commonTest/kotlin/com/personal/twelveweek/media/WgerApiTest.kt` (new — no test existed for this file before)

**`ExerciseMediaRepository.kt` is explicitly OUT of scope for this task** — see the correction note after Task 3's fix round: Task 3's commit was re-scoped mid-plan to the true pre-Part-2 baseline (2-arg constructor, `get()` method — NOT the 3-arg/`WgerApi`/`getBundle()` shape that exists only in this repo's other unrelated uncommitted work). Wiring `WgerApi` into `ExerciseMediaRepository` as a committed dependency (constructor + `getBundle()`) is real, valuable work, but it's a *different*, self-contained unit from "migrate WgerApi.kt to commonMain" — it deserves its own task with its own brief and test-porting plan (there's already a matching uncommitted `ExerciseMediaRepositoryTest.kt` rewrite in the working tree covering multi-provider `getBundle()` behavior — a future task should port that properly, the same way this plan has ported every other test). Do not touch `ExerciseMediaRepository.kt` in this task.

**Interfaces:**
- Consumes: `ExerciseDbDetail` (Task 2).
- Produces: `class WgerApi(client: HttpClient, baseUrl: String = ...)` with `suspend fun fetchExercise(wgerId: String): ExerciseDbDetail?` and `fun parseWgerDetail(jsonText: String, wgerId: String): ExerciseDbDetail` — same method names, but the constructor's client parameter type changes from `OkHttpClient` to `HttpClient`, and `parseWgerDetail` gains a second parameter (`wgerId`) it didn't have before — see Step 3's rationale. Nothing in this plan consumes `WgerApi` yet (wiring it into `ExerciseMediaRepository` is deferred, see above), so this is a safe, self-contained, dependency-free move — no downstream commit in this plan references the new constructor shape.

- [ ] **Step 1: Write the failing test (new — none existed for this file before)**

  `app/src/commonTest/kotlin/com/personal/twelveweek/media/WgerApiTest.kt`:

  ```kotlin
  package com.personal.twelveweek.media

  import io.ktor.client.HttpClient
  import io.ktor.client.engine.mock.MockEngine
  import io.ktor.client.engine.mock.respond
  import io.ktor.http.HttpHeaders
  import io.ktor.http.HttpStatusCode
  import io.ktor.http.headersOf
  import kotlinx.coroutines.test.runTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull

  class WgerApiTest {

      private fun clientReturning(status: HttpStatusCode, body: String) = HttpClient(
          MockEngine { _ ->
              respond(
                  content = body,
                  status = status,
                  headers = headersOf(HttpHeaders.ContentType, "application/json")
              )
          }
      )

      private fun wgerBody(hasVideo: Boolean, hasImage: Boolean) = """
          {"translations":[{"language":2,"name":"X","description":"<p>d</p>"}],
           "images":[${'$'}{if (hasImage) """{"image":"https://wger.de/i.png"}""" else ""}],
           "videos":[${'$'}{if (hasVideo) """{"video":"https://wger.de/v.mp4"}""" else ""}]}
      """.trimIndent()

      @Test
      fun `parses video and image, strips html from description`() = runTest {
          val api = WgerApi(clientReturning(HttpStatusCode.OK, wgerBody(hasVideo = true, hasImage = true)))
          val detail = api.fetchExercise("615")
          assertEquals("615", detail?.exerciseId)
          assertEquals("X", detail?.name)
          assertEquals("https://wger.de/v.mp4", detail?.videoUrl)
          assertEquals("https://wger.de/i.png", detail?.imageUrl)
          assertEquals(listOf("d"), detail?.instructions)
      }

      @Test
      fun `text-only entry (no images or videos) still parses name and instructions`() = runTest {
          val api = WgerApi(clientReturning(HttpStatusCode.OK, wgerBody(hasVideo = false, hasImage = false)))
          val detail = api.fetchExercise("591")
          assertEquals("X", detail?.name)
          assertNull(detail?.videoUrl)
          assertNull(detail?.imageUrl)
      }

      @Test
      fun `non-200 response returns null`() = runTest {
          val api = WgerApi(clientReturning(HttpStatusCode.NotFound, ""))
          assertNull(api.fetchExercise("999"))
      }

      @Test
      fun `structurally missing translations returns null instead of a blank result`() = runTest {
          val api = WgerApi(clientReturning(HttpStatusCode.OK, """{"images":[],"videos":[]}"""))
          assertNull(api.fetchExercise("1"))
      }
  }
  ```

- [ ] **Step 2: Run it to verify it fails**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: FAIL — `WgerApi.kt` doesn't exist in `commonMain` yet.

- [ ] **Step 3: Write the Ktor + kotlinx.serialization implementation**

  `app/src/commonMain/kotlin/com/personal/twelveweek/media/WgerApi.kt`:

  ```kotlin
  package com.personal.twelveweek.media

  import io.ktor.client.HttpClient
  import io.ktor.client.request.get
  import io.ktor.client.statement.bodyAsText
  import io.ktor.http.isSuccess
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext
  import kotlinx.serialization.SerialName
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json

  /**
   * Thin client for wger.de's public exercise API (`/api/v2/exerciseinfo/`) —
   * free, no key, no account, no rate-limit gate for reasonable personal use.
   * Chosen as the *primary* media source (see [ExerciseMediaRepository]):
   * coverage of any single exercise's photo/video is thinner than ExerciseDB's
   * (~31% of wger's catalog has an image, ~5% has video), so a miss here just
   * falls through to the ExerciseDB fallback rather than showing nothing.
   */
  class WgerApi(
      private val client: HttpClient,
      private val baseUrl: String = "https://wger.de/api/v2"
  ) {
      suspend fun fetchExercise(wgerId: String): ExerciseDbDetail? =
          withContext(Dispatchers.Default) {
              runCatching {
                  val response = client.get("$baseUrl/exerciseinfo/$wgerId/?format=json")
                  if (!response.status.isSuccess()) return@runCatching null
                  val body = response.bodyAsText()
                  if (body.isBlank()) return@runCatching null
                  parseWgerDetail(body, wgerId)
              }.getOrNull()
          }
  }

  /** English translation's language id in wger's `/api/v2/language/` table. */
  private const val WGER_ENGLISH_LANGUAGE_ID = 2

  private val HTML_TAG = Regex("<[^>]+>")

  private fun stripHtml(html: String): String =
      HTML_TAG.replace(html, "")
          .replace("&nbsp;", " ")
          .replace("&amp;", "&")
          .replace("&#39;", "'")
          .trim()

  @Serializable
  private data class WgerTranslationDto(val language: Int = 0, val name: String = "", val description: String = "")

  @Serializable
  private data class WgerImageDto(val image: String = "")

  @Serializable
  private data class WgerVideoDto(val video: String = "")

  @Serializable
  private data class WgerMuscleDto(@SerialName("name_en") val nameEn: String = "")

  @Serializable
  private data class WgerEquipmentDto(val name: String = "")

  @Serializable
  private data class WgerExerciseInfoDto(
      // translations/images/videos have NO default: the original org.json code used
      // getJSONArray (required, throws if the key is structurally missing) for all three.
      // muscles/muscles_secondary/equipment used optJSONArray (genuinely optional) — keep
      // those three defaulted, do not add defaults to the first three.
      val translations: List<WgerTranslationDto>,
      val images: List<WgerImageDto>,
      val videos: List<WgerVideoDto>,
      val muscles: List<WgerMuscleDto> = emptyList(),
      @SerialName("muscles_secondary") val musclesSecondary: List<WgerMuscleDto> = emptyList(),
      val equipment: List<WgerEquipmentDto> = emptyList()
  )

  private val wgerJson = Json { ignoreUnknownKeys = true }

  /**
   * Parses one `exerciseinfo/{id}` response into the shared [ExerciseDbDetail]
   * shape (same fields ExerciseDbApi produces) so the repository and UI never
   * need to know which provider actually served a given exercise.
   *
   * [wgerId] is the id that was *requested* (from [WgerApi.fetchExercise]),
   * not re-parsed from the response body's own numeric "id" field — that
   * field is never read by any caller of this function today (checked:
   * [ExerciseMediaRepository] only reads videoUrl/imageUrl/instructions off
   * a wger-sourced [ExerciseDbDetail]), and org.json's permissive
   * number-to-string coercion on that field has no equivalent in
   * kotlinx.serialization without a custom serializer — using the already-
   * known request id sidesteps an untested type assumption entirely, with no
   * behavior change for any current caller.
   */
  fun parseWgerDetail(jsonText: String, wgerId: String): ExerciseDbDetail {
      val dto = wgerJson.decodeFromString(WgerExerciseInfoDto.serializer(), jsonText)

      val translation = dto.translations.firstOrNull { it.language == WGER_ENGLISH_LANGUAGE_ID }
      val name = translation?.name ?: ""
      val instruction = translation?.description?.takeIf { it.isNotBlank() }?.let { stripHtml(it) }

      val imageUrl = dto.images.firstOrNull()?.image?.takeIf { it.isNotBlank() }
      val videoUrl = dto.videos.firstOrNull()?.video?.takeIf { it.isNotBlank() }

      return ExerciseDbDetail(
          exerciseId = wgerId,
          name = name,
          videoUrl = videoUrl,
          imageUrl = imageUrl,
          instructions = instruction?.let { listOf(it) } ?: emptyList(),
          targetMuscles = dto.muscles.mapNotNull { it.nameEn.takeIf(String::isNotBlank) },
          secondaryMuscles = dto.musclesSecondary.mapNotNull { it.nameEn.takeIf(String::isNotBlank) },
          equipments = dto.equipment.map { it.name }
      )
  }
  ```

- [ ] **Step 4: Delete the old file** (it never had a dedicated test file, so there's nothing to delete on that side)

  ```bash
  git rm app/src/main/java/com/personal/twelveweek/media/WgerApi.kt
  ```

- [ ] **Step 5: Run the new test to verify it passes**

  Run: `./gradlew :app:allTests --console=plain`
  Expected: PASS, all 4 new `WgerApiTest` cases green, plus everything from Tasks 2-3 still green.

- [ ] **Step 6: Full regression — Android build, tests, release signing**

  Run: `./gradlew testDebugUnitTest :app:allTests :app:assembleDebug --console=plain` — expected PASS, all tests green (Parts 1+2 combined).
  Run: `./gradlew bundleRelease assembleRelease --console=plain` then `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk` — expected same `CN=TwelveWeek`.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/commonMain/kotlin/com/personal/twelveweek/media/WgerApi.kt app/src/commonTest/kotlin/com/personal/twelveweek/media/WgerApiTest.kt app/src/main/java/com/personal/twelveweek/media/WgerApi.kt
  git commit -m "Move WgerApi.kt into commonMain, OkHttp -> Ktor"
  ```

  Note: `ExerciseMediaRepository.kt` is deliberately NOT in this commit — verify `git status --porcelain` still shows it as modified-not-staged (its current working-tree content, untouched by this task) before committing, exactly as `ConnectMediaScreen.kt` and `ExerciseMediaRepositoryTest.kt` already sit uncommitted.

---

## What's deliberately not in this part

- `ProgramSyncRepository.kt` and `ExerciseMediaRepository.kt` stay in `androidMain`, untouched beyond Task 3's narrow `default()`-for-`ExerciseDbApi` edit — both still depend on Android-only storage (`ProgramLibrary`, `ApiKeyManager`), and `ExerciseMediaRepository`'s `WgerApi` wiring (constructor + `getBundle()`, porting the matching uncommitted `ExerciseMediaRepositoryTest.kt` rewrite) is now explicitly its own future task, not folded into Part 2 or Part 3 by default — scope it deliberately when picked up, the same rigor as every task in this plan.
- `ProgramLibrary.kt`, `ApiKeyManager.kt`, `ProgressStore` (`Progress.kt`) — untouched, Part 3's subject.
- No Compose screens move in this part — that's a later part (spec step 5), after storage exists for screens to actually use.
