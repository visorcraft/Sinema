package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQueryTest {
    @Test
    fun `visible rows always load`() {
        assertTrue(shouldLoadHomeRow(rowVisible = true, channelsEnabled = false, feedsLauncher = false))
        assertTrue(shouldLoadHomeRow(rowVisible = true, channelsEnabled = true, feedsLauncher = true))
    }

    @Test
    fun `hidden non-launcher rows do not load`() {
        assertFalse(shouldLoadHomeRow(rowVisible = false, channelsEnabled = true, feedsLauncher = false))
    }

    @Test
    fun `launcher-backed rows still load while hidden when channels are enabled`() {
        assertTrue(shouldLoadHomeRow(rowVisible = false, channelsEnabled = true, feedsLauncher = true))
        assertFalse(shouldLoadHomeRow(rowVisible = false, channelsEnabled = false, feedsLauncher = true))
    }

    @Test
    fun `channel sync only runs for successfully loaded rows`() {
        assertTrue(shouldSyncTvChannelRow(RowResult(value = emptyList<Any>())))
        assertFalse(shouldSyncTvChannelRow(RowResult<Any>(error = java.io.IOException("server unreachable"))))
        // Defensive: a result with neither value nor error must not wipe channels.
        assertFalse(shouldSyncTvChannelRow(RowResult<Any>()))
    }
}
