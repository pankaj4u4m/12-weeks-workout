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
