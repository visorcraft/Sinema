package com.sinema.ui

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.withGraphqlServer
import com.sinema.model.PlaybackAspectRatio
import com.sinema.model.PlaybackStreamQuality
import com.sinema.util.DiscreetMode
import com.sinema.util.SceneIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class PlaybackHudStateTest {
    @Test
    fun `playbackDisplayTitle masks scene title when discreet mode is enabled`() {
        assertEquals("My Scene", playbackDisplayTitle("My Scene", "42", discreetMode = false))
        assertEquals(
            DiscreetMode.neutralTitle("42"),
            playbackDisplayTitle("My Scene", "42", discreetMode = true)
        )
        assertEquals("", playbackDisplayTitle("My Scene", "", discreetMode = true))
    }

    @Test
    fun `qualityButtonText stays concise on AUTO and reflects manual overrides`() {
        assertEquals("Quality", qualityButtonText("Quality", PlaybackStreamQuality.AUTO))
        assertEquals("Quality · Direct", qualityButtonText("Quality", PlaybackStreamQuality.DIRECT))
        assertEquals("Quality · 720p", qualityButtonText("Quality", PlaybackStreamQuality.HLS_720P))
        assertEquals("Quality · 1080p", qualityButtonText("Quality", PlaybackStreamQuality.HLS_1080P))
    }

    @Test
    fun `status badges stay empty for default playing state`() {
        val badges = buildPlaybackStatusBadges(
            isBuffering = false,
            isPaused = false,
            isLooping = false,
            isIncognito = false,
            isEndlessMix = false,
            streamQuality = PlaybackStreamQuality.AUTO,
            forceAlignedHls = false,
            aspectRatio = PlaybackAspectRatio.AUTO,
            playbackSpeed = 1.0f
        )
        assertTrue(badges.isEmpty())
    }

    @Test
    fun `status badges surface buffering priority and active overrides`() {
        val buffering = buildPlaybackStatusBadges(
            isBuffering = true,
            isPaused = true,
            isLooping = true,
            isIncognito = true,
            isEndlessMix = true,
            streamQuality = PlaybackStreamQuality.HLS_720P,
            forceAlignedHls = true,
            aspectRatio = PlaybackAspectRatio.RATIO_16_9,
            playbackSpeed = 1.25f
        )
        assertEquals(
            listOf("Buffering", "Loop", "1.25×", "720p", "16:9", "Incognito", "Endless"),
            buffering
        )

        val fallbackPaused = buildPlaybackStatusBadges(
            isBuffering = false,
            isPaused = true,
            isLooping = false,
            isIncognito = false,
            isEndlessMix = false,
            streamQuality = PlaybackStreamQuality.AUTO,
            forceAlignedHls = true,
            aspectRatio = PlaybackAspectRatio.AUTO,
            playbackSpeed = 2.0f
        )
        assertEquals(listOf("Paused", "2×", "HLS"), fallbackPaused)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class PlaybackHudUiTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.prefs.discreetMode = false
        SinemaApp.instance.prefs.incognitoMode = false
        SinemaApp.instance.prefs.endlessMix = false
        SinemaApp.instance.prefs.playbackStreamQuality = PlaybackStreamQuality.AUTO.name
        SinemaApp.instance.prefs.playbackAspectRatio = PlaybackAspectRatio.AUTO.name
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `discreet mode masks title and hides chapter label in playback HUD`() {
        SinemaApp.instance.prefs.discreetMode = true
        val response = fullSceneResponse(0.0).replace(
            "\"title\":\"Scene\"",
            "\"title\":\"Sensitive Title\""
        ).replace(
            "\"scene_markers\":[]",
            "\"scene_markers\":[{\"title\":\"Private Chapter\",\"seconds\":0.0,\"primary_tag\":{\"name\":\"Tag\"}}]"
        )
        withGraphqlServer(response, configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene-99",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            val title = activity.findViewById<TextView>(R.id.playback_scene_title).text.toString()
            assertFalse(title.contains("Sensitive"))
            assertEquals(DiscreetMode.neutralTitle("scene-99"), title)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.playback_chapter_now).visibility)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `status badges and quality button reflect active playback overrides`() {
        SinemaApp.instance.prefs.incognitoMode = true
        SinemaApp.instance.prefs.playbackStreamQuality = PlaybackStreamQuality.HLS_720P.name
        SinemaApp.instance.prefs.playbackAspectRatio = PlaybackAspectRatio.RATIO_16_9.name
        withGraphqlServer(fullSceneResponse(0.0), configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            activity.player?.playWhenReady = false
            activity.updatePlaybackText()

            val badges = activity.findViewById<TextView>(R.id.playback_status_badges)
            assertEquals(View.VISIBLE, badges.visibility)
            assertTrue(badges.text.contains("720p"))
            assertTrue(badges.text.contains("16:9"))
            assertTrue(badges.text.contains("Incognito"))
            assertEquals(
                "Quality · 720p",
                activity.findViewById<Button>(R.id.playback_quality_button).text.toString()
            )
            controller.pause().stop().destroy()
        }
    }
}
