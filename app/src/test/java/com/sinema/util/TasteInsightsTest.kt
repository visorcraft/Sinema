package com.sinema.util

import com.sinema.api.testScene
import com.sinema.model.EntityItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteInsightsTest {
    private fun scene(
        id: String,
        playCount: Int,
        playedAt: String?,
        tags: List<String> = emptyList(),
        performers: List<String> = emptyList(),
        studio: String? = null,
        durationSec: Double = 60.0
    ) = testScene(
        id = id, playCount = playCount, lastPlayedAt = playedAt,
        tags = tags.map { "t-$it" to it },
        performers = performers.map { "p-$it" to it },
        studio = studio,
        durationSec = durationSec
    )

    @Test
    fun `trends rank recent entities per scene and compare against older`() {
        val scenes = listOf(
            scene("new1", 2, "2026-11-01T10:00:00Z", tags = listOf("alpha")),
            scene("new2", 3, "2026-10-25T10:00:00Z", tags = listOf("alpha"), performers = listOf("Pat")),
            scene("new3", 1, "2026-10-20T10:00:00Z", performers = listOf("Pat")),
            scene("old1", 5, "2026-01-01T10:00:00Z", tags = listOf("alpha")),
            scene("never", 4, null)
        )

        val trends = TasteInsights.trends(scenes, cutoffDate = "2026-10-18")

        // Per-scene counting: alpha appears in 2 recent scenes (lifetime
        // play_count is deliberately NOT attributed to the window).
        val alpha = trends.first { it.label == "🏷 alpha" }
        assertEquals(2, alpha.scenes)
        assertEquals(1, alpha.delta) // 2 recent vs 1 older scene
        val pat = trends.first { it.label == "👤 Pat" }
        assertEquals(2, pat.scenes)
        assertEquals(2, pat.delta)
        // "never" contributes nothing to the recent window.
        assertTrue(trends.none { it.label.contains("never") })
    }

    @Test
    fun `no recent plays yields no trends`() {
        val scenes = listOf(scene("old", 3, "2026-01-01T00:00:00Z"))
        assertTrue(TasteInsights.trends(scenes, "2026-10-18").isEmpty())
        assertTrue(TasteInsights.recentScenes(scenes, "2026-10-18").isEmpty())
        assertNull(TasteInsights.summary(emptyList()))
    }

    @Test
    fun `summary counts minutes and recent selection matches window`() {
        val scenes = listOf(
            scene("a", 1, "2026-10-30T00:00:00Z", durationSec = 300.0),
            scene("b", 1, "2026-10-29T00:00:00Z", durationSec = 300.0),
            scene("c", 1, "2026-01-01T00:00:00Z", durationSec = 999.0)
        )
        val recent = TasteInsights.recentScenes(scenes, "2026-10-18")
        assertEquals(listOf("a", "b"), recent.map { it.id })
        assertEquals("10 min · 2 scenes in the last 14 days", TasteInsights.summary(recent))
    }
}

class EntityFavoriteTest {
    @Test
    fun `entity favorite follows rating100`() {
        val fav = EntityItem("1", "n", 3, null, EntityItem.Kind.PERFORMER, rating100 = 100)
        val plain = EntityItem("2", "n", 3, null, EntityItem.Kind.TAG)
        val zero = EntityItem("3", "n", 3, null, EntityItem.Kind.STUDIO, rating100 = 0)
        assertTrue(fav.isFavorite)
        assertEquals(false, plain.isFavorite)
        assertEquals(false, zero.isFavorite)
    }
}

class BackupTypesTest {
    @Test
    fun `resume keys restore as long even when they fit an int`() {
        assertEquals(BackupTypes.Kind.LONG, BackupTypes.numberKind("resume_abc", "120"))
        assertEquals(BackupTypes.Kind.LONG, BackupTypes.numberKind("resume_xyz", "9999999999"))
    }

    @Test
    fun `other numbers restore by literal shape`() {
        assertEquals(BackupTypes.Kind.INT, BackupTypes.numberKind("auto_lock_minutes", "15"))
        assertEquals(BackupTypes.Kind.FLOAT, BackupTypes.numberKind("some_ratio", "1.5"))
        assertEquals(BackupTypes.Kind.FLOAT, BackupTypes.numberKind("some_exp", "1e3"))
    }
}
