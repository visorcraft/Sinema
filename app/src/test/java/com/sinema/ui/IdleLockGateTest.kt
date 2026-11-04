package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleLockGateTest {
    @Test
    fun stoppedMainNeverPresentsLock() {
        assertFalse(
            shouldAutoLock(
                isResumed = false,
                hasPin = true,
                pinVerified = true,
                decoySession = false,
                lockMinutes = 5,
                idleMs = 10 * 60_000L
            )
        )
    }

    @Test
    fun resumedMainBelowTimeoutDoesNotLock() {
        assertFalse(
            shouldAutoLock(
                isResumed = true,
                hasPin = true,
                pinVerified = true,
                decoySession = false,
                lockMinutes = 5,
                idleMs = 4 * 60_000L
            )
        )
    }

    @Test
    fun resumedMainAtTimeoutLocks() {
        assertTrue(
            shouldAutoLock(
                isResumed = true,
                hasPin = true,
                pinVerified = true,
                decoySession = false,
                lockMinutes = 5,
                idleMs = 5 * 60_000L
            )
        )
    }

    @Test
    fun recentActivityTransitionPreventsStaleLock() {
        assertFalse(
            shouldAutoLock(
                isResumed = true,
                hasPin = true,
                pinVerified = true,
                decoySession = false,
                lockMinutes = 5,
                idleMs = 100_000L - 95_000L
            )
        )
    }
}
