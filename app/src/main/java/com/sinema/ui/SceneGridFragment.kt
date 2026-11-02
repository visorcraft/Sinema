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
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import com.sinema.model.SceneFilterOptions
import com.sinema.model.SortOption
import com.sinema.model.cycled
import com.sinema.model.effectiveFavorites
import com.sinema.model.toggledQuickFavorites
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
    /** Defaults to the first page; override only for special accumulation. */
    protected open suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

    protected open suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        if (page > 1) return Pair(0, emptyList())
        val items = loadItems()
        return Pair(items.size, items)
    }

    protected open val sortScreenKey: String? = null
    /** Long-press "Assign tags" availability (Untagged triage grid). */
    protected open val canAssignTags: Boolean = false
    /** Alpha jump: filter by title for title sorts and by path otherwise. */
    protected open val supportsAlphaJump: Boolean = false
    protected var alphaPrefix: String? = null

    /** Anchored regex for the active alpha filter, or null when off. */
    protected fun alphaRegex(): String? =
        alphaPrefix?.takeIf { it.isNotBlank() }?.let { prefix ->
            if (prefix == "0-9") "^[0-9]" else "^$prefix"
        }
    protected open val filterScreenKey: String? get() = sortScreenKey
    protected open val defaultSort: SortOption = SortOption.PATH_ASC
    /** Cards shown in this grid; grids of non-Scene items override this. */
    protected open fun createCardPresenter(): Presenter =
        CardPresenter(app.api, onItemLongClick = ::showCardActions)
    protected var sort: SortOption = SortOption.PATH_ASC
    /** Backed by [filterController]; safe default before onCreate init. */
    protected var sceneFilter: SceneFilterOptions
        get() = if (::filterController.isInitialized) filterController.value else SceneFilterOptions()
        private set(_) { /* mutations go through SceneFilterController */ }
    protected var randomSeed: Int = 0
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var loadingPage = false
    private lateinit var pageState: GridPageState
    private var pagingGrid: VerticalGridView? = null

    override val filterLabel: String get() = filterController.value.summary

    override val canQuickFilter: Boolean get() = canFilter
    override val quickWatchLabel: String get() = filterController.watchLabel
    override val quickFavoritesLabel: String get() = filterController.favoritesLabel

    override fun toggleQuickWatch() = filterController.toggleWatch()
    override fun toggleQuickFavorites() = filterController.toggleFavorites()

    private lateinit var filterController: SceneFilterController

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
        val key = sortScreenKey
        if (key != null) {
            GridMenuDialog.show(requireContext(), sort, canPlayAll = true,
                onSort = { chosen ->
                    sort = chosen
                    app.prefs.setSortFor(key, chosen.name)
                    updateTitle()
                    reload()
                },
                onPlayAll = { playAll() },
                onShuffle = { playAll(shuffle = true) },
                onMarkAllWatched = { markAllWatched() },
                onRateAll = { showRateAllDialog() },
                onJumpToLetter = if (supportsAlphaJump) {
                    { showAlphaJumpDialog() }
                } else null,
                onBackToTop = { scrollToTop() }
            )
        } else {
            showSortDialog()
        }
    }

    protected open fun playAll(shuffle: Boolean = false) {
        val scenes = (0 until gridAdapter.size())
            .mapNotNull { gridAdapter.get(it) as? Scene }
        SceneIntents.playAll(requireContext(), scenes, shuffle = shuffle)
    }

    /** Long-press actions for an entity card: favorite toggle. */
    private fun showEntityActions(entity: com.sinema.model.EntityItem) {
        val index = (0 until gridAdapter.size()).firstOrNull { gridAdapter.get(it) === entity } ?: return
        val favoriteLabel = if (entity.isFavorite) getString(R.string.card_unfavorite) else getString(R.string.card_favorite)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(entity.name)
            .setItems(arrayOf(favoriteLabel)) { dialog, _ ->
                dialog.dismiss()
                toggleEntityFavorite(entity, index)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleEntityFavorite(entity: com.sinema.model.EntityItem, index: Int) {
        launchSafely(
            "Entity favorite toggle",
            onError = { e ->
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.entity_favorite_unsupported, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        ) {
            val wasFavorite = entity.isFavorite
            app.api.entitySetRating(entity.kind, entity.id, if (wasFavorite) null else 100)
            val updated = entity.copy(rating100 = if (wasFavorite) null else 100)
            if (index < gridAdapter.size() && gridAdapter.get(index) === entity) {
                gridAdapter.replace(index, updated)
            }
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    if (wasFavorite) getString(R.string.favorite_removed) else getString(R.string.favorite_added),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Alpha jump: filter the grid to scenes whose path starts with a letter. */
    private fun showAlphaJumpDialog() {
        val allLabel = getString(R.string.grid_jump_all)
        val digitsLabel = getString(R.string.grid_jump_digits)
        val letters = listOf(allLabel) + ('A'..'Z').map { it.toString() } + listOf(digitsLabel)
        // Canonical tokens, independent of localization: null | A..Z | "0-9".
        fun tokenAt(which: Int): String? = when (which) {
            0 -> null
            letters.size - 1 -> "0-9"
            else -> letters[which]
        }
        val currentIndex = when (alphaPrefix) {
            null -> 0
            "0-9" -> letters.size - 1
            else -> letters.indexOf(alphaPrefix).takeIf { it > 0 } ?: 0
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.grid_jump_title))
            .setSingleChoiceItems(letters.toTypedArray(), currentIndex) { dialog, which ->
                dialog.dismiss()
                alphaPrefix = tokenAt(which)
                updateTitle()
                reload()
            }
            .setNegativeButton(R.string.grid_cancel, null)
            .show()
    }

    /** Scenes currently visible in this grid (used by bulk actions). */
    private fun scenesInGrid(): List<Scene> =
        (0 until gridAdapter.size()).mapNotNull { gridAdapter.get(it) as? Scene }

    private fun markAllWatched() {
        bulkEdit(getString(R.string.bulk_mark_all_watched)) { scene ->
            if (!scene.isWatched) {
                app.api.clearResumeTime(scene.id)
                app.api.incrementPlayCount(scene.id)
            }
        }
    }

    private fun showRateAllDialog() {
        val options = (0..5).map { if (it == 0) getString(R.string.rate_all_no_rating) else "★".repeat(it) }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.rate_all_title))
            .setSingleChoiceItems(options.toTypedArray(), -1) { dialog, which ->
                dialog.dismiss()
                val rating100 = if (which == 0) null else which * 20
                bulkEdit(getString(R.string.bulk_rate_all)) { scene ->
                    app.api.setSceneRating(scene.id, rating100)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var bulkJob: Job? = null

    private fun bulkEdit(label: String, action: suspend (Scene) -> Unit) {
        val scenes = scenesInGrid()
        if (scenes.isEmpty()) return
        // One bulk job at a time: concurrent loops over stale snapshots would
        // double-apply play counts and ratings.
        if (bulkJob?.isActive == true) {
            Toast.makeText(requireContext(), getString(R.string.bulk_in_progress), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(requireContext(), getString(R.string.bulk_working, label), Toast.LENGTH_SHORT).show()
        bulkJob = lifecycleScope.launch {
            var ok = 0
            var failed = 0
            for (scene in scenes) {
                try {
                    action(scene)
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    Log.e("Sinema", "$label failed for ${scene.id}", e)
                }
            }
            // Hold the single-flight guard until fresh server state replaced
            // the grid, so a retried bulk edit never re-applies to scenes
            // that already succeeded.
            if (isAdded) {
                val suffix = if (failed > 0) getString(R.string.bulk_failed_suffix, failed) else ""
                Toast.makeText(requireContext(), getString(R.string.bulk_done, label, ok, suffix), Toast.LENGTH_LONG).show()
                reload()?.join()
            }
        }
    }

    /** Long-press actions for a focused scene card. */
    private fun showCardActions(item: Any) {
        if (item is com.sinema.model.EntityItem) {
            showEntityActions(item)
            return
        }
        val scene = item as? Scene ?: return
        val index = (0 until gridAdapter.size()).firstOrNull { gridAdapter.get(it) === scene } ?: return
        val playLabel = if (scene.resumeTime > 0.0) getString(R.string.card_resume) else getString(R.string.card_play)
        val actions = buildList {
            add(playLabel to Runnable {
                startActivity(
                    SceneIntents.playback(
                        requireContext(), scene.id,
                        resumeMs = (scene.resumeTime * 1000).toLong(),
                        width = scene.width, height = scene.height
                    )
                )
            })
            add(getString(R.string.card_play_from_here) to Runnable {
                val rest = (index until gridAdapter.size())
                    .mapNotNull { gridAdapter.get(it) as? Scene }
                SceneIntents.playAll(requireContext(), rest, startAt = 0)
            })
            if (scene.resumeTime > 0.0) {
                add(getString(R.string.card_remove_continue) to Runnable { clearResume(scene, index) })
            }
            if (canAssignTags) {
                add(getString(R.string.untagged_assign_tags) to Runnable { showAssignTagsDialog(scene) })
            }
            add(WatchedToggle.buttonLabel(scene.isWatched) to Runnable { toggleWatched(scene, index) })
            add(
                (if (scene.isFavorite) getString(R.string.card_unfavorite) else getString(R.string.card_favorite)) to
                    Runnable { toggleFavorite(scene, index) }
            )
            add(getString(R.string.card_details) to Runnable {
                startActivity(SceneIntents.detail(requireContext(), scene))
            })
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(scene.displayTitle)
            .setItems(actions.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions[which].second.run()
            }
            .show()
    }

    /** Multi-select tag picker for the untagged triage grid. */
    private fun showAssignTagsDialog(scene: Scene) {
        lifecycleScope.launch {
            try {
                val tags = mutableListOf<com.sinema.model.EntityItem>()
                var page = 1
                while (page <= 20) { // cap: 2000 tags
                    val (count, items) = app.api.findEntities(
                        com.sinema.model.EntityItem.Kind.TAG, page = page, perPage = 100
                    )
                    tags += items
                    if (items.isEmpty() || tags.size >= count) break
                    page++
                }
                if (!isAdded) return@launch
                if (tags.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.untagged_no_tags), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val checked = BooleanArray(tags.size)
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.untagged_assign_title, scene.displayTitle))
                    .setMultiChoiceItems(tags.map { it.name }.toTypedArray(), checked) { _, which, value ->
                        checked[which] = value
                    }
                    .setPositiveButton("Assign") { dialog, _ ->
                        dialog.dismiss()
                        val selected = tags.filterIndexed { i, _ -> checked[i] }.map { it.id }
                        if (selected.isEmpty()) return@setPositiveButton
                        lifecycleScope.launch {
                            try {
                                app.api.sceneUpdateTags(scene.id, selected)
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.untagged_assigned, selected.size), Toast.LENGTH_SHORT
                                    ).show()
                                    reload()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e("Sinema", "Tag assign failed for ${scene.id}", e)
                                if (isAdded) {
                                    Toast.makeText(requireContext(), getString(R.string.action_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Tag list load failed", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.tags_load_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Clears the server resume point so the scene drops off Continue Playing. */
    private fun clearResume(scene: Scene, index: Int) {
        launchSafely("Remove from Continue Playing") {
            app.api.clearResumeTime(scene.id)
            val updated = scene.copy(resumeTime = 0.0)
            if (index < gridAdapter.size() && gridAdapter.get(index) === scene) {
                gridAdapter.replace(index, updated)
            }
            Toast.makeText(requireContext(), getString(R.string.action_removed_continue), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleWatched(scene: Scene, index: Int) {
        launchSafely("Watched toggle") {
            val wasWatched = scene.isWatched
            if (wasWatched) {
                app.api.resetPlayCount(scene.id)
            } else {
                app.api.clearResumeTime(scene.id)
                app.api.incrementPlayCount(scene.id)
            }
            val updated = scene.copy(
                playCount = WatchedToggle.nextPlayCount(scene.playCount, wasWatched),
                resumeTime = if (wasWatched) scene.resumeTime else 0.0
            )
            if (index < gridAdapter.size() && gridAdapter.get(index) === scene) {
                gridAdapter.replace(index, updated)
            }
            Toast.makeText(requireContext(),
                if (wasWatched) getString(R.string.watched_unmarked) else getString(R.string.watched_marked),
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFavorite(scene: Scene, index: Int) {
        launchSafely("Favorite toggle") {
            val wasFavorite = scene.isFavorite
            app.api.setSceneRating(scene.id, if (wasFavorite) null else 100)
            val updated = scene.copy(rating100 = if (wasFavorite) null else 100)
            if (index < gridAdapter.size() && gridAdapter.get(index) === scene) {
                gridAdapter.replace(index, updated)
            }
            Toast.makeText(requireContext(),
                if (wasFavorite) getString(R.string.favorite_removed) else getString(R.string.favorite_added),
                Toast.LENGTH_SHORT).show()
        }
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

    private val discreetRebind = DiscreetRebind()

    override fun onResume() {
        super.onResume()
        // Discreet toggle while away: rebind visible cards without a reload.
        discreetRebind.onResume { gridAdapter.rebindCardsRecursively() }
    }

    private fun updateTitle() {
        title = buildString {
            append(if (sortScreenKey != null) "$gridTitle  •  ${sort.label}" else gridTitle)
            alphaPrefix?.takeIf { it.isNotBlank() }?.let { append("  •  $it") }
        }
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
        if (canFilter) filterScreenKey?.let { key ->
            filterController = SceneFilterController(
                prefs = app.prefs,
                screenKey = key,
                initial = app.prefs.sceneFilterFor(key),
                refreshControls = { activity?.refreshGridControls() },
                reload = { reload() }
            )
        }
        updateTitle()
        // No badgeDrawable here: it overlays the first grid row on screens
        // with their own headers (entity scenes, folders). The title text
        // alone identifies the screen.
        val gridPresenter = VerticalGridPresenter(TvMotion.focusZoom(), false)
        gridPresenter.numberOfColumns = columns
        setGridPresenter(gridPresenter)
        gridAdapter = ArrayObjectAdapter(createCardPresenter())
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
                attachGridNavigation(grid)
            }
        }
    }

    private val positionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hidePosition = Runnable {
        activity?.findViewById<View>(R.id.grid_position)?.visibility = View.GONE
    }

    override fun onDestroyView() {
        positionHandler.removeCallbacks(hidePosition)
        activity?.findViewById<View>(R.id.grid_position)?.visibility = View.GONE
        pagingGrid = null
        super.onDestroyView()
    }

    override fun pageBy(direction: Int): Boolean {
        val grid = gridView ?: return false
        if (gridAdapter.size() == 0) return false
        val positions = (0 until grid.childCount).map { index ->
            grid.getChildAdapterPosition(grid.getChildAt(index))
        }.filter { it >= 0 }
        if (positions.isEmpty()) return false
        val distance = grid.childCount.coerceAtLeast(columns)
        val target = if (direction > 0) {
            (positions.max() + distance).coerceAtMost(gridAdapter.size() - 1)
        } else {
            (positions.min() - distance).coerceAtLeast(0)
        }
        setSelectedPosition(target)
        grid.post { showGridPosition(grid) }
        return true
    }

    override fun scrollToTop(): Boolean {
        val grid = gridView ?: return false
        if (gridAdapter.size() == 0) return false
        setSelectedPosition(0)
        grid.post { showGridPosition(grid) }
        return true
    }

    private fun showGridPosition(grid: RecyclerView) {
        val positions = (0 until grid.childCount).map { index ->
            grid.getChildAdapterPosition(grid.getChildAt(index))
        }.filter { it >= 0 }
        if (positions.isEmpty()) return
        val label = GridNavigation.positionLabel(
            positions.min(), positions.max(), pageState.totalCount,
            template = getString(R.string.grid_position_range)
        )
        val positionView = activity?.findViewById<android.widget.TextView>(R.id.grid_position) ?: return
        positionView.text = label
        positionView.visibility = View.VISIBLE
        positionHandler.removeCallbacks(hidePosition)
        positionHandler.postDelayed(hidePosition, 1_500L)
    }

    private fun attachGridNavigation(grid: VerticalGridView) {
        if (pagingGrid === grid) return
        pagingGrid = grid
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy == 0) return
                showGridPosition(recyclerView)
                if (!paged || dy <= 0 || loadingPage || !pageState.canLoadMore) return
                val lastVisible = (0 until recyclerView.childCount).maxOfOrNull { index ->
                    recyclerView.getChildAdapterPosition(recyclerView.getChildAt(index))
                } ?: return
                if (lastVisible >= gridAdapter.size() - columns * 2) loadNextPage()
            }
        })
    }

    private fun renderLoadState(state: GridLoadState) {
        activity?.renderGridLoadState(state) { reload() }
    }

    protected fun reload(): Job? {
        updateTitle()
        loadJob?.cancel()
        val generation = ++loadGeneration
        pageState.reset()
        loadingPage = true
        renderLoadState(GridLoadState.loading(gridAdapter.size() > 0))
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
                renderLoadState(GridLoadState.loaded(gridAdapter.size(), emptyMessage))
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
                val message = UiErrorMessage.forException(e)
                if (gridAdapter.size() > 0) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                } else {
                    renderLoadState(GridLoadState.failed(
                        hasContent = false,
                        message = getString(R.string.grid_load_failed, message)
                    ))
                }
            } finally {
                if (generation == loadGeneration) loadingPage = false
            }
        }
        return loadJob
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
