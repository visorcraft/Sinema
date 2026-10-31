package com.sinema.util

import com.sinema.model.Scene
import kotlin.random.Random

/**
 * Pure queue-order planning for Play All / Play from here / Shuffle play.
 * Kept free of Android types so it is unit-testable; SceneIntents turns a
 * [Plan] into queue items and a launch intent.
 */
object QueuePlanning {
    /** Matches Stash-friendly request sizes and keeps the Up Next list navigable. */
    const val MAX_QUEUE = 500

    data class Plan(val ordered: List<Scene>, val capped: Boolean)

    /**
     * @param startAt index of the first scene to play; ignored when shuffling
     * @param shuffle randomize the whole list before dropping to [startAt]
     */
    fun plan(
        scenes: List<Scene>,
        startAt: Int = 0,
        shuffle: Boolean = false,
        random: Random = Random.Default
    ): Plan {
        if (scenes.isEmpty()) return Plan(emptyList(), capped = false)
        val base = if (shuffle) scenes.shuffled(random) else scenes
        val from = if (shuffle) 0 else startAt.coerceIn(0, base.lastIndex)
        val window = base.drop(from)
        val capped = window.size > MAX_QUEUE
        return Plan(if (capped) window.take(MAX_QUEUE) else window, capped)
    }

    fun toQueueItems(ordered: List<Scene>): List<PlaybackQueue.Item> =
        ordered.map {
            PlaybackQueue.Item(it.id, it.displayTitle, it.duration, it.playCount)
        }
}
