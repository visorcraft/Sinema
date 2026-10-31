package com.sinema.ui

import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDetailStateTest {
    private fun scene(playCount: Int = 0) = Scene(
        id = "1",
        title = "Scene",
        path = "/data/scene.mkv",
        size = 1L,
        duration = 60.0,
        width = 1920,
        height = 1080,
        playCount = playCount
    )

    @Test
    fun `accepted refresh updates the watched action`() {
        val state = SceneDetailState(scene(playCount = 0))
        val refresh = state.startRefresh()
        assertTrue(state.applyRefresh(refresh, scene(playCount = 1)))
        assertEquals("❌ Mark Unwatched", WatchedToggle.buttonLabel(state.scene.isWatched))
    }

    @Test
    fun `watched mutation invalidates an older detail response`() {
        val state = SceneDetailState(scene(playCount = 0))
        val refresh = state.startRefresh()
        state.applyWatchedToggle(wasWatched = false)
        assertFalse(state.applyRefresh(refresh, scene(playCount = 0)))
        assertEquals(1, state.scene.playCount)
        assertEquals("❌ Mark Unwatched", WatchedToggle.buttonLabel(state.scene.isWatched))
    }

    @Test
    fun `only the newest refresh is accepted`() {
        val state = SceneDetailState(scene(playCount = 0))
        val oldRefresh = state.startRefresh()
        val newRefresh = state.startRefresh()
        assertFalse(state.applyRefresh(oldRefresh, scene(playCount = 2)))
        assertTrue(state.applyRefresh(newRefresh, scene(playCount = 3)))
        assertEquals(3, state.scene.playCount)
    }

    @Test
    fun `rating mutation invalidates an older detail response`() {
        // Mirrors the watched-toggle guarantee: a stale refresh carrying the
        // old rating must not clobber a fresh star rating the user just set.
        val state = SceneDetailState(scene())
        val refresh = state.startRefresh()
        state.applyRating(stars = 4)
        assertFalse(state.applyRefresh(refresh, scene()))
        assertEquals(80, state.scene.rating100)
        assertEquals(4, state.scene.starCount)
    }

    @Test
    fun `clearing the rating invalidates refreshes too`() {
        val rated = scene().copy(rating100 = 100)
        val state = SceneDetailState(rated)
        val refresh = state.startRefresh()
        state.applyRating(stars = 0)
        assertFalse(state.applyRefresh(refresh, rated))
        assertEquals(null, state.scene.rating100)
        assertEquals(0, state.scene.starCount)
    }
}
