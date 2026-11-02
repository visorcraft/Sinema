package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitlePreferencesTest {
    @Test
    fun `subtitle size cycles through stable choices`() {
        assertEquals(SubtitleSize.NORMAL, SubtitleSize.SMALL.next())
        assertEquals(SubtitleSize.LARGE, SubtitleSize.NORMAL.next())
        assertEquals(SubtitleSize.EXTRA_LARGE, SubtitleSize.LARGE.next())
        assertEquals(SubtitleSize.SMALL, SubtitleSize.EXTRA_LARGE.next())
    }

    @Test
    fun `subtitle background and position wrap`() {
        assertEquals(SubtitleBackground.OFF, SubtitleBackground.OPAQUE.next())
        assertEquals(SubtitlePosition.LOW, SubtitlePosition.HIGH.next())
    }

    @Test
    fun `invalid persisted values use readable defaults`() {
        assertEquals(SubtitleSize.NORMAL, SubtitleSize.fromName("invalid"))
        assertEquals(SubtitleBackground.STRONG, SubtitleBackground.fromName("invalid"))
        assertEquals(SubtitlePosition.LOW, SubtitlePosition.fromName("invalid"))
        assertEquals(SubtitleLanguage.AUTO, SubtitleLanguage.fromCode("invalid"))
    }

    @Test
    fun `language preference exposes a BCP 47 code`() {
        assertEquals("en", SubtitleLanguage.ENGLISH.code)
        assertEquals("pt", SubtitleLanguage.PORTUGUESE.code)
    }
}
