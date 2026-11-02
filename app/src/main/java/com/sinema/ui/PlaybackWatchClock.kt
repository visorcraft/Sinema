package com.sinema.ui

internal class PlaybackWatchClock {
    private var completedPlayingMs = 0L
    private var playingSinceMs: Long? = null
    private var reportedMs = 0L
    private var playing = false

    @Synchronized
    fun onIsPlayingChanged(isPlaying: Boolean, nowMs: Long) {
        if (isPlaying == playing) return
        if (isPlaying) {
            playingSinceMs = nowMs
        } else {
            completedPlayingMs += (nowMs - (playingSinceMs ?: nowMs)).coerceAtLeast(0L)
            playingSinceMs = null
        }
        playing = isPlaying
    }

    @Synchronized
    fun totalMs(nowMs: Long): Long = completedPlayingMs +
        (playingSinceMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)

    @Synchronized
    fun pendingMs(nowMs: Long): Long = (totalMs(nowMs) - reportedMs).coerceAtLeast(0L)

    @Synchronized
    fun markReported(durationMs: Long) {
        reportedMs += durationMs.coerceAtLeast(0L)
    }

    @Synchronized
    fun reset() {
        completedPlayingMs = 0L
        playingSinceMs = null
        reportedMs = 0L
        playing = false
    }
}
