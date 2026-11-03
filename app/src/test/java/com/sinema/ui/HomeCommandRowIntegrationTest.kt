package com.sinema.ui

import android.os.Looper
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import com.sinema.SinemaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class HomeCommandRowIntegrationTest {
    @Before
    fun configureApp() {
        val app = SinemaApp.instance
        app.prefs.serverUrl = "http://127.0.0.1:1"
        app.prefs.apiKey = "test"
        app.prefs.sessionCookie = ""
        app.prefs.authMode = "apikey"
        app.prefs.channelsEnabled = false
        app.pinVerifiedThisSession = true
        app.decoySession = false
        app.refreshApi()
    }

    @Test
    fun `pin command row is above browse and log out is first`() {
        val activity = launch(pin = true)
        val rows = homeRows(activity)
        val quick = rows.single { it.headerItem.id == 7L }
        val browse = rows.single { it.headerItem.id == 6L }
        assertEquals(listOf("Log Out", "Refresh", "Settings", "About"), items(quick))
        assertTrue(rows.indexOf(quick) < rows.indexOf(browse))
        assertFalse(items(browse).contains("Log Out"))
    }

    @Test
    fun `no pin starts commands with refresh`() {
        val activity = launch(pin = false)
        val quick = homeRows(activity).single { it.headerItem.id == 7L }
        assertEquals(listOf("Refresh", "Settings", "About"), items(quick))
    }

    @Test
    fun `log out clears only session verification and finishes`() {
        val app = SinemaApp.instance
        val activity = launch(pin = true)
        val fragment = activity.supportFragmentManager.findFragmentById(com.sinema.R.id.main_frame) as MainFragment
        fragment.onItemViewClickedListener.onItemClicked(null, "Log Out", null, null)
        assertFalse(app.pinVerifiedThisSession)
        assertTrue(app.prefs.hasPinSet())
        assertTrue(activity.isFinishing)
    }

    private fun launch(pin: Boolean): MainActivity {
        if (pin) SinemaApp.instance.prefs.setPinHash("1234")
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun homeRows(activity: MainActivity): List<ListRow> {
        val fragment = activity.supportFragmentManager.findFragmentById(com.sinema.R.id.main_frame) as MainFragment
        val adapter = fragment.adapter as ArrayObjectAdapter
        shadowOf(Looper.getMainLooper()).idle()
        return (0 until adapter.size()).mapNotNull { adapter.get(it) as? ListRow }
    }

    private fun items(row: ListRow): List<String> {
        val adapter = row.adapter as ArrayObjectAdapter
        return (0 until adapter.size()).map { adapter.get(it) as String }
    }
}
