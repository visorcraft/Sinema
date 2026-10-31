package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.EntityItem

enum class EntitySortOption(val label: String, val field: String, val direction: String) {
    COUNT_DESC("Scene count", "scenes_count", "DESC"),
    NAME_ASC("Name (A–Z)", "name", "ASC"),
    NAME_DESC("Name (Z–A)", "name", "DESC");

    companion object {
        fun fromName(name: String?): EntitySortOption =
            entries.firstOrNull { it.name == name } ?: COUNT_DESC
    }
}

class EntityGridActivity : FragmentActivity() {
    companion object {
        fun intent(context: Context, kind: EntityItem.Kind): Intent =
            Intent(context, EntityGridActivity::class.java).putExtra("kind", kind.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val kind = parseKind(intent.getStringExtra("kind"))
            val fragment = EntityGridFragment.create(kind)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, fragment)
                .commit()
            bindGridControls(fragment)
        } else {
            (supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen)
                ?.let { bindGridControls(it) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }
}

class EntityGridFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind) = EntityGridFragment().apply {
            arguments = Bundle().apply { putString("kind", kind.name) }
        }
    }

    private val kind get() = parseKind(requireArguments().getString("kind"))
    private var entitySort = EntitySortOption.COUNT_DESC
    private var searchQuery = ""

    override val columns = 4
    override val paged = true
    override val canSearch = true
    override val gridTitle: String
        get() = buildString {
            append(kind.label)
            if (searchQuery.isNotBlank()) append(": $searchQuery")
            append("  •  ${entitySort.label}")
        }
    override val emptyMessage get() = "No ${kind.label.lowercase()} found"

    override fun onCreate(savedInstanceState: Bundle?) {
        entitySort = EntitySortOption.fromName(app.prefs.sortFor("entity_${kind.name}"))
        searchQuery = savedInstanceState?.getString("entity_query").orEmpty()
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("entity_query", searchQuery)
    }

    override fun showSortDialog() {
        val options = EntitySortOption.entries
        AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), options.indexOf(entitySort)) { dialog, which ->
                dialog.dismiss()
                entitySort = options[which]
                app.prefs.setSortFor("entity_${kind.name}", entitySort.name)
                reload()
            }
            .show()
    }

    override fun showSearchDialog() {
        val input = EditText(requireContext()).apply {
            setText(searchQuery)
            setSelection(text.length)
            hint = "Name"
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Search ${kind.label.lowercase()}")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                searchQuery = input.text.toString().trim()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findEntities(
            kind,
            page = page,
            perPage = perPage,
            searchTerm = searchQuery.ifBlank { null },
            sort = entitySort.field,
            direction = entitySort.direction
        )

    override fun onItemClicked(item: Any) {
        if (item is EntityItem) startActivity(EntityScenesActivity.intent(requireContext(), item))
    }

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }
}
