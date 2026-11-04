package com.sinema.ui

import android.os.Handler

internal class PausedSeekDwell(
    private val handler: Handler,
    private val playWhenReady: () -> Boolean,
    private val seekTo: (Long) -> Unit,
    private val onComplete: () -> Unit = {}
) {
    private var targetMs: Long? = null
    private val seek = Runnable {
        val target = targetMs ?: return@Runnable
        targetMs = null
        if (!playWhenReady()) seekTo(target)
        onComplete()
    }

    fun onPositionChanged(positionMs: Long) {
        handler.removeCallbacks(seek)
        targetMs = positionMs
        handler.postDelayed(seek, 2_000L)
    }

    fun cancel() {
        handler.removeCallbacks(seek)
        targetMs = null
    }
}
