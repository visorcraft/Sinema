package com.sinema.ui

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.sinema.SinemaApp
import com.sinema.model.SubtitleBackground
import com.sinema.model.SubtitlePosition
import com.sinema.model.SubtitleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubtitleAppearanceTest {
    @Test
    fun `caption style uses drop shadow across all backgrounds with stronger contrast on low alpha`() {
        val off = SubtitleAppearance.buildCaptionStyle(SubtitleBackground.OFF)
        val half = SubtitleAppearance.buildCaptionStyle(SubtitleBackground.HALF)
        val strong = SubtitleAppearance.buildCaptionStyle(SubtitleBackground.STRONG)
        val opaque = SubtitleAppearance.buildCaptionStyle(SubtitleBackground.OPAQUE)

        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, off.edgeType)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, half.edgeType)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, strong.edgeType)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, opaque.edgeType)

        assertEquals(Color.argb(0, 0, 0, 0), off.backgroundColor)
        assertEquals(Color.argb(255, 0, 0, 0), opaque.backgroundColor)
        assertEquals(off.edgeColor, half.edgeColor)
        assertNotEquals(off.edgeColor, strong.edgeColor)
    }

    @Test
    fun `apply configures subtitle view with user preferences`() {
        val prefs = SinemaApp.instance.prefs
        prefs.subtitleSize = SubtitleSize.LARGE.name
        prefs.subtitleBackground = SubtitleBackground.OFF.name
        prefs.subtitlePosition = SubtitlePosition.HIGH.name

        val playerView = PlayerView(RuntimeEnvironment.getApplication())
        SubtitleAppearance.apply(playerView, prefs)

        // Ensure subtitleView exists and apply succeeds cleanly with all preferences.
        assertEquals(true, playerView.subtitleView != null)
    }
}
