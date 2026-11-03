package com.sinema.api

import com.google.gson.Gson
import com.sinema.model.Scene

/**
 * Shared test factory: fills the seven mandatory Scene fields so test files
 * only specify what matters. Kept in test source; not visible to main.
 */
fun testScene(
    id: String = "1",
    title: String = "Scene $id",
    durationSec: Double = 60.0,
    playCount: Int = 0,
    rating100: Int? = null,
    resumeTimeSec: Double = 0.0,
    lastPlayedAt: String? = null,
    tags: List<Pair<String, String>> = emptyList(),
    performers: List<Pair<String, String>> = emptyList(),
    studio: String? = null
) = Scene(
    id = id,
    title = title,
    path = "/$id.mp4",
    size = 1L,
    duration = durationSec,
    width = 1,
    height = 1,
    playCount = playCount,
    rating100 = rating100,
    resumeTime = resumeTimeSec,
    lastPlayedAt = lastPlayedAt,
    tags = tags.map { com.sinema.model.TagRef(it.first, it.second) },
    performers = performers.map { com.sinema.model.PerformerRef(it.first, it.second) },
    studio = studio?.let { com.sinema.model.StudioRef("s-$it", it) }
)

fun testSceneListResponse(vararg scenes: Scene): String = Gson().toJson(
    mapOf(
        "data" to mapOf(
            "findScenes" to mapOf(
                "count" to scenes.size,
                "scenes" to scenes.map { scene ->
                    mapOf(
                        "id" to scene.id,
                        "title" to scene.title,
                        "play_count" to scene.playCount,
                        "rating100" to scene.rating100,
                        "resume_time" to scene.resumeTime,
                        "last_played_at" to scene.lastPlayedAt,
                        "files" to listOf(
                            mapOf(
                                "path" to scene.path,
                                "size" to scene.size,
                                "duration" to scene.duration,
                                "width" to scene.width,
                                "height" to scene.height
                            )
                        )
                    )
                }
            )
        )
    )
)
