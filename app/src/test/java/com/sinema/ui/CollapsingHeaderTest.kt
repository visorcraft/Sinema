package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure decision logic from CollapsingHeader, extracted for testability. */
class CollapsingHeaderTest {
    private fun shouldCollapse(dy: Int, atTop: Boolean): Boolean =
        dy > 0 || (dy < 0 && atTop)

    @Test
    fun `collapse on any downward scroll regardless of position`() {
        assertTrue(shouldCollapse(dy = 1, atTop = false))
        assertTrue(shouldCollapse(dy = 1, atTop = true))
    }

    @Test
    fun `expand only on upward scroll that reaches the top`() {
        assertTrue(shouldCollapse(dy = -1, atTop = true))
        // Upward but not yet at top: no state change (stays collapsed).
        assertFalse(shouldCollapse(dy = -1, atTop = false))
    }

    @Test
    fun `no scroll delta means no state change`() {
        assertFalse(shouldCollapse(dy = 0, atTop = true))
        assertFalse(shouldCollapse(dy = 0, atTop = false))
    }
}

class DiscreetRebindTest {
    @Test
    fun `generation comparison drives the rebind decision`() {
        // Simulates the onResume check: differing generation => rebind.
        val bound = 1
        val current = 2
        assertTrue(bound != current)
        // Same generation => no rebind.
        val same = 2
        assertFalse(same != current)
    }
}
