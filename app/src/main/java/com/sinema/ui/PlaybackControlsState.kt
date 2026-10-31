package com.sinema.ui

import com.sinema.util.TimeFormat

private const val PREVIEW_BUCKET_MS = 5_000L
const val RESUME_PROMPT_THRESHOLD_MS = 10_000L

enum class SeekPreset(val backwardMs: Long, val forwardMs: Long, val label: String) {
    TEN_THIRTY(10_000L, 30_000L, "−10s / +30s"),
    THIRTY_SIXTY(30_000L, 60_000L, "−30s / +60s"),
    SIXTY_SIXTY(60_000L, 60_000L, "−60s / +60s");

    fun next(): SeekPreset = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): SeekPreset = entries.firstOrNull { it.name == name } ?: TEN_THIRTY
    }
}

fun previewBucketMs(positionMs: Long, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    val position = positionMs.coerceIn(0L, durationMs)
    return (position / PREVIEW_BUCKET_MS * PREVIEW_BUCKET_MS).coerceAtMost(durationMs)
}

fun markerFractions(markers: List<Pair<String, Double>>, durationMs: Long): List<Float> {
    if (durationMs <= 0L) return emptyList()
    return markers.mapNotNull { (_, seconds) ->
        if (!seconds.isFinite() || seconds < 0.0 || seconds * 1000.0 > durationMs) null
        else (seconds * 1000.0 / durationMs).toFloat()
    }.sorted()
}

fun formatRemainingMs(positionMs: Long, durationMs: Long): String =
    if (durationMs <= 0L) "" else "-${TimeFormat.formatMs((durationMs - positionMs).coerceAtLeast(0L))}"

/** Only offer the resume/start-over choice for meaningful positions. */
fun shouldPromptResume(resumeMs: Long, thresholdMs: Long = RESUME_PROMPT_THRESHOLD_MS): Boolean =
    resumeMs >= thresholdMs

fun upNextChipLabel(remainingCount: Int): String = "Up Next · $remainingCount"
