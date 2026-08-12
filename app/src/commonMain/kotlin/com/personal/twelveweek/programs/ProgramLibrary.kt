package com.personal.twelveweek.programs

import com.personal.twelveweek.storage.BundledAssetReader
import com.personal.twelveweek.storage.RawPreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the program library: a synced-from-GitHub cache if present (fresher,
 * written via [writeCache]), else the bundled copy that ships with the app
 * (Android: APK assets; wasmJs: static files alongside the site) so the app
 * works fully offline on first launch, before any sync ever runs — plus a
 * third source, custom imports: programs the user hand-imported from a
 * `.json` file (e.g. one an LLM chat generated for them). Custom imports are
 * checked first for [load] so a re-import always wins, and are merged into
 * [index] after the synced/bundled list so they always show, sync or no sync.
 */
class ProgramLibrary(
    private val assets: BundledAssetReader = BundledAssetReader(),
    private val cache: RawPreferenceStore = RawPreferenceStore("programs_cache"),
    private val custom: RawPreferenceStore = RawPreferenceStore("custom_programs")
) {

    private suspend fun readCacheOrAsset(cacheKey: String, assetPath: String): String? =
        cache.getString(cacheKey) ?: assets.read(assetPath)

    suspend fun index(): List<IndexEntry> {
        val json = readCacheOrAsset("index.json", "programs/index.json")
        val synced = json?.let { runCatching { parseIndex(it) }.getOrDefault(emptyList()) }.orEmpty()
        return synced + readCustomIndex()
    }

    suspend fun load(id: String): LibraryProgram? {
        custom.getString("program:$id")?.let { json ->
            runCatching { parseProgram(json) }.getOrNull()?.let { return it }
        }
        val json = readCacheOrAsset("$id.json", "programs/$id.json") ?: return null
        return runCatching { parseProgram(json) }.getOrNull()
    }

    /** Used by a future sync repository to drop freshly-fetched files in place. */
    fun writeCache(name: String, content: String) {
        cache.putString(name, content)
    }

    /**
     * Validates [json] as a full program file (same schema as any GitHub
     * program), and if valid, saves it under the custom store and adds it to
     * the local custom index so it shows in the picker from now on —
     * re-importing the same `id` overwrites the previous copy rather than
     * duplicating it. Never touches the synced cache or bundled assets.
     */
    suspend fun importProgram(json: String): Result<ProgramMeta> {
        val program = runCatching { parseProgram(json) }.getOrElse {
            return Result.failure(IllegalArgumentException("That file isn't a valid program (couldn't parse it)."))
        }
        if (program.weeks.isEmpty()) {
            return Result.failure(IllegalArgumentException("That program has no weeks in it."))
        }
        runCatching {
            custom.putString("program:${program.meta.id}", json)
            writeCustomIndexEntry(program.meta)
        }.onFailure {
            return Result.failure(IllegalArgumentException("Couldn't save that file on-device."))
        }
        return Result.success(program.meta)
    }

    private fun readCustomIndex(): List<IndexEntry> {
        val json = custom.getString("index") ?: return emptyList()
        return runCatching {
            customIndexJson.decodeFromString(CustomIndexDto.serializer(), json).programs.map { dto ->
                IndexEntry(meta = dto.toDomain(), file = "custom:${dto.id}")
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCustomIndexEntry(meta: ProgramMeta) {
        val existing = readCustomIndex().filterNot { it.meta.id == meta.id }
        val all = (existing.map { it.meta } + meta).map { it.toDto() }
        custom.putString("index", customIndexJson.encodeToString(CustomIndexDto.serializer(), CustomIndexDto(all)))
    }
}

private val customIndexJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ProgramMetaDto(
    val id: String,
    val title: String,
    val level: String = "",
    val focusAreas: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val weeks: Int = 0,
    val sessionMinutes: Int = 0
)

@Serializable
private data class CustomIndexDto(val programs: List<ProgramMetaDto> = emptyList())

private fun ProgramMetaDto.toDomain() = ProgramMeta(
    id = id,
    title = title,
    level = parseLevel(level),
    focusAreas = parseFocusAreas(focusAreas),
    equipment = parseEquipment(equipment),
    weekCount = weeks,
    sessionMinutes = sessionMinutes
)

private fun ProgramMeta.toDto() = ProgramMetaDto(
    id = id,
    title = title,
    level = level.name,
    focusAreas = focusAreas.map { it.name },
    equipment = equipment.map { it.name },
    weeks = weekCount,
    sessionMinutes = sessionMinutes
)
