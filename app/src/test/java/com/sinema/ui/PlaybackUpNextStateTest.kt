package com.sinema.ui

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.withGraphqlServer
import com.sinema.util.DiscreetMode
import com.sinema.util.PlaybackQueue
import com.sinema.util.SceneIntents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class PlaybackUpNextStateTest {
    private val next = PlaybackQueue.Item("next-1", "Next Scene", 95.0, playCount = 1)

    @Test
    fun `computeUpNextBanner appears only in the final 15 seconds while playing`() {
        assertNull(
            computeUpNextBanner(
                currentPositionMs = 40_000L,
                durationMs = 60_000L,
                isPlaying = true,
                isScrubbing = false,
                isLooping = false,
                nextItem = next,
                currentSceneId = "cur",
                dismissedForSceneId = null,
                discreetMode = false
            )
        )

        val visible = computeUpNextBanner(
            currentPositionMs = 50_200L,
            durationMs = 60_000L,
            isPlaying = true,
            isScrubbing = false,
            isLooping = false,
            nextItem = next,
            currentSceneId = "cur",
            dismissedForSceneId = null,
            discreetMode = false
        )
        assertNotNull(visible)
        assertEquals("next-1", visible!!.itemId)
        assertEquals("Next Scene", visible.title)
        assertEquals("1:35", visible.durationText)
        assertEquals(10, visible.countdownSeconds)
    }

    @Test
    fun `computeUpNextBanner suppresses for loop scrub pause short clip and dismissal`() {
        assertNull(computeUpNextBanner(50_000L, 60_000L, isPlaying = false, isScrubbing = false, isLooping = false, next, "cur", null, false))
        assertNull(computeUpNextBanner(50_000L, 60_000L, isPlaying = true, isScrubbing = true, isLooping = false, next, "cur", null, false))
        assertNull(computeUpNextBanner(50_000L, 60_000L, isPlaying = true, isScrubbing = false, isLooping = true, next, "cur", null, false))
        assertNull(computeUpNextBanner(20_000L, 25_000L, isPlaying = true, isScrubbing = false, isLooping = false, next, "cur", null, false))
        assertNull(computeUpNextBanner(50_000L, 60_000L, isPlaying = true, isScrubbing = false, isLooping = false, next, "cur", "cur", false))
    }

    @Test
    fun `computeUpNextBanner masks title in discreet mode`() {
        val state = computeUpNextBanner(
            currentPositionMs = 55_000L,
            durationMs = 60_000L,
            isPlaying = true,
            isScrubbing = false,
            isLooping = false,
            nextItem = next,
            currentSceneId = "cur",
            dismissedForSceneId = null,
            discreetMode = true
        )
        assertNotNull(state)
        assertEquals(DiscreetMode.neutralTitle("next-1"), state!!.title)
    }

    @Test
    fun `buildUpNextRowModel disables move up on first upcoming and move down on last`() {
        val currentRow = buildUpNextRowModel(next, position = 0, totalCount = 3, isCurrent = true, hasCurrent = true, discreetMode = false)
        assertEquals("NOW", currentRow.badgeText)
        assertFalse(currentRow.canMoveUp)
        assertFalse(currentRow.canMoveDown)
        assertFalse(currentRow.canRemove)

        val firstUpcoming = buildUpNextRowModel(next, position = 1, totalCount = 3, isCurrent = false, hasCurrent = true, discreetMode = false)
        assertEquals("#1", firstUpcoming.badgeText)
        assertFalse(firstUpcoming.canMoveUp)
        assertTrue(firstUpcoming.canMoveDown)
        assertTrue(firstUpcoming.canRemove)
        assertEquals("1:35 · Watched", firstUpcoming.metaText)

        val lastUpcoming = buildUpNextRowModel(next.copy(playCount = 0), position = 2, totalCount = 3, isCurrent = false, hasCurrent = true, discreetMode = true)
        assertEquals("#2", lastUpcoming.badgeText)
        assertTrue(lastUpcoming.canMoveUp)
        assertFalse(lastUpcoming.canMoveDown)
        assertTrue(lastUpcoming.canRemove)
        assertEquals(DiscreetMode.neutralTitle("next-1"), lastUpcoming.titleText)
        assertEquals("1:35", lastUpcoming.metaText)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class UpNextAdapterAndHudTest {
    @Before
    fun configureApp() {
        PlaybackQueue.clear()
        SinemaApp.instance.prefs.apiKey = "test"
        SinemaApp.instance.prefs.sessionCookie = ""
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.prefs.discreetMode = false
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
    }

    @After
    fun cleanUp() {
        PlaybackQueue.clear()
    }

    @Test
    fun `adapter disables move down on last row and notifies queue mutations`() {
        PlaybackQueue.start(
            listOf(
                PlaybackQueue.Item("s0", "Current", 60.0),
                PlaybackQueue.Item("s1", "First Next", 90.0),
                PlaybackQueue.Item("s2", "Last Next", 120.0)
            ),
            startAt = 0
        )
        var mutations = 0
        val context = RuntimeEnvironment.getApplication()
        val adapter = UpNextAdapter(
            context = context,
            onQueueChanged = { mutations++ },
            discreetMode = { false },
            onJump = {}
        )
        adapter.refresh()
        val parent = FrameLayout(context)

        val firstRow = adapter.getView(1, null, parent)
        assertFalse(firstRow.findViewById<Button>(R.id.up_next_move_up).isEnabled)
        assertTrue(firstRow.findViewById<Button>(R.id.up_next_move_down).isEnabled)

        val lastRow = adapter.getView(2, null, parent)
        assertTrue(lastRow.findViewById<Button>(R.id.up_next_move_up).isEnabled)
        assertFalse(lastRow.findViewById<Button>(R.id.up_next_move_down).isEnabled)

        lastRow.findViewById<Button>(R.id.up_next_move_up).performClick()
        assertEquals(1, mutations)
        assertEquals(listOf("s2", "s1"), PlaybackQueue.remaining().map { it.id })
    }

    @Test
    fun `discreet mode masks top bar up next title and hides thumbnail`() {
        SinemaApp.instance.prefs.discreetMode = true
        PlaybackQueue.start(
            listOf(
                PlaybackQueue.Item("scene", "Current Secret", 120.0),
                PlaybackQueue.Item("next-7", "Secret Upcoming", 185.0)
            ),
            startAt = 0
        )
        withGraphqlServer(fullSceneResponse(0.0), configureSinemaApp = true) { _, _ ->
            val intent = SceneIntents.playback(
                RuntimeEnvironment.getApplication(), "scene",
                resumeConfirmed = true
            )
            val controller = Robolectric.buildActivity(PlaybackActivity::class.java, intent).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()

            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.playback_next_up).visibility)
            val title = activity.findViewById<TextView>(R.id.playback_next_title).text.toString()
            assertFalse(title.contains("Secret"))
            assertEquals(DiscreetMode.neutralTitle("next-7"), title)
            assertEquals("3:05", activity.findViewById<TextView>(R.id.playback_next_duration).text.toString())
            assertEquals(View.GONE, activity.findViewById<View>(R.id.playback_next_thumb).visibility)
            controller.pause().stop().destroy()
        }
    }
}
