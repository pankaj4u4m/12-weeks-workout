package com.personal.twelveweek.media

/**
 * [free-exercise-db](https://github.com/yuhonas/free-exercise-db) —
 * public-domain, no key, no rate limit. Every entry ships exactly two step
 * photos (`0.jpg`, `1.jpg`, start/end pose) at a fixed, predictable path, so
 * there's nothing to fetch/parse just to know the URLs — only [imageUrls]
 * needs a curated `freeExerciseDbId` (the entry's folder slug).
 */
object FreeExerciseDb {
    private const val BASE =
        "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises"

    fun imageUrls(id: String): List<String> = listOf("$BASE/$id/0.jpg", "$BASE/$id/1.jpg")
}
