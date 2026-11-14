package com.sinema.api

import com.sinema.api.testScene

import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyPlayedPagingTest {

    private fun scene(id: String, playedAt: String?) = testScene(
        id = id,
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

    @Test
    fun `desc scan stops once the dated prefix for the page is loaded`() {
        assertEquals(25, recencyDatedTarget(page = 1, perPage = 25, direction = "DESC"))
        assertEquals(50, recencyDatedTarget(page = 2, perPage = 25, direction = "DESC"))
        assertEquals(Int.MAX_VALUE, recencyDatedTarget(page = 1, perPage = 25, direction = "ASC"))
        assertTrue(recencyScanComplete(
            datedCount = 25, datedTarget = 25, batchSize = 100, chunk = 100, collected = 100, serverCount = 4000
        ))
        assertFalse(recencyScanComplete(
            datedCount = 10, datedTarget = 25, batchSize = 100, chunk = 100, collected = 100, serverCount = 4000
        ))
        assertTrue(recencyScanComplete(
            datedCount = 0, datedTarget = 25, batchSize = 40, chunk = 100, collected = 40, serverCount = 40
        ))
    }
}
