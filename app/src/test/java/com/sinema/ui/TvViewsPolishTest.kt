package com.sinema.ui

import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import com.sinema.SinemaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class TvViewsPolishTest {
    @Before
    fun setUp() {
        SinemaApp.instance.prefs.reduceMotion = false
    }

    @Test
    fun `tvButton uses rounded drawable background and lifts on focus`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val button = context.tvButton("Home Settings") {}
        parent.addView(button)

        val unfocusedBg = button.background
        assertTrue("TV button should use a rounded GradientDrawable", unfocusedBg is GradientDrawable)
        assertTrue((unfocusedBg as GradientDrawable).cornerRadius > 0f)
        assertEquals(0f, button.translationZ, 0.001f)

        SinemaApp.instance.prefs.reduceMotion = true
        assertTrue(button.requestFocus())
        assertNotSame(unfocusedBg, button.background)
        assertTrue(button.background is GradientDrawable)
        assertTrue("Focused TV button must lift in Z", button.translationZ > 0f)
        assertEquals("Reduced motion must not scale button", 1f, button.scaleX, 0.001f)
    }

    @Test
    fun `tvChip applies focus elevation lift and resets on blur`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val chip = context.tvChip("#Favorites") {}
        parent.addView(chip)

        SinemaApp.instance.prefs.reduceMotion = true
        assertTrue(chip.requestFocus())
        assertTrue(chip.translationZ > 0f)

        chip.clearFocus()
        assertEquals(0f, chip.translationZ, 0.001f)
    }
}
