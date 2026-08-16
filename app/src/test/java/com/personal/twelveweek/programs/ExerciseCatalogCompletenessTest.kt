package com.personal.twelveweek.programs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against silent media gaps: every exercise name used anywhere in
 * the bundled program library, and every entry in the rotation catalog,
 * must have at least one real media identifier — or be "Pause", the only
 * intentional exception (rest, not a movement). Reads the actual repo files
 * on disk; this test lives in app/src/test (JVM-only) rather than
 * commonTest specifically so it can use java.io.File — see ProgramJsonTest
 * in commonTest for the equivalent parsing-behavior tests using inline JSON.
 */
class ExerciseCatalogCompletenessTest {

    // Exercises with no real matching media after searching wger, RapidAPI ExerciseDB,
    // free-exercise-db, and Wikimedia Commons (Task 2). These are niche compound
    // movements with genuinely unavailable media — accepted outcome per plan spec.
    private val INTENTIONALLY_UNMATCHED = setOf(
        "Curtsy Lunge",
        "Side Plank with Rotation L",
        "Side Plank with Rotation R",
        "Single-Arm Plank L",
        "Single-Arm Plank R",
        "Skater Jumps"
    )

    // Gradle's working directory for `:app:test` is the `app/` module dir;
    // walk up one level to the repo root where `programs/` lives. If a
    // different Gradle version changes this, adjust here (print
    // `repoRoot.absolutePath` while debugging).
    private val repoRoot = File(System.getProperty("user.dir")).let {
        if (it.name == "app") it.parentFile!! else it
    }

    private fun hasMedia(obj: JsonObject): Boolean =
        listOf("wgerId", "exerciseDbId", "freeExerciseDbId", "externalMediaUrl")
            .any { key -> obj[key]?.jsonPrimitive?.contentOrNull != null }

    @Test
    fun `every exercise name in every bundled program has real media or is Pause`() {
        val programsDir = File(repoRoot, "programs")
        val programFiles = programsDir.listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }
            ?: error("No program files found under $programsDir")
        assertTrue("Expected bundled programs, found none", programFiles.isNotEmpty())

        val gaps = mutableListOf<String>()
        for (file in programFiles) {
            val program = Json.parseToJsonElement(file.readText()).jsonObject
            for (week in program["weeks"]!!.jsonArray) {
                for (workout in week.jsonObject["workouts"]!!.jsonArray) {
                    for (section in workout.jsonObject["sections"]!!.jsonArray) {
                        for (exercise in section.jsonObject["exercises"]!!.jsonArray) {
                            val obj = exercise.jsonObject
                            val name = obj["name"]!!.jsonPrimitive.content
                            if (name == "Pause" || name in INTENTIONALLY_UNMATCHED) continue
                            if (!hasMedia(obj)) gaps.add("${file.name}: $name")
                        }
                    }
                }
            }
        }
        assertTrue(
            "Exercises with no media match (add to CREDITS.md / catalog): ${gaps.distinct()}",
            gaps.isEmpty()
        )
    }

    @Test
    fun `every catalog entry has real media or is explicitly excluded`() {
        val catalogFile = File(repoRoot, "programs/_pools/exercise-catalog.json")
        assertTrue("Catalog not found at $catalogFile", catalogFile.exists())
        val catalog = Json.parseToJsonElement(catalogFile.readText()).jsonObject
        val gaps = catalog["exercises"]!!.jsonArray
            .map { it.jsonObject }
            .filterNot { hasMedia(it) || it["name"]!!.jsonPrimitive.content in INTENTIONALLY_UNMATCHED }
            .map { it["name"]!!.jsonPrimitive.content }
        assertTrue("Catalog entries with no media match: $gaps", gaps.isEmpty())
    }
}
