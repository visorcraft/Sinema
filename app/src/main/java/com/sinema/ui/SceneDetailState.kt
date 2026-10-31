package com.sinema.ui

import com.sinema.model.Scene

internal class SceneDetailState(initialScene: Scene) {
    var scene = initialScene
        private set

    private var refreshGeneration = 0

    fun startRefresh(): Int = ++refreshGeneration

    fun applyRefresh(generation: Int, refreshedScene: Scene): Boolean {
        if (generation != refreshGeneration) return false
        scene = refreshedScene
        return true
    }

    fun applyWatchedToggle(wasWatched: Boolean) {
        scene = scene.copy(playCount = WatchedToggle.nextPlayCount(scene.playCount, wasWatched))
        refreshGeneration++
    }
}
