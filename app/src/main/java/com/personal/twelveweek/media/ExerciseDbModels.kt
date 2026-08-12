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
