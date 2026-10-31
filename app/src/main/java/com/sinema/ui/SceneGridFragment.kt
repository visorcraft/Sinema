package com.sinema.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import com.sinema.model.SceneFilterOptions
import com.sinema.model.SortOption
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class SceneGridFragment : VerticalGridSupportFragment(), SortableScreen {
    protected val app get() = SinemaApp.instance
    protected lateinit var gridAdapter: ArrayObjectAdapter

    protected open val columns = 3
    protected open val paged = false
    protected open val pageSize = 100
    protected abstract val gridTitle: String
    protected abstract val emptyMessage: String
    protected abstract suspend fun loadItems(): List<Any>

    protected open suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        if (page > 1) return Pair(0, emptyList())
        val items = loadItems()
        return Pair(items.size, items)
    }

    protected open val sortScreenKey: String? = null
    protected open val filterScreenKey: String? get() = sortScreenKey
    protected open val defaultSort: SortOption = SortOption.PATH_ASC
    protected var sort: SortOption = SortOption.PATH_ASC
    protected var sceneFilter = SceneFilterOptions()
    protected var randomSeed: Int = 0
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var loadingPage = false
    private lateinit var pageState: GridPageState
    private var pagingGrid: VerticalGridView? = null

    override val filterLabel: String get() = sceneFilter.summary

    override fun showSortDialog() {
        val key = sortScreenKey ?: return
        SortDialog.show(requireContext(), sort) { chosen ->
            sort = chosen
            app.prefs.setSortFor(key, chosen.name)
            updateTitle()
            reload()
        }
    }

    override fun showFilterDialog() {
        FilterDialog.show(requireContext(), sceneFilter) { chosen ->
            sceneFilter = chosen
            filterScreenKey?.let { app.prefs.setSceneFilterFor(it, chosen) }
            activity?.refreshGridControls()
            reload()
        }
    }

    override fun showGridMenu() {
        val key = sortScreenKey
        if (key != null) {
            GridMenuDialog.show(requireContext(), sort, canPlayAll = true,
                onSort = { chosen ->
                    sort = chosen
                    app.prefs.setSortFor(key, chosen.name)
                    updateTitle()
                    reload()
                },
                onPlayAll = { playAll() }
            )
        } else {
            showSortDialog()
        }
    }

    protected open fun playAll() {
        val scenes = (0 until gridAdapter.size())
            .mapNotNull { gridAdapter.get(it) as? Scene }
        SceneIntents.playAll(requireContext(), scenes)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("random_seed", randomSeed)
    }

    private var pausedFocusIndex = -1
    private var pausedFocusKey: String? = null

    private val gridView get() = view?.let { findGridView(it as? ViewGroup) }

    override fun onPause() {
        super.onPause()
        val idx = gridView?.focusedChild
            ?.let { gridView!!.getChildAdapterPosition(it) } ?: -1
        pausedFocusIndex = idx
        pausedFocusKey = if (idx in 0 until gridAdapter.size())
            gridAdapter.get(idx)?.let(FocusRestore::keyOf) else null
    }

    private fun updateTitle() {
        title = if (sortScreenKey != null) "$gridTitle  •  ${sort.label}" else gridTitle
    }

    protected open fun onItemClicked(item: Any) {
        if (item is Scene) startActivity(SceneIntents.detail(requireContext(), item))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageState = GridPageState(paged)
        randomSeed = savedInstanceState?.getInt("random_seed")
            ?: (0..99_999_999).random()
        sortScreenKey?.let { sort = SortOption.fromName(app.prefs.sortFor(it), defaultSort) }
        if (canFilter) filterScreenKey?.let { sceneFilter = app.prefs.sceneFilterFor(it) }
        updateTitle()
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.sinema_logo)
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = columns
        setGridPresenter(gridPresenter)
        gridAdapter = ArrayObjectAdapter(CardPresenter(app.api))
        adapter = gridAdapter
        setOnItemViewClickedListener { _, item, _, _ -> item?.let { onItemClicked(it) } }
        reload()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val vPad = (27 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        activity?.refreshGridControls()
        view.viewTreeObserver.addOnGlobalLayoutListener {
            findGridView(view as? ViewGroup)?.let { grid ->
                if (grid.paddingLeft != hPad) {
                    grid.setPadding(hPad, vPad, hPad, vPad)
                    grid.clipToPadding = false
                }
                attachPagination(grid)
            }
        }
    }

    override fun onDestroyView() {
        pagingGrid = null
        super.onDestroyView()
    }

    private fun attachPagination(grid: VerticalGridView) {
        if (!paged || pagingGrid === grid) return
        pagingGrid = grid
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || loadingPage || !pageState.canLoadMore) return
                val lastVisible = (0 until recyclerView.childCount).maxOfOrNull { index ->
                    recyclerView.getChildAdapterPosition(recyclerView.getChildAt(index))
                } ?: return
                if (lastVisible >= gridAdapter.size() - columns * 2) loadNextPage()
            }
        })
    }

    protected fun reload() {
        updateTitle()
        loadJob?.cancel()
        val generation = ++loadGeneration
        pageState.reset()
        loadingPage = true
        var oldIndex = gridView?.focusedChild
            ?.let { gridView!!.getChildAdapterPosition(it) } ?: -1
        var oldKey: String? = null
        if (oldIndex >= 0) {
            oldKey = if (oldIndex < gridAdapter.size())
                gridAdapter.get(oldIndex)?.let(FocusRestore::keyOf) else null
        } else {
            oldIndex = pausedFocusIndex
            oldKey = pausedFocusKey
        }
        loadJob = lifecycleScope.launch {
            try {
                val (count, items) = loadPage(1, pageSize)
                if (generation != loadGeneration) return@launch
                gridAdapter.clear()
                items.forEach { gridAdapter.add(it) }
                pageState.record(count, items.size, gridAdapter.size())
                if (items.isEmpty()) {
                    Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_SHORT).show()
                }
                if (oldIndex >= 0) {
                    val newIndex = FocusRestore.positionAfterReload(oldKey, oldIndex,
                        (0 until gridAdapter.size()).map { FocusRestore.keyOf(gridAdapter.get(it)!!) })
                    if (newIndex >= 0) {
                        gridView?.post {
                            if (gridView != null) setSelectedPosition(newIndex)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Failed to load first page for $gridTitle", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (generation == loadGeneration) loadingPage = false
            }
        }
    }

    private fun loadNextPage() {
        if (loadingPage || !pageState.canLoadMore) return
        loadingPage = true
        val generation = loadGeneration
        val page = pageState.nextPage
        loadJob = lifecycleScope.launch {
            try {
                val (count, items) = loadPage(page, pageSize)
                if (generation != loadGeneration) return@launch
                items.forEach { gridAdapter.add(it) }
                pageState.record(count, items.size, gridAdapter.size())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Failed to load page $page for $gridTitle", e)
                Toast.makeText(requireContext(), "Error loading more: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (generation == loadGeneration) loadingPage = false
            }
        }
    }

    private fun findGridView(vg: ViewGroup?): VerticalGridView? {
        if (vg == null) return null
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)
            if (child is VerticalGridView) return child
            if (child is ViewGroup) findGridView(child)?.let { return it }
        }
        return null
    }
}
