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
import com.sinema.model.GroupItem

enum class GroupSortOption(val label: String, val field: String, val direction: String) {
    COUNT_DESC("Scene count", "scenes_count", "DESC"),
    NAME_ASC("Name (A–Z)", "name", "ASC"),
    NAME_DESC("Name (Z–A)", "name", "DESC"),
    UPDATED_DESC("Recently updated", "updated_at", "DESC");

    companion object {
        fun fromName(name: String?): GroupSortOption =
            entries.firstOrNull { it.name == name } ?: UPDATED_DESC
    }
}

class GroupGridActivity : FragmentActivity() {
    companion object {
        fun intent(context: Context): Intent = Intent(context, GroupGridActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        val fragment = supportFragmentManager.findFragmentById(R.id.main_frame) as? GroupGridFragment
            ?: GroupGridFragment().also {
                supportFragmentManager.beginTransaction().replace(R.id.main_frame, it).commit()
            }
        bindGridControls(fragment)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)
}

class GroupGridFragment : SceneGridFragment() {
    override val columns = 4
    override val paged = true
    override val canSearch = true
    override val gridTitle: String
        get() = buildString {
            append("Groups")
            if (searchQuery.isNotBlank()) append(": $searchQuery")
            append("  •  ${groupSort.label}")
        }
    override val emptyMessage = "No groups found"

    private var groupSort = GroupSortOption.UPDATED_DESC
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        groupSort = GroupSortOption.fromName(app.prefs.sortFor("group_grid"))
        searchQuery = savedInstanceState?.getString("group_query").orEmpty()
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("group_query", searchQuery)
    }

    override fun showSortDialog() {
        Dialogs.showChoiceDialog(
            requireContext(), "Sort by", GroupSortOption.entries, groupSort,
            label = { it.label }
        ) { chosen ->
            groupSort = chosen
            app.prefs.setSortFor("group_grid", chosen.name)
            reload()
        }
    }

    override fun showSearchDialog() {
        Dialogs.showTextSearchDialog(
            requireContext(), title = "Search groups", initialQuery = searchQuery
        ) { query ->
            searchQuery = query
            reload()
        }
    }

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findGroups(page, perPage, searchQuery.ifBlank { null }, groupSort.field, groupSort.direction)

    override fun onItemClicked(item: Any) {
        if (item is GroupItem) startActivity(GroupScenesActivity.intent(requireContext(), item))
    }
}
