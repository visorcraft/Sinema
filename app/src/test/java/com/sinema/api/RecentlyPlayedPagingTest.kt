package com.sinema.api

import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyPlayedPagingTest {
    private fun scene(id: String, playedAt: String?) = Scene(
        id = id,
        title = id,
        path = "/$id.mp4",
        size = 1L,
        duration = 1.0,
        width = 1,
        height = 1,
        lastPlayedAt = playedAt
    )

    @Test
    fun `null timestamps sort after all dated scenes across page boundaries`() {
        val allScenes = listOf(
            scene("null-first", null),
            scene("older", "2026-01-01T00:00:00Z"),
            scene("newest", "2026-10-01T00:00:00Z"),
            scene("null-last", null)
        )

        val firstPage = recentlyPlayedPage(allScenes, page = 1, perPage = 2, direction = "DESC")
        val secondPage = recentlyPlayedPage(allScenes, page = 2, perPage = 2, direction = "DESC")

        assertEquals(listOf("newest", "older"), firstPage.map { it.id })
        assertEquals(listOf("null-first", "null-last"), secondPage.map { it.id })
    }

    @Test
    fun `ascending order and out-of-range pages are deterministic`() {
        val scenes = listOf(scene("null", null), scene("dated", "2026-01-01T00:00:00Z"))

        assertEquals(listOf("null", "dated"), recentlyPlayedPage(scenes, 1, 10, "ASC").map { it.id })
        assertEquals(emptyList<Scene>(), recentlyPlayedPage(scenes, 3, 1, "DESC"))
    }
}
