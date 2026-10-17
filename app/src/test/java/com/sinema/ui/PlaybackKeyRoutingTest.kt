package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackKeyRoutingTest {
    @Test
    fun `controller button focus leaves dpad available for navigation`() {
        assertFalse(
            PlaybackKeyRouting.shouldSeekToAdjacentMarker(
                controllerVisible = true,
                hasMarkers = true,
                playerSurfaceFocused = false
            )
        )
    }

    @Test
    fun `surface focus allows dpad marker navigation`() {
        assertTrue(
            PlaybackKeyRouting.shouldSeekToAdjacentMarker(
                controllerVisible = true,
                hasMarkers = true,
                playerSurfaceFocused = true
            )
        )
    }

    @Test
    fun `dpad marker navigation requires visible controls and markers`() {
        assertFalse(
            PlaybackKeyRouting.shouldSeekToAdjacentMarker(
                controllerVisible = false,
                hasMarkers = true,
                playerSurfaceFocused = true
            )
        )
        assertFalse(
            PlaybackKeyRouting.shouldSeekToAdjacentMarker(
                controllerVisible = true,
                hasMarkers = false,
                playerSurfaceFocused = true
            )
        )
    }
}
