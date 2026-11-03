package com.sinema.ui

import android.app.AlertDialog
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import com.sinema.model.SceneFilterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

object SavedFilterActions {
    fun promptSave(
        activity: FragmentActivity,
        options: SceneFilterOptions,
        sort: String,
        direction: String
    ) {
        val input = EditText(activity).apply {
            setText(options.summary.take(80))
            setSelection(text.length)
            hint = "Filter name"
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
        }
        AlertDialog.Builder(activity)
            .setTitle("Save Stash filter")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(activity, "Enter a filter name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                activity.launchSafely(
                    "Save scene filter",
                    onError = { e ->
                        Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                ) {
                    val saved = SinemaApp.instance.api.saveSceneFilter(name, options, sort, direction)
                    Toast.makeText(activity, "Saved ‘${saved.name}’", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
