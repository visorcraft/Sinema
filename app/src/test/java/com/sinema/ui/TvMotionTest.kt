package com.sinema.ui

import android.view.View
import android.widget.FrameLayout
import com.sinema.SinemaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class TvMotionTest {
    @Test
    fun `reduced motion disables focus zoom`() {
        assertEquals(0, TvMotion.focusZoom(reduceMotion = true))
    }

    @Test
    fun `default motion keeps subtle focus zoom`() {
        assertEquals(1, TvMotion.focusZoom(reduceMotion = false))
    }

    @Test
    fun `reduced motion disables image crossfade and animation durations`() {
        assertEquals(0, TvMotion.imageCrossfadeMs(reduceMotion = true))
        assertTrue(TvMotion.imageCrossfadeMs(reduceMotion = false) > 0)

        TvMotion.FocusRole.entries.forEach { role ->
            val focused = TvMotion.focusChoreography(role, focused = true, reduceMotion = true)
            val unfocused = TvMotion.focusChoreography(role, focused = false, reduceMotion = true)
            assertEquals("Reduced motion must not scale $role", 1f, focused.scale, 0.0001f)
            assertEquals("Reduced motion must settle instantly for $role", 0L, focused.durationMs)
            assertEquals("Reduced motion must settle instantly on blur for $role", 0L, unfocused.durationMs)
            assertTrue("Focused $role keeps Z separation", focused.translationZDp > unfocused.translationZDp)
        }
    }

    @Test
    fun `default motion choreographs elevation without double-scaling leanback cards`() {
        val cardFocused = TvMotion.focusChoreography(TvMotion.FocusRole.CARD, focused = true, reduceMotion = false)
        val cardUnfocused = TvMotion.focusChoreography(TvMotion.FocusRole.CARD, focused = false, reduceMotion = false)
        assertEquals(1f, cardFocused.scale, 0.0001f)
        assertTrue(cardFocused.translationZDp > cardUnfocused.translationZDp)
        assertTrue(cardFocused.durationMs > 0L)

        val heroFocused = TvMotion.focusChoreography(TvMotion.FocusRole.HERO, focused = true, reduceMotion = false)
        assertEquals(1f, heroFocused.scale, 0.0001f)
        assertTrue(heroFocused.translationZDp > cardFocused.translationZDp)

        val commandFocused = TvMotion.focusChoreography(TvMotion.FocusRole.COMMAND, focused = true, reduceMotion = false)
        assertTrue(commandFocused.scale > 1f)
        assertTrue(commandFocused.translationZDp > 0f)

        val controlFocused = TvMotion.focusChoreography(TvMotion.FocusRole.CONTROL, focused = true, reduceMotion = false)
        assertTrue(controlFocused.scale > 1f)
        assertTrue(controlFocused.translationZDp > 0f)
    }

    @Test
    fun `applyFocusMotion and resetFocusMotion settle cleanly on views`() {
        val view = View(RuntimeEnvironment.getApplication())
        TvMotion.applyFocusMotion(view, TvMotion.FocusRole.CONTROL, focused = true, reduceMotion = false, animate = false)
        assertTrue(view.scaleX > 1f)
        assertTrue(view.scaleY > 1f)
        assertTrue(view.translationZ > 0f)

        TvMotion.resetFocusMotion(view)
        assertEquals(1f, view.scaleX, 0.0001f)
        assertEquals(1f, view.scaleY, 0.0001f)
        assertEquals(0f, view.translationZ, 0.0001f)
    }

    @Test
    fun `clipRoundedCorners enables outline clipping`() {
        val container = FrameLayout(RuntimeEnvironment.getApplication())
        TvMotion.clipRoundedCorners(container, 8f)
        assertTrue(container.clipToOutline)
    }
}
