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

    @Test
    fun `home reloads only after the ttl or when forced`() {
        // Never loaded
        assertTrue(shouldReloadHome(lastLoadElapsedMs = 0, nowElapsedMs = 500))
        // Fresh load within TTL: skip
        assertFalse(shouldReloadHome(lastLoadElapsedMs = 10_000, nowElapsedMs = 20_000))
        // TTL boundary and beyond: reload
        assertTrue(shouldReloadHome(lastLoadElapsedMs = 10_000, nowElapsedMs = 10_000 + HOME_RELOAD_TTL_MS))
        assertTrue(shouldReloadHome(lastLoadElapsedMs = 10_000, nowElapsedMs = 10_000 + HOME_RELOAD_TTL_MS + 1))
        // Force always wins, even mid-TTL
        assertTrue(shouldReloadHome(lastLoadElapsedMs = 10_000, nowElapsedMs = 10_001, force = true))
    }

    @Test
    fun `server error row shows only when nothing loaded`() {
        assertTrue(shouldShowServerErrorRow(hadError = true, anyContentLoaded = false))
        assertFalse(shouldShowServerErrorRow(hadError = true, anyContentLoaded = true))
        assertFalse(shouldShowServerErrorRow(hadError = false, anyContentLoaded = false))
    }
}
