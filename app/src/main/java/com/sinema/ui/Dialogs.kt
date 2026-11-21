package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import com.sinema.R

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
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options.map(label).toTypedArray(), index) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
        return showDialog(builder) { dialog ->
            dialog.listView.setSelection(index)
            dialog.listView.requestFocus()
        }
    }

    /** Label→action list menu: rows become data instead of when(which) indices. */
    fun showActions(context: Context, title: String, actions: List<Pair<String, () -> Unit>>) {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions[which].second()
            }
        showDialog(builder) { dialog ->
            dialog.listView.requestFocus()
        }
    }

    /** Label→action pairs builder for [showActions]. */
    fun <T> action(label: String, run: () -> Unit): Pair<String, () -> Unit> = label to run

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        positiveLabel: String,
        negativeLabel: String = context.getString(android.R.string.cancel),
        focusNegativeByDefault: Boolean = false,
        onConfirm: () -> Unit
    ): AlertDialog {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveLabel) { _, _ -> onConfirm() }
            .setNegativeButton(negativeLabel, null)
        return showDialog(builder) { dialog ->
            val targetButton = if (focusNegativeByDefault) {
                AlertDialog.BUTTON_NEGATIVE
            } else {
                AlertDialog.BUTTON_POSITIVE
            }
            dialog.getButton(targetButton)?.requestFocus()
        }
    }

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
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Search") { _, _ -> onSearch(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
        if (onClear != null) {
            builder.setNeutralButton("Clear") { _, _ -> onClear() }
        }
        showDialog(builder) {
            input.requestFocus()
        }
    }

    private fun showDialog(
        builder: AlertDialog.Builder,
        onShow: (AlertDialog) -> Unit = {}
    ): AlertDialog {
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.choice_dialog_background)
            onShow(dialog)
        }
        dialog.show()
        return dialog
    }
}
