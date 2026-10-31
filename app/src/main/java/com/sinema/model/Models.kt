package com.sinema.model

data class TagRef(val id: String, val name: String)
data class PerformerRef(val id: String, val name: String)
data class StudioRef(val id: String, val name: String)
data class CaptionRef(val languageCode: String, val captionType: String)
data class MarkerRef(val id: String, val title: String, val seconds: Double, val primaryTag: String)

/** A browsable Stash entity (tag, performer, or studio). */
data class EntityItem(
    val id: String,
    val name: String,
    val sceneCount: Int,
    val imagePath: String?,   // absolute URL from Stash, needs auth headers
    val kind: Kind
) {
    enum class Kind(val label: String) { TAG("Tags"), PERFORMER("Performers"), STUDIO("Studios") }
}

/** Full single-scene payload for the detail screen and player. */
data class SceneDetails(
    val scene: Scene,
    val resumeTime: Double,
    val markers: List<MarkerRef>
)

data class Scene(
    val id: String,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Double,
    val width: Int,
    val height: Int,
    val playCount: Int = 0,
    val rating100: Int? = null,
    // Metadata below is empty/null on list-query results; populated only by the full scene query.
    val date: String? = null,
    val studio: StudioRef? = null,
    val tags: List<TagRef> = emptyList(),
    val performers: List<PerformerRef> = emptyList(),
    val captions: List<CaptionRef> = emptyList(),
    // Populated by list queries (Stash ≥ 0.24) and used for the card progress bar.
    val resumeTime: Double = 0.0,
    // Synopsis text and O-count come from the full scene query only.
    val details: String? = null,
    val oCounter: Int = 0,
    // ISO-8601 timestamp populated only when the query asked for `last_played_at`
    // (currently just the Recently Played row). Null if unscored.
    val lastPlayedAt: String? = null
) {
    val isWatched: Boolean get() = playCount > 0
    val isFavorite: Boolean get() = (rating100 ?: 0) > 0
    val filename: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/')

    /**
     * Resume progress 0..100 for card overlays, or 0 when there is nothing
     * meaningful to show: no resume point, unknown duration, barely started
     * (<2%), or nearly finished (≥98% — finishing playback clears
     * resume_time server-side, so a full bar would just flash and vanish).
     */
    val watchProgressPercent: Int get() {
        if (duration <= 0.0 || resumeTime <= 0.0) return 0
        val fraction = resumeTime / duration
        if (fraction < 0.02 || fraction >= 0.98) return 0
        return (fraction * 100).toInt().coerceIn(1, 100)
    }

    fun formatDuration(): String = com.sinema.util.TimeFormat.formatSeconds(duration)
    
    fun formatSize(): String {
        val mb = size / (1024.0 * 1024.0)
        return if (mb > 1024) String.format("%.1f GB", mb / 1024.0)
        else String.format("%.0f MB", mb)
    }
}

data class ImageItem(
    val id: String,
    val title: String,
    val path: String,
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rating100: Int? = null
) {
    val filename: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/')
}

data class FolderItem(
    val name: String,
    val fullPath: String,
    val isFolder: Boolean,
    val scene: Scene? = null,
    val image: ImageItem? = null,
    val childCount: Int = 0,
    val firstSceneId: String? = null,
    val firstImageId: String? = null,
    val hasFavorites: Boolean = false
)
