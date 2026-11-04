package com.sinema.ui

import android.app.AlertDialog
import android.os.Looper
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.withGraphqlServer
import com.sinema.util.SceneIntents
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class PlaybackActivityUiTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `confirmed resume starts without resume prompt`() = withPlayback(resumeTimeSec = 45.0, resumeConfirmed = true) { activity, previousDialog ->
        assertSame(previousDialog, ShadowAlertDialog.getLatestAlertDialog())
        activity.finish()
    }

    @Test
    fun `ordinary play with saved position shows resume prompt`() = withPlayback(resumeTimeSec = 45.0) { activity, previousDialog ->
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(previousDialog, dialog)
        assertTrue(dialog.isShowing)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.contains("Resume"))
        activity.finish()
    }

    @Test
    fun `ordinary play without saved position starts without prompt`() = withPlayback(resumeTimeSec = 0.0) { activity, previousDialog ->
        assertSame(previousDialog, ShadowAlertDialog.getLatestAlertDialog())
        activity.finish()
    }

    @Test
    fun `activity dwell seeks paused and center resumes`() = withPlayback(resumeTimeSec = 45.0) { activity, _ ->
        ShadowAlertDialog.getLatestAlertDialog().dismiss()
        val playerView = activity.findViewById<android.widget.FrameLayout>(R.id.player_container)
            .getChildAt(0) as AspectRatioPlayerView
        val player = playerView.player!!
        val timeBar = activity.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress).apply {
            isEnabled = true
            setDuration(120_000L)
            setPosition(45_000L)
        }
        timeBar.onKeyDown(
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        )
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        org.junit.Assert.assertEquals(0L, player.currentPosition)
        assertTrue(!player.playWhenReady)
        org.junit.Assert.assertEquals(
            android.view.View.GONE,
            activity.findViewById<android.view.View>(R.id.playback_preview_container).visibility
        )
        timeBar.isEnabled = true
        timeBar.onKeyDown(
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        )
        assertTrue(player.playWhenReady)
        activity.finish()
    }

    @Test
    fun `metadata clear hides outgoing marker controls`() = withPlayback(resumeTimeSec = 0.0) { activity, _ ->
        val skip = activity.findViewById<android.view.View>(R.id.playback_skip_marker)
        skip.visibility = android.view.View.VISIBLE
        val markerButton = activity.findViewById<android.view.View>(R.id.playback_marker_button)
        markerButton.isEnabled = true

        PlaybackActivity::class.java.getDeclaredMethod("clearSceneMetadata")
            .apply { isAccessible = true }
            .invoke(activity)

        org.junit.Assert.assertEquals(android.view.View.GONE, skip.visibility)
        assertTrue(!markerButton.isEnabled)
        activity.finish()
    }

    @Test
    fun `settings button opens settings menu with only player configuration`() = withPlayback(resumeTimeSec = 0.0) { activity, previousDialog ->
        activity.findViewById<android.view.View>(R.id.playback_settings_button).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(previousDialog, dialog)
        assertTrue(dialog.isShowing)
        org.junit.Assert.assertEquals("Settings menu", dialogTitle(dialog))
        org.junit.Assert.assertEquals(
            listOf("Aspect ratio", "Stream quality", "Seek buttons"), dialogItems(dialog)
        )
        activity.finish()
    }

    @Test
    fun `personalization button opens scene personalization menu`() = withPlayback(resumeTimeSec = 0.0) { activity, previousDialog ->
        SinemaApp.instance.prefs.incognitoMode = false
        SinemaApp.instance.prefs.endlessMix = false
        val controls = activity.findViewById<android.view.ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls)
        val button = (0 until controls.childCount).map { controls.getChildAt(it) }
            .firstOrNull { it.contentDescription == "Personalization" }
            ?: throw AssertionError("Personalization button is missing")
        button.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(previousDialog, dialog)
        assertTrue(dialog.isShowing)
        org.junit.Assert.assertEquals("Personalization menu", dialogTitle(dialog))
        org.junit.Assert.assertEquals(
            listOf("★ Rate scene", "🔥 O +1", "🕶 Incognito: OFF", "♾ Endless Mix: OFF"),
            dialogItems(dialog)
        )
        activity.finish()
    }

    private fun dialogTitle(dialog: AlertDialog): String = shadowOf(dialog).title.toString()

    private fun dialogItems(dialog: AlertDialog): List<String> =
        (0 until dialog.listView.count).map { dialog.listView.adapter.getItem(it).toString() }

    @Test
    fun `watch next keeps playback save profile after mid save switch`() {
        ServerTransitionTestSupport.reset()
        SinemaApp.instance.prefs.channelsEnabled = true
        SinemaApp.instance.prefs.incognitoMode = false
        SinemaApp.instance.prefs.activeProfileId = "profile-a"
        val saveEntered = java.util.concurrent.CountDownLatch(1)
        val finishSave = java.util.concurrent.CountDownLatch(1)
        val responses = listOf(
            com.sinema.api.SequencedResponse(fullSceneResponse(45.0)),
            com.sinema.api.SequencedResponse("""{"data":{"sceneSaveActivity":true}}"""),
            com.sinema.api.SequencedResponse(com.sinema.api.testSceneListResponse(com.sinema.api.testScene("scene")))
        )
        withGraphqlServer(
            responses,
            responder = { body ->
                when {
                    body.contains("sceneSaveActivity") -> {
                        saveEntered.countDown()
                        finishSave.await(2, java.util.concurrent.TimeUnit.SECONDS)
                        responses[1]
                    }
                    body.contains("findScenes") -> responses[2]
                    else -> responses[0]
                }
            },
            configureSinemaApp = true
        ) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene", 45_000L,
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            controller.pause()
            assertTrue(saveEntered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            SinemaApp.instance.prefs.activeProfileId = "profile-b"
            finishSave.countDown()
            val saveQueue = PlaybackActivity::class.java.getDeclaredField("saveQueue")
                .apply { isAccessible = true }
                .get(controller.get()) as PlaybackSaveQueue
            kotlinx.coroutines.runBlocking {
                saveQueue.close()
                saveQueue.join()
            }
            controller.stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(com.sinema.util.TvChannelsIsolationTest.RecordingProvider.inserts.isEmpty())
        }
    }

    private fun withPlayback(
        resumeTimeSec: Double,
        resumeConfirmed: Boolean = false,
        assertions: (PlaybackActivity, AlertDialog?) -> Unit
    ) {
        withGraphqlServer(fullSceneResponse(resumeTimeSec), configureSinemaApp = true) { _, _ ->
            val previousDialog = ShadowAlertDialog.getLatestAlertDialog()
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeMs = if (resumeConfirmed) (resumeTimeSec * 1000).toLong() else 0L,
                resumeConfirmed = resumeConfirmed
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            assertions(controller.get(), previousDialog)
            controller.pause().stop().destroy()
        }
    }
}

internal fun fullSceneResponse(resumeTimeSec: Double): String = """
    {"data":{"findScene":{"id":"scene","title":"Scene","play_count":0,"rating100":null,
    "resume_time":$resumeTimeSec,"o_counter":0,"files":[{"path":"/scene.mp4","size":100,
    "duration":120.0,"width":1920,"height":1080}],"date":null,"details":null,"studio":null,
    "tags":[],"performers":[],"captions":[],"scene_markers":[],"galleries":[]}}}
""".trimIndent()
