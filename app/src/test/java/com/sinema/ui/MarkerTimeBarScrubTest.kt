package com.sinema.ui

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarkerTimeBarScrubTest {
    private var playWhenReady = false
    private var previewStops = 0
    private val seeks = mutableListOf<Long>()
    private lateinit var timeBar: MarkerTimeBar
    private lateinit var dwell: PausedSeekDwell

    @Before
    fun setUp() {
        playWhenReady = false
        previewStops = 0
        seeks.clear()
        dwell = PausedSeekDwell(
            Handler(Looper.getMainLooper()),
            { playWhenReady },
            { seeks += it },
            onComplete = {
                previewStops++
                timeBar.cancelKeyScrub()
            }
        )
        timeBar = MarkerTimeBar(RuntimeEnvironment.getApplication()).apply {
            setDuration(120_000L)
            setPosition(0L)
            setKeyTimeIncrements(10_000L, 30_000L)
            scrubPreviewListener = object : MarkerTimeBar.ScrubPreviewListener {
                override fun onPreviewStart(positionMs: Long) = dwell.onPositionChanged(positionMs)
                override fun onPreviewMove(positionMs: Long) = dwell.onPositionChanged(positionMs)
                override fun onPreviewStop(positionMs: Long, canceled: Boolean, fromKeyScrub: Boolean) {
                    previewStops++
                    if (!canceled && fromKeyScrub) seeks += positionMs
                    dwell.cancel()
                }
                override fun onPreviewPlayPause() {
                    playWhenReady = !playWhenReady
                }
            }
        }
        Robolectric.buildActivity(android.app.Activity::class.java).setup().get().setContentView(timeBar)
    }

    @Test
    fun `dpad scrub waits two seconds then seeks without resuming`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
        assertEquals(0, previewStops)
        assertTrue(seeks.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf(30_000L), seeks)
        assertEquals(1, previewStops)
        assertFalse(playWhenReady)
    }

    @Test
    fun `dpad movement restarts the two second dwell`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
        assertTrue(seeks.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf(20_000L), seeks)
        assertEquals(1, previewStops)
    }

    @Test
    fun `center commits the target and resumes playback`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf(30_000L), seeks)
        assertEquals(1, previewStops)
        assertTrue(playWhenReady)
    }

    @Test
    fun `center after dwell resumes without a duplicate seek`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf(30_000L), seeks)
        assertTrue(playWhenReady)
    }

    @Test
    fun `center repeats do not toggle transport again`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        timeBar.onKeyDown(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1)
        )
        assertTrue(playWhenReady)
    }

    @Test
    fun `disabled time bar does not toggle transport`() {
        timeBar.isEnabled = false
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(playWhenReady)
    }

    @Test
    fun `player progress cannot overwrite the displayed scrub target`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        timeBar.setPosition(90_000L)
        timeBar.cancelKeyScrub()
        dwell.cancel()
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(60_000L), seeks)
    }

    @Test
    fun `play cancellation prevents the dpad dwell seek`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        playWhenReady = true
        dwell.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(seeks.isEmpty())
    }
}
