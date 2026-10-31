package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.SortOption

object GridMenuDialog {
    private const val SORT = "Sort by…"
    private const val PLAY_ALL = "▶ Play All"
    private const val SHUFFLE = "🔀 Shuffle Play"

    fun show(
        context: Context,
        current: SortOption,
        canPlayAll: Boolean,
        onSort: (SortOption) -> Unit,
        onPlayAll: () -> Unit,
        onShuffle: () -> Unit = onPlayAll
    ) {
        val items = buildList {
            add(SORT)
            if (canPlayAll) {
                add(PLAY_ALL)
                add(SHUFFLE)
            }
        }
        AlertDialog.Builder(context)
            .setTitle("Menu")
            .setItems(items.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                when (items[which]) {
                    SORT -> SortDialog.show(context, current, onSort)
                    PLAY_ALL -> onPlayAll()
                    SHUFFLE -> onShuffle()
                }
            }
            .show()
    }
}
