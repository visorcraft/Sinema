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
    fun `settings button opens Sinema player menu`() = withPlayback(resumeTimeSec = 0.0) { activity, previousDialog ->
        activity.findViewById<android.view.View>(R.id.playback_settings_button).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(previousDialog, dialog)
        assertTrue(dialog.isShowing)
        assertTrue(dialog.listView.count > 0)
        activity.finish()
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
