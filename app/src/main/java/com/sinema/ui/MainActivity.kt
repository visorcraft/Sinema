package com.sinema.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import com.sinema.model.Scene
import com.sinema.util.SceneIntents
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private var waitingForPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Wipe saved fragment state to prevent zombie fragment restoration
        savedInstanceState?.remove("android:support_fragments")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Check if API key is configured - if not, go to setup
        if (!app.prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        
        // Check if PIN is required and not verified
        if (app.prefs.hasPinSet() && !app.pinVerifiedThisSession) {
            waitingForPin = true
            startActivity(Intent(this, PinActivity::class.java))
            return
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame, MainFragment())
            .commitNowAllowingStateLoss()
    }
    
    override fun onResume() {
        super.onResume()
        if (waitingForPin && app.pinVerifiedThisSession) {
            waitingForPin = false
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, MainFragment())
                .commitNowAllowingStateLoss()
        } else if (waitingForPin && !app.pinVerifiedThisSession) {
            // PIN was cancelled/failed — PinActivity calls finishAffinity on back
        }
    }
}

class MainFragment : RowsSupportFragment() {
    private val app get() = SinemaApp.instance
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var isLoading = false
    private var continueState = HomeRowState<Pair<Scene, Double>>(CONTINUE_ROW_ID, "Continue Playing")
    private var recentlyPlayedState = HomeRowState<Scene>(PLAYED_ROW_ID, "Recently Played")
    private var recentlyAddedState = HomeRowState<Scene>(RECENT_ROW_ID, "Recently Added")
    private var favoritesState = HomeRowState<Scene>(FAVORITES_ROW_ID, "Favorites")
    private val contentRows = mutableMapOf<Long, ListRow>()
    private var heroRow: ListRow? = null
    private lateinit var shortcutsRow: ListRow
    private lateinit var settingsRow: ListRow

    private var logoView: android.widget.ImageView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        // Overscan-safe padding so content isn't clipped at screen edges
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val vPad = (27 * density).toInt()
        val topPad = (80 * density).toInt()   // room for logo above content
        view.setPadding(hPad, topPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        // Also set padding on the VerticalGridView (actual row container)
        verticalGridView?.let { grid ->
            grid.setPadding(hPad, topPad, hPad, vPad)
            grid.clipToPadding = false
        }

        // Add Sinema logo to activity's FrameLayout (not the fragment's RecyclerView!)
        val logo = android.widget.ImageView(requireContext()).apply {
            setImageResource(R.drawable.sinema_logo)
            adjustViewBounds = true
            val logoW = (180 * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(logoW, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = hPad
                topMargin = (20 * density).toInt()
            }
        }
        // Add to the activity's root FrameLayout, above the fragment
        (requireActivity().findViewById<View>(R.id.main_frame)?.parent as? ViewGroup)?.addView(logo)
        logoView = logo

        // Scroll the logo away with content
        verticalGridView?.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            private var totalScrolled = 0
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                totalScrolled += dy
                logoView?.translationY = -totalScrolled.toFloat()
            }
        })

        val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        lrp.shadowEnabled = false
        rowsAdapter = ArrayObjectAdapter(lrp)
        adapter = rowsAdapter
        createStaticRows()

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Scene -> startActivity(SceneIntents.detail(requireContext(), item))
                is HeroItem -> {
                    // One click on the hero resumes (or plays) directly —
                    // the scene detail screen stays available via rows/cards.
                    startActivity(
                        SceneIntents.playback(
                            requireContext(), item.scene.id, item.resumeMs,
                            item.scene.width, item.scene.height
                        )
                    )
                }
                is String -> {
                    when (item) {
                        "Settings" -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                        "Search" -> startActivity(Intent(requireContext(), SearchActivity::class.java))
                        "Surprise Me" -> playRandomScene()
                        "Favorites" -> startActivity(Intent(requireContext(), FavoritesActivity::class.java))
                        "Browse Folders" -> startActivity(Intent(requireContext(), BrowseFoldersActivity::class.java))
                        "Tags" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.TAG))
                        "Performers" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.PERFORMER))
                        "Studios" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.STUDIO))
                        "Saved Filters" -> startActivity(Intent(requireContext(), SavedFiltersActivity::class.java))
                        "Log Out" -> {
                            app.pinVerifiedThisSession = false
                            requireActivity().finishAffinity()
                        }
                        "Refresh" -> {
                            loadContent()
                            Toast.makeText(requireContext(), "Refreshing...", Toast.LENGTH_SHORT).show()
                        }
                        "About" -> {
                            android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Sinema")
                                .setMessage("Version ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}\n\nAn Android TV client for Stash.\n\nCreated by VisorCraft\nhttps://github.com/visorcraft/Sinema")
                                .setPositiveButton("OK", null)
                                .setNeutralButton("Check for Updates") { _, _ ->
                                    lifecycleScope.launch {
                                        val update = com.sinema.util.UpdateChecker.checkForUpdate(requireContext())
                                        if (update != null) {
                                            com.sinema.util.UpdateChecker.promptUpdate(requireContext(), update)
                                        } else {
                                            Toast.makeText(requireContext(), "You're up to date!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                }
                // FolderItem clicks handled in BrowseFoldersActivity
            }
        }
    }

    private fun createStaticRows() {
        val shortcutsAdapter = ArrayObjectAdapter(SettingsPresenter())
        shortcutsAdapter.add("Search")
        shortcutsAdapter.add("Surprise Me")
        shortcutsAdapter.add("Favorites")
        shortcutsAdapter.add("Browse Folders")
        shortcutsAdapter.add("Tags")
        shortcutsAdapter.add("Performers")
        shortcutsAdapter.add("Studios")
        shortcutsAdapter.add("Saved Filters")
        if (app.prefs.hasPinSet()) shortcutsAdapter.add("Log Out")
        shortcutsRow = ListRow(HeaderItem(SHORTCUTS_ROW_ID, ""), shortcutsAdapter)

        val settingsAdapter = ArrayObjectAdapter(SettingsPresenter())
        settingsAdapter.add("Refresh")
        settingsAdapter.add("Settings")
        settingsAdapter.add("About")
        settingsRow = ListRow(HeaderItem(SETTINGS_ROW_ID, ""), settingsAdapter)

        rowsAdapter.add(shortcutsRow)
        rowsAdapter.add(settingsRow)
    }

    override fun onResume() {
        super.onResume()
        loadContent()
    }

    fun forceReload() {
        loadContent()
    }

    /**
     * "Surprise Me" — pick one random scene from the whole library and
     * jump straight into playback (no queue). In-flight guard keeps a
     * double D-pad press from stacking two player activities.
     */
    private var randomSceneJob: Job? = null

    private fun playRandomScene() {
        if (randomSceneJob?.isActive == true) return
        val appCtx = context?.applicationContext ?: return
        Toast.makeText(appCtx, "Picking a random scene...", Toast.LENGTH_SHORT).show()
        randomSceneJob = lifecycleScope.launch {
            try {
                val scene = app.api.findRandomScene()
                if (scene == null) {
                    Toast.makeText(appCtx, "No scenes found on server", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (!isAdded) return@launch
                startActivity(SceneIntents.playback(requireContext(), scene.id, 0L, scene.width, scene.height))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Surprise Me failed", e)
                Toast.makeText(appCtx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private data class RowFocus(val rowIndex: Int, val rowKey: String?,
                                val itemIndex: Int, val itemKey: String?)

    private var pausedFocus = RowFocus(-1, null, -1, null)

    override fun onPause() {
        super.onPause()
        pausedFocus = captureFocus()
    }

    private fun rowKey(row: ListRow): String {
        val headerId = row.headerItem?.id ?: 0L
        if (headerId != 0L) return "row:$headerId"
        val first = (row.adapter as? ArrayObjectAdapter)
            ?.takeIf { it.size() > 0 }?.get(0)
        return "${row.headerItem?.name ?: ""}|${first?.let(FocusRestore::keyOf) ?: ""}"
    }

    private fun captureFocus(): RowFocus {
        val rowIndex = verticalGridView?.focusedChild
            ?.let { verticalGridView!!.getChildAdapterPosition(it) } ?: -1
        if (rowIndex !in 0 until rowsAdapter.size()) return RowFocus(-1, null, -1, null)
        val row = rowsAdapter.get(rowIndex) as? ListRow
        var itemIndex = -1
        var itemKey: String? = null
        val card = verticalGridView!!.focusedChild?.findFocus()
        var child: View? = card
        var ancestor = card?.parent as? View
        while (ancestor != null && ancestor !is HorizontalGridView) {
            child = ancestor
            ancestor = ancestor.parent as? View
        }
        val hGrid = ancestor as? HorizontalGridView
        if (hGrid != null && child != null) {
            itemIndex = hGrid.getChildAdapterPosition(child)
            val rowAdapter = row?.adapter as? ArrayObjectAdapter
            if (itemIndex in 0 until (rowAdapter?.size() ?: 0)) {
                itemKey = rowAdapter!!.get(itemIndex)?.let(FocusRestore::keyOf)
            }
        }
        return RowFocus(rowIndex, row?.let { rowKey(it) }, itemIndex, itemKey)
    }

    private fun updateItems(row: ListRow, items: List<Any>) {
        val adapter = row.adapter as ArrayObjectAdapter
        val current = (0 until adapter.size()).map { adapter.get(it) }
        if (current == items) return
        adapter.clear()
        items.forEach(adapter::add)
    }

    private fun indexOfRow(row: ListRow): Int =
        (0 until rowsAdapter.size()).indexOfFirst { rowsAdapter.get(it) === row }

    private fun reconcileContentRow(state: HomeRowState<out Any>, items: List<Any>?) {
        val existing = contentRows[state.id]
        if (!state.visible) {
            existing?.let(rowsAdapter::remove)
            contentRows.remove(state.id)
            return
        }
        val row = existing ?: ListRow(
            HeaderItem(state.id, state.displayTitle),
            ArrayObjectAdapter(CardPresenter(app.api))
        ).also { contentRows[state.id] = it }
        if (row.headerItem?.name != state.displayTitle) {
            row.headerItem = HeaderItem(state.id, state.displayTitle)
            val index = indexOfRow(row)
            if (index >= 0) rowsAdapter.notifyItemRangeChanged(index, 1)
        }
        if (items != null) updateItems(row, items)
    }

    private fun reconcileHomeRows() {
        val heroItem = continueState.items.orEmpty().firstOrNull()?.let { (scene, resumeSeconds) ->
            HeroItem(scene, (resumeSeconds * 1000).toLong())
        }
        if (heroItem == null) {
            heroRow?.let(rowsAdapter::remove)
            heroRow = null
        } else {
            val row = heroRow ?: ListRow(
                HeaderItem(HERO_ROW_ID, ""),
                ArrayObjectAdapter(HeroPresenter(app.api))
            ).also { heroRow = it }
            updateItems(row, listOf(heroItem))
        }

        reconcileContentRow(continueState, continueState.items?.map { it.first })
        reconcileContentRow(recentlyPlayedState, recentlyPlayedState.items)
        reconcileContentRow(recentlyAddedState, recentlyAddedState.items)
        reconcileContentRow(favoritesState, favoritesState.items?.take(20))

        val orderedRows = buildList {
            heroRow?.let(::add)
            add(shortcutsRow)
            listOf(continueState, recentlyPlayedState, recentlyAddedState, favoritesState).forEach { state ->
                if (state.visible) contentRows[state.id]?.let(::add)
            }
            add(settingsRow)
        }
        orderedRows.forEachIndexed { index, row ->
            val currentIndex = indexOfRow(row)
            if (currentIndex < 0) {
                rowsAdapter.add(index, row)
            } else if (currentIndex != index) {
                rowsAdapter.remove(row)
                rowsAdapter.add(index, row)
            }
        }
    }

    private fun <T> applyResult(state: HomeRowState<T>, result: RowResult<List<T>>): HomeRowState<T> =
        result.error?.let(state::failed) ?: state.loaded(result.value.orEmpty())

    private fun loadContent() {
        if (isLoading) return
        isLoading = true
        val live = captureFocus()
        val focus = if (live.rowIndex >= 0) live else pausedFocus
        continueState = continueState.refreshing()
        recentlyPlayedState = recentlyPlayedState.refreshing()
        recentlyAddedState = recentlyAddedState.refreshing()
        favoritesState = favoritesState.refreshing()
        reconcileHomeRows()
        lifecycleScope.launch {
            try {
                Log.d("Sinema", "loadContent starting")
                var firstError: Exception? = null

                fun noteError(label: String, e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("Sinema", "loadContent $label failed", e)
                    if (firstError == null) firstError = e
                }

                val rows = coroutineScope {
                    val continueJob = async { loadRow { app.api.findContinuePlaying() } }
                    val playedJob = async { loadRow { app.api.findRecentlyPlayed(25) } }
                    val recentJob = async { loadRow { app.api.findRecentScenes(25) } }
                    val favJob = async { loadRow { app.api.findFavoriteScenes() } }
                    DashboardLoad(continueJob.await(), playedJob.await(), recentJob.await(), favJob.await())
                }

                continueState = applyResult(continueState, rows.continuePlaying)
                recentlyPlayedState = applyResult(recentlyPlayedState, rows.recentlyPlayed)
                recentlyAddedState = applyResult(recentlyAddedState, rows.recentlyAdded)
                favoritesState = applyResult(favoritesState, rows.favorites)
                rows.continuePlaying.error?.let { noteError("Continue Playing", it) }
                rows.recentlyPlayed.error?.let { noteError("Recently Played", it) }
                rows.recentlyAdded.error?.let { noteError("Recently Added", it) }
                rows.favorites.error?.let { noteError("Favorites", it) }
                reconcileHomeRows()

                val continuePairs = rows.continuePlaying.value.orEmpty()
                val recentScenes = rows.recentlyAdded.value.orEmpty()
                Log.d("Sinema", "Rows loaded: ${rowsAdapter.size()}")

                // Sync TV channels (fire-and-forget on appScope). Capture the
                // application context now, while the fragment is attached:
                // appScope outlives this fragment, so calling requireContext()
                // inside the coroutine would throw if the user navigated away
                // before it ran. context may already be null after a long load.
                val appCtx = context?.applicationContext
                if (appCtx != null) {
                    app.appScope.launch {
                        com.sinema.util.TvChannels.syncWatchNext(appCtx, continuePairs)
                        com.sinema.util.TvChannels.syncRecentlyAdded(appCtx, recentScenes)
                    }
                }

                firstError?.let { e ->
                    if (!isAdded) return@let
                    val message = e.message ?: "Unknown error"
                    if (message.contains("401")) {
                        Toast.makeText(requireContext(), "Authentication failed. Please sign in again.", Toast.LENGTH_LONG).show()
                        startActivity(Intent(requireContext(), SetupActivity::class.java))
                        requireActivity().finish()
                        return@launch
                    } else {
                        Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("Sinema", "loadContent failed", e)
                if (continueState.loading) continueState = continueState.failed(e)
                if (recentlyPlayedState.loading) recentlyPlayedState = recentlyPlayedState.failed(e)
                if (recentlyAddedState.loading) recentlyAddedState = recentlyAddedState.failed(e)
                if (favoritesState.loading) favoritesState = favoritesState.failed(e)
                reconcileHomeRows()
                if (!isAdded) return@launch
                val message = e.message ?: "Unknown error"
                if (message.contains("401")) {
                    Toast.makeText(requireContext(), "Authentication failed. Please sign in again.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(requireContext(), SetupActivity::class.java))
                    requireActivity().finish()
                    return@launch
                } else {
                    Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
            }

            // Restore focus to the same logical row/item after reconciliation.
            if (focus.rowIndex >= 0) {
                val newRowIndex = FocusRestore.positionAfterReload(focus.rowKey, focus.rowIndex,
                    (0 until rowsAdapter.size()).mapNotNull { i ->
                        (rowsAdapter.get(i) as? ListRow)?.let { rowKey(it) }
                    })
                if (newRowIndex >= 0) {
                    verticalGridView?.post {
                        if (verticalGridView == null) return@post
                        setSelectedPosition(newRowIndex)
                        val rowAdapter = (rowsAdapter.get(newRowIndex) as? ListRow)
                            ?.adapter as? ArrayObjectAdapter
                        val itemKeys = (0 until (rowAdapter?.size() ?: 0))
                            .map { FocusRestore.keyOf(rowAdapter!!.get(it)!!) }
                        val newItemIndex = FocusRestore.positionAfterReload(
                            focus.itemKey, focus.itemIndex, itemKeys)
                        if (newItemIndex >= 0) {
                            (verticalGridView?.findViewHolderForAdapterPosition(newRowIndex)
                                as? ListRowPresenter.ViewHolder)?.gridView
                                ?.setSelectedPosition(newItemIndex)
                        }
                    }
                }
            }

            // Check for updates (once per session). Outside the finally so
            // the 401→Setup path skips it, and guarded against the fragment
            // detaching while the check is in flight.
            if (!app.updateCheckedThisSession) {
                app.updateCheckedThisSession = true
                try {
                    val ctx = context ?: return@launch
                    val update = com.sinema.util.UpdateChecker.checkForUpdate(ctx)
                    if (update != null && isAdded && activity?.isFinishing == false) {
                        com.sinema.util.UpdateChecker.promptUpdate(requireActivity(), update)
                    }
                } catch (e: Exception) {
                    Log.e("Sinema", "Update check failed", e)
                }
            }
        }
    }

    companion object {
        private const val HERO_ROW_ID = 1L
        private const val CONTINUE_ROW_ID = 2L
        private const val PLAYED_ROW_ID = 3L
        private const val RECENT_ROW_ID = 4L
        private const val FAVORITES_ROW_ID = 5L
        private const val SHORTCUTS_ROW_ID = 6L
        private const val SETTINGS_ROW_ID = 7L
    }
}

/** Result of one independently-failing dashboard row load. */
internal class RowResult<out T>(val value: T? = null, val error: Exception? = null)

/**
 * Runs a dashboard row query, converting any failure (except cancellation)
 * into a [RowResult.error] so one bad Stash response cannot cancel sibling
 * loads — structured concurrency would otherwise fail the whole scope on
 * the first child exception. Internal for unit tests.
 */
internal suspend fun <T> loadRow(block: suspend () -> T): RowResult<T> = try {
    RowResult(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    RowResult(error = e)
}

/** The four home-screen row queries, fetched concurrently. */
private class DashboardLoad(
    val continuePlaying: RowResult<List<Pair<Scene, Double>>>,
    val recentlyPlayed: RowResult<List<Scene>>,
    val recentlyAdded: RowResult<List<Scene>>,
    val favorites: RowResult<List<Scene>>
)
