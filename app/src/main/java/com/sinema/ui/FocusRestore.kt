package com.sinema.ui

import com.sinema.model.EntityItem
import com.sinema.model.Scene

/** Restores focus to the same logical item after a grid/row reload, or the
 * nearest surviving position if that item was removed (e.g. unfavorited). */
object FocusRestore {
    fun keyOf(item: Any): String = when (item) {
        is Scene -> "scene:${item.id}"
        is EntityItem -> "entity:${item.kind}:${item.id}"
        is String -> "string:$item"
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
