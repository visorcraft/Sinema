package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFolderDetourTest {
    @Test
    fun `folder detour near the end keeps the position and does not count as finished`() {
        val save = resumeSave(positionMs = 100_000L, durationMs = 120_000L, folderDetour = true)
        assertEquals(100.0, save!!.resumeTimeSec, 0.001)
        assertFalse(save.countAsFinished)
        assertEquals(100_000L, keptResumePositionMs(100_000L, 120_000L, folderDetour = true))
    }

    @Test
    fun `leaving near the end still clears resume and counts as finished`() {
        val save = resumeSave(positionMs = 100_000L, durationMs = 120_000L, folderDetour = false)
        assertEquals(0.0, save!!.resumeTimeSec, 0.001)
        assertTrue(save.countAsFinished)
        assertEquals(0L, keptResumePositionMs(100_000L, 120_000L, folderDetour = false))
    }

    @Test
    fun `a few seconds of playback is not recorded`() {
        assertNull(resumeSave(positionMs = 3_000L, durationMs = 120_000L, folderDetour = false))
        assertNull(resumeSave(positionMs = 3_000L, durationMs = 120_000L, folderDetour = true))
    }

    @Test
    fun `parent directory treats a leading slash as the root`() {
        assertEquals("/data/library", folderContaining("/data/library/clip.mp4"))
        assertEquals("/", folderContaining("/clip.mp4"))
        assertEquals("", folderContaining("clip.mp4"))
    }
}
