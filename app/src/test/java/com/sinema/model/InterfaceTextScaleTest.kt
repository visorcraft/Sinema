package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceTextScaleTest {
    @Test
    fun `text scale cycles and wraps`() {
        assertEquals(InterfaceTextScale.LARGE, InterfaceTextScale.DEFAULT.next())
        assertEquals(InterfaceTextScale.EXTRA_LARGE, InterfaceTextScale.LARGE.next())
        assertEquals(InterfaceTextScale.DEFAULT, InterfaceTextScale.EXTRA_LARGE.next())
    }

    @Test
    fun `invalid saved scale uses default`() {
        assertEquals(InterfaceTextScale.DEFAULT, InterfaceTextScale.fromName("invalid"))
    }

    @Test
    fun `larger options use accessible font multipliers`() {
        assertEquals(1.2f, InterfaceTextScale.LARGE.fontScale)
        assertEquals(1.4f, InterfaceTextScale.EXTRA_LARGE.fontScale)
    }

    @Test
    fun `app scale extends rather than replaces the system scale`() {
        assertEquals(1.3f, InterfaceTextScale.DEFAULT.effectiveFontScale(1.3f), 0.0001f)
        assertEquals(1.56f, InterfaceTextScale.LARGE.effectiveFontScale(1.3f), 0.0001f)
    }
}
