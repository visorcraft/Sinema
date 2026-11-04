package com.sinema.util

/**
 * In-process playback queue; survives activity recreation, dies with the process (fine for TV).
 *
 * Holds lightweight metadata so the Up Next UI can render without refetching
 * every queued scene. `index` is the currently playing item.
 */
object PlaybackQueue {
    data class Item(
        val id: String,
        val title: String,
        val durationSec: Double,
        // Snapshot of play_count when the queue was built; powers the
        // "Unwatched only" filter. May go stale during playback — fine.
        val playCount: Int = 0
    )

    data class Snapshot(val items: List<Item>, val index: Int)

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

    /**
     * Remove one upcoming item by id. Returns false when the id is the
     * current item or unknown.
     */
    @Synchronized
    fun removeRemaining(id: String): Boolean {
        if (index < 0) return false
        val pos = items.drop(index + 1).indexOfFirst { it.id == id }
        if (pos < 0) return false
        items = items.toMutableList().apply { removeAt(index + 1 + pos) }
        return true
    }

    /** Move one upcoming item by [delta] (-1 up / +1 down) within the remainder. */
    @Synchronized
    fun moveRemaining(id: String, delta: Int): Boolean {
        if (index < 0 || delta == 0) return false
        val from = items.drop(index + 1).indexOfFirst { it.id == id }
        if (from < 0) return false
        val absFrom = index + 1 + from
        val absTo = (absFrom + delta).coerceIn(index + 1, items.size - 1)
        if (absTo == absFrom) return false
        items = items.toMutableList().apply { add(absTo, removeAt(absFrom)) }
        return true
    }

    /** Randomize everything after the current item. Seedable for tests. */
    @Synchronized
    fun shuffleRemaining(random: kotlin.random.Random = kotlin.random.Random.Default): Boolean {        if (index < 0 || index + 1 >= items.size - 1) return false
        val head = items.take(index + 1)
        val tail = items.drop(index + 1).shuffled(random)
        items = head + tail
        return true
    }

    /**
     * Drop already-watched items (snapshot play_count > 0) from the
     * remainder; the current item always stays. Returns true when any were
     * removed.
     */
    @Synchronized
    fun filterRemainingUnwatched(): Boolean {
        if (index < 0) return false
        val head = items.take(index + 1)
        val tail = items.drop(index + 1)
        val kept = tail.filter { it.playCount == 0 }
        items = head + kept
        return kept.size < tail.size
    }

    @Synchronized
    fun start(newItems: List<Item>, startAt: Int) {
        items = newItems.toList()
        index = if (items.isEmpty()) -1 else startAt.coerceIn(-1, items.size - 1)
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(items.toList(), index)

    fun restore(snapshot: Snapshot) = start(snapshot.items, snapshot.index)

    @Synchronized
    fun clear() = clearInternal()

    private fun clearInternal() { items = emptyList(); index = -1 }
}
