package com.sinema.ui

import com.sinema.model.EntityItem
import com.sinema.model.GalleryItem
import com.sinema.model.GroupItem
import com.sinema.model.ImageItem
import com.sinema.model.SavedSceneFilter
import com.sinema.model.Scene
import com.sinema.model.SceneMarkerItem

/** Restores focus to the same logical item after a grid/row reload, or the
 * nearest surviving position if that item was removed (e.g. unfavorited). */
object FocusRestore {
    fun keyOf(item: Any): String = when (item) {
        is Scene -> "scene:${item.id}"
        is EntityItem -> "entity:${item.kind}:${item.id}"
        is GroupItem -> "group:${item.id}"
        is GalleryItem -> "gallery:${item.id}"
        is ImageItem -> "image:${item.id}"
        is SceneMarkerItem -> "marker:${item.id}"
        is SavedSceneFilter -> "saved_filter:${item.id}"
        is String -> "string:$item"
        is HomeViewAll -> item.key
        else -> "obj:$item"
    }

    fun positionAfterReload(oldKey: String?, oldIndex: Int, newKeys: List<String>): Int {
        if (newKeys.isEmpty()) return -1
        if (oldKey != null) {
            val idx = newKeys.indexOf(oldKey)
            if (idx >= 0) return idx
        }
        if (oldIndex < 0) return -1
        return oldIndex.coerceAtMost(newKeys.size - 1)
    }
}
