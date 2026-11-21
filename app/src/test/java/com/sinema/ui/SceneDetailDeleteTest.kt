package com.sinema.ui

import android.app.AlertDialog
import android.os.Looper
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SequencedResponse
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
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SceneDetailDeleteTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `cancel warns about file deletion without sending mutation`() =
        withSceneDetail("""{"data":{"sceneDestroy":true}}""") { activity, requests ->
            activity.findViewById<android.widget.Button>(R.id.btn_delete).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog

            assertEquals("Delete video?", shadowOf(dialog).title.toString())
            val message = dialog.findViewById<TextView>(android.R.id.message).text.toString()
            assertTrue(message.contains("Scene"))
            assertTrue(message.contains("video file on disk"))
            assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus())

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(activity.isFinishing)
            assertTrue(requests().none { it.contains("sceneDestroy") })
        }

    @Test
    fun `confirm destroys scene and closes details`() =
        withSceneDetail("""{"data":{"sceneDestroy":true}}""") { activity, requests ->
            activity.findViewById<android.widget.Button>(R.id.btn_delete).performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            await {
                requests().any { it.contains("sceneDestroy") } && activity.isFinishing
            }

            assertTrue(requests().any {
                it.contains("sceneDestroy") && it.contains("\"delete_file\":true")
            })
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `failed deletion keeps details open and reenables action`() =
        withSceneDetail("""{"data":{"sceneDestroy":false}}""") { activity, requests ->
            val delete = activity.findViewById<android.widget.Button>(R.id.btn_delete)
            delete.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            await {
                requests().any { it.contains("sceneDestroy") } && delete.isEnabled
            }

            assertTrue(requests().any { it.contains("sceneDestroy") })
            assertFalse(activity.isFinishing)
            assertTrue(delete.isEnabled)
        }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun withSceneDetail(
        deleteResponse: String,
        assertions: (SceneDetailActivity, () -> List<String>) -> Unit
    ) {
        withGraphqlServer(
            responses = listOf(
                SequencedResponse(fullSceneResponse(resumeTimeSec = 0.0)),
                SequencedResponse(deleteResponse)
            ),
            configureSinemaApp = true
        ) { _, requests ->
            val context = RuntimeEnvironment.getApplication()
            val intent = SceneIntents.detail(context, testScene(id = "scene"))
            val controller = Robolectric.buildActivity(SceneDetailActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            assertions(controller.get(), requests)
            controller.pause().stop().destroy()
        }
    }
}
