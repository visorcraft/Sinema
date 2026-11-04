package com.sinema.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import com.sinema.SinemaApp
import com.sinema.model.ServerProfile
import com.sinema.ui.ServerTransitionTestSupport.assertCleared
import com.sinema.ui.ServerTransitionTestSupport.button
import com.sinema.ui.ServerTransitionTestSupport.buttons
import com.sinema.util.TvChannelsIsolationTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class ServerProfileTransitionTest {
    private val app get() = SinemaApp.instance
    private lateinit var activity: android.content.Context
    private lateinit var layout: LinearLayout
    private lateinit var primary: ServerProfile
    private lateinit var secondary: ServerProfile

    @Before
    fun setUp() {
        ServerTransitionTestSupport.reset()
        primary = ServerProfile("profile-a", "A", "http://server-a", "key-a")
        secondary = ServerProfile("profile-b", "B", "http://server-b", "key-b")
        app.prefs.profiles = listOf(primary, secondary)
        app.prefs.applyProfile(primary)
        app.refreshApi()
        activity = android.view.ContextThemeWrapper(SinemaApp.instance, com.sinema.R.style.Theme_Sinema)
        layout = LinearLayout(activity)
    }

    @Test
    fun `profile switch clears old launcher entries`() {
        ServerProfileSection.build(activity, layout) {}
        button(layout, "B\n").performClick()
        positiveDialog()

        assertEquals("profile-b", app.prefs.activeProfileId)
        assertCleared()
    }

    @Test
    fun `active profile removal clears old launcher entries`() {
        ServerProfileSection.build(activity, layout) {}
        buttons(layout, "...").first().performClick()
        chooseDialogItem(2)
        positiveDialog()

        assertEquals("profile-b", app.prefs.activeProfileId)
        assertCleared()
    }

    @Test
    fun `rename only preserves api and does not clear launcher`() {
        ServerProfileSection.build(activity, layout) {}
        val api = app.api
        val queries = TvChannelsIsolationTest.RecordingProvider.queries

        buttons(layout, "...").first().performClick()
        chooseDialogItem(0)
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        ServerTransitionTestSupport.views(dialog.window!!.decorView)
            .filterIsInstance<EditText>()
            .single()
            .setText("Renamed")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        assertSame(api, app.api)
        assertEquals(queries, TvChannelsIsolationTest.RecordingProvider.queries)
    }

    @Test
    fun `tls only refresh preserves launcher entries`() {
        ServerProfileSection.build(activity, layout) {}
        val api = app.api
        val queries = TvChannelsIsolationTest.RecordingProvider.queries

        ServerProfileSection::class.java.getDeclaredMethod(
            "applySelfSigned",
            android.content.Context::class.java,
            ServerProfile::class.java,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(ServerProfileSection, activity, primary, true)

        assertNotSame(api, app.api)
        assertEquals(queries, TvChannelsIsolationTest.RecordingProvider.queries)
        assertEquals("sinema:legacy", TvChannelsIsolationTest.RecordingProvider.watchRows[1L])
    }

    private fun chooseDialogItem(index: Int) {
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.listView.performItemClick(null, index, 0L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun positiveDialog() {
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}
