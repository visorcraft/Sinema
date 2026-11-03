package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCommandRowTest {
    @Test
    fun `pin makes log out the fixed first command`() {
        assertEquals(
            listOf("Log Out", "Refresh", "Settings", "About"),
            HomeCommandRow.items(hasPin = true)
        )
    }

    @Test
    fun `no pin omits only log out`() {
        assertEquals(
            listOf("Refresh", "Settings", "About"),
            HomeCommandRow.items(hasPin = false)
        )
    }

    @Test
    fun `retry is the initial target during a full dashboard failure`() {
        assertEquals(
            HomeInitialTarget.RETRY,
            HomeFocusPolicy.initialTarget(retryVisible = true, heroVisible = false, browseItemCount = 3)
        )
    }

    @Test
    fun `hero outranks browse when retry is absent`() {
        assertEquals(
            HomeInitialTarget.HERO,
            HomeFocusPolicy.initialTarget(retryVisible = false, heroVisible = true, browseItemCount = 3)
        )
    }

    @Test
    fun `browse and refresh are safe fallbacks`() {
        assertEquals(
            HomeInitialTarget.BROWSE,
            HomeFocusPolicy.initialTarget(retryVisible = false, heroVisible = false, browseItemCount = 1)
        )
        assertEquals(
            HomeInitialTarget.REFRESH,
            HomeFocusPolicy.initialTarget(retryVisible = false, heroVisible = false, browseItemCount = 0)
        )
    }

    @Test
    fun `restoration keeps an existing command after reorder`() {
        val keys = listOf(HomeCommandRow.LOG_OUT_KEY, HomeCommandRow.REFRESH_KEY, HomeCommandRow.SETTINGS_KEY)
        assertEquals(
            2,
            HomeFocusPolicy.restoredItemIndex(HomeCommandRow.SETTINGS_KEY, 2, keys)
        )
    }

    @Test
    fun `removed item fallback never selects log out`() {
        val keys = listOf(HomeCommandRow.LOG_OUT_KEY, HomeCommandRow.REFRESH_KEY)
        assertEquals(
            1,
            HomeFocusPolicy.restoredItemIndex(HomeCommandRow.SETTINGS_KEY, 1, keys)
        )
        assertEquals(
            1,
            HomeFocusPolicy.restoredItemIndex("string:removed", 0, keys)
        )
    }

    @Test
    fun `an explicitly focused log out can restore while it remains visible`() {
        val keys = listOf(HomeCommandRow.LOG_OUT_KEY, HomeCommandRow.REFRESH_KEY)
        assertEquals(0, HomeFocusPolicy.restoredItemIndex(HomeCommandRow.LOG_OUT_KEY, 0, keys))
    }
}
