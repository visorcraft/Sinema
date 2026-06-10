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
    val captions: List<CaptionRef> = emptyList()
) {
    val isWatched: Boolean get() = playCount > 0
    val isFavorite: Boolean get() = (rating100 ?: 0) > 0
    val filename: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/')
    
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
