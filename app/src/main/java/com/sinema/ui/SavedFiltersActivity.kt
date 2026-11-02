package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.model.SavedSceneFilter
import com.sinema.model.SortOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SavedFiltersActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = SavedFiltersFragment()
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
}

internal fun savedFilterDefaultSort(sortField: String?, direction: String?): SortOption =
    SortOption.entries.firstOrNull { it.field == sortField && it.direction == direction }
        ?: SortOption.entries.firstOrNull { it.field == sortField }
        ?: SortOption.PATH_ASC

class SavedFiltersFragment : SceneGridFragment() {
    override val gridTitle = "Saved Filters"
    override val emptyMessage = "No saved filters on this server"
    override val paged = true
    override val canFilter = false

    override fun createCardPresenter() = SavedFilterCardPresenter(::showFilterActions)

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        val filters = app.api.findSavedSceneFilters()
        val pageItems = filters.drop((page - 1) * perPage).take(perPage)
        return Pair(filters.size, pageItems)
    }

    override fun onItemClicked(item: Any) {
        if (item is SavedSceneFilter) {
            startActivity(SavedFilterScenesActivity.intent(requireContext(), item))
        }
    }

    private fun showFilterActions(filter: SavedSceneFilter) {
        Dialogs.showActions(requireContext(), filter.name, listOf(
            "Rename" to { promptRename(filter) },
            "Delete" to { confirmDelete(filter) }
        ))
    }

    private fun promptRename(filter: SavedSceneFilter) {
        val input = EditText(requireContext()).apply {
            setText(filter.name)
            setSelection(text.length)
            hint = "Filter name"
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename saved filter")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Enter a filter name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        val renamed = app.api.renameSavedSceneFilter(filter, name)
                        Toast.makeText(requireContext(), "Renamed to ‘${renamed.name}’", Toast.LENGTH_SHORT).show()
                        reload()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("Sinema", "Failed to rename saved filter ${filter.id}", e)
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(filter: SavedSceneFilter) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete saved filter?")
            .setMessage("Delete ‘${filter.name}’ from Stash? Scenes are not affected.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        app.api.destroySavedSceneFilter(filter.id)
                        Toast.makeText(requireContext(), "Deleted ‘${filter.name}’", Toast.LENGTH_SHORT).show()
                        reload()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("Sinema", "Failed to delete saved filter ${filter.id}", e)
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class SavedFilterCardPresenter(
    private val onItemLongClick: (SavedSceneFilter) -> Unit
) : androidx.leanback.widget.Presenter() {
    override fun onCreateViewHolder(
        parent: android.view.ViewGroup
    ): androidx.leanback.widget.Presenter.ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        viewHolder: androidx.leanback.widget.Presenter.ViewHolder,
        item: Any?
    ) {
        val view = viewHolder.view
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val image = view.findViewById<ImageView>(R.id.card_image)
        if (item is SavedSceneFilter) {
            title.text = item.name
            content.text = "Scene filter  •  Hold OK to manage"
            image.setImageResource(android.R.drawable.ic_menu_sort_by_size)
            image.scaleType = ImageView.ScaleType.CENTER
            image.setBackgroundColor(0xFF444444.toInt())
            view.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: androidx.leanback.widget.Presenter.ViewHolder) {
        viewHolder.view.setOnLongClickListener(null)
    }
}

class SavedFilterScenesActivity : SinemaActivity() {
    companion object {
        const val EXTRA_ID = "saved_filter_id"
        const val EXTRA_NAME = "saved_filter_name"
        const val EXTRA_SORT = "saved_filter_sort"
        const val EXTRA_DIRECTION = "saved_filter_direction"
        const val EXTRA_OBJECT_FILTER = "saved_filter_object_filter"

        fun intent(context: Context, filter: SavedSceneFilter): Intent {
            val gson = com.google.gson.Gson()
            return Intent(context, SavedFilterScenesActivity::class.java)
                .putExtra(EXTRA_ID, filter.id)
                .putExtra(EXTRA_NAME, filter.name)
                .putExtra(EXTRA_SORT, filter.sort)
                .putExtra(EXTRA_DIRECTION, filter.direction)
                .putExtra(EXTRA_OBJECT_FILTER, filter.sceneFilter?.let { gson.toJson(it) } ?: "")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = SavedFilterScenesFragment.create(
                intent.getStringExtra(EXTRA_ID) ?: "",
                intent.getStringExtra(EXTRA_NAME) ?: "",
                intent.getStringExtra(EXTRA_SORT),
                intent.getStringExtra(EXTRA_DIRECTION),
                intent.getStringExtra(EXTRA_OBJECT_FILTER) ?: ""
            )
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
}

class SavedFilterScenesFragment : SceneGridFragment() {
    companion object {
        fun create(id: String, name: String, sort: String?, direction: String?, objectFilterJson: String) =
            SavedFilterScenesFragment().apply {
                arguments = Bundle().apply {
                    putString("id", id)
                    putString("name", name)
                    putString("sort", sort)
                    putString("direction", direction)
                    putString("object_filter", objectFilterJson)
                }
            }
    }

    private val filterId get() = requireArguments().getString("id") ?: ""
    override val gridTitle get() = requireArguments().getString("name") ?: ""
    override val emptyMessage = "No scenes match this filter"
    override val sortScreenKey get() = "saved_filter_${filterId}"
    override val canFilter = false
    override val paged = true

    override val defaultSort: SortOption
        get() = savedFilterDefaultSort(
            requireArguments().getString("sort"),
            requireArguments().getString("direction")
        )

    private val savedFilter: SavedSceneFilter by lazy {
        val raw = requireArguments().getString("object_filter") ?: ""
        val map: Map<String, Any?>? = if (raw.isBlank()) null
        else com.google.gson.Gson().fromJson(
            raw, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        )
        SavedSceneFilter(
            id = filterId,
            name = gridTitle,
            sceneFilter = map,
            sort = requireArguments().getString("sort"),
            direction = requireArguments().getString("direction")
        )
    }

    override suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findScenesForSavedFilter(
            savedFilter,
            page = page,
            perPage = perPage,
            sort = sort.apiSort(randomSeed),
            direction = sort.direction
        )
}
