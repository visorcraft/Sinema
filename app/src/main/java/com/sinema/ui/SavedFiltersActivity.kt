package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.SavedSceneFilter
import com.sinema.model.SortOption

/**
 * Lists scene saved filters stored server-side in Stash. Selecting one opens
 * its scenes in a grid driven by the filter's raw criteria.
 */
class SavedFiltersActivity : FragmentActivity() {
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

/**
 * Maps a saved filter's sort field + direction to the grid's default sort.
 * Falls back to a field-only match, then Name A-Z.
 */
internal fun savedFilterDefaultSort(sortField: String?, direction: String?): SortOption =
    SortOption.entries.firstOrNull { it.field == sortField && it.direction == direction }
        ?: SortOption.entries.firstOrNull { it.field == sortField }
        ?: SortOption.PATH_ASC

class SavedFiltersFragment : SceneGridFragment() {
    override val gridTitle = "Saved Filters"
    override val emptyMessage = "No saved filters on this server"
    override val paged = true
    override val canFilter = false

    override fun createCardPresenter() = SavedFilterCardPresenter()

    override suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        // Stash returns the full list in one shot; page client-side so the
        // incremental scroll path stays exercised.
        val filters = app.api.findSavedSceneFilters()
        val pageItems = filters.drop((page - 1) * perPage).take(perPage)
        return Pair(filters.size, pageItems)
    }

    override fun onItemClicked(item: Any) {
        if (item is SavedSceneFilter) {
            startActivity(SavedFilterScenesActivity.intent(requireContext(), item))
        }
    }
}

/** Simple name card for a saved filter; Stash has no artwork for filters. */
class SavedFilterCardPresenter : androidx.leanback.widget.Presenter() {
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
            content.text = "Scene filter"
            image.setImageResource(android.R.drawable.ic_menu_sort_by_size)
            image.scaleType = ImageView.ScaleType.CENTER
            image.setBackgroundColor(0xFF444444.toInt())
        }
    }

    override fun onUnbindViewHolder(viewHolder: androidx.leanback.widget.Presenter.ViewHolder) {}
}

/**
 * Scene grid driven by one saved filter's raw criteria. The activity fetches
 * the filter once and hands the fragment everything it needs via arguments,
 * so paging never re-fetches the filter definition.
 */
class SavedFilterScenesActivity : FragmentActivity() {
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

    /**
     * Match the saved filter's sort field AND direction (e.g. path/DESC →
     * Name Z-A). Getter, not initializer: requireArguments() is only valid
     * after arguments are attached.
     */
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
