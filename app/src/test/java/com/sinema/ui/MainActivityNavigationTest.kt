package com.sinema.ui

import android.app.AlertDialog
import android.os.SystemClock
import com.sinema.SinemaApp
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.withGraphqlServer
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class MainActivityNavigationTest {
    @Before
    fun configureApp() {
        val app = SinemaApp.instance
        app.prefs.serverUrl = "http://127.0.0.1:1"
        app.prefs.apiKey = "test"
        app.prefs.sessionCookie = ""
        app.prefs.authMode = "apikey"
        app.prefs.channelsEnabled = false
        app.prefs.setPinHash("1234")
        app.prefs.autoLockMinutes = 5
        app.pinVerifiedThisSession = true
        app.decoySession = false
        app.updateCheckedThisSession = true
        app.refreshApi()
    }

    @Test
    fun `returning from playback does not lock on stale idle timestamp`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val main = Robolectric.buildActivity(MainActivity::class.java).setup()
            main.pause().stop()
            withGraphqlServer(fullSceneResponse(0.0), configureSinemaApp = true) { _, _ ->
                val intent = SceneIntents.playback(
                    RuntimeEnvironment.getApplication(), "scene", resumeConfirmed = true
                )
                val playback = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()

                playback.pause()
                setStaleInteraction()
                resume(main)

                assertTrue(SinemaApp.instance.pinVerifiedThisSession)
                assertNull(shadowOf(main.get()).nextStartedActivity)
                playback.stop().destroy()
                main.destroy()
            }
        }
    }

    @Test
    fun `returning from background still locks after idle timeout`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val main = Robolectric.buildActivity(MainActivity::class.java).setup()
            main.pause().stop()
            setStaleInteraction()

            resume(main)

            assertFalse(SinemaApp.instance.pinVerifiedThisSession)
            assertEquals(PinActivity::class.java.name, shadowOf(main.get()).nextStartedActivity.component?.className)
            main.destroy()
        }
    }

    @Test
    fun `home back shows minimize and logout exit choices`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val main = Robolectric.buildActivity(MainActivity::class.java).setup()
            main.get().onBackPressedDispatcher.onBackPressed()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            assertEquals("Exit Sinema?", shadowOf(dialog).title.toString())
            assertEquals(listOf("Minimize", "Logout & Exit"), dialogItems(dialog))
            main.destroy()
        }
    }

    @Test
    fun `minimize backgrounds task and preserves session`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val main = Robolectric.buildActivity(MainActivity::class.java).setup()
            main.get().onBackPressedDispatcher.onBackPressed()
            shadowOf(ShadowAlertDialog.getLatestAlertDialog() ?: throw AssertionError("Exit dialog missing")).clickOnItem(0)

            assertTrue(shadowOf(main.get()).isTaskMovedToBack)
            assertFalse(main.get().isFinishing)
            assertTrue(SinemaApp.instance.pinVerifiedThisSession)
            main.destroy()
        }
    }

    @Test
    fun `logout and exit clears verification but keeps configured pin`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val main = Robolectric.buildActivity(MainActivity::class.java).setup()
            main.get().onBackPressedDispatcher.onBackPressed()
            shadowOf(ShadowAlertDialog.getLatestAlertDialog() ?: throw AssertionError("Exit dialog missing")).clickOnItem(1)

            assertTrue(main.get().isFinishing)
            assertFalse(SinemaApp.instance.pinVerifiedThisSession)
            assertTrue(SinemaApp.instance.prefs.hasPinSet())
            main.destroy()
        }
    }

    private fun resume(controller: ActivityController<MainActivity>) {
        controller.restart().resume()
        MainActivity::class.java.getDeclaredMethod("onResume").apply {
            isAccessible = true
        }.invoke(controller.get())
    }

    private fun setStaleInteraction() {
        val value = SystemClock.elapsedRealtime() - 6 * 60_000L
        SinemaApp::class.java.getDeclaredField("lastInteractionMs").apply {
            isAccessible = true
        }.setLong(SinemaApp.instance, value)
    }

    private fun dialogItems(dialog: AlertDialog): List<String> =
        (0 until dialog.listView.count).map { dialog.listView.adapter.getItem(it).toString() }
}
