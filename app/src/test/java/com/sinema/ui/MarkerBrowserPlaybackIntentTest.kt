package com.sinema.ui

import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.withGraphqlServer
import com.sinema.model.SceneMarkerItem
import com.sinema.util.SceneIntents
import org.junit.Assert.assertEquals
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
class MarkerBrowserPlaybackIntentTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @Test
    fun `timestamp marker emits confirmed playback intent`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val controller = Robolectric.buildActivity(MarkerBrowserActivity::class.java).setup()
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as MarkerBrowserFragment
            fragment.onItemViewClickedListener!!.onItemClicked(
                null,
                SceneMarkerItem(
                    id = "marker", title = "Chapter", seconds = 42.5,
                    sceneId = "scene", sceneTitle = "Scene", sceneWidth = 1920, sceneHeight = 1080,
                    primaryTagId = "tag", primaryTagName = "Tag"
                ),
                null,
                null
            )
            val intent = shadowOf(activity).nextStartedActivity
            assertTrue(SceneIntents.resumeConfirmedFrom(intent))
            assertEquals(42_500L, SceneIntents.resumeMsFrom(intent))
            controller.pause().stop().destroy()
        }
    }
}
