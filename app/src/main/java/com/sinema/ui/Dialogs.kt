package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import com.sinema.R

/** Shared TV dialog builders: single-choice, multi-choice, actions, confirmation, and text input. */
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
        return showSingleChoiceIndexDialog(
            context = context,
            title = title,
            items = options.map(label),
            checkedIndex = index
        ) { which ->
            onChosen(options[which])
        }
    }

    fun showSingleChoiceIndexDialog(
        context: Context,
        title: String,
        items: List<String>,
        checkedIndex: Int,
        negativeLabel: String = context.getString(android.R.string.cancel),
        onChosen: (Int) -> Unit
    ): AlertDialog {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()
                onChosen(which)
            }
            .setNegativeButton(negativeLabel, null)
        return showDialog(builder) { dialog ->
            if (checkedIndex >= 0 && checkedIndex < items.size) {
                dialog.listView.setSelection(checkedIndex)
            }
            dialog.listView.requestFocus()
        }
    }

    fun showMultiChoiceDialog(
        context: Context,
        title: String,
        items: List<String>,
        checked: BooleanArray,
        positiveLabel: String,
        negativeLabel: String = context.getString(android.R.string.cancel),
        onConfirm: (BooleanArray) -> Unit
    ): AlertDialog {
        val selection = checked.copyOf()
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMultiChoiceItems(items.toTypedArray(), selection) { _, which, isChecked ->
                selection[which] = isChecked
            }
            .setPositiveButton(positiveLabel) { dialog, _ ->
                dialog.dismiss()
                onConfirm(selection)
            }
            .setNegativeButton(negativeLabel, null)
        return showDialog(builder) { dialog ->
            dialog.listView.requestFocus()
        }
    }

    /** Label→action list menu: rows become data instead of when(which) indices. */
    fun showActions(
        context: Context,
        title: String,
        actions: List<Pair<String, () -> Unit>>,
        negativeLabel: String? = null
    ): AlertDialog {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions[which].second()
            }
        if (negativeLabel != null) {
            builder.setNegativeButton(negativeLabel, null)
        }
        return showDialog(builder) { dialog ->
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

    fun showTextInputDialog(
        context: Context,
        title: String,
        initialValue: String,
        hint: String,
        positiveLabel: String,
        negativeLabel: String = context.getString(android.R.string.cancel),
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        onSubmit: (String) -> Unit
    ): AlertDialog {
        val input = EditText(context).apply {
            setText(initialValue)
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
            .setPositiveButton(positiveLabel) { _, _ -> onSubmit(input.text.toString().trim()) }
            .setNegativeButton(negativeLabel, null)
        if (neutralLabel != null && onNeutral != null) {
            builder.setNeutralButton(neutralLabel) { _, _ -> onNeutral() }
        }
        return showDialog(builder) {
            input.requestFocus()
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
        showTextInputDialog(
            context = context,
            title = title,
            initialValue = initialQuery,
            hint = hint,
            positiveLabel = "Search",
            negativeLabel = "Cancel",
            neutralLabel = if (onClear != null) "Clear" else null,
            onNeutral = onClear,
            onSubmit = onSearch
        )
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
