package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPageStateTest {
    @Test
    fun `initial state cannot append`() {
        val state = GridPageState(paged = true)
        assertFalse(state.canLoadMore)
        assertEquals(1, state.nextPage)
    }

    @Test
    fun `full first page with larger server count has another page`() {
        val state = GridPageState(paged = true)
        state.record(totalCount = 250, batchSize = 100, loadedCount = 100)
        assertTrue(state.canLoadMore)
        assertEquals(2, state.nextPage)
    }

    @Test
    fun `empty page stops loading even when count is larger`() {
        val state = GridPageState(paged = true)
        state.record(totalCount = 250, batchSize = 100, loadedCount = 100)
        state.record(totalCount = 250, batchSize = 0, loadedCount = 100)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `final page stops loading`() {
        val state = GridPageState(paged = true)
        state.record(totalCount = 150, batchSize = 100, loadedCount = 100)
        state.record(totalCount = 150, batchSize = 50, loadedCount = 150)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `unpaged screen never appends`() {
        val state = GridPageState(paged = false)
        state.record(totalCount = 500, batchSize = 40, loadedCount = 40)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `reset requires a fresh first page`() {
        val state = GridPageState(paged = true)
        state.record(totalCount = 250, batchSize = 100, loadedCount = 100)
        state.reset()
        assertFalse(state.canLoadMore)
        assertEquals(1, state.nextPage)
    }
}
