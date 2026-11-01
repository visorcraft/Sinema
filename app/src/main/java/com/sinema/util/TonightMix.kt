package com.sinema.util

import com.sinema.model.Scene
import kotlin.random.Random

/**
 * "Tonight's Mix": one button → a seeded ~N-minute queue of personal
 * candidates (unwatched favorites, falling back to unwatched recent).
 * Pure so the selection is unit-testable.
 */
object TonightMix {
    const val DEFAULT_MINUTES = 30

    /**
     * Shuffled pick until the cumulative runtime reaches [targetMinutes];
     * the scene that crosses the target is kept so the session never ends
     * abruptly short. Scenes without a duration are appended at most once.
     */
    fun select(
        candidates: List<Scene>,
        targetMinutes: Int = DEFAULT_MINUTES,
        random: Random = Random.Default
    ): List<Scene> {
        if (candidates.isEmpty()) return emptyList()
        val targetSec = targetMinutes * 60.0
        val timed = candidates.filter { it.duration > 0.0 }.shuffled(random)
        val picked = mutableListOf<Scene>()
        var total = 0.0
        for (scene in timed) {
            picked += scene
            total += scene.duration
            if (total >= targetSec) break
        }
        // Zero-duration stragglers (server gaps) fill an empty mix only.
        if (picked.isEmpty()) {
            candidates.filter { it.duration <= 0.0 }.randomOrNull(random)?.let { picked += it }
        }
        return picked
    }

    fun summary(scenes: List<Scene>): String {
        if (scenes.isEmpty()) return "Nothing to play"
        val minutes = (scenes.sumOf { it.duration } / 60.0).toInt().coerceAtLeast(1)
        val unit = if (scenes.size == 1) "scene" else "scenes"
        return "${scenes.size} $unit · ~${minutes} min"
    }
}
