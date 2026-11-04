package com.sinema.ui

internal fun shouldAutoLock(
    isResumed: Boolean,
    hasPin: Boolean,
    pinVerified: Boolean,
    decoySession: Boolean,
    lockMinutes: Int,
    idleMs: Long
): Boolean {
    val lockMs = lockMinutes.toLong() * 60_000L
    return isResumed && hasPin && pinVerified && !decoySession && lockMs > 0L && idleMs >= lockMs
}
