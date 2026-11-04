package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWatchClockTest {
    @Test
    fun pausedIntervalsAreExcludedFromPlayedAndPendingTime() {
        val clock = PlaybackWatchClock()
        clock.onIsPlayingChanged(true, 0)
        clock.onIsPlayingChanged(false, 10_000)
        clock.onIsPlayingChanged(false, 20_000)
        clock.onIsPlayingChanged(true, 30_000)

        assertEquals(15_000, clock.totalMs(35_000))
        assertEquals(15_000, clock.pendingMs(35_000))
    }

    @Test
    fun reportedTimeIsNotSentAgain() {
        val clock = PlaybackWatchClock()
        clock.onIsPlayingChanged(true, 0)
        clock.onIsPlayingChanged(false, 15_000)
        clock.markReported(15_000)
        clock.onIsPlayingChanged(true, 20_000)

        assertEquals(0, clock.pendingMs(20_000))
        assertEquals(5_000, clock.pendingMs(25_000))
        assertEquals(20_000, clock.totalMs(25_000))
    }

    @Test
    fun unreportedTimeRemainsPendingUntilMarked() {
        val clock = PlaybackWatchClock()
        clock.onIsPlayingChanged(true, 0)
        clock.onIsPlayingChanged(false, 8_000)

        assertEquals(8_000, clock.pendingMs(9_000))
        assertEquals(8_000, clock.pendingMs(10_000))
    }

    @Test
    fun newClockStartsWithoutPlayedOrReportedTimeForANewScene() {
        val clock = PlaybackWatchClock()
        clock.onIsPlayingChanged(true, 20_000)

        assertEquals(2_000, clock.totalMs(22_000))
        assertEquals(2_000, clock.pendingMs(22_000))
    }
}
