# Guided Workout Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This run:** executed inline, same session, by the agent that already holds full
> codebase context from brainstorming — no fresh-subagent handoff needed, so task
> steps are stated at deliverable granularity (file + code + test) rather than the
> full write-test/run-fail/implement/run-pass/commit micro-choreography.

**Goal:** Add in-app playable exercise video/instructions (via ExerciseDB) and a
full-screen "guided workout" session mode, per
`docs/superpowers/specs/2026-08-12-guided-workout-media-design.md`.

**Architecture:** New `media/` package (models, catalog, HTTP client, repository)
feeding two new UI surfaces — `GuidedSessionScreen` (new full-screen mode) and an
upgraded `ExerciseDetailDialog` (existing). A new `security/ApiKeyManager` stores
the user's own RapidAPI key. Everything degrades to the existing external-search
buttons when there's no key, no network, or no curated match — that fallback is
real shipped behavior, not a stub.

**Tech Stack:** OkHttp (manual `org.json` parsing — no Retrofit/Moshi/kapt, keeps
the build toolchain simple), androidx.media3 ExoPlayer (video), Coil (images),
androidx.security EncryptedSharedPreferences (key storage), JUnit (new test
source set — project currently has none).

## Global Constraints

- minSdk 26 / compileSdk 34 / targetSdk 34, Kotlin 1.9.24, Compose BOM 2024.06.00
  (from `app/build.gradle.kts`) — every new dependency must be compatible.
- No ViewModel/Hilt/DI framework — existing code uses plain classes constructed
  via `remember { }` inside composables (see `ProgressStore` usage in
  `AppRoot`). New code follows the same pattern.
- All new UI is dark-theme-first, matching `AppTheme` (Material3 dynamic/dark
  color scheme already in place).
- Existing behavior must not regress: checklist tick-off, local asset override
  (`assets/exercises/<slug>.*`), external search buttons — all unchanged as the
  final fallback layer.
- No automated account creation / credential harvesting for RapidAPI (explicitly
  rejected in the spec) — the connect flow is: open browser → user signs up →
  user pastes key.

---

## File Structure

```
app/src/main/java/com/personal/twelveweek/
  media/
    GuidedSteps.kt            (new, pure logic: flatten Workout → ordered steps)
    ExerciseDbModels.kt       (new, ExerciseDbDetail + pure JSON parser)
    ExerciseDbApi.kt          (new, OkHttp GET wrapper → sealed ApiResult)
    ExerciseMediaCatalog.kt   (new, slug → exerciseId map, hand-curated)
    ExerciseMediaRepository.kt(new, key/catalog/cache decision logic)
    ExerciseVideoPlayer.kt    (new, Compose ExoPlayer wrapper, muted/loop/autoplay)
  security/
    ApiKeyManager.kt          (new, EncryptedSharedPreferences wrapper)
  ui/
    ConnectMediaScreen.kt     (new, RapidAPI key connect flow)
    GuidedSessionScreen.kt    (new, full-screen guided workout mode)
  MainActivity.kt             (modify: Screen sealed interface, AppRoot routing,
                                WorkoutScreen "Start Workout" button,
                                ExerciseDetailDialog media upgrade)
app/src/test/java/com/personal/twelveweek/
  media/
    GuidedStepsTest.kt         (new)
    ExerciseDbModelsTest.kt    (new)
    ExerciseDbApiTest.kt       (new, MockWebServer)
    ExerciseMediaRepositoryTest.kt (new, fake ExerciseDbApi)
    ExerciseMediaCatalogTest.kt (new, completeness check)
app/build.gradle.kts           (modify: new dependencies)
```

---

### Task 1: Guided step sequencing (pure logic)

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/GuidedSteps.kt`
- Test: `app/src/test/java/com/personal/twelveweek/media/GuidedStepsTest.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation("junit:junit:4.13.2")`)

**Interfaces:**
- Consumes: `Workout`, `Section`, `Exercise` from `Program.kt` (existing, no changes).
- Produces: `data class GuidedStep(val sectionIndex: Int, val sectionTitle: String, val itemIndex: Int, val exercise: Exercise, val key: String)`,
  `fun Workout.guidedSteps(): List<GuidedStep>`,
  `fun List<GuidedStep>.firstIncompleteIndex(isDone: (String) -> Boolean): Int`.
  Used by Task 9 (`GuidedSessionScreen`).

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Workout

/** One step in a linear guided-workout sequence. */
data class GuidedStep(
    val sectionIndex: Int,
    val sectionTitle: String,
    val itemIndex: Int,
    val exercise: Exercise,
    val key: String
)

/** Flattens Warm up → Round 1..N → Cool down into one ordered list. */
fun Workout.guidedSteps(): List<GuidedStep> = buildList {
    sections.forEachIndexed { s, section ->
        section.exercises.forEachIndexed { i, exercise ->
            add(GuidedStep(s, section.title, i, exercise, keyFor(s, i)))
        }
    }
}

/** Index of the first not-yet-done step, or 0 if everything is done. */
fun List<GuidedStep>.firstIncompleteIndex(isDone: (String) -> Boolean): Int {
    val idx = indexOfFirst { !isDone(it.key) }
    return if (idx == -1) 0 else idx
}
```

- [ ] **Step 2: Test**

```kotlin
package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.Section
import com.personal.twelveweek.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedStepsTest {

    private fun workout() = Workout(
        week = 1, index = 1,
        sections = listOf(
            Section("Warm up", listOf(Exercise.parse("30 Jumping Jacks"))),
            Section("Round 1", listOf(Exercise.parse("20 Squats"), Exercise.parse("45s Wall Sit"))),
            Section("Cool Down", listOf(Exercise.parse("30s Cat Cow")))
        )
    )

    @Test
    fun `flattens all sections in order`() {
        val steps = workout().guidedSteps()
        assertEquals(4, steps.size)
        assertEquals("Warm up", steps[0].sectionTitle)
        assertEquals("Round 1", steps[1].sectionTitle)
        assertEquals("Squats", steps[1].exercise.name)
        assertEquals("Cool Down", steps[3].sectionTitle)
    }

    @Test
    fun `resumes at first incomplete step`() {
        val steps = workout().guidedSteps()
        val done = setOf(steps[0].key, steps[1].key)
        val idx = steps.firstIncompleteIndex { it in done }
        assertEquals(2, idx)
    }

    @Test
    fun `resumes at 0 when everything is done`() {
        val steps = workout().guidedSteps()
        val idx = steps.firstIncompleteIndex { true }
        assertEquals(0, idx)
    }
}
```

- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.personal.twelveweek.media.GuidedStepsTest"` — expect 3 passed.

---

### Task 2: `ApiKeyManager`

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/security/ApiKeyManager.kt`
- Modify: `app/build.gradle.kts` (add `implementation("androidx.security:security-crypto:1.1.0-alpha06")`)

**Interfaces:**
- Produces: `class ApiKeyManager(context: Context) { fun get(): String?; fun set(key: String); fun clear() }`.
  Consumed by Task 4 (`ExerciseDbApi` caller) and Task 5 (`ExerciseMediaRepository`), Task 8 (`ConnectMediaScreen`).

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's own RapidAPI key for ExerciseDB. Nothing else lives here —
 * this app has no accounts, no other settings.
 */
class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "exercise_media_key",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun set(key: String) {
        prefs.edit().putString(KEY, key.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "rapidapi_key"
    }
}
```

Not unit tested (thin wrapper over Android Keystore-backed APIs — no Robolectric
in this project, and adding it just for this wrapper is disproportionate).
Verified manually on-device in Task 13.

---

### Task 3: ExerciseDB model + JSON parser

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/ExerciseDbModels.kt`
- Test: `app/src/test/java/com/personal/twelveweek/media/ExerciseDbModelsTest.kt`

**Interfaces:**
- Produces: `data class ExerciseDbDetail(val exerciseId: String, val name: String, val videoUrl: String?, val imageUrl: String?, val instructions: List<String>, val targetMuscles: List<String>, val secondaryMuscles: List<String>, val equipments: List<String>)`,
  `fun parseExerciseDbDetail(json: String): ExerciseDbDetail`.
  Consumed by Task 4 (`ExerciseDbApi`).

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.media

import org.json.JSONObject

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

/** Parses one ExerciseDB v2 exercise object (see docs.exercisedb.dev schema). */
fun parseExerciseDbDetail(json: String): ExerciseDbDetail {
    val o = JSONObject(json)
    fun strings(field: String): List<String> {
        if (!o.has(field)) return emptyList()
        val arr = o.getJSONArray(field)
        return (0 until arr.length()).map { arr.getString(it) }
    }
    return ExerciseDbDetail(
        exerciseId = o.getString("exerciseId"),
        name = o.getString("name"),
        videoUrl = o.optString("videoUrl").takeIf { it.isNotBlank() },
        imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
        instructions = strings("instructions"),
        targetMuscles = strings("targetMuscles"),
        secondaryMuscles = strings("secondaryMuscles"),
        equipments = strings("equipments")
    )
}
```

- [ ] **Step 2: Test** (fixture is the real sample object from docs.exercisedb.dev)

```kotlin
package com.personal.twelveweek.media

import org.junit.Assert.assertEquals
import org.junit.Test

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

- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.personal.twelveweek.media.ExerciseDbModelsTest"` — expect 2 passed.

---

### Task 4: `ExerciseDbApi` (OkHttp client)

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/ExerciseDbApi.kt`
- Test: `app/src/test/java/com/personal/twelveweek/media/ExerciseDbApiTest.kt`
- Modify: `app/build.gradle.kts`:
  ```kotlin
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
  ```

**Interfaces:**
- Consumes: `parseExerciseDbDetail` (Task 3).
- Produces: `sealed interface ApiResult { data class Success(val detail: ExerciseDbDetail): ApiResult; object Unauthorized: ApiResult; object NetworkError: ApiResult }`,
  `class ExerciseDbApi(private val client: OkHttpClient, private val baseUrl: String = "https://edb-with-videos-and-images-by-ascendapi.p.rapidapi.com") { suspend fun fetchExercise(exerciseId: String, apiKey: String): ApiResult }`.
  Consumed by Task 5 (`ExerciseMediaRepository`).

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface ApiResult {
    data class Success(val detail: ExerciseDbDetail) : ApiResult
    data object Unauthorized : ApiResult
    data object NetworkError : ApiResult
}

/**
 * Thin client for the ExerciseDB v2 API (RapidAPI-gated). Host/header names
 * per docs.exercisedb.dev — confirm against live docs if RapidAPI changes them.
 */
class ExerciseDbApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://edb-with-videos-and-images-by-ascendapi.p.rapidapi.com"
) {
    suspend fun fetchExercise(exerciseId: String, apiKey: String): ApiResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/exercises/exercise/$exerciseId")
                .header("X-RapidAPI-Key", apiKey)
                .header("X-RapidAPI-Host", "edb-with-videos-and-images-by-ascendapi.p.rapidapi.com")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 -> ApiResult.Unauthorized
                        !response.isSuccessful -> ApiResult.NetworkError
                        else -> {
                            val body = response.body?.string()
                            if (body.isNullOrBlank()) ApiResult.NetworkError
                            else ApiResult.Success(parseExerciseDbDetail(body))
                        }
                    }
                }
            } catch (e: IOException) {
                ApiResult.NetworkError
            } catch (e: org.json.JSONException) {
                ApiResult.NetworkError
            }
        }
}
```

- [ ] **Step 2: Test** (MockWebServer — no real network)

```kotlin
package com.personal.twelveweek.media

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseDbApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ExerciseDbApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ExerciseDbApi(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `success response parses to Success`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"exerciseId":"e1","name":"Squat"}""")
        )
        val result = api.fetchExercise("e1", "fake-key")
        assertTrue(result is ApiResult.Success)
        assertEquals("Squat", (result as ApiResult.Success).detail.name)
    }

    @Test
    fun `401 maps to Unauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.fetchExercise("e1", "bad-key")
        assertEquals(ApiResult.Unauthorized, result)
    }

    @Test
    fun `500 maps to NetworkError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = api.fetchExercise("e1", "fake-key")
        assertEquals(ApiResult.NetworkError, result)
    }
}
```

- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.personal.twelveweek.media.ExerciseDbApiTest"` — expect 3 passed.

---

### Task 5: `ExerciseMediaCatalog` (curated mapping)

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/ExerciseMediaCatalog.kt`
- Test: `app/src/test/java/com/personal/twelveweek/media/ExerciseMediaCatalogTest.kt`

**Interfaces:**
- Produces: `object ExerciseMediaCatalog { val exerciseIds: Map<String, String> }` keyed
  by `Exercise.slug`. Consumed by Task 6 (`ExerciseMediaRepository`).

- [ ] **Step 1: Implement (real state today — populated in Task 12)**

```kotlin
package com.personal.twelveweek.media

/**
 * Maps this program's exercise slugs to a curated ExerciseDB `exerciseId`.
 * Curated by hand against the live API, NOT a runtime fuzzy search — an
 * exercise with no entry here always falls back to the external search
 * buttons, on purpose (never a forced bad match).
 *
 * Populated in the "curate exercise media" pass, which requires a live
 * RapidAPI key (see ConnectMediaScreen) — that pass is tracked as its own
 * task (Task 12 of the implementation plan) because it needs the user's key
 * to run at all. Until then this map is empty and every exercise correctly
 * shows the existing fallback UI — real, working, shipped behavior.
 */
object ExerciseMediaCatalog {
    val exerciseIds: Map<String, String> = emptyMap()
}
```

- [ ] **Step 2: Test** (guards against silent gaps as `ProgramData` changes —
  passes today since it only checks internal consistency, not coverage %)

```kotlin
package com.personal.twelveweek.media

import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMediaCatalogTest {

    @Test
    fun `every mapped id is non-blank and keys are non-blank`() {
        ExerciseMediaCatalog.exerciseIds.forEach { (slug, id) ->
            assertTrue("slug blank", slug.isNotBlank())
            assertTrue("id blank for $slug", id.isNotBlank())
        }
    }
}
```

- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.personal.twelveweek.media.ExerciseMediaCatalogTest"` — expect 1 passed.

---

### Task 6: `ExerciseMediaRepository`

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/ExerciseMediaRepository.kt`
- Test: `app/src/test/java/com/personal/twelveweek/media/ExerciseMediaRepositoryTest.kt`

**Interfaces:**
- Consumes: `ApiKeyManager.get()/clear()` (Task 2), `ExerciseDbApi.fetchExercise` (Task 4, via an
  interface so it's fakeable), `ExerciseMediaCatalog.exerciseIds` (Task 5), `Exercise.slug` (existing).
- Produces: `class ExerciseMediaRepository(private val keyManager: ApiKeyManager, private val api: ExerciseDbApi) { suspend fun get(exercise: Exercise): ExerciseDbDetail? }`.
  Consumed by Task 9 (`GuidedSessionScreen`) and Task 11 (`ExerciseDetailDialog`).

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.media

import com.personal.twelveweek.Exercise
import com.personal.twelveweek.security.ApiKeyManager

class ExerciseMediaRepository(
    private val keyManager: ApiKeyManager,
    private val api: ExerciseDbApi
) {
    /**
     * Returns curated media/instructions for [exercise], or null when there's
     * no stored key, no catalog match, or the fetch failed — callers render
     * the existing fallback UI in every null case, never crash.
     */
    suspend fun get(exercise: Exercise): ExerciseDbDetail? {
        val apiKey = keyManager.get() ?: return null
        val exerciseId = ExerciseMediaCatalog.exerciseIds[exercise.slug] ?: return null
        return when (val result = api.fetchExercise(exerciseId, apiKey)) {
            is ApiResult.Success -> result.detail
            ApiResult.Unauthorized -> {
                keyManager.clear()
                null
            }
            ApiResult.NetworkError -> null
        }
    }
}
```

Caching note: `ExerciseDbApi`'s `OkHttpClient` (constructed once in Task 9/11's
composition root, see Task 9 Step 1) is configured with a 100MB disk `Cache` and
ExerciseDB's CDN responses are cacheable GETs — OkHttp's standard HTTP caching
handles "instant on repeat view" without any extra code here.

- [ ] **Step 2: Test** (fake `ExerciseDbApi` via a tiny seam — see note)

Since `ExerciseDbApi` is a concrete class wrapping `OkHttpClient`, the
repository test uses a **fake key manager + a real `ExerciseDbApi` pointed at a
MockWebServer** (same technique as Task 4), which also re-validates the
repository/api integration end to end:

```kotlin
package com.personal.twelveweek.media

import android.content.Context
import com.personal.twelveweek.Exercise
import com.personal.twelveweek.security.ApiKeyManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExerciseMediaRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ExerciseDbApi
    private val squat = Exercise.parse("20 Squats") // slug: "squats"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ExerciseDbApi(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun fakeKeyManager(key: String?): ApiKeyManager {
        val m = mockk<ApiKeyManager>(relaxed = true)
        every { m.get() } returns key
        return m
    }

    @Test
    fun `no stored key returns null without a network call`() = runBlocking {
        val repo = ExerciseMediaRepository(fakeKeyManager(null), api)
        assertNull(repo.get(squat))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `exercise not in catalog returns null without a network call`() = runBlocking {
        val repo = ExerciseMediaRepository(fakeKeyManager("key"), api)
        // "squats" is not curated yet (Task 5 ships an empty catalog)
        assertNull(repo.get(squat))
        assertEquals(0, server.requestCount)
    }
}
```

> Note: this test file uses `mockk` for `ApiKeyManager`, since it wraps Android
> Keystore APIs that don't run on the JVM unit-test target. Add
> `testImplementation("io.mockk:mockk:1.13.12")` to `app/build.gradle.kts`.
> The catalog-hit path (curated id present) is covered implicitly once Task 12
> populates real entries — re-run this suite after Task 12 and add one more
> case pointing at a real curated slug if desired.

- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.personal.twelveweek.media.ExerciseMediaRepositoryTest"` — expect 2 passed.

---

### Task 7: `ExerciseVideoPlayer` (ExoPlayer wrapper)

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/media/ExerciseVideoPlayer.kt`
- Modify: `app/build.gradle.kts`:
  ```kotlin
  implementation("androidx.media3:media3-exoplayer:1.4.1")
  implementation("androidx.media3:media3-ui:1.4.1")
  implementation("io.coil-kt:coil-compose:2.6.0")
  ```

**Interfaces:**
- Produces: `@Composable fun ExerciseVideoPlayer(videoUrl: String?, imageUrl: String?, contentDescription: String, modifier: Modifier = Modifier)`
  — plays `videoUrl` muted/looping/autoplay via ExoPlayer if present, else shows
  `imageUrl` via Coil `AsyncImage`, else nothing (caller handles the null/null
  fallback state). Consumed by Task 9 and Task 11.

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * Ambient exercise demo: plays a muted, looping video if [videoUrl] is given,
 * otherwise falls back to a static [imageUrl]. Shows nothing if both are null
 * — callers render their own fallback UI in that case.
 */
@Composable
fun ExerciseVideoPlayer(
    videoUrl: String?,
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    when {
        videoUrl != null -> {
            val context = LocalContext.current
            val player = remember(videoUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    volume = 0f
                    repeatMode = ExoPlayer.REPEAT_MODE_ALL
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        this.player = player
                    }
                }
            )
        }
        imageUrl != null -> AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    }
}
```

Not unit tested — pure Compose/ExoPlayer view glue, verified visually on-device
in Task 13 (this is exactly the kind of thing instrumented UI tests would cover
in a larger project; skipped here per the spec's YAGNI call on test scope).

---

### Task 8: `ConnectMediaScreen`

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/ui/ConnectMediaScreen.kt`

**Interfaces:**
- Consumes: `ApiKeyManager` (Task 2), `ExerciseMediaRepository`/`ExerciseDbApi` for
  validation (Task 4/6), existing `openUrl(context, url)` helper (`MainActivity.kt`, unchanged).
- Produces: `@Composable fun ConnectMediaScreen(keyManager: ApiKeyManager, onConnected: () -> Unit, onDismiss: () -> Unit)`.
  Consumed by Task 9 and Task 11 (shown when `ExerciseMediaRepository.get()` would
  short-circuit to null due to a missing key).

- [ ] **Step 1: Implement**

```kotlin
package com.personal.twelveweek.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.media.ApiResult
import com.personal.twelveweek.media.ExerciseDbApi
import com.personal.twelveweek.security.ApiKeyManager
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private const val SIGNUP_URL =
    "https://rapidapi.com/auth/login?referral=%2Fascendapi%2Fapi%2Fedb-with-videos-and-images-by-ascendapi%2Fpricing"

/** exerciseId used purely to validate a freshly-pasted key. Any valid, stable id works. */
private const val VALIDATION_EXERCISE_ID = "exr_41n2hxnFMotsXTj3" // Bench Press

@Composable
fun ConnectMediaScreen(
    keyManager: ApiKeyManager,
    onConnected: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val api = remember { ExerciseDbApi(OkHttpClient()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect exercise videos") },
        text = {
            Column {
                Text(
                    "Exercise videos come from ExerciseDB. Sign up for a free key " +
                        "(about 30 seconds), then paste it below."
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SIGNUP_URL)))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Get free API key") }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; error = null },
                    label = { Text("RapidAPI key") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = keyInput.isNotBlank() && !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        when (api.fetchExercise(VALIDATION_EXERCISE_ID, keyInput.trim())) {
                            is ApiResult.Success -> {
                                keyManager.set(keyInput.trim())
                                checking = false
                                onConnected()
                            }
                            ApiResult.Unauthorized -> {
                                checking = false
                                error = "That key was rejected. Check it and try again."
                            }
                            ApiResult.NetworkError -> {
                                checking = false
                                error = "Couldn't reach ExerciseDB. Check your connection."
                            }
                        }
                    }
                }
            ) { Text(if (checking) "Checking…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
```

Not unit tested — UI/dialog glue over already-tested `ExerciseDbApi`/`ApiKeyManager`;
verified visually on-device in Task 13 (paste an intentionally wrong key to see
the rejected-key path, then a real one once available).

---

### Task 9: `GuidedSessionScreen`

**Files:**
- Create: `app/src/main/java/com/personal/twelveweek/ui/GuidedSessionScreen.kt`

**Interfaces:**
- Consumes: `Workout.guidedSteps()`/`firstIncompleteIndex` (Task 1), `ExerciseMediaRepository`
  (Task 6), `ExerciseVideoPlayer` (Task 7), `ConnectMediaScreen` (Task 8),
  existing `ProgressStore` (`isDone`, `setDone`), existing `buzz(context)` and
  `formatClock(Int)` (`MainActivity.kt`, made non-private — see Step 1 note).
- Produces: `@Composable fun GuidedSessionScreen(workout: Workout, progress: ProgressStore, onExit: () -> Unit)`.
  Consumed by Task 10 (`AppRoot` routing).

- [ ] **Step 1: Make shared helpers accessible**

In `MainActivity.kt`, change `private fun buzz(context: Context)` and
`private fun formatClock(seconds: Int): String` to internal visibility (drop
`private`) so `GuidedSessionScreen` can reuse them instead of duplicating the
countdown/vibrate logic:

```kotlin
// was: private fun buzz(context: Context) {
fun buzz(context: Context) {
```
```kotlin
// was: private fun formatClock(seconds: Int): String {
fun formatClock(seconds: Int): String {
```

- [ ] **Step 2: Implement** (immersive/cinematic layout — approved mockup "C")

```kotlin
package com.personal.twelveweek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.twelveweek.Exercise
import com.personal.twelveweek.ProgressStore
import com.personal.twelveweek.Workout
import com.personal.twelveweek.buzz
import com.personal.twelveweek.formatClock
import com.personal.twelveweek.media.ExerciseDbDetail
import com.personal.twelveweek.media.ExerciseMediaRepository
import com.personal.twelveweek.media.ExerciseVideoPlayer
import com.personal.twelveweek.media.guidedSteps
import com.personal.twelveweek.media.firstIncompleteIndex
import com.personal.twelveweek.security.ApiKeyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GuidedSessionScreen(
    workout: Workout,
    progress: ProgressStore,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val steps = remember(workout) { workout.guidedSteps() }
    val keyManager = remember { ApiKeyManager(context) }
    val repository = remember { ExerciseMediaRepository.default(context, keyManager) }

    var index by remember(workout) {
        mutableStateOf(steps.firstIncompleteIndex { progress.isDone(it) })
    }
    val step = steps[index]

    var media by remember(step.key) { mutableStateOf<ExerciseDbDetail?>(null) }
    var showConnect by remember { mutableStateOf(false) }
    LaunchedEffect(step.key) {
        media = null
        if (!step.exercise.isRest) media = repository.get(step.exercise)
    }

    // timed exercises: inline countdown that auto-marks done and advances
    var remaining by remember(step.key) { mutableStateOf(step.exercise.seconds ?: 0) }
    LaunchedEffect(step.key) {
        val total = step.exercise.seconds ?: return@LaunchedEffect
        remaining = total
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        buzz(context)
        progress.setDone(step.key, true)
        delay(600)
        if (index < steps.lastIndex) index += 1
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ExerciseVideoPlayer(
            videoUrl = media?.videoUrl,
            imageUrl = media?.imageUrl,
            contentDescription = step.exercise.name,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        0.88f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )

        IconButton(onClick = onExit, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit workout", tint = Color.White)
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "${step.sectionTitle.uppercase()} · EXERCISE ${index + 1}/${steps.size}",
                color = Color(0xFF8FB8E8),
                style = MaterialTheme.typography.labelSmall
            )
            Text(step.exercise.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    step.exercise.seconds != null -> "${formatClock(remaining)} remaining"
                    step.exercise.reps != null -> "${step.exercise.reps} reps"
                    else -> ""
                },
                color = Color(0xFFC9D1D9),
                style = MaterialTheme.typography.bodyMedium
            )
            if (media == null && !step.exercise.isRest) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No demo video for this one — use ▶ from the checklist for search links.",
                    color = Color(0xFF8B949E),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    enabled = index > 0,
                    onClick = { index -= 1 }
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = Color.White) }

                if (step.exercise.reps != null || step.exercise.isRest) {
                    IconButton(
                        onClick = {
                            progress.setDone(step.key, true)
                            if (index < steps.lastIndex) index += 1 else onExit()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) { Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = "Mark done", tint = Color.White) }
                }

                IconButton(
                    enabled = index < steps.lastIndex,
                    onClick = { index += 1 }
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = Color.White) }
            }
        }
    }
}
```

> Requires `ExerciseMediaRepository.default(context, keyManager)` — a small
> factory added in Task 6 alongside the primary constructor, so call sites
> don't hand-wire `OkHttpClient`/cache setup:
> ```kotlin
> companion object {
>     fun default(context: Context, keyManager: ApiKeyManager): ExerciseMediaRepository {
>         val cacheDir = java.io.File(context.cacheDir, "exercise_media_http")
>         val client = OkHttpClient.Builder()
>             .cache(okhttp3.Cache(cacheDir, 100L * 1024 * 1024))
>             .build()
>         return ExerciseMediaRepository(keyManager, ExerciseDbApi(client))
>     }
> }
> ```
> (Add this to `ExerciseMediaRepository.kt` from Task 6 when implementing this task.)

Verified on-device in Task 13 (this is UI composition over already-unit-tested
logic — no new pure logic here to unit test).

---

### Task 10: Wire "Start Workout" + navigation

**Files:**
- Modify: `app/src/main/java/com/personal/twelveweek/MainActivity.kt`

**Interfaces:**
- Consumes: `GuidedSessionScreen` (Task 9).

- [ ] **Step 1: Add a screen state**

```kotlin
// in `private sealed interface Screen { ... }`, add:
data class GuidedSession(val week: Int, val workout: Int) : Screen
```

- [ ] **Step 2: Route it in `AppRoot`**

```kotlin
// in AppRoot's `when (val current = screen) { ... }`, add a branch:
is Screen.GuidedSession -> {
    BackHandler { screen = Screen.WorkoutDetail(current.week, current.workout) }
    com.personal.twelveweek.ui.GuidedSessionScreen(
        workout = ProgramData.workout(current.week, current.workout),
        progress = progress,
        onExit = { screen = Screen.WorkoutDetail(current.week, current.workout) }
    )
}
```

- [ ] **Step 3: Add the button in `WorkoutScreen`**

In `WorkoutScreen`'s `TopAppBar { actions = { ... } }` (after the existing
Reset/Mark-all-done icons), add a text/icon button:

```kotlin
IconButton(onClick = { screen = Screen.GuidedSession(workout.week, workout.index) }) {
    Icon(Icons.Filled.PlayCircle, contentDescription = "Start guided workout")
}
```

`WorkoutScreen` doesn't currently have access to `screen`/setter — add an
`onStartGuided: () -> Unit` parameter to `WorkoutScreen`, pass
`{ screen = Screen.GuidedSession(workout.week, workout.index) }` from `AppRoot`'s
call site, and use `onStartGuided()` in the button instead of touching `screen`
directly (keeps `WorkoutScreen` free of navigation-state coupling, matching how
`onBack`/`onOpenWorkout` are already threaded through as callbacks elsewhere in
this file).

---

### Task 11: Upgrade `ExerciseDetailDialog`

**Files:**
- Modify: `app/src/main/java/com/personal/twelveweek/MainActivity.kt` (`ExerciseDetailDialog`, ~L461-540)

**Interfaces:**
- Consumes: `ExerciseMediaRepository` (Task 6), `ExerciseVideoPlayer` (Task 7).

- [ ] **Step 1: Fetch curated media alongside the existing local asset**

Inside `ExerciseDetailDialog`, next to the existing
`val localImage = rememberAssetImage(exercise.slug)`, add:

```kotlin
val keyManager = remember { com.personal.twelveweek.security.ApiKeyManager(context) }
val repository = remember { com.personal.twelveweek.media.ExerciseMediaRepository.default(context, keyManager) }
var curated by remember(exercise) { mutableStateOf<com.personal.twelveweek.media.ExerciseDbDetail?>(null) }
LaunchedEffect(exercise) { curated = repository.get(exercise) }
```

- [ ] **Step 2: Priority order in the dialog body**

Replace the existing `if (localImage != null) { ... Image(...) }` block with:

```kotlin
when {
    localImage != null -> {
        Spacer(Modifier.height(12.dp))
        Image(
            bitmap = localImage,
            contentDescription = exercise.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
    }
    curated != null -> {
        Spacer(Modifier.height(12.dp))
        com.personal.twelveweek.media.ExerciseVideoPlayer(
            videoUrl = curated?.videoUrl,
            imageUrl = curated?.imageUrl,
            contentDescription = exercise.name,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
        curated?.instructions?.takeIf { it.isNotEmpty() }?.let { steps ->
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
```

(local asset keeps priority — unchanged existing behavior; the external
search/timer buttons below this block are untouched.)

---

### Task 12: Curate exercise media mapping — requires a live RapidAPI key

**Files:**
- Modify: `app/src/main/java/com/personal/twelveweek/media/ExerciseMediaCatalog.kt`

**Blocking dependency:** needs a working RapidAPI key for
`edb-with-videos-and-images-by-ascendapi`, subscribed via
https://rapidapi.com/auth/login?referral=%2Fascendapi%2Fapi%2Fedb-with-videos-and-images-by-ascendapi%2Fpricing
(free tier is watermarked media — acceptable for now, upgradeable later).

- [ ] **Step 1:** Search ExerciseDB for each of the program's ~65 unique exercise
  names (extracted from `ProgramData.kt`: Jumping Jacks, Squats, Push-ups, Wall
  Sit, Mountain Climbers, 4-Count/6-Count/Flat Out Burpees, High/Low Plank and
  its ~8 named variants, Jump Squats, Up Downs, etc. — full list derivable via
  `grep -oE` over `reps(`/`secs(`/`row(` calls in `ProgramData.kt`). For each,
  pick the single best real match by name/movement (not a guess) and record
  `slug to exerciseId` in `ExerciseMediaCatalog.exerciseIds`.
- [ ] **Step 2:** Exercises with no good match are left out of the map on
  purpose (per design — no forced bad matches); update the KDoc comment with
  the resulting curated count, e.g. "38 of 65 curated as of 2026-08-12".
- [ ] **Step 3:** Re-run `ExerciseMediaCatalogTest` and spot-check a few
  mapped ids resolve via `ExerciseDbApi` against the live API.

---

### Task 13: Full build + device verification

**Files:** none (verification only)

- [ ] **Step 1:** `./gradlew testDebugUnitTest` — all unit tests from Tasks 1–6 pass.
- [ ] **Step 2:** `./gradlew installDebug` (same device/setup used earlier this
  session) — build succeeds, installs.
- [ ] **Step 3:** Launch app, open a workout, tap "Start Workout" — confirm the
  guided session renders (fallback state if Task 12 hasn't run yet), Prev/Next
  and Done work, ticking reflects back on the checklist screen after exiting.
- [ ] **Step 4:** Trigger `ConnectMediaScreen` (no key yet), confirm the signup
  link opens, paste a real key once available, confirm Save validates and
  persists it, confirm media now loads in both the guided session and the
  detail sheet (▶ icon) for a curated exercise.
- [ ] **Step 5:** Screenshot each state (fallback, guided session with video,
  connect screen) via `adb shell screencap`, same method used earlier this
  session, and visually confirm — no blank/crashed frames.

---

## Self-review

- **Spec coverage:** guided session (Task 9–10) ✓, media pipeline/curation
  (Tasks 3–6, 12) ✓, RapidAPI onboarding (Task 8) ✓, detail sheet upgrade
  (Task 11) ✓, fallback behavior (built into Tasks 6/9/11, not a separate task
  since it's the natural null-path, not new code) ✓, testing approach (Tasks
  1, 3–6 unit tests + Task 13 device verification) ✓.
- **Placeholder scan:** no TBD/"implement later"/"handle appropriately" text;
  Task 12's data population is a real, scoped, externally-gated task (needs a
  key that doesn't exist yet), not a vague deferral.
- **Type consistency:** `ExerciseDbDetail`, `ApiResult`, `ExerciseMediaRepository.get(Exercise): ExerciseDbDetail?`,
  `GuidedStep`/`guidedSteps()`/`firstIncompleteIndex` used identically across
  Tasks 6, 9, 11.
- **Scope:** single sub-project (media + guided session), matches the spec;
  the other two sub-projects (RapidAPI automation — rejected; AI-generated
  programs) are not touched here.
