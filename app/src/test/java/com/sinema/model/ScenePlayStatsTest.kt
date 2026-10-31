package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Client-side aggregation for the stats screen: play counts weight each
 * scene's entities (a scene played 3× contributes 3 plays to its performers,
 * tags, and studio).
 */
class ScenePlayStatsTest {

    private fun scene(
        id: String,
        plays: Int,
        performers: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        studio: String? = null
    ) = Scene(
        id = id, title = id, path = "/$id.mp4", size = 1L, duration = 10.0,
        width = 1, height = 1, playCount = plays,
        studio = studio?.let { StudioRef(it, it) },
        tags = tags.map { TagRef(it, it) },
        performers = performers.map { PerformerRef(it, it) }
    )

    @Test
    fun `performer plays are weighted by scene play count`() {
        val scenes = listOf(
            scene("a", plays = 3, performers = listOf("p1", "p2")),
            scene("b", plays = 1, performers = listOf("p1")),
            scene("c", plays = 0, performers = listOf("p2")) // unplayed scenes count for nothing
        )
        val top = ScenePlayStats.topPerformers(scenes)
        // p1: 3 + 1 = 4; p2: 3 only; p2 also present on an unplayed scene.
        assertEquals(listOf("p1" to 4, "p2" to 3), top.map { it.item.id to it.plays })
    }

    @Test
    fun `tags and studios aggregate across scenes`() {
        val scenes = listOf(
            scene("a", plays = 2, tags = listOf("t1"), studio = "s1"),
            scene("b", plays = 2, tags = listOf("t1", "t2"), studio = "s1"),
            scene("c", plays = 5, tags = listOf("t2"))
        )
        val tags = ScenePlayStats.topTags(scenes)
        assertEquals(listOf("t2" to 7, "t1" to 4), tags.map { it.item.id to it.plays })
        val studios = ScenePlayStats.topStudios(scenes)
        assertEquals(listOf("s1" to 4), studios.map { it.item.id to it.plays })
    }

    @Test
    fun `limit trims and blanks are skipped`() {
        val many = (1..10).map { scene("s$it", plays = it, performers = listOf("p$it", " ")) }
        val top = ScenePlayStats.topPerformers(many, limit = 3)
        assertEquals(3, top.size)
        assertEquals(listOf("p10" to 10, "p9" to 9, "p8" to 8), top.map { it.item.id to it.plays })
    }

    @Test
    fun `entity item carries scene count for display`() {
        val scenes = listOf(scene("a", plays = 4, tags = listOf("t1"), studio = "s1"))
        val tags = ScenePlayStats.topTags(scenes)
        assertEquals(1, tags.single().item.sceneCount)
        assertEquals(EntityItem.Kind.TAG, tags.single().item.kind)
    }

    @Test
    fun `ties keep first-seen insertion order`() {
        val scenes = listOf(
            scene("a", plays = 2, tags = listOf("first")),
            scene("b", plays = 2, tags = listOf("second"))
        )
        val tags = ScenePlayStats.topTags(scenes)
        assertEquals(listOf("first", "second"), tags.map { it.item.id })
    }
}
