package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedToggleTest {

    @Test
    fun `marking watched increments play count from zero`() {
        assertEquals(1, WatchedToggle.nextPlayCount(0, wasWatched = false))
    }

    @Test
    fun `marking watched increments existing play count`() {
        assertEquals(4, WatchedToggle.nextPlayCount(3, wasWatched = false))
    }

    @Test
    fun `marking unwatched resets play count to zero`() {
        assertEquals(0, WatchedToggle.nextPlayCount(5, wasWatched = true))
        assertEquals(0, WatchedToggle.nextPlayCount(1, wasWatched = true))
    }

    @Test
    fun `toggling twice returns to the original state`() {
        val watched = WatchedToggle.nextPlayCount(0, wasWatched = false)
        assertEquals(0, WatchedToggle.nextPlayCount(watched, wasWatched = true))
    }

    @Test
    fun `button label offers unwatched when scene is watched`() {
        assertEquals("❌ Mark Unwatched", WatchedToggle.buttonLabel(watched = true))
    }

    @Test
    fun `button label offers watched when scene is unwatched`() {
        assertEquals("👁️ Mark Watched", WatchedToggle.buttonLabel(watched = false))
    }
}
