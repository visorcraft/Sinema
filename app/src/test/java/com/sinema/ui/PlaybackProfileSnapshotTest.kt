package com.sinema.ui

import android.os.Looper
import com.sinema.SinemaApp
import com.sinema.api.SequencedResponse
import com.sinema.api.withGraphqlServer
import com.sinema.util.SceneIntents
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
class PlaybackProfileSnapshotTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
        SinemaApp.instance.prefs.incognitoMode = false
    }

    @Test
    fun `o counter uses api captured with scene`() {
        val responses = listOf(
            SequencedResponse(fullSceneResponse(0.0)),
            SequencedResponse("""{"data":{"sceneIncrementO":true}}""")
        )
        withGraphqlServer(
            responses,
            responder = { body ->
                if (body.contains("sceneIncrementO")) responses[1] else responses[0]
            },
            configureSinemaApp = true
        ) { _, requestBody ->
            val intent = SceneIntents.playback(RuntimeEnvironment.getApplication(), "scene")
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val currentScene = PlaybackActivity::class.java.getDeclaredField("sceneId")
                .apply { isAccessible = true }
                .get(controller.get()) as String
            assertTrue(currentScene == "scene")

            PlaybackActivity::class.java.getDeclaredMethod("incrementOCount")
                .apply { isAccessible = true }
                .invoke(controller.get())
            SinemaApp.instance.prefs.serverUrl = "http://127.0.0.1:1"
            SinemaApp.instance.refreshApi()
            shadowOf(Looper.getMainLooper()).idle()

            repeat(100) {
                if (requestBody().any { it.contains("sceneIncrementO") }) return@repeat
                Thread.sleep(20)
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(
                "Requests: ${requestBody()} toast=${org.robolectric.shadows.ShadowToast.getTextOfLatestToast()}",
                requestBody().any { it.contains("sceneIncrementO") }
            )
            controller.pause().stop().destroy()
        }
    }
}
