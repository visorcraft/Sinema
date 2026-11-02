package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerSkipStateTest {
    private val markers = listOf("Intro" to 10.0, "Next" to 30.0)

    @Test
    fun `crossing a marker once offers that marker`() {
        assertEquals("Intro", MarkerSkipState.entered(9_000, 10_500, markers)?.first)
        assertNull(MarkerSkipState.entered(10_500, 11_000, markers))
    }

    @Test
    fun `backward movement and large seeks do not offer a skip`() {
        assertNull(MarkerSkipState.entered(11_000, 9_000, markers))
        assertNull(MarkerSkipState.entered(9_000, 40_000, markers))
    }

    @Test
    fun `skip chooses the following marker`() {
        assertEquals(30_000L, MarkerSkipState.nextAfter(10_500, markers))
        assertNull(MarkerSkipState.nextAfter(30_500, markers))
    }
}
