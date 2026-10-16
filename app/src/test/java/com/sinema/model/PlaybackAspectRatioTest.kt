package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAspectRatioTest {
    @Test
    fun `auto returns source ratio unchanged`() {
        assertEquals(2.35f, PlaybackAspectRatio.AUTO.effectiveRatio(2.35f), 0f)
    }

    @Test
    fun `fixed ratios ignore source ratio`() {
        assertEquals(16f / 9f, PlaybackAspectRatio.RATIO_16_9.effectiveRatio(0.5f), 0f)
        assertEquals(4f / 3f, PlaybackAspectRatio.RATIO_4_3.effectiveRatio(0.5f), 0f)
        assertEquals(9f / 16f, PlaybackAspectRatio.RATIO_9_16.effectiveRatio(2f), 0f)
    }

    @Test
    fun `fromName round trips every entry`() {
        PlaybackAspectRatio.entries.forEach { option ->
            assertEquals(option, PlaybackAspectRatio.fromName(option.name))
        }
    }

    @Test
    fun `fromName falls back to auto for missing or unknown values`() {
        assertEquals(PlaybackAspectRatio.AUTO, PlaybackAspectRatio.fromName(null))
        assertEquals(PlaybackAspectRatio.AUTO, PlaybackAspectRatio.fromName(""))
        assertEquals(PlaybackAspectRatio.AUTO, PlaybackAspectRatio.fromName("garbage"))
    }
}
