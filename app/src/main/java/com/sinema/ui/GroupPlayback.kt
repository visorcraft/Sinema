package com.sinema.ui

import com.sinema.model.Scene

object GroupPlayback {
    fun resumeIndex(scenes: List<Scene>): Int {
        if (scenes.isEmpty()) return -1
        val partial = scenes.indexOfFirst { it.resumeTime > 5.0 }
        if (partial >= 0) return partial
        val unwatched = scenes.indexOfFirst { !it.isWatched }
        return if (unwatched >= 0) unwatched else 0
    }

    fun resumePositionMs(scene: Scene): Long =
        if (scene.resumeTime > 5.0) (scene.resumeTime * 1000).toLong() else 0L
}
