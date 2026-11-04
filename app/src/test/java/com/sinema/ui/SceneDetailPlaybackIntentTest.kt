package com.sinema.ui

import android.os.Looper
import android.view.View
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.testScene
import com.sinema.api.withGraphqlServer
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SceneDetailPlaybackIntentTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `resume button emits confirmed resume intent`() = withSceneDetail { activity ->
        val resume = activity.findViewById<android.widget.Button>(R.id.btn_resume)
        assertTrue(resume.visibility == View.VISIBLE)
        resume.performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertTrue(SceneIntents.resumeConfirmedFrom(intent))
        assertEquals(45_000L, SceneIntents.resumeMsFrom(intent))
    }

    @Test
    fun `play button emits unconfirmed playback intent`() = withSceneDetail { activity ->
        activity.findViewById<android.widget.Button>(R.id.btn_play).performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertFalse(SceneIntents.resumeConfirmedFrom(intent))
        assertEquals(0L, SceneIntents.resumeMsFrom(intent))
    }

    private fun withSceneDetail(assertions: (SceneDetailActivity) -> Unit) {
        withGraphqlServer(fullSceneResponse(resumeTimeSec = 45.0), configureSinemaApp = true) { _, _ ->
            val context = RuntimeEnvironment.getApplication()
            val intent = SceneIntents.detail(context, testScene(id = "scene", resumeTimeSec = 45.0))
            val controller = Robolectric.buildActivity(SceneDetailActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            assertions(controller.get())
            controller.pause().stop().destroy()
        }
    }
}
