package com.sinema.ui

import com.sinema.model.PlaybackAspectRatio
import com.sinema.model.PlaybackStreamQuality
import com.sinema.util.DiscreetMode
import kotlin.math.abs

/**
 * Returns the title to display in the playback HUD, applying discreet-mode
 * masking whenever enabled so a passing glance at the TV reveals nothing.
 */
internal fun playbackDisplayTitle(
    sceneTitle: String,
    sceneId: String,
    discreetMode: Boolean
): String {
    if (!discreetMode) return sceneTitle
    return if (sceneId.isNotBlank()) DiscreetMode.neutralTitle(sceneId) else ""
}

/** Short label for non-default stream qualities, or null when on AUTO. */
internal fun qualityCompactLabel(quality: PlaybackStreamQuality): String? = when (quality) {
    PlaybackStreamQuality.AUTO -> null
    PlaybackStreamQuality.DIRECT -> "Direct"
    PlaybackStreamQuality.HLS_480P -> "480p"
    PlaybackStreamQuality.HLS_720P -> "720p"
    PlaybackStreamQuality.HLS_1080P -> "1080p"
}

/** Label for the action-row Quality button showing any active override. */
internal fun qualityButtonText(baseLabel: String, quality: PlaybackStreamQuality): String {
    val compact = qualityCompactLabel(quality)
    return if (compact == null) baseLabel else "$baseLabel · $compact"
}

/** Formats playback speed compactly (e.g. "1.25×", "1.5×", "2×"). */
internal fun formatSpeedCompact(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100f) / 100f
    return when {
        abs(rounded - rounded.toInt()) < 0.005f -> "${rounded.toInt()}×"
        abs(rounded * 10f - kotlin.math.round(rounded * 10f)) < 0.05f ->
            String.format(java.util.Locale.US, "%.1f×", rounded)
        else -> String.format(java.util.Locale.US, "%.2f×", rounded)
    }
}

/**
 * Builds concise, glanceable playback state badges for the top HUD bar.
 * Only non-default or noteworthy states appear so default playback stays clean.
 */
internal fun buildPlaybackStatusBadges(
    isBuffering: Boolean,
    isPaused: Boolean,
    isLooping: Boolean,
    isIncognito: Boolean,
    isEndlessMix: Boolean,
    streamQuality: PlaybackStreamQuality,
    forceAlignedHls: Boolean,
    aspectRatio: PlaybackAspectRatio,
    playbackSpeed: Float = 1.0f
): List<String> = buildList {
    if (isBuffering) {
        add("Buffering")
    } else if (isPaused) {
        add("Paused")
    }
    if (isLooping) add("Loop")
    if (abs(playbackSpeed - 1.0f) >= 0.01f) add(formatSpeedCompact(playbackSpeed))
    val qualityTag = qualityCompactLabel(streamQuality)
    if (qualityTag != null) {
        add(qualityTag)
    } else if (forceAlignedHls) {
        add("HLS")
    }
    if (aspectRatio != PlaybackAspectRatio.AUTO) add(aspectRatio.label)
    if (isIncognito) add("Incognito")
    if (isEndlessMix) add("Endless")
}
