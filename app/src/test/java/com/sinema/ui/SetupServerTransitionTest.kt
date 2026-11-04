package com.sinema.ui

import android.os.Looper
import android.widget.EditText
import com.sinema.SinemaApp
import com.sinema.model.ServerProfile
import com.sinema.ui.ServerTransitionTestSupport.assertCleared
import com.sinema.ui.ServerTransitionTestSupport.button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SetupServerTransitionTest {
    private val app get() = SinemaApp.instance

    @Before
    fun setUp() {
        ServerTransitionTestSupport.reset()
        val profile = ServerProfile("profile-a", "A", "http://server-a", "key-a")
        app.prefs.profiles = listOf(profile)
        app.prefs.applyProfile(profile)
        app.refreshApi()
    }

    @Test
    fun `manual initial setup clears legacy launcher entries`() {
        app.prefs.profiles = emptyList()
        app.prefs.activeProfileId = ""
        app.prefs.serverUrl = ""
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        button(activity.window.decorView, "Enter API Key Manually").performClick()
        val edits = ServerTransitionTestSupport.views(activity.window.decorView)
            .filterIsInstance<EditText>()
        edits.single { it.text.toString().startsWith("http") }.setText("http://server-b")
        edits.single { it.text.toString().isBlank() }.setText("key-b")

        button(activity.window.decorView, "Save & Continue").performClick()

        assertEquals("http://server-b", app.prefs.serverUrl)
        assertTrue(app.prefs.activeProfileId.isNotBlank())
        assertEquals(1, app.prefs.profiles.size)
        assertCleared()
    }

    @Test
    fun `web setup completion clears old launcher entries`() {
        app.prefs.profiles = emptyList()
        app.prefs.activeProfileId = ""
        app.prefs.serverUrl = ""
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val started = SetupActivity::class.java.getDeclaredMethod(
            "startHttpServer", String::class.java, String::class.java
        ).apply { isAccessible = true }.invoke(activity, "http://server-a", "token") as Boolean
        assertTrue(started)
        val server = SetupActivity::class.java.getDeclaredField("webSetupServer")
            .apply { isAccessible = true }
            .get(activity) as WebSetupServer

        val body = form("serverUrl" to "http://server-b", "apiKey" to "key-b", "setupToken" to "token")
        val response = Socket(InetAddress.getLoopbackAddress(), server.localPort).use { socket ->
            socket.soTimeout = 2_000
            socket.getOutputStream().apply {
                write(("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body").toByteArray())
                flush()
            }
            socket.shutdownOutput()
            socket.getInputStream().bufferedReader().readText()
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(response.startsWith("HTTP/1.1 200"))
        assertEquals("http://server-b", app.prefs.serverUrl)
        assertTrue(app.prefs.activeProfileId.isNotBlank())
        assertEquals(1, app.prefs.profiles.size)
        assertCleared()
    }

    private fun form(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }
}
