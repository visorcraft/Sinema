package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsStateTest {

    @Test
    fun `seek presets expose safe defaults and cycle`() {
        assertEquals(SeekPreset.TEN_THIRTY, SeekPreset.fromName(null))
        assertEquals(SeekPreset.THIRTY_SIXTY, SeekPreset.fromName("THIRTY_SIXTY"))
        assertEquals(SeekPreset.TEN_THIRTY, SeekPreset.fromName("invalid"))
        assertEquals(10_000L, SeekPreset.TEN_THIRTY.backwardMs)
        assertEquals(30_000L, SeekPreset.TEN_THIRTY.forwardMs)
        assertEquals(SeekPreset.SIXTY_SIXTY, SeekPreset.THIRTY_SIXTY.next())
        assertEquals(SeekPreset.TEN_THIRTY, SeekPreset.SIXTY_SIXTY.next())
    }

    @Test
    fun `preview buckets are throttled and clamped to duration`() {
        assertEquals(5_000L, previewBucketMs(7_200L, 60_000L))
        assertEquals(5_000L, previewBucketMs(9_999L, 60_000L))
        assertEquals(0L, previewBucketMs(-100L, 60_000L))
        assertEquals(60_000L, previewBucketMs(90_000L, 60_000L))
        assertEquals(0L, previewBucketMs(10_000L, 0L))
    }

    @Test
    fun `marker fractions are sorted and exclude invalid positions`() {
        val fractions = markerFractions(
            listOf(
                "end" to 60.0,
                "early" to 10.0,
                "negative" to -1.0,
                "beyond" to 100.0,
                "invalid" to Double.NaN
            ),
            durationMs = 60_000L
        )

        assertEquals(listOf(10.0f / 60.0f, 1.0f), fractions)
        assertTrue(markerFractions(listOf("a" to 1.0), 0L).isEmpty())
    }

    @Test
    fun `remaining time clamps and handles unknown duration`() {
        assertEquals("-0:30", formatRemainingMs(30_000L, 60_000L))
        assertEquals("-0:00", formatRemainingMs(90_000L, 60_000L))
        assertEquals("", formatRemainingMs(10_000L, 0L))
    }

    @Test
    fun `resume prompt only for meaningful positions`() {
        assertTrue(shouldPromptResume(RESUME_PROMPT_THRESHOLD_MS))
        assertTrue(shouldPromptResume(45 * 60_000L))
        assertEquals(false, shouldPromptResume(RESUME_PROMPT_THRESHOLD_MS - 1))
        assertEquals(false, shouldPromptResume(0L))
    }

    @Test
    fun `up next chip shows remaining count`() {
        assertEquals("Up Next · 12", upNextChipLabel(12))
        assertEquals("Up Next · 1", upNextChipLabel(1))
    }
}
