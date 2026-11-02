package com.sinema.ui

import android.app.Activity
import android.content.Intent
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class GridColdLaunchTest {

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
    fun favoritesColdLaunchBindsControlsBeforeFragmentCreation() {
        assertColdLaunch(FavoritesActivity::class.java)
    }

    @Test
    fun groupsColdLaunchDoesNotRequireSceneFilters() {
        assertColdLaunch(GroupGridActivity::class.java)
    }

    @Test
    fun galleriesColdLaunchDoesNotRequireSceneFilters() {
        assertColdLaunch(GalleryGridActivity::class.java)
    }

    @Test
    fun markersColdLaunchUsesCustomFilterWithoutQuickFilters() {
        assertColdLaunch(MarkerBrowserActivity::class.java)
    }

    @Test
    fun entityScenesColdLaunchBindsFilterableGridBeforeFragmentCreation() {
        val context = SinemaApp.instance
        val entity = EntityItem(
            id = "7",
            name = "Tag",
            sceneCount = 1,
            imagePath = null,
            kind = EntityItem.Kind.TAG,
            rating100 = null
        )
        assertColdLaunch(
            EntityScenesActivity::class.java,
            EntityScenesActivity.intent(context, entity)
        )
    }

    private fun <T : Activity> assertColdLaunch(type: Class<T>, intent: Intent? = null) {
        val controller = if (intent == null) {
            Robolectric.buildActivity(type)
        } else {
            Robolectric.buildActivity(type, intent)
        }
        controller.setup().destroy()
    }
}
