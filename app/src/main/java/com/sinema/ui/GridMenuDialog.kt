package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.R
import com.sinema.model.SortOption

object GridMenuDialog {
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
        // Pair each label with its action up front so the localized label is
        // never used as an identity key.
        val entries = buildList {
            add(context.getString(R.string.grid_menu_sort) to Runnable { SortDialog.show(context, current, onSort) })
            if (canPlayAll) {
                add(context.getString(R.string.grid_menu_play_all) to Runnable(onPlayAll))
                add(context.getString(R.string.grid_menu_shuffle) to Runnable(onShuffle))
            }
            onMarkAllWatched?.let { add(context.getString(R.string.grid_menu_mark_all_watched) to Runnable(it)) }
            onRateAll?.let { add(context.getString(R.string.grid_menu_rate_all) to Runnable(it)) }
            onJumpToLetter?.let { add(context.getString(R.string.grid_menu_jump_letter) to Runnable(it)) }
        }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.grid_menu_title))
            .setItems(entries.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                entries[which].second.run()
            }
            .show()
    }
}
