package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamQualityTest {

    @Test
    fun `auto and direct have no resolution tier`() {
        assertNull(PlaybackStreamQuality.AUTO.resolutionParam)
        assertNull(PlaybackStreamQuality.DIRECT.resolutionParam)
        assertFalse(PlaybackStreamQuality.AUTO.isForcedHls)
        assertFalse(PlaybackStreamQuality.DIRECT.isForcedHls)
    }

    @Test
    fun `hls tiers map to stash resolution enums`() {
        assertEquals("STANDARD", PlaybackStreamQuality.HLS_480P.resolutionParam)
        assertEquals("STANDARD_HD", PlaybackStreamQuality.HLS_720P.resolutionParam)
        assertEquals("FULL_HD", PlaybackStreamQuality.HLS_1080P.resolutionParam)
        assertTrue(PlaybackStreamQuality.HLS_1080P.isForcedHls)
    }

    @Test
    fun `unknown names fall back to auto`() {
        assertEquals(PlaybackStreamQuality.AUTO, PlaybackStreamQuality.fromName(null))
        assertEquals(PlaybackStreamQuality.AUTO, PlaybackStreamQuality.fromName(""))
        assertEquals(PlaybackStreamQuality.AUTO, PlaybackStreamQuality.fromName("bogus"))
    }

    @Test
    fun `names round trip for persistence`() {
        PlaybackStreamQuality.entries.forEach { q ->
            assertEquals(q, PlaybackStreamQuality.fromName(q.name))
        }
    }
}
