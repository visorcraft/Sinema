package com.sinema.ui

import com.sinema.model.PlaybackStreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRecoveryQualityTest {
    @Test
    fun `temporary transcode remembers the session quality`() {
        val selected = recoveryQualitySelection(
            current = PlaybackStreamQuality.AUTO,
            restoreAfterScene = null,
            temporary = PlaybackStreamQuality.HLS_720P
        )
        assertEquals(PlaybackStreamQuality.HLS_720P, selected.quality)
        assertEquals(PlaybackStreamQuality.AUTO, selected.restoreAfterScene)
    }

    @Test
    fun `second recovery choice keeps the original session quality`() {
        val selected = recoveryQualitySelection(
            current = PlaybackStreamQuality.HLS_720P,
            restoreAfterScene = PlaybackStreamQuality.AUTO,
            temporary = PlaybackStreamQuality.DIRECT
        )
        assertEquals(PlaybackStreamQuality.DIRECT, selected.quality)
        assertEquals(PlaybackStreamQuality.AUTO, selected.restoreAfterScene)
    }

    @Test
    fun `retry keeps the temporary choice for this scene`() {
        val selected = recoveryQualitySelection(
            current = PlaybackStreamQuality.HLS_720P,
            restoreAfterScene = PlaybackStreamQuality.AUTO,
            temporary = null
        )
        assertEquals(PlaybackStreamQuality.HLS_720P, selected.quality)
        assertEquals(PlaybackStreamQuality.AUTO, selected.restoreAfterScene)
    }

    @Test
    fun `next scene restores the session quality`() {
        val selected = qualityAfterRecoveredScene(
            current = PlaybackStreamQuality.DIRECT,
            restoreAfterScene = PlaybackStreamQuality.HLS_1080P
        )
        assertEquals(PlaybackStreamQuality.HLS_1080P, selected.quality)
        assertNull(selected.restoreAfterScene)
    }

    @Test
    fun `next scene leaves ordinary playback unchanged`() {
        val selected = qualityAfterRecoveredScene(
            current = PlaybackStreamQuality.AUTO,
            restoreAfterScene = null
        )
        assertEquals(PlaybackStreamQuality.AUTO, selected.quality)
        assertNull(selected.restoreAfterScene)
    }
}
