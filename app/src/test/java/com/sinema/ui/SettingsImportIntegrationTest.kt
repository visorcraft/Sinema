package com.sinema.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import com.sinema.SinemaApp
import com.sinema.model.ServerProfile
import com.sinema.util.GlobalSearchProvider
import com.sinema.util.TvChannelsIsolationTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SettingsImportIntegrationTest {
    private val app get() = SinemaApp.instance
    private lateinit var activity: SettingsActivity
    private lateinit var fragment: SettingsFragment

    @Before
    fun setUp() {
        ServerTransitionTestSupport.reset()
        val profile = ServerProfile("profile-a", "A", "http://server-a", "key-a")
        app.prefs.profiles = listOf(profile)
        app.prefs.applyProfile(profile)
        app.prefs.channelsEnabled = true
        app.prefs.globalSearchEnabled = false
        app.refreshApi()
        activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        fragment = activity.supportFragmentManager.fragments.single() as SettingsFragment
    }

    @Test
    fun `import enables global search components immediately and on recreate`() {
        assertGlobalSearchImport(enabled = true)
    }

    @Test
    fun `import disables global search components immediately and on recreate`() {
        assertGlobalSearchImport(enabled = false)
    }

    @Test
    fun `channels disabled by import clears launcher entries`() {
        val json = snapshot { app.prefs.channelsEnabled = false }

        importBackup(json)

        ServerTransitionTestSupport.assertCleared()
    }

    @Test
    fun `unchanged identity and channels preserve launcher entries`() {
        val json = snapshot { }

        importBackup(json)

        assertEquals("sinema:legacy", TvChannelsIsolationTest.RecordingProvider.watchRows[1L])
    }

    @Test
    fun `same profile endpoint change clears launcher entries`() {
        val json = snapshot {
            app.prefs.profiles = listOf(app.prefs.profiles.single().copy(serverUrl = "http://server-b"))
            app.prefs.applyProfile(app.prefs.profiles.single())
        }

        importBackup(json)

        ServerTransitionTestSupport.assertCleared()
    }

    private fun assertGlobalSearchImport(enabled: Boolean) {
        val json = snapshot { app.prefs.globalSearchEnabled = enabled }
        app.prefs.globalSearchEnabled = !enabled

        importBackup(json)
        assertComponents(enabled)
        SinemaApp.instance.onCreate()
        assertComponents(enabled)
    }

    private fun assertComponents(enabled: Boolean) {
        val expected = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        listOf(
            ComponentName(activity, GlobalSearchProvider::class.java),
            ComponentName(activity, GlobalSearchActivity::class.java)
        ).forEach { component ->
            val state = activity.packageManager.getComponentEnabledSetting(component)
            if (enabled) {
                assertEquals(expected, state)
            } else {
                org.junit.Assert.assertTrue(
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                )
            }
        }
    }

    private fun snapshot(change: () -> Unit): String {
        val profiles = app.prefs.profiles
        val channels = app.prefs.channelsEnabled
        val globalSearch = app.prefs.globalSearchEnabled
        change()
        val json = app.prefs.exportSnapshot()
        app.prefs.profiles = profiles
        app.prefs.applyProfile(profiles.single())
        app.prefs.channelsEnabled = channels
        app.prefs.globalSearchEnabled = globalSearch
        return json
    }

    private fun importBackup(json: String) {
        val uri = android.net.Uri.parse("content://test/backup.json")
        shadowOf(activity.contentResolver).registerInputStream(
            uri,
            java.io.ByteArrayInputStream(json.toByteArray())
        )
        ServerTransitionTestSupport.button(fragment.requireView(), "System").performClick()
        ServerTransitionTestSupport.button(fragment.requireView(), "Import Settings Backup").performClick()
        val confirmation = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as android.app.AlertDialog
        confirmation.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val launched = shadowOf(activity).nextStartedActivityForResult
        activity.activityResultRegistry.dispatchResult(
            launched.requestCode,
            android.app.Activity.RESULT_OK,
            android.content.Intent().setData(uri)
        )
    }
}
