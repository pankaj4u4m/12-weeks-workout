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
