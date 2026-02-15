package com.sinema.model

data class Scene(
    val id: String,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Double,
    val width: Int,
    val height: Int,
    val playCount: Int = 0,
    val rating100: Int? = null
) {
    val isWatched: Boolean get() = playCount > 0
    val isFavorite: Boolean get() = (rating100 ?: 0) > 0
    val filename: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/')
    
    fun formatDuration(): String {
        val totalSecs = duration.toInt()
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
    
    fun formatSize(): String {
        val mb = size / (1024.0 * 1024.0)
        return if (mb > 1024) String.format("%.1f GB", mb / 1024.0)
        else String.format("%.0f MB", mb)
    }
}

data class FolderItem(
    val name: String,
    val fullPath: String,
    val isFolder: Boolean,
    val scene: Scene? = null,
    val childCount: Int = 0,
    val firstSceneId: String? = null,
    val hasFavorites: Boolean = false
)
