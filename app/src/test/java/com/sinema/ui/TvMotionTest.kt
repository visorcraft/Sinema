package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TvMotionTest {
    @Test
    fun `reduced motion disables focus zoom`() {
        assertEquals(0, TvMotion.focusZoom(reduceMotion = true))
    }

    @Test
    fun `default motion keeps subtle focus zoom`() {
        assertEquals(1, TvMotion.focusZoom(reduceMotion = false))
    }
}
