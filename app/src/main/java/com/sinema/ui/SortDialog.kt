package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.SortOption

object SortDialog {
    fun show(context: Context, current: SortOption, onChosen: (SortOption) -> Unit) {
        val options = SortOption.entries
        AlertDialog.Builder(context)
            .setTitle("Sort by")
            .setSingleChoiceItems(
                options.map { it.label }.toTypedArray(),
                options.indexOf(current)
            ) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .show()
    }
}
