package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HomeRowStateTest {

    @Test
    fun `first refresh shows a loading row`() {
        val state = HomeRowState<String>(4L, "Recently Added").refreshing()

        assertTrue(state.visible)
        assertEquals("Recently Added · Loading…", state.displayTitle)
        assertEquals(null, state.items)
    }

    @Test
    fun `refresh retains loaded items and shows refreshing status`() {
        val loaded = HomeRowState<String>(4L, "Recently Added").loaded(listOf("scene"))

        val refreshing = loaded.refreshing()

        assertEquals(listOf("scene"), refreshing.items)
        assertTrue(refreshing.visible)
        assertEquals("Recently Added · Refreshing…", refreshing.displayTitle)
    }

    @Test
    fun `successful load replaces items and clears status`() {
        val state = HomeRowState<String>(4L, "Recently Added")
            .loaded(listOf("old"))
            .refreshing()
            .loaded(listOf("new"))

        assertEquals(listOf("new"), state.items)
        assertEquals(null, state.error)
        assertFalse(state.loading)
        assertEquals("Recently Added", state.displayTitle)
    }

    @Test
    fun `successful empty load hides the row`() {
        val state = HomeRowState<String>(4L, "Recently Added")
            .loaded(listOf("scene"))
            .refreshing()
            .loaded(emptyList())

        assertFalse(state.visible)
        assertEquals(emptyList<String>(), state.items)
    }

    @Test
    fun `first load failure shows unavailable row`() {
        val error = IOException("offline")
        val state = HomeRowState<String>(4L, "Recently Added")
            .refreshing()
            .failed(error)

        assertTrue(state.visible)
        assertEquals(error, state.error)
        assertEquals("Recently Added · Unavailable", state.displayTitle)
    }

    @Test
    fun `refresh failure retains content and later recovers`() {
        val loaded = HomeRowState<String>(4L, "Recently Added").loaded(listOf("scene"))
        val failed = loaded.refreshing().failed(IOException("offline"))

        assertEquals(listOf("scene"), failed.items)
        assertTrue(failed.visible)
        assertEquals("Recently Added · Refresh failed", failed.displayTitle)
        assertEquals(loaded, failed.loaded(listOf("scene")))
    }
}
