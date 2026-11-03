package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import com.sinema.model.EntityFilter
import com.sinema.model.EntityFilterMode
import com.sinema.model.EntityItem
import com.sinema.model.EntitySelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

object EntityFilterDialog {
    fun show(
        context: FragmentActivity,
        kind: EntityItem.Kind,
        current: EntityFilter,
        onChosen: (EntityFilter) -> Unit
    ) {
        val options = arrayOf("Any", "Include selected", "Exclude selected")
        AlertDialog.Builder(context)
            .setTitle(kind.label)
            .setSingleChoiceItems(options, current.mode.ordinal) { dialog, which ->
                dialog.dismiss()
                val mode = EntityFilterMode.entries[which]
                if (mode == EntityFilterMode.ANY) {
                    onChosen(EntityFilter())
                } else {
                    promptSearch(context, kind, current.copy(mode = mode), onChosen)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptSearch(
        context: FragmentActivity,
        kind: EntityItem.Kind,
        current: EntityFilter,
        onChosen: (EntityFilter) -> Unit
    ) {
        val input = EditText(context).apply {
            hint = "Name (blank for first 100)"
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
        }
        AlertDialog.Builder(context)
            .setTitle("Search ${kind.label.lowercase()}")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                loadEntities(context, kind, current, input.text.toString().trim(), onChosen)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadEntities(
        context: FragmentActivity,
        kind: EntityItem.Kind,
        current: EntityFilter,
        query: String,
        onChosen: (EntityFilter) -> Unit
    ) {
        context.launchSafely(
            "Load ${kind.label} filter choices",
            onError = { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        ) {
            val results = SinemaApp.instance.api.findEntities(
                kind,
                page = 1,
                perPage = 100,
                searchTerm = query.ifBlank { null },
                sort = "name",
                direction = "ASC"
            ).second
            val existing = current.entities.map {
                EntityItem(it.id, it.name, sceneCount = 0, imagePath = null, kind = kind)
            }
            val entities = (existing + results).distinctBy { it.id }
            if (entities.isEmpty()) {
                Toast.makeText(context, "No ${kind.label.lowercase()} found", Toast.LENGTH_SHORT).show()
                return@launchSafely
            }
            showPicker(context, kind, current, entities, onChosen)
        }
    }

    private fun showPicker(
        context: Context,
        kind: EntityItem.Kind,
        current: EntityFilter,
        entities: List<EntityItem>,
        onChosen: (EntityFilter) -> Unit
    ) {
        val selectedIds = current.entities.map { it.id }.toSet()
        val checked = entities.map { it.id in selectedIds }.toBooleanArray()
        val dialog = AlertDialog.Builder(context)
            .setTitle("Select ${kind.label.lowercase()}")
            .setMultiChoiceItems(entities.map { it.name }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Apply", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = entities.filterIndexed { index, _ -> checked[index] }
                    .map { EntitySelection(it.id, it.name) }
                if (selected.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Select at least one ${kind.label.lowercase().dropLast(1)} or choose Any",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onChosen(EntityFilter(current.mode, selected))
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
