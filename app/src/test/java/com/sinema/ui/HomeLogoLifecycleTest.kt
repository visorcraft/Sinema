package com.sinema.ui

import android.view.ViewGroup
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.withGraphqlServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class HomeLogoLifecycleTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
        SinemaApp.instance.updateCheckedThisSession = true
    }

    @Test
    fun `logo and listener are removed and recreated cleanly`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            val activity = controller.get()
            val fragment = mainFragment(activity)
            assertEquals(1, logoCount(activity))

            fragment.onDestroyView()
            assertEquals(0, logoCount(activity))
            assertNull(field(fragment, "logoView").get(fragment))
            assertNull(field(fragment, "logoScrollListener").get(fragment))
            var view = fragment.onCreateView(android.view.LayoutInflater.from(activity), android.widget.FrameLayout(activity), null)
            fragment.onViewCreated(view!!, null)
            assertEquals(1, logoCount(activity))

            fragment.onDestroyView()
            assertEquals(0, logoCount(activity))
            assertNull(field(fragment, "logoView").get(fragment))
            assertNull(field(fragment, "logoScrollListener").get(fragment))
            view = fragment.onCreateView(android.view.LayoutInflater.from(activity), android.widget.FrameLayout(activity), null)
            fragment.onViewCreated(view!!, null)
            assertEquals(1, logoCount(activity))
            controller.destroy()
        }
    }

    private fun mainFragment(activity: MainActivity): MainFragment =
        activity.supportFragmentManager.findFragmentById(R.id.main_frame) as MainFragment

    private fun logoCount(activity: MainActivity): Int {
        val root = activity.window.decorView as ViewGroup
        return ServerTransitionTestSupport.views(root).count { it.id == R.id.home_logo }
    }

    private fun field(fragment: MainFragment, name: String) =
        MainFragment::class.java.getDeclaredField(name).apply { isAccessible = true }
}
