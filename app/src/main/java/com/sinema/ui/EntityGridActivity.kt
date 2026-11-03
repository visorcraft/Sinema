package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

class EntityGridActivity : GridActivity() {
    companion object {
        fun intent(context: Context, kind: EntityItem.Kind): Intent =
            Intent(context, EntityGridActivity::class.java).putExtra("kind", kind.name)
    }

    override fun createGridFragment(): SceneGridFragment =
        EntityGridFragment.create(EntityItem.parseOrTag(intent.getStringExtra("kind")))
}

class EntityGridFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind) = EntityGridFragment().apply {
            arguments = Bundle().apply { putString("kind", kind.name) }
        }
    }

    private val kind get() = EntityItem.parseOrTag(requireArguments().getString("kind"))
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
        Dialogs.showChoiceDialog(
            requireContext(), "Sort by", EntitySortOption.entries, entitySort,
            label = { it.label }
        ) { chosen ->
            entitySort = chosen
            app.prefs.setSortFor("entity_${kind.name}", chosen.name)
            reload()
        }
    }

    override fun showSearchDialog() {
        Dialogs.showTextSearchDialog(
            requireContext(),
            title = "Search ${kind.label.lowercase()}",
            initialQuery = searchQuery
        ) { query ->
            searchQuery = query
            reload()
        }
    }

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

}
