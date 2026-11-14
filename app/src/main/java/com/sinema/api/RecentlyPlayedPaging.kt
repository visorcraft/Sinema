package com.sinema.api

import com.sinema.model.Scene

/** Page size for a recency walk. Large enough that one round trip usually covers a home row. */
internal const val RECENCY_PAGE_CHUNK = 100

/**
 * How many dated scenes a DESC page needs before older rows cannot affect it.
 * ASC puts nulls first, so the first page is not known until the scan ends.
 */
internal fun recencyDatedTarget(page: Int, perPage: Int, direction: String): Int {
    if (direction == "ASC") return Int.MAX_VALUE
    val size = perPage.coerceAtLeast(1)
    val product = page.coerceAtLeast(1).toLong() * size
    return if (product > Int.MAX_VALUE) Int.MAX_VALUE else product.toInt()
}

/** Stop once the dated prefix for this page is in hand, or the server has no more rows. */
internal fun recencyScanComplete(
    datedCount: Int,
    datedTarget: Int,
    batchSize: Int,
    chunk: Int,
    collected: Int,
    serverCount: Int
): Boolean {
    if (batchSize < chunk || collected >= serverCount) return true
    return datedCount >= datedTarget
}

internal fun recentlyPlayedPage(
    scenes: List<Scene>,
    page: Int,
    perPage: Int,
    direction: String
): List<Scene> {
    val (withTimestamps, withoutTimestamps) = scenes.partition { it.lastPlayedAt != null }
    val nullsLast = withTimestamps.sortedByDescending { it.lastPlayedAt } + withoutTimestamps
    val ordered = if (direction == "ASC") nullsLast.reversed() else nullsLast
    val size = perPage.coerceAtLeast(1)
    val from = ((page.coerceAtLeast(1) - 1).toLong() * size)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return ordered.drop(from).take(size)
}
