package com.sinema.ui

import com.sinema.util.TimeFormat
import kotlin.math.abs

internal data class PlaybackScrubPreview(
    val timestampText: String,
    val deltaText: String,
    val chapterTitle: String,
    val showPausedDwellHint: Boolean
)

/**
 * Finds the active marker at or immediately before [positionMs].
 * Ignores non-finite or negative marker timestamps and handles unsorted inputs.
 */
internal fun activeMarkerAt(
    positionMs: Long,
    markers: List<Pair<String, Double>>
): Pair<String, Double>? {
    if (positionMs < 0L || markers.isEmpty()) return null
    val posSec = positionMs / 1_000.0
    return markers
        .asSequence()
        .filter { (_, sec) -> sec.isFinite() && sec >= 0.0 && sec <= posSec + 0.5 }
        .maxByOrNull { it.second }
}

/**
 * Fraction [0f..1f] along the timeline for the active marker at [positionMs],
 * or null when no valid marker is active or duration is unknown.
 */
internal fun activeMarkerFraction(
    positionMs: Long,
    markers: List<Pair<String, Double>>,
    durationMs: Long
): Float? {
    if (durationMs <= 0L) return null
    val active = activeMarkerAt(positionMs, markers) ?: return null
    val markerMs = active.second * 1_000.0
    if (markerMs < 0.0 || markerMs > durationMs) return null
    return (markerMs / durationMs).toFloat()
}

/**
 * Finds the next or previous marker relative to [positionMs] using a 3-second
 * hysteresis window so rapid "previous" presses step cleanly to earlier chapters.
 */
internal fun adjacentMarker(
    positionMs: Long,
    markers: List<Pair<String, Double>>,
    forward: Boolean
): Pair<String, Double>? {
    if (markers.isEmpty()) return null
    val posSec = positionMs.coerceAtLeast(0L) / 1_000.0
    val sorted = markers
        .asSequence()
        .filter { (_, sec) -> sec.isFinite() && sec >= 0.0 }
        .sortedBy { it.second }
    return if (forward) {
        sorted.firstOrNull { it.second > posSec + 3.0 }
    } else {
        sorted.lastOrNull { it.second < posSec - 3.0 }
    }
}

/** Formats a signed seek delta (e.g. "+0:30", "−0:10"), or empty when zero. */
internal fun formatSeekDeltaMs(deltaMs: Long): String {
    val absMs = abs(deltaMs)
    if (absMs < 1_000L) return ""
    val sign = if (deltaMs > 0L) "+" else "−"
    return "$sign${TimeFormat.formatMs(absMs)}"
}

/** Builds structured preview text for the timeline scrub HUD. */
internal fun buildScrubPreview(
    targetMs: Long,
    originMs: Long?,
    durationMs: Long,
    isPaused: Boolean,
    markers: List<Pair<String, Double>>
): PlaybackScrubPreview {
    val clamped = clampSeekPositionMs(targetMs, durationMs)
    val delta = originMs?.let { formatSeekDeltaMs(clamped - it) }.orEmpty()
    val chapter = activeMarkerAt(clamped, markers)?.first?.trim().orEmpty()
    return PlaybackScrubPreview(
        timestampText = TimeFormat.formatMs(clamped),
        deltaText = delta,
        chapterTitle = chapter,
        showPausedDwellHint = isPaused
    )
}
