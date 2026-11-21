package com.sinema.ui

import com.sinema.model.CaptionRef
import com.sinema.model.SubtitleLanguage
import java.util.Locale
import kotlin.math.abs

internal enum class PlaybackSpeedPreset(val speed: Float, val label: String) {
    HALF(0.5f, "0.5×"),
    THREE_QUARTERS(0.75f, "0.75×"),
    NORMAL(1.0f, "1×"),
    ONE_AND_QUARTER(1.25f, "1.25×"),
    ONE_AND_HALF(1.5f, "1.5×"),
    DOUBLE(2.0f, "2×");

    val isDefault: Boolean get() = this == NORMAL

    companion object {
        fun fromSpeed(speed: Float): PlaybackSpeedPreset =
            entries.minByOrNull { abs(it.speed - speed) } ?: NORMAL
    }
}

/**
 * Formats Stash captions into readable TV track labels (e.g. "English · SRT")
 * and disambiguates duplicate tracks sharing the same language and format.
 */
internal fun formatSubtitleTrackLabels(
    captions: List<CaptionRef>,
    genericLabel: String = "Subtitles"
): List<String> {
    val baseNames = captions.map { caption ->
        val language = resolveSubtitleLanguageLabel(caption.languageCode, genericLabel)
        val type = caption.captionType.trim().uppercase(Locale.US)
        language to type
    }
    val seenCounts = mutableMapOf<Pair<String, String>, Int>()
    return baseNames.map { (language, type) ->
        val key = language to type
        val count = (seenCounts[key] ?: 0) + 1
        seenCounts[key] = count
        val namePart = if (count == 1) language else "$language ($count)"
        if (type.isBlank()) namePart else "$namePart · $type"
    }
}

internal fun resolveSubtitleLanguageLabel(code: String, genericLabel: String = "Subtitles"): String {
    val normalized = code.trim().lowercase(Locale.US)
    if (normalized.isBlank()) return genericLabel
    val known = SubtitleLanguage.entries.firstOrNull { it.code.isNotEmpty() && it.code == normalized }
    if (known != null) return known.label
    val primaryTag = normalized.substringBefore('-').substringBefore('_')
    val knownPrimary = SubtitleLanguage.entries.firstOrNull { it.code.isNotEmpty() && it.code == primaryTag }
    if (knownPrimary != null) return knownPrimary.label
    val localeName = Locale.forLanguageTag(primaryTag).getDisplayLanguage(Locale.ENGLISH)
    if (localeName.isNotBlank() && !localeName.equals(primaryTag, ignoreCase = true)) {
        return localeName.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }
    return code.trim().uppercase(Locale.US)
}

/**
 * Index in [markers] of the chapter active at [positionMs], or -1 if none.
 * Uses the original list order so ListView.setSelection highlights the exact row.
 */
internal fun activeChapterIndex(
    positionMs: Long,
    markers: List<Pair<String, Double>>
): Int {
    if (positionMs < 0L || markers.isEmpty()) return -1
    val posSec = positionMs / 1_000.0
    var bestIndex = -1
    var bestSeconds = Double.NEGATIVE_INFINITY
    markers.forEachIndexed { index, (_, seconds) ->
        if (seconds.isFinite() && seconds >= 0.0 && seconds <= posSec + 0.5 && seconds >= bestSeconds) {
            bestSeconds = seconds
            bestIndex = index
        }
    }
    return bestIndex
}
