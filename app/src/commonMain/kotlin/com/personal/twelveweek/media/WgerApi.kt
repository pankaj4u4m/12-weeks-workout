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
