package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.FolderItem
import com.sinema.model.SceneFilterOptions
import com.sinema.model.SortOption
import com.sinema.model.cycled
import com.sinema.model.effectiveFavorites
import com.sinema.model.toggledQuickFavorites
import com.sinema.util.FolderHelper
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class FolderBrowseActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        val path = intent.getStringExtra("path") ?: "/data"
        if (savedInstanceState == null) {
            val fragment = FolderGridFragment().apply {
                arguments = Bundle().apply { putString("path", path) }
            }
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

class FolderGridFragment : VerticalGridSupportFragment(), SortableScreen {
    private val app get() = SinemaApp.instance
    private lateinit var gridAdapter: ArrayObjectAdapter
    private var currentPath = "/data"

    private var sort: SortOption = SortOption.PATH_ASC
    private val randomSeed: Int = (0..99_999_999).random()
    private var loadJob: Job? = null

    override val canFilter = true
    override val filterLabel get() = filterController.value.summary

    // Quick watch/favorites toggles, shared semantics with SceneGridFragment.
    override val canQuickFilter = true
    override val quickWatchLabel: String get() = filterController.watchLabel
    override val quickFavoritesLabel: String get() = filterController.favoritesLabel
    override fun toggleQuickWatch() = filterController.toggleWatch()
    override fun toggleQuickFavorites() = filterController.toggleFavorites()

    private val filterController = SceneFilterController(
        prefs = app.prefs,
        screenKey = "folder",
        initial = app.prefs.sceneFilterFor("folder"),
        refreshControls = { activity?.refreshGridControls() },
        reload = { loadFolder() }
    )
    private val sceneFilter get() = filterController.value

    override fun showFilterDialog() {
        FilterDialog.show(
            requireContext(),
            filterController.value,
            onChosen = { chosen -> filterController.apply(chosen) },
            onSave = { chosen ->
                SavedFilterActions.promptSave(
                    requireActivity(), chosen, sort.apiSort(randomSeed), sort.direction
                )
            }
        )
    }

    override fun showGridMenu() {
        GridMenuDialog.show(requireContext(), sort, canPlayAll = true,
            onSort = { chosen ->
                sort = chosen
                app.prefs.setSortFor("folder", chosen.name)
                updateTitle()
                loadFolder()
            },
            onPlayAll = { playAll() },
            onShuffle = { playAll(shuffle = true) }
        )
    }

    private fun playAll(shuffle: Boolean = false) {
        val scenes = (0 until gridAdapter.size())
            .mapNotNull { (gridAdapter.get(it) as? FolderItem)?.scene }
        SceneIntents.playAll(requireContext(), scenes, shuffle = shuffle)
    }

    override fun showSortDialog() {
        SortDialog.show(requireContext(), sort) { chosen ->
            sort = chosen
            app.prefs.setSortFor("folder", chosen.name)
            updateTitle()
            loadFolder()
        }
    }

    private fun updateTitle() {
        title = "$currentPath  •  ${sort.label}"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (24 * density).toInt()
        val vPad = (20 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        activity?.refreshGridControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPath = arguments?.getString("path") ?: "/data"
        sort = SortOption.fromName(app.prefs.sortFor("folder"))
        updateTitle()
        badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false)
        gridPresenter.numberOfColumns = 4
        setGridPresenter(gridPresenter)

        gridAdapter = ArrayObjectAdapter(FolderCardPresenter())
        adapter = gridAdapter

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is FolderItem -> {
                    if (item.isFolder) {
                        val intent = Intent(requireContext(), FolderBrowseActivity::class.java)
                        intent.putExtra("path", item.fullPath)
                        startActivity(intent)
                    } else if (item.image != null) {
                        openImageViewer(item)
                    } else if (item.scene != null) {
                        startActivity(SceneIntents.detail(requireContext(), item.scene))
                    }
                }
            }
        }

        loadFolder()
    }

    private fun openImageViewer(item: FolderItem) {
        val imageItems = (0 until gridAdapter.size())
            .mapNotNull { (gridAdapter.get(it) as? FolderItem)?.image }
        val index = imageItems.indexOfFirst { it.id == item.image?.id }
        startActivity(ImageViewActivity.intentFor(requireContext(), imageItems, index))
    }

    private fun loadFolder() {
        loadJob?.cancel()
        activity?.renderGridLoadState(GridLoadState.loading(gridAdapter.size() > 0)) { loadFolder() }
        loadJob = lifecycleScope.launch {
            try {
                val scenesJob = async {
                    // sort applies to scene queries only; image/folder queries keep their current order
                    fetchAllPages { page ->
                        app.api.findScenesByPath(
                            currentPath, page, PAGE_SIZE,
                            sort = sort.apiSort(randomSeed),
                            direction = sort.direction,
                            filters = sceneFilter
                        )
                    }
                }
                val imagesJob = async {
                    fetchAllPages { page -> app.api.findImagesByPath(currentPath, page, PAGE_SIZE) }
                }
                val (scenes, scenesTruncated) = scenesJob.await()
                val (images, imagesTruncated) = imagesJob.await()
                val items = FolderHelper.buildFolderContents(scenes, images, currentPath)
                gridAdapter.clear()
                items.forEach { gridAdapter.add(it) }
                activity?.renderGridLoadState(
                    GridLoadState.loaded(items.size, "This folder is empty")
                ) { loadFolder() }
                if (scenesTruncated || imagesTruncated) {
                    Toast.makeText(requireContext(),
                        "Large folder: showing the first $MAX_ITEMS videos/pictures",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e // back-press cancels the scope; not a real error
            } catch (e: Exception) {
                val message = UiErrorMessage.forException(e)
                if (gridAdapter.size() > 0) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                } else {
                    activity?.renderGridLoadState(GridLoadState.failed(false, message)) { loadFolder() }
                }
            }
        }
    }

    /**
     * Fetches every page of a (count, items) query up to MAX_ITEMS.
     * Returns the items and whether the result was truncated.
     */
    private suspend fun <T> fetchAllPages(
        fetch: suspend (page: Int) -> Pair<Int, List<T>>
    ): Pair<List<T>, Boolean> {
        val all = mutableListOf<T>()
        var page = 1
        while (true) {
            val (count, batch) = fetch(page)
            all.addAll(batch)
            if (all.size >= MAX_ITEMS) return Pair(all.take(MAX_ITEMS), all.size > MAX_ITEMS || all.size < count)
            if (all.size >= count || batch.isEmpty()) return Pair(all, false)
            page++
        }
    }

    companion object {
        private const val PAGE_SIZE = 1000
        private const val MAX_ITEMS = 10_000
    }
}
