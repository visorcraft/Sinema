package com.sinema.util

/** Recent-search bookkeeping, pure so it is unit-testable. */
object RecentSearches {
    const val MAX = 10
    const val CLEAR_ACTION = "🗑 Clear recent searches"

    /** Newest first, deduplicated, capped at [MAX]. Short queries ignored. */
    fun updated(existing: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.length < 2) return existing
        return (listOf(q) + existing.filter { !it.equals(q, ignoreCase = true) }).take(MAX)
    }
}
