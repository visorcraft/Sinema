package com.sinema.ui

import com.sinema.SinemaApp
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SetupWebServerLifecycleTest {
    private var activity: SetupActivity? = null
    private var controller: org.robolectric.android.controller.ActivityController<SetupActivity>? = null

    @Before
    fun configureApp() {
        val app = SinemaApp.instance
        app.prefs.serverUrl = "http://127.0.0.1:1"
        app.prefs.apiKey = "test"
        app.prefs.sessionCookie = ""
        app.prefs.authMode = "apikey"
        app.refreshApi()
    }

    @After
    fun tearDown() {
        activity?.let { current ->
            runCatching {
                val field = SetupActivity::class.java.getDeclaredField("webSetupServer")
                field.isAccessible = true
                (field.get(current) as? WebSetupServer)?.close()
            }
        }
        controller?.destroy()
    }

    @Test
    fun backFromWebSetupStopsTheListeningServer() {
        val setup = launchSetup()
        startServer(setup)
        assertNotNull(serverField(setup))

        setup.onBackPressedDispatcher.onBackPressed()

        assertNull(serverField(setup))
    }

    @Test
    fun occupiedPortShowsFailureWithoutThrowingOrStartingServer() {
        ServerSocket(8888).use {
            val setup = launchSetup()

            startServer(setup)

            assertNull(serverField(setup))
        }
    }

    private fun launchSetup(): SetupActivity {
        val created = Robolectric.buildActivity(SetupActivity::class.java).setup()
        controller = created
        return created.get().also { activity = it }
    }

    private fun startServer(activity: SetupActivity) {
        val method = SetupActivity::class.java.getDeclaredMethod(
            "startHttpServer",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(activity, "http://127.0.0.1:1", "token")
    }

    private fun serverField(activity: SetupActivity): WebSetupServer? {
        val field = SetupActivity::class.java.getDeclaredField("webSetupServer")
        field.isAccessible = true
        return field.get(activity) as? WebSetupServer
    }
}
