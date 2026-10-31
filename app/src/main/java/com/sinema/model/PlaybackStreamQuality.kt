package com.sinema.model

/**
 * Manual stream-quality override for playback, persisted like the aspect
 * ratio. AUTO keeps the out-of-the-box behavior (direct stream, aligned-HLS
 * fallback); DIRECT disables transcoding fallback entirely; the HLS tiers
 * start on Stash's aligned HLS transcode at that resolution.
 */
enum class PlaybackStreamQuality(val label: String, val resolutionParam: String? = null) {
    AUTO("Auto (direct with fallback)"),
    DIRECT("Direct stream only"),
    HLS_480P("Transcoded 480p", "STANDARD"),
    HLS_720P("Transcoded 720p", "STANDARD_HD"),
    HLS_1080P("Transcoded 1080p", "FULL_HD");

    val isForcedHls: Boolean get() = resolutionParam != null

    companion object {
        fun fromName(name: String?): PlaybackStreamQuality =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}
