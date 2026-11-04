package com.sinema.ui

import android.os.Looper
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import com.sinema.SinemaApp
import com.sinema.api.SequencedResponse
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.testScene
import com.sinema.api.testSceneListResponse
import com.sinema.api.withGraphqlServer
import com.sinema.model.HomeContentRow
import com.sinema.model.HomeLayoutSettings
import java.util.concurrent.atomic.AtomicInteger
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
class HomeRecencyIntegrationTest {
    private val recencyRequests = AtomicInteger(0)

    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.channelsEnabled = false
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
        SinemaApp.instance.updateCheckedThisSession = true
        recencyRequests.set(0)
    }

    @Test
    fun `recently played and discovery share one ordered recency request`() {
        withHome(recentlyPlayedVisible = true) { activity ->
            awaitRow(activity, HomeContentRow.DISCOVER)
            awaitItems(activity, HomeContentRow.DISCOVER)
            awaitItems(activity, HomeContentRow.RECENTLY_PLAYED)
            assertEquals(1, recencyRequests.get())
            val recentlyPlayed = row(activity, HomeContentRow.RECENTLY_PLAYED)
            val discovery = row(activity, HomeContentRow.DISCOVER)
            assertEquals(
                "Scene",
                ((recentlyPlayed.adapter as ArrayObjectAdapter).get(0) as com.sinema.model.Scene).displayTitle
            )
            assertTrue(discovery.headerItem.name.contains("Because You Watched · Scene"))
        }
    }

    @Test
    fun `hidden recently played still shares recency with discovery`() {
        withHome(recentlyPlayedVisible = false) { activity ->
            awaitRow(activity, HomeContentRow.DISCOVER)
            awaitItems(activity, HomeContentRow.DISCOVER)
            assertEquals(1, recencyRequests.get())
            assertTrue(row(activity, HomeContentRow.DISCOVER).headerItem.name.contains("Because You Watched · Scene"))
        }
    }

    @Test
    fun `recency failure leaves independent row errors with one request`() {
        withHome(recentlyPlayedVisible = true, recencyFailure = true) { activity ->
            awaitRow(activity, HomeContentRow.RECENTLY_PLAYED)
            assertTrue(row(activity, HomeContentRow.RECENTLY_PLAYED).headerItem.name.contains("Unavailable"))
            assertEquals(1, recencyRequests.get())
            assertTrue(runCatching { row(activity, HomeContentRow.DISCOVER) }.getOrNull() == null)
        }
    }

    private fun withHome(
        recentlyPlayedVisible: Boolean,
        recencyFailure: Boolean = false,
        assertions: (MainActivity) -> Unit
    ) {
        val visible = buildList {
            if (recentlyPlayedVisible) add(HomeContentRow.RECENTLY_PLAYED)
            add(HomeContentRow.DISCOVER)
        }
        SinemaApp.instance.prefs.homeLayout = HomeLayoutSettings(
            rowOrder = visible,
            hiddenRows = (HomeContentRow.entries - visible.toSet()).toSet(),
            showHero = false,
            optionalRowsVersion = 1
        )
        val scene = testScene(id = "scene", title = "Scene", playCount = 1, lastPlayedAt = "2026-11-03T12:00:00Z")
        val responses = listOf(SequencedResponse(testGraphqlErrorResponse("fallback")))
        withGraphqlServer(
            responses,
            responder = { body ->
                when {
                    body.contains("\"play_count\":{\"value\":0") -> {
                        recencyRequests.incrementAndGet()
                        if (recencyFailure) {
                            SequencedResponse(testGraphqlErrorResponse("offline"), status = 500)
                        } else {
                            SequencedResponse(testSceneListResponse(scene))
                        }
                    }
                    body.contains("findScene(id") -> SequencedResponse(
                        fullSceneResponse(45.0).replace(
                            "\"studio\":null",
                            "\"studio\":{\"id\":\"studio\",\"name\":\"Studio\"}"
                        )
                    )
                    body.contains("findScenes") -> SequencedResponse(
                        testSceneListResponse(testScene(id = "related", title = "Related")).replace(
                            "\"files\":",
                            "\"studio\":{\"id\":\"studio\",\"name\":\"Studio\"},\"files\":"
                        )
                    )
                    else -> responses[0]
                }
            },
            configureSinemaApp = true
        ) { _, requests ->
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            try {
                assertions(activity)
            } catch (e: Throwable) {
                throw AssertionError("Home requests: ${requests()}", e)
            }
            activity.finish()
        }
    }

    private fun row(activity: MainActivity, rowType: HomeContentRow): ListRow {
        val fragment = activity.supportFragmentManager.findFragmentById(com.sinema.R.id.main_frame) as MainFragment
        val adapter = fragment.adapter as ArrayObjectAdapter
        return (0 until adapter.size()).mapNotNull { adapter.get(it) as? ListRow }
            .single { it.headerItem.id == testRowId(rowType) }
    }

    private fun testRowId(rowType: HomeContentRow): Long = when (rowType) {
        HomeContentRow.RECENTLY_PLAYED -> 3L
        HomeContentRow.DISCOVER -> 12L
        else -> throw IllegalArgumentException("Unexpected row in recency test: $rowType")
    }

    private fun awaitItems(activity: MainActivity, rowType: HomeContentRow) {
        repeat(500) {
            if ((row(activity, rowType).adapter as ArrayObjectAdapter).size() > 0) return
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        throw AssertionError("Row ${row(activity, rowType).headerItem.name} has no items")
    }

    private fun awaitRow(activity: MainActivity, rowType: HomeContentRow) {
        repeat(500) {
            runCatching { row(activity, rowType) }.getOrNull()?.let { return }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        throw AssertionError("Row ${rowType.label} was not loaded")
    }
}
