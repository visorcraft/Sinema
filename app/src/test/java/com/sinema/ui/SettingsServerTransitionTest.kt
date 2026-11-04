package com.sinema.ui

import android.widget.EditText
import com.sinema.SinemaApp
import com.sinema.model.ServerProfile
import com.sinema.ui.ServerTransitionTestSupport.assertCleared
import com.sinema.ui.ServerTransitionTestSupport.button
import com.sinema.util.TvChannelsIsolationTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SettingsServerTransitionTest {
    private val app get() = SinemaApp.instance
    private lateinit var activity: SettingsActivity
    private lateinit var fragment: SettingsFragment

    @Before
    fun setUp() {
        ServerTransitionTestSupport.reset()
        val profile = ServerProfile("profile-a", "A", "http://server-a", "key-a")
        app.prefs.profiles = listOf(profile)
        app.prefs.applyProfile(profile)
        app.refreshApi()
        activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        fragment = activity.supportFragmentManager.fragments.single() as SettingsFragment
    }

    @Test
    fun `server url save clears old launcher entries`() {
        button(fragment.requireView(), "Server\n").performClick()
        ServerTransitionTestSupport.views(fragment.requireView())
            .filterIsInstance<EditText>()
            .single { it.text.toString() == "http://server-a" }
            .setText("http://server-b")

        button(fragment.requireView(), "Save Server Settings").performClick()

        assertEquals("http://server-b", app.prefs.serverUrl)
        assertCleared()
    }

    @Test
    fun `credential only save preserves launcher entries`() {
        button(fragment.requireView(), "Server\n").performClick()
        button(fragment.requireView(), "Save Server Settings").performClick()

        assertEquals("sinema:legacy", TvChannelsIsolationTest.RecordingProvider.watchRows[1L])
    }

    @Test
    fun `setup launched profile addition clears old launcher entries`() {
        button(fragment.requireView(), "Server\n").performClick()
        button(fragment.requireView(), "+ Add Server").performClick()
        val launched = org.robolectric.Shadows.shadowOf(activity).nextStartedActivityForResult
        app.prefs.serverUrl = "http://server-b"
        app.prefs.apiKey = "key-b"
        app.prefs.authMode = "apikey"
        app.prefs.sessionCookie = ""

        activity.activityResultRegistry.dispatchResult(
            launched.requestCode,
            android.app.Activity.RESULT_OK,
            null
        )

        assertEquals("http://server-b", app.prefs.serverUrl)
        assertCleared()
    }

    @Test
    fun `backup import clears launcher entries through activity result`() {
        val current = app.prefs.profiles.single()
        val imported = ServerProfile("profile-b", "B", "http://server-b", "key-b")
        app.prefs.profiles = listOf(imported)
        app.prefs.applyProfile(imported)
        val json = app.prefs.exportSnapshot()
        app.prefs.profiles = listOf(current)
        app.prefs.applyProfile(current)
        val uri = android.net.Uri.parse("content://test/backup.json")
        org.robolectric.Shadows.shadowOf(activity.contentResolver).registerInputStream(
            uri,
            java.io.ByteArrayInputStream(json.toByteArray())
        )

        button(fragment.requireView(), "System").performClick()
        button(fragment.requireView(), "Import Settings Backup").performClick()
        val confirmation = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as android.app.AlertDialog
        confirmation.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val launched = org.robolectric.Shadows.shadowOf(activity).nextStartedActivityForResult
        activity.activityResultRegistry.dispatchResult(
            launched.requestCode,
            android.app.Activity.RESULT_OK,
            android.content.Intent().setData(uri)
        )

        assertEquals("profile-b", app.prefs.activeProfileId)
        assertCleared()
    }
}
