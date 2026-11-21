package com.sinema.ui

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.PlaybackException
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.withGraphqlServer
import com.sinema.model.CaptionRef
import com.sinema.util.DiscreetMode
import com.sinema.util.SceneIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

class PlaybackTrackPresentationTest {
    @Test
    fun `formatSubtitleTrackLabels produces readable labels and disambiguates duplicates`() {
        val labels = formatSubtitleTrackLabels(
            listOf(
                CaptionRef("en", "srt"),
                CaptionRef("en", "srt"),
                CaptionRef("ja", "vtt"),
                CaptionRef("pt-BR", "ass"),
                CaptionRef("", "vtt")
            )
        )
        assertEquals(
            listOf(
                "English · SRT",
                "English (2) · SRT",
                "Japanese · VTT",
                "Portuguese · ASS",
                "Subtitles · VTT"
            ),
            labels
        )
    }

    @Test
    fun `playback speed presets resolve nearest speed cleanly`() {
        assertEquals(PlaybackSpeedPreset.NORMAL, PlaybackSpeedPreset.fromSpeed(1.0f))
        assertEquals(PlaybackSpeedPreset.ONE_AND_QUARTER, PlaybackSpeedPreset.fromSpeed(1.24f))
        assertEquals(PlaybackSpeedPreset.HALF, PlaybackSpeedPreset.fromSpeed(0.25f))
        assertEquals(PlaybackSpeedPreset.DOUBLE, PlaybackSpeedPreset.fromSpeed(3.0f))
        assertTrue(PlaybackSpeedPreset.NORMAL.isDefault)
        assertFalse(PlaybackSpeedPreset.ONE_AND_HALF.isDefault)
    }

    @Test
    fun `activeChapterIndex selects the latest chapter at or before position`() {
        val markers = listOf("Opening" to 5.0, "Middle" to 40.0, "Outro" to 90.0)
        assertEquals(-1, activeChapterIndex(2_000L, markers))
        assertEquals(0, activeChapterIndex(5_000L, markers))
        assertEquals(0, activeChapterIndex(39_000L, markers))
        assertEquals(1, activeChapterIndex(40_000L, markers))
        assertEquals(2, activeChapterIndex(100_000L, markers))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class PlaybackDialogsPolishTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.prefs.discreetMode = false
        SinemaApp.instance.prefs.reduceMotion = true
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `speed button opens speed dialog and updates player speed and HUD`() {
        withGraphqlServer(fullSceneResponse(0.0), configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()

            val speedButton = activity.findViewById<Button>(R.id.playback_speed_button)
            assertEquals("Speed: 1×", speedButton.text.toString())
            speedButton.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            assertTrue(dialog.isShowing)
            assertEquals("Playback speed", shadowOf(dialog).title.toString())

            // Select 1.5× (index 4).
            shadowOf(dialog).clickOnItem(PlaybackSpeedPreset.ONE_AND_HALF.ordinal)
            shadowOf(Looper.getMainLooper()).idle()

            assertNull(activity.speedDialog.dialog)
            assertEquals(1.5f, activity.player!!.playbackParameters.speed, 0.001f)
            assertEquals("Speed: 1.5×", speedButton.text.toString())
            assertTrue(activity.findViewById<TextView>(R.id.playback_status_badges).text.contains("1.5×"))
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.playback_seek_feedback).visibility)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `discreet mode masks scene title in playback recovery dialog`() {
        SinemaApp.instance.prefs.discreetMode = true
        val response = fullSceneResponse(0.0).replace(
            "\"title\":\"Scene\"",
            "\"title\":\"Private Title\""
        )
        withGraphqlServer(response, configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene-77",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()

            val error = PlaybackException(
                "decode",
                null,
                PlaybackException.ERROR_CODE_DECODING_FAILED
            )
            activity.showPlaybackRecovery(error)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            val message = shadowOf(dialog).message.toString()
            assertFalse(message.contains("Private Title"))
            assertTrue(message.contains(DiscreetMode.neutralTitle("scene-77")))
            controller.pause().stop().destroy()
        }
    }
}
