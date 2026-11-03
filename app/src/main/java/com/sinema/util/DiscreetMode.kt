package com.sinema.util

import kotlin.math.abs

/**
 * Discreet mode: scene cards show neutral labels and hidden artwork until the
 * card is focused, so a passing glance at the TV reveals nothing.
 */
object DiscreetMode {
    /** Stable neutral label independent of the item's title. */
    fun neutralTitle(kind: String, id: String): String {
        val safeKind = kind.ifBlank { "Item" }
        if (id.isBlank()) return "$safeKind 0"
        return "$safeKind ${abs(id.hashCode().toLong() % 10_000L)}"
    }

    fun neutralTitle(sceneId: String): String = neutralTitle("Scene", sceneId)
}
