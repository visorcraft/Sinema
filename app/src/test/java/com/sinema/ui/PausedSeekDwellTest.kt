package com.sinema.ui

import android.os.Handler
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PausedSeekDwellTest {
    private var playWhenReady = false
    private val seeks = mutableListOf<Long>()
    private lateinit var dwell: PausedSeekDwell

    @Before
    fun setUp() {
        playWhenReady = false
        seeks.clear()
        dwell = PausedSeekDwell(
            Handler(Looper.getMainLooper()),
            { playWhenReady },
            { seeks += it }
        )
    }

    @Test
    fun `stationary paused target seeks once after two seconds without playing`() {
        dwell.onPositionChanged(42_000L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
        assertTrue(seeks.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf(42_000L), seeks)
        assertFalse(playWhenReady)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(listOf(42_000L), seeks)
    }

    @Test
    fun `movement replaces pending target and cancel prevents seek`() {
        dwell.onPositionChanged(10_000L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        dwell.onPositionChanged(20_000L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
        assertTrue(seeks.isEmpty())
        dwell.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun `cancel after play request prevents pending dwell seek`() {
        dwell.onPositionChanged(20_000L)
        playWhenReady = true
        dwell.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun `play request before dwell fire suppresses stale callback`() {
        dwell.onPositionChanged(20_000L)
        playWhenReady = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(seeks.isEmpty())
    }
}
