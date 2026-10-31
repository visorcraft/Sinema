package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedScenesTest {

    @Test
    fun `source and candidates without shared metadata are excluded`() {
        val source = scene("source", performers = listOf("p1"), tags = listOf("t1"))
        val candidates = listOf(
            source,
            scene("other", performers = listOf("p2"), tags = listOf("t2")),
            scene("match", performers = listOf("p1"))
        )
        assertEquals(listOf("match"), RelatedScenes.rank(source, candidates).map { it.id })
    }

    @Test
    fun `performer studio and tag weights determine order`() {
        val source = scene(
            "source",
            performers = listOf("p1", "p2"),
            studio = "studio",
            tags = listOf("t1", "t2")
        )
        val strongest = scene(
            "strong", performers = listOf("p1", "p2"), studio = "studio", tags = listOf("t1", "t2")
        )
        val medium = scene("medium", performers = listOf("p1"), studio = "studio")
        val weakest = scene("weak", tags = listOf("t1", "t2"))
        val ranked = RelatedScenes.rank(source, listOf(weakest, medium, strongest))
        assertEquals(listOf("strong", "medium", "weak"), ranked.map { it.id })
    }

    @Test
    fun `equal scores use rating then scene id as deterministic tie breakers`() {
        val source = scene("source", tags = listOf("t1"))
        val candidates = listOf(
            scene("b", rating100 = 100, tags = listOf("t1")),
            scene("c", rating100 = 80, tags = listOf("t1")),
            scene("a", rating100 = 100, tags = listOf("t1")),
            scene("d", tags = listOf("t1"))
        )
        assertEquals(listOf("a", "b", "c", "d"), RelatedScenes.rank(source, candidates).map { it.id })
    }

    @Test
    fun `duplicate candidates are merged and limit is applied`() {
        val source = scene("source", performers = listOf("p1"))
        val first = scene("first", performers = listOf("p1"))
        val second = scene("second", performers = listOf("p1"))
        val ranked = RelatedScenes.rank(source, listOf(first, second, first.copy(title = "duplicate")), limit = 2)
        assertEquals(listOf("first", "second"), ranked.map { it.id })
    }

    @Test
    fun `source without usable metadata returns no recommendations`() {
        val source = scene("source")
        val ranked = RelatedScenes.rank(source, listOf(scene("other")))
        assertTrue(ranked.isEmpty())
    }

    private fun scene(
        id: String,
        rating100: Int? = null,
        performers: List<String> = emptyList(),
        studio: String? = null,
        tags: List<String> = emptyList()
    ) = Scene(
        id = id,
        title = "Scene $id",
        path = "/data/$id.mp4",
        size = 1,
        duration = 100.0,
        width = 1920,
        height = 1080,
        rating100 = rating100,
        performers = performers.map { PerformerRef(it, it) },
        studio = studio?.let { StudioRef(it, it) },
        tags = tags.map { TagRef(it, it) }
    )
}
