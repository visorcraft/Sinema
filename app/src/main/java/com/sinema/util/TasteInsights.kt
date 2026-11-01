package com.sinema.util

import com.sinema.model.Scene

/**
 * Taste trends for the Stats screen: which tags/performers/studios your
 * *recently played* scenes carry, and how that compares with everything
 * played before the window. Counting is per-scene (a scene credits each of
 * its entities once) — Stash exposes no per-play history, so attributing
 * lifetime play_count to one window would overstate it.
 */
object TasteInsights {
    data class Trend(val label: String, val scenes: Int, val delta: Int)

    /**
     * @param cutoffDate inclusive lower bound ("2026-10-18") of the recent
     *   window; scenes with no `last_played_at` count as older.
     */
    fun trends(scenes: List<Scene>, cutoffDate: String, limit: Int = 6): List<Trend> {
        val (recent, older) = scenes.partition { scene ->
            val playedAt = scene.lastPlayedAt ?: return@partition false
            playedAt.length >= 10 && playedAt.substring(0, 10) >= cutoffDate
        }
        if (recent.isEmpty()) return emptyList()

        fun entities(scene: Scene): List<String> =
            scene.tags.map { "🏷 ${it.name}" } +
                scene.performers.map { "👤 ${it.name}" } +
                listOfNotNull(scene.studio?.let { "🎬 ${it.name}" })

        fun aggregate(source: List<Scene>): Map<String, Int> {
            val counts = mutableMapOf<String, Int>()
            source.forEach { scene ->
                entities(scene).distinct().forEach { label ->
                    counts[label] = (counts[label] ?: 0) + 1
                }
            }
            return counts
        }

        val recentCounts = aggregate(recent)
        val olderCounts = aggregate(older)
        return recentCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (label, count) ->
                Trend(label = label, scenes = count, delta = count - (olderCounts[label] ?: 0))
            }
    }

    /** "N scenes · ~M min in the last 14 days" or null when nothing recent. */
    fun summary(recentScenes: List<Scene>): String? {
        if (recentScenes.isEmpty()) return null
        val minutes = (recentScenes.sumOf { it.duration } / 60.0).toInt().coerceAtLeast(1)
        val unit = if (recentScenes.size == 1) "scene" else "scenes"
        return "$minutes min · ${recentScenes.size} $unit in the last 14 days"
    }

    /** Scenes whose play date falls inside the window (date-part compare). */
    fun recentScenes(scenes: List<Scene>, cutoffDate: String): List<Scene> =
        scenes.filter { scene ->
            val playedAt = scene.lastPlayedAt ?: return@filter false
            playedAt.length >= 10 && playedAt.substring(0, 10) >= cutoffDate
        }
}
