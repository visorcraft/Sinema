package com.sinema.util

import kotlin.math.abs

/**
 * Discreet mode: scene cards show neutral labels and hidden artwork until the
 * card is focused, so a passing glance at the TV reveals nothing.
 */
object DiscreetMode {
    /** Stable per-scene neutral label ("Scene 4271") independent of title. */
    fun neutralTitle(sceneId: String): String {
        if (sceneId.isBlank()) return "Scene 0"
        return "Scene ${abs(sceneId.hashCode().toLong() % 10_000L)}"
    }
}
