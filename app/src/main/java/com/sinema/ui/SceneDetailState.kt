package com.sinema.ui

import com.sinema.model.Scene
import com.sinema.model.StarRating

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

    /** Store a star rating (0 = unrated) so the next refresh doesn't clobber it. */
    fun applyRating(stars: Int) {
        val rating100 = StarRating.toRating100(stars)
        scene = scene.copy(rating100 = rating100)
        refreshGeneration++
    }
}
