package com.sinema.api

import com.sinema.model.Scene

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
