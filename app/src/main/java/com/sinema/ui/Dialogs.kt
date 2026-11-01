package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText

/** Shared TV dialog builders: single-choice pickers and text search. */
object Dialogs {
    fun <T> showChoiceDialog(
        context: Context,
        title: String,
        options: List<T>,
        current: T,
        label: (T) -> String,
        onChosen: (T) -> Unit
    ) {
        val index = options.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options.map(label).toTypedArray(), index) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showTextSearchDialog(
        context: Context,
        title: String,
        initialQuery: String,
        hint: String = "Name",
        onSearch: (String) -> Unit
    ) {
        val input = EditText(context).apply {
            setText(initialQuery)
            setSelection(text.length)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Search") { _, _ -> onSearch(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
