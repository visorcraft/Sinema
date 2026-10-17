package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridResumeReloadGateTest {
    @Test
    fun `first resume does not duplicate initial grid load`() {
        val gate = GridResumeReloadGate()

        assertFalse(gate.onResume())
    }

    @Test
    fun `returning to grid reloads on every later resume`() {
        val gate = GridResumeReloadGate()
        gate.onResume()

        assertTrue(gate.onResume())
        assertTrue(gate.onResume())
    }
}
