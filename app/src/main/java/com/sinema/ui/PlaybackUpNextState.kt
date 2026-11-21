package com.sinema.ui

import com.sinema.util.PlaybackQueue
import com.sinema.util.TimeFormat

internal const val UP_NEXT_BANNER_WINDOW_MS = 15_000L
internal const val UP_NEXT_BANNER_MIN_DURATION_MS = 30_000L

internal data class UpNextBannerState(
    val itemId: String,
    val title: String,
    val durationText: String,
    val countdownSeconds: Int
)

internal data class UpNextRowModel(
    val badgeText: String,
    val titleText: String,
    val metaText: String,
    val isCurrent: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canRemove: Boolean
)

/**
 * Decides whether to show the end-of-scene Up Next transition overlay.
 * Appears only during the final 15 seconds of an actively playing non-looping
 * scene when another queued scene follows and the user has not dismissed it.
 */
internal fun computeUpNextBanner(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isScrubbing: Boolean,
    isLooping: Boolean,
    nextItem: PlaybackQueue.Item?,
    currentSceneId: String,
    dismissedForSceneId: String?,
    discreetMode: Boolean
): UpNextBannerState? {
    if (nextItem == null || !isPlaying || isScrubbing || isLooping) return null
    if (currentSceneId.isNotBlank() && dismissedForSceneId == currentSceneId) return null
    if (durationMs < UP_NEXT_BANNER_MIN_DURATION_MS) return null
    val remainingMs = durationMs - currentPositionMs
    if (remainingMs !in 1L..UP_NEXT_BANNER_WINDOW_MS) return null
    val seconds = ((remainingMs + 999L) / 1_000L).toInt().coerceIn(1, 15)
    return UpNextBannerState(
        itemId = nextItem.id,
        title = playbackDisplayTitle(nextItem.title, nextItem.id, discreetMode),
        durationText = TimeFormat.formatSeconds(nextItem.durationSec),
        countdownSeconds = seconds
    )
}

/** Builds presentation and action enablement for a single Up Next dialog row. */
internal fun buildUpNextRowModel(
    item: PlaybackQueue.Item,
    position: Int,
    totalCount: Int,
    isCurrent: Boolean,
    hasCurrent: Boolean,
    discreetMode: Boolean,
    nowLabel: String = "NOW",
    watchedLabel: String = "Watched"
): UpNextRowModel {
    val firstUpcomingIndex = if (hasCurrent) 1 else 0
    val queueRank = if (hasCurrent) position else position + 1
    val duration = TimeFormat.formatSeconds(item.durationSec)
    val meta = if (item.playCount > 0) "$duration · $watchedLabel" else duration
    return UpNextRowModel(
        badgeText = if (isCurrent) nowLabel else "#$queueRank",
        titleText = playbackDisplayTitle(item.title, item.id, discreetMode),
        metaText = meta,
        isCurrent = isCurrent,
        canMoveUp = !isCurrent && position > firstUpcomingIndex,
        canMoveDown = !isCurrent && position < totalCount - 1,
        canRemove = !isCurrent
    )
}
