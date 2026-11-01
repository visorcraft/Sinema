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
    protected abstract suspend fun loadItems(): List<Any>

    protected open suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        if (page > 1) return Pair(0, emptyList())
        val items = loadItems()
        return Pair(items.size, items)
    }

    protected open val sortScreenKey: String? = null
    /** Long-press "Assign tags" availability (Untagged triage grid). */
    protected open val canAssignTags: Boolean = false
    /** Alpha jump: "Jump to letter" entry filters grids by path prefix. */
    protected open val supportsAlphaJump: Boolean = false
    protected var alphaPrefix: String? = null

    /** Anchored regex for the active alpha filter, or null when off. */
    protected fun alphaPathRegex(): String? =
        alphaPrefix?.takeIf { it.isNotBlank() }?.let { prefix ->
            if (prefix == "0-9") "^[0-9]" else "^$prefix"
        }
    protected open val filterScreenKey: String? get() = sortScreenKey
    protected open val defaultSort: SortOption = SortOption.PATH_ASC
    /** Cards shown in this grid; grids of non-Scene items override this. */
    protected open fun createCardPresenter(): Presenter =
        CardPresenter(app.api, onItemLongClick = ::showCardActions)
    protected var sort: SortOption = SortOption.PATH_ASC
    protected var sceneFilter = SceneFilterOptions()
    protected var randomSeed: Int = 0
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var loadingPage = false
    private lateinit var pageState: GridPageState
    private var pagingGrid: VerticalGridView? = null

    override val filterLabel: String get() = sceneFilter.summary

    override val canQuickFilter: Boolean get() = canFilter
    override val quickWatchLabel: String get() = sceneFilter.watch.label
    override val quickFavoritesLabel: String
        get() = if (sceneFilter.effectiveFavorites()) "♥ Favorites ✓" else "♡ Favorites"

    override fun toggleQuickWatch() {
        sceneFilter = sceneFilter.copy(watch = sceneFilter.watch.cycled())
        filterScreenKey?.let { app.prefs.setSceneFilterFor(it, sceneFilter) }
        reload()
    }

    override fun toggleQuickFavorites() {
        sceneFilter = sceneFilter.toggledQuickFavorites()
        filterScreenKey?.let { app.prefs.setSceneFilterFor(it, sceneFilter) }
        reload()
    }

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
            sceneFilter,
            onChosen = { chosen ->
                sceneFilter = chosen
                filterScreenKey?.let { app.prefs.setSceneFilterFor(it, chosen) }
                activity?.refreshGridControls()
                reload()
            },
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
                } else null
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
        val favoriteLabel = if (entity.isFavorite) "💔 Unfavorite" else "❤️ Favorite"
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
        lifecycleScope.launch {
            try {
                val wasFavorite = entity.isFavorite
                app.api.entitySetRating(entity.kind, entity.id, if (wasFavorite) null else 100)
                val updated = entity.copy(rating100 = if (wasFavorite) null else 100)
                if (index < gridAdapter.size() && gridAdapter.get(index) === entity) {
                    gridAdapter.replace(index, updated)
                }
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        if (wasFavorite) "Removed from favorites" else "Added to favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Entity favorite toggle failed for ${entity.id}", e)
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Server does not support entity ratings: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Alpha jump: filter the grid to scenes whose path starts with a letter. */
    private fun showAlphaJumpDialog() {
        val letters = listOf("All") + ('A'..'Z').map { it.toString() } + listOf("0-9")
        val current = alphaPrefix?.takeIf { it.isNotBlank() } ?: "All"
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Jump to letter")
            .setSingleChoiceItems(letters.toTypedArray(), letters.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                alphaPrefix = if (which == 0) null else letters[which]
                updateTitle()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Scenes currently visible in this grid (used by bulk actions). */
    private fun scenesInGrid(): List<Scene> =
        (0 until gridAdapter.size()).mapNotNull { gridAdapter.get(it) as? Scene }

    private fun markAllWatched() {
        bulkEdit("Mark all watched") { scene ->
            if (!scene.isWatched) {
                app.api.clearResumeTime(scene.id)
                app.api.incrementPlayCount(scene.id)
            }
        }
    }

    private fun showRateAllDialog() {
        val options = (0..5).map { if (it == 0) "No rating" else "★".repeat(it) }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Rate all scenes")
            .setSingleChoiceItems(options.toTypedArray(), -1) { dialog, which ->
                dialog.dismiss()
                val rating100 = if (which == 0) null else which * 20
                bulkEdit("Rated all scenes") { scene ->
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
            Toast.makeText(requireContext(), "Still working on the previous bulk edit", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(requireContext(), "$label…", Toast.LENGTH_SHORT).show()
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
                val suffix = if (failed > 0) ", $failed failed" else ""
                Toast.makeText(requireContext(), "$label: $ok scenes$suffix", Toast.LENGTH_LONG).show()
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
        val playLabel = if (scene.resumeTime > 0.0) "▶ Resume" else "▶ Play"
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
            add("▶ Play from here" to Runnable {
                val rest = (index until gridAdapter.size())
                    .mapNotNull { gridAdapter.get(it) as? Scene }
                SceneIntents.playAll(requireContext(), rest, startAt = 0)
            })
            if (scene.resumeTime > 0.0) {
                add("✖ Remove from Continue Playing" to Runnable { clearResume(scene, index) })
            }
            if (canAssignTags) {
                add("🏷 Assign tags" to Runnable { showAssignTagsDialog(scene) })
            }
            add(WatchedToggle.buttonLabel(scene.isWatched) to Runnable { toggleWatched(scene, index) })
            add(
                (if (scene.isFavorite) "💔 Unfavorite" else "❤️ Favorite") to
                    Runnable { toggleFavorite(scene, index) }
            )
            add("ℹ️ Details" to Runnable {
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
                    Toast.makeText(requireContext(), "No tags on this server", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val checked = BooleanArray(tags.size)
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Assign tags — ${scene.displayTitle}")
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
                                        "Assigned ${selected.size} tags", Toast.LENGTH_SHORT
                                    ).show()
                                    reload()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e("Sinema", "Tag assign failed for ${scene.id}", e)
                                if (isAdded) {
                                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(requireContext(), "Could not load tags: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Clears the server resume point so the scene drops off Continue Playing. */    private fun clearResume(scene: Scene, index: Int) {
        lifecycleScope.launch {
            try {
                app.api.clearResumeTime(scene.id)
                val updated = scene.copy(resumeTime = 0.0)
                if (index < gridAdapter.size() && gridAdapter.get(index) === scene) {
                    gridAdapter.replace(index, updated)
                }
                Toast.makeText(requireContext(), "Removed from Continue Playing", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Remove from Continue Playing failed for ${scene.id}", e)
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleWatched(scene: Scene, index: Int) {
        lifecycleScope.launch {
            try {
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
                    if (wasWatched) "Marked as unwatched" else "Marked as watched",
                    Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Watched toggle failed for ${scene.id}", e)
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleFavorite(scene: Scene, index: Int) {
        lifecycleScope.launch {
            try {
                val wasFavorite = scene.isFavorite
                app.api.setSceneRating(scene.id, if (wasFavorite) null else 100)
                val updated = scene.copy(rating100 = if (wasFavorite) null else 100)
                if (index < gridAdapter.size() && gridAdapter.get(index) === scene) {
                    gridAdapter.replace(index, updated)
                }
                Toast.makeText(requireContext(),
                    if (wasFavorite) "Removed from favorites" else "Added to favorites",
                    Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Favorite toggle failed for ${scene.id}", e)
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("random_seed", randomSeed)
    }

    private var pausedFocusIndex = -1
    private var pausedFocusKey: String? = null
    private var boundDiscreetGeneration = CardPresenter.discreetGeneration

    private val gridView get() = view?.let { findGridView(it as? ViewGroup) }

    override fun onPause() {
        super.onPause()
        val idx = gridView?.focusedChild
            ?.let { gridView!!.getChildAdapterPosition(it) } ?: -1
        pausedFocusIndex = idx
        pausedFocusKey = if (idx in 0 until gridAdapter.size())
            gridAdapter.get(idx)?.let(FocusRestore::keyOf) else null
    }

    override fun onResume() {
        super.onResume()
        // Discreet toggle while away: rebind visible cards without a reload.
        if (boundDiscreetGeneration != CardPresenter.discreetGeneration) {
            boundDiscreetGeneration = CardPresenter.discreetGeneration
            if (gridAdapter.size() > 0) gridAdapter.notifyItemRangeChanged(0, gridAdapter.size())
        }
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
        if (canFilter) filterScreenKey?.let { sceneFilter = app.prefs.sceneFilterFor(it) }
        updateTitle()
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.sinema_logo)
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
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

    protected fun reload(): Job? {
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
