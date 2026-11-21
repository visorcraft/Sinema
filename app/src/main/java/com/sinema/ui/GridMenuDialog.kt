package com.sinema.ui

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
        onJumpToLetter: (() -> Unit)? = null,
        onBackToTop: (() -> Unit)? = null
    ) {
        // Pair each label with its action up front so the localized label is
        // never used as an identity key.
        val entries = buildList<Pair<String, () -> Unit>> {
            add(context.getString(R.string.grid_menu_sort) to { SortDialog.show(context, current, onSort) })
            if (canPlayAll) {
                add(context.getString(R.string.grid_menu_play_all) to onPlayAll)
                add(context.getString(R.string.grid_menu_shuffle) to onShuffle)
            }
            onMarkAllWatched?.let { add(context.getString(R.string.grid_menu_mark_all_watched) to it) }
            onRateAll?.let { add(context.getString(R.string.grid_menu_rate_all) to it) }
            onJumpToLetter?.let { add(context.getString(R.string.grid_menu_jump_letter) to it) }
            onBackToTop?.let { add(context.getString(R.string.grid_menu_back_to_top) to it) }
        }
        Dialogs.showActions(context, context.getString(R.string.grid_menu_title), entries)
    }
}
