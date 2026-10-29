package com.sinema.ui

/** Pure state transitions for the scene detail Mark Watched/Unwatched toggle. */
object WatchedToggle {
    fun nextPlayCount(current: Int, wasWatched: Boolean): Int =
        if (wasWatched) 0 else current + 1

    fun buttonLabel(watched: Boolean): String =
        if (watched) "❌ Mark Unwatched" else "👁️ Mark Watched"
}
