package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLoadStateTest {
    @Test
    fun `initial loading blocks the empty grid`() {
        val state = GridLoadState.loading(hasContent = false)

        assertTrue(state.visible)
        assertTrue(state.showProgress)
        assertFalse(state.showRetry)
    }

    @Test
    fun `refresh loading leaves existing content unobstructed`() {
        assertFalse(GridLoadState.loading(hasContent = true).visible)
    }

    @Test
    fun `empty success shows guidance without retry`() {
        val state = GridLoadState.loaded(itemCount = 0, emptyMessage = "Nothing here")

        assertTrue(state.visible)
        assertFalse(state.showProgress)
        assertEquals("Nothing here", state.message)
        assertFalse(state.showRetry)
    }

    @Test
    fun `nonempty success hides the overlay`() {
        assertFalse(GridLoadState.loaded(itemCount = 3, emptyMessage = "Nothing here").visible)
    }

    @Test
    fun `refresh failure keeps loaded content unobstructed`() {
        assertFalse(GridLoadState.failed(hasContent = true, message = "offline").visible)
    }

    @Test
    fun `first-page failure exposes retry`() {
        val state = GridLoadState.failed(hasContent = false, message = "offline")

        assertTrue(state.visible)
        assertFalse(state.showProgress)
        assertEquals("offline", state.message)
        assertTrue(state.showRetry)
    }
}
