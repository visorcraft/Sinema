package com.sinema.ui

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.withGraphqlServer
import com.sinema.util.SceneIntents
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class PlaybackScrubHudTest {
    private val markers = listOf(
        "Finale" to 90.0,
        "Intro" to 10.0,
        "Middle" to 45.0,
        "Bad" to Double.NaN,
        "Negative" to -5.0
    )

    @Test
    fun `activeMarkerAt resolves latest valid marker at or before position`() {
        assertNull(activeMarkerAt(5_000L, markers))
        assertEquals("Intro", activeMarkerAt(10_000L, markers)?.first)
        assertEquals("Intro", activeMarkerAt(44_000L, markers)?.first)
        assertEquals("Middle", activeMarkerAt(45_000L, markers)?.first)
        assertEquals("Finale", activeMarkerAt(100_000L, markers)?.first)
        assertNull(activeMarkerAt(-1_000L, markers))
    }

    @Test
    fun `activeMarkerFraction returns timeline fraction for active marker`() {
        assertNull(activeMarkerFraction(5_000L, markers, 120_000L))
        assertEquals(10f / 120f, activeMarkerFraction(20_000L, markers, 120_000L)!!, 0.0001f)
        assertEquals(45f / 120f, activeMarkerFraction(50_000L, markers, 120_000L)!!, 0.0001f)
        assertNull(activeMarkerFraction(50_000L, markers, 0L))
    }

    @Test
    fun `adjacentMarker handles unsorted input and hysteresis window`() {
        assertEquals("Intro", adjacentMarker(0L, markers, forward = true)?.first)
        assertEquals("Middle", adjacentMarker(10_000L, markers, forward = true)?.first)
        assertEquals("Finale", adjacentMarker(46_000L, markers, forward = true)?.first)
        assertNull(adjacentMarker(90_000L, markers, forward = true))

        assertNull(adjacentMarker(12_000L, markers, forward = false))
        assertEquals("Intro", adjacentMarker(45_000L, markers, forward = false)?.first)
        assertEquals("Middle", adjacentMarker(90_000L, markers, forward = false)?.first)
    }

    @Test
    fun `formatSeekDeltaMs formats positive negative and zero deltas`() {
        assertEquals("", formatSeekDeltaMs(0L))
        assertEquals("", formatSeekDeltaMs(999L))
        assertEquals("+0:30", formatSeekDeltaMs(30_000L))
        assertEquals("−0:10", formatSeekDeltaMs(-10_000L))
        assertEquals("+1:05", formatSeekDeltaMs(65_000L))
    }

    @Test
    fun `buildScrubPreview combines timestamp delta chapter and dwell hint`() {
        val preview = buildScrubPreview(
            targetMs = 50_000L,
            originMs = 20_000L,
            durationMs = 120_000L,
            isPaused = true,
            markers = markers
        )
        assertEquals("0:50", preview.timestampText)
        assertEquals("+0:30", preview.deltaText)
        assertEquals("Middle", preview.chapterTitle)
        assertTrue(preview.showPausedDwellHint)

        val playingPreview = buildScrubPreview(
            targetMs = 5_000L,
            originMs = 5_000L,
            durationMs = 120_000L,
            isPaused = false,
            markers = markers
        )
        assertEquals("0:05", playingPreview.timestampText)
        assertEquals("", playingPreview.deltaText)
        assertEquals("", playingPreview.chapterTitle)
        assertFalse(playingPreview.showPausedDwellHint)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class PlaybackScrubHudUiTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.prefs.reduceMotion = true
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `scrub preview shows delta chapter and dwell hint while paused`() {
        val response = fullSceneResponse(0.0).replace(
            "\"scene_markers\":[]",
            "\"scene_markers\":[{\"title\":\"Opening\",\"seconds\":15.0,\"primary_tag\":{\"name\":\"Intro\"}}]"
        )
        withGraphqlServer(response, configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            activity.player?.playWhenReady = false
            val timeBar = activity.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress).apply {
                isEnabled = true
                setDuration(120_000L)
                setPosition(0L)
            }
            timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.playback_preview_container).visibility)
            assertEquals("0:30", activity.findViewById<TextView>(R.id.playback_preview_time).text.toString())
            assertEquals("+0:30", activity.findViewById<TextView>(R.id.playback_preview_delta).text.toString())
            assertEquals("Opening", activity.findViewById<TextView>(R.id.playback_preview_chapter).text.toString())
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.playback_preview_hint).visibility)
            assertEquals(15f / 120f, timeBar.activeMarkerPos!!, 0.0001f)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `quick seek shows transient feedback badge and auto hides`() {
        withGraphqlServer(fullSceneResponse(0.0), configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            activity.seekRelative(30_000L)
            val feedback = activity.findViewById<TextView>(R.id.playback_seek_feedback)
            assertEquals(View.VISIBLE, feedback.visibility)
            assertEquals("+30s", feedback.text.toString())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
            assertEquals(View.GONE, feedback.visibility)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `marker jump shows marker name feedback and prev next buttons reflect marker availability`() {
        val response = fullSceneResponse(0.0).replace(
            "\"scene_markers\":[]",
            "\"scene_markers\":[{\"title\":\"Chorus\",\"seconds\":25.0,\"primary_tag\":{\"name\":\"Tag\"}}]"
        )
        withGraphqlServer(response, configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            assertTrue(activity.findViewById<View>(R.id.playback_next_marker_button).isEnabled)
            activity.seekToAdjacentMarker(forward = true)
            val feedback = activity.findViewById<TextView>(R.id.playback_seek_feedback)
            assertEquals(View.VISIBLE, feedback.visibility)
            assertEquals("▸ Chorus", feedback.text.toString())
            ShadowAlertDialog.reset()
            controller.pause().stop().destroy()
        }
    }
}
