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
    ): AlertDialog {
        val index = options.indexOf(current).coerceAtLeast(0)
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options.map(label).toTypedArray(), index) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Label→action list menu: rows become data instead of when(which) indices. */
    fun showActions(context: Context, title: String, actions: List<Pair<String, () -> Unit>>) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions[which].second()
            }
            .show()
    }

    /** Label→action pairs builder for [showActions]. */
    fun <T> action(label: String, run: () -> Unit): Pair<String, () -> Unit> = label to run

    fun showTextSearchDialog(
        context: Context,
        title: String,
        initialQuery: String,
        hint: String = "Name",
        onClear: (() -> Unit)? = null,
        onSearch: (String) -> Unit
    ) {
        val input = EditText(context).apply {
            setText(initialQuery)
            setSelection(text.length)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Search") { _, _ -> onSearch(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
        if (onClear != null) {
            builder.setNeutralButton("Clear") { _, _ -> onClear() }
        }
        builder.show()
    }
}
