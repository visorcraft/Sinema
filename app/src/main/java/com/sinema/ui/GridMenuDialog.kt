package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.sinema.model.SortOption

object GridMenuDialog {
    fun show(
        context: Context,
        current: SortOption,
        canPlayAll: Boolean,
        onSort: (SortOption) -> Unit,
        onPlayAll: () -> Unit
    ) {
        val items = buildList {
            add("Sort by…")
            if (canPlayAll) add("▶ Play All")
        }
        AlertDialog.Builder(context)
            .setTitle("Menu")
            .setItems(items.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> SortDialog.show(context, current, onSort)
                    1 -> onPlayAll()
                }
            }
            .show()
    }
}
