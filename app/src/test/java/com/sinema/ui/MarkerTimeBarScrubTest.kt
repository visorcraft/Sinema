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
        dwell = PausedSeekDwell(Handler(Looper.getMainLooper()), { playWhenReady }, { seeks += it })
        timeBar = MarkerTimeBar(RuntimeEnvironment.getApplication()).apply {
            setDuration(120_000L)
            setPosition(0L)
            setKeyTimeIncrements(10_000L, 30_000L)
            scrubPreviewListener = object : MarkerTimeBar.ScrubPreviewListener {
                override fun onPreviewStart(positionMs: Long) = dwell.onPositionChanged(positionMs)
                override fun onPreviewMove(positionMs: Long) = dwell.onPositionChanged(positionMs)
                override fun onPreviewStop(positionMs: Long, canceled: Boolean) {
                    previewStops++
                    if (!canceled) seeks += positionMs
                    dwell.cancel()
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
        assertEquals(0, previewStops)
    }

    @Test
    fun `center commits the target without requesting playback`() {
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        timeBar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf(30_000L), seeks)
        assertEquals(1, previewStops)
        assertFalse(playWhenReady)
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
