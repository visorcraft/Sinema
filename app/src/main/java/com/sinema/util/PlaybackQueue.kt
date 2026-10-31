package com.sinema.util

/**
 * In-process playback queue; survives activity recreation, dies with the process (fine for TV).
 *
 * Holds lightweight metadata so the Up Next UI can render without refetching
 * every queued scene. `index` is the currently playing item.
 */
object PlaybackQueue {
    data class Item(val id: String, val title: String, val durationSec: Double)

    private var items: List<Item> = emptyList()
    private var index: Int = -1

    val isActive: Boolean get() = index in items.indices

    /** Currently playing item, if any. */
    fun current(): Item? = items.getOrNull(index)

    /** Items after the current one, in play order. */
    fun remaining(): List<Item> =
        if (index < 0) emptyList() else items.drop(index + 1)

    fun remainingCount(): Int = remaining().size

    /**
     * Advance to and return the next item, or null (and clear) when the queue
     * is exhausted.
     */
    @Synchronized
    fun next(): Item? =
        if (index + 1 < items.size) items[++index] else null.also { clearInternal() }

    /**
     * Jump to the item [positionInRemaining] slots ahead of the current one;
     * skipped items are consumed. Returns the selected item.
     */
    @Synchronized
    fun jumpTo(positionInRemaining: Int): Item? {
        if (index < 0) return null
        val target = index + 1 + positionInRemaining
        if (target !in items.indices) return null
        index = target
        return items[target]
    }

    /**
     * Jump to a specific queued item by id, searching the remaining window.
     * Safer than a relative index when the queue advanced between snapshot
     * and selection. Returns null (no-op) when the id is unknown or current.
     */
    @Synchronized
    fun jumpToItem(id: String): Item? {
        if (index < 0) return null
        if (items.getOrNull(index)?.id == id) return null
        val targetIndex = items.indexOfFirst { it.id == id }
        if (targetIndex <= index) return null
        index = targetIndex
        return items[targetIndex]
    }

    /** Randomize everything after the current item. Seedable for tests. */
    @Synchronized
    fun shuffleRemaining(random: kotlin.random.Random = kotlin.random.Random.Default): Boolean {
        if (index < 0 || index + 1 >= items.size - 1) return false
        val head = items.take(index + 1)
        val tail = items.drop(index + 1).shuffled(random)
        items = head + tail
        return true
    }

    @Synchronized
    fun start(newItems: List<Item>, startAt: Int) {
        items = newItems.toList()
        index = if (items.isEmpty()) -1 else startAt.coerceIn(-1, items.size - 1)
    }

    @Synchronized
    fun clear() = clearInternal()

    private fun clearInternal() { items = emptyList(); index = -1 }
}
