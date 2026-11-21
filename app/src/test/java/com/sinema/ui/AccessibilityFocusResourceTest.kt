package com.sinema.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class AccessibilityFocusResourceTest {
    @Test
    fun `focus resources remain static and high contrast`() {
        val border = String(Files.readAllBytes(Paths.get("src/main/res/drawable/card_focus_border.xml")))
        val ring = String(Files.readAllBytes(Paths.get("src/main/res/drawable/accessibility_focus_ring.xml")))
        assertTrue(border.contains("android:state_focused=\"true\""))
        assertTrue(border.contains("android:width=\"4dp\""))
        assertTrue(border.contains("#FF18242A"))
        assertTrue(ring.contains("android:width=\"5dp\""))
    }
}
