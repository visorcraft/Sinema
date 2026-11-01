package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.SortOption

object GridMenuDialog {
    private const val SORT = "Sort by…"
    private const val PLAY_ALL = "▶ Play All"
    private const val SHUFFLE = "🔀 Shuffle Play"
    private const val MARK_ALL_WATCHED = "👁 Mark All Watched"
    private const val RATE_ALL = "★ Rate All…"
    private const val JUMP_TO_LETTER = "🔤 Jump to letter…"

    fun show(
        context: Context,
        current: SortOption,
        canPlayAll: Boolean,
        onSort: (SortOption) -> Unit,
        onPlayAll: () -> Unit,
        onShuffle: () -> Unit = onPlayAll,
        onMarkAllWatched: (() -> Unit)? = null,
        onRateAll: (() -> Unit)? = null,
        onJumpToLetter: (() -> Unit)? = null
    ) {
        val items = buildList {
            add(SORT)
            if (canPlayAll) {
                add(PLAY_ALL)
                add(SHUFFLE)
            }
            onMarkAllWatched?.let { add(MARK_ALL_WATCHED) }
            onRateAll?.let { add(RATE_ALL) }
            onJumpToLetter?.let { add(JUMP_TO_LETTER) }
        }
        AlertDialog.Builder(context)
            .setTitle("Menu")
            .setItems(items.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                when (items[which]) {
                    SORT -> SortDialog.show(context, current, onSort)
                    PLAY_ALL -> onPlayAll()
                    SHUFFLE -> onShuffle()
                    MARK_ALL_WATCHED -> onMarkAllWatched?.invoke()
                    RATE_ALL -> onRateAll?.invoke()
                    JUMP_TO_LETTER -> onJumpToLetter?.invoke()
                }
            }
            .show()
    }
}
