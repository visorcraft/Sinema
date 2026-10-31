package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroCardTest {

    @Test
    fun `resume hero shows the resume position`() {
        assertEquals("▶ RESUME  0:34", HeroCard.actionLabel(34_125))
        assertEquals("▶ RESUME  34:12", HeroCard.actionLabel(2_052_900))
        assertEquals("▶ RESUME  1:02:03", HeroCard.actionLabel(3_723_000))
    }

    @Test
    fun `hero without resume offers plain play`() {
        assertEquals("▶ PLAY", HeroCard.actionLabel(0))
        assertEquals("▶ PLAY", HeroCard.actionLabel(-5))
    }

    @Test
    fun `labels never leak formatting whitespace`() {
        assertTrue(HeroCard.actionLabel(90_000).isNotBlank())
        assertTrue(!HeroCard.actionLabel(0).contains("RESUME"))
    }
}
