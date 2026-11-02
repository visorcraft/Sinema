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
import com.sinema.model.GroupItem
import com.sinema.model.HomeContentRow
import com.sinema.model.HomeLayoutSettings
import com.sinema.model.SavedSceneFilter
import com.sinema.model.Scene
import com.sinema.util.SceneIntents
import com.sinema.util.TonightMix
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private var waitingForPin = false
    private var lastInteractionMs = 0L
    private var sessionRestoreOffered = false
    private var sessionRestoreDialog: android.app.AlertDialog? = null
    private val ambientHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ambientCheck = object : Runnable {
        override fun run() {
            val idle = android.os.SystemClock.elapsedRealtime() - lastInteractionMs
            // Auto-lock: re-ask for the PIN after the configured idle time.
            // No window-focus gate — an expired lock must fire even under an
            // open dialog, and time spent in other activities counts as idle
            // because lastInteractionMs only tracks home interactions.
            maybeAutoLock(idle)
            if (app.prefs.ambientGallery &&
                !app.prefs.discreetMode &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                // Dialogs (About, sort menus…) steal window focus; never
                // launch the gallery over an open dialog.
                hasWindowFocus() &&
                (!app.prefs.hasPinSet() || app.pinVerifiedThisSession) &&
                idle >= AMBIENT_IDLE_MS
            ) {
                startActivity(Intent(this@MainActivity, AmbientActivity::class.java))
                // Reset so exiting the gallery buys a fresh idle window.
                lastInteractionMs = android.os.SystemClock.elapsedRealtime()
            }
            ambientHandler.postDelayed(this, AMBIENT_CHECK_MS)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionMs = android.os.SystemClock.elapsedRealtime()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Wipe saved fragment state to prevent zombie fragment restoration
        savedInstanceState?.remove("android:support_fragments")
        sessionRestoreOffered = savedInstanceState?.getBoolean(STATE_RESTORE_OFFERED) ?: false
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
        startAmbientWatch()
        maybeOfferPlaybackSessionRestore()
    }

    override fun onResume() {
        super.onResume()
        // A successful PIN return must not immediately re-lock off the stale
        // pre-PIN idle timestamp — only other activities count as idle time.
        val returningFromPinSuccess = waitingForPin && app.pinVerifiedThisSession
        if (!returningFromPinSuccess) {
            maybeAutoLock(android.os.SystemClock.elapsedRealtime() - lastInteractionMs)
        }
        lastInteractionMs = android.os.SystemClock.elapsedRealtime()
        // A decoy session must never fall through to the real library.
        if (app.decoySession && !isFinishing) {
            startActivity(Intent(this, DecoyActivity::class.java))
            return
        }
        if (waitingForPin && app.pinVerifiedThisSession) {
            waitingForPin = false
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, MainFragment())
                .commitNowAllowingStateLoss()
            startAmbientWatch()
            maybeOfferPlaybackSessionRestore()
        } else if (waitingForPin && !app.pinVerifiedThisSession) {
            // PIN was cancelled/failed — PinActivity calls finishAffinity on back
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_RESTORE_OFFERED, sessionRestoreOffered)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        sessionRestoreDialog?.dismiss()
        ambientHandler.removeCallbacks(ambientCheck)
        super.onDestroy()
    }

    private fun maybeOfferPlaybackSessionRestore() {
        if (sessionRestoreOffered || intent?.data != null || app.decoySession || isFinishing) return
        sessionRestoreOffered = true
        val session = com.sinema.util.PlaybackSessionCodec.decode(app.prefs.playbackSessionJson) ?: return
        if (!com.sinema.util.PlaybackSessionRestore.isEligible(
                session,
                activeProfileId = app.prefs.activeProfileId,
                hasPin = app.prefs.hasPinSet(),
                pinVerified = app.pinVerifiedThisSession,
                incognito = app.prefs.incognitoMode
            )
        ) return
        val message = if (app.prefs.discreetMode || session.title.isBlank()) {
            getString(R.string.playback_restore_message_neutral)
        } else {
            getString(R.string.playback_restore_message, session.title)
        }
        sessionRestoreDialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.playback_restore_title)
            .setMessage(message)
            .setPositiveButton(R.string.playback_restore_action) { _, _ ->
                if (session.queueItems.isNotEmpty()) {
                    com.sinema.util.PlaybackQueue.restore(
                        com.sinema.util.PlaybackQueue.Snapshot(session.queueItems, session.queueIndex)
                    )
                } else {
                    com.sinema.util.PlaybackQueue.clear()
                }
                startActivity(SceneIntents.playbackRestored(this, session))
            }
            .setNegativeButton(R.string.playback_restore_not_now, null)
            .setOnDismissListener { sessionRestoreDialog = null }
            .show()
    }

    private fun startAmbientWatch() {
        lastInteractionMs = android.os.SystemClock.elapsedRealtime()
        ambientHandler.removeCallbacks(ambientCheck)
        ambientHandler.postDelayed(ambientCheck, AMBIENT_CHECK_MS)
    }

    private fun maybeAutoLock(idleMs: Long) {
        val lockMs = app.prefs.autoLockMinutes * 60_000L
        if (!app.prefs.hasPinSet() || !app.pinVerifiedThisSession || app.decoySession) return
        if (lockMs <= 0 || idleMs < lockMs) return
        app.pinVerifiedThisSession = false
        waitingForPin = true
        sessionRestoreDialog?.dismiss()
        startActivity(Intent(this, PinActivity::class.java))
        lastInteractionMs = android.os.SystemClock.elapsedRealtime()
    }

    companion object {
        private const val AMBIENT_IDLE_MS = 5 * 60_000L
        private const val AMBIENT_CHECK_MS = 30_000L
        private const val STATE_RESTORE_OFFERED = "restore_offered"
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
    private var unwatchedFavoritesState = HomeRowState<Scene>(UNWATCHED_ROW_ID, "Unwatched Favorites")
    private var recentlyReleasedState = HomeRowState<Scene>(RELEASED_ROW_ID, "Recently Released")
    private var savedFilterState = HomeRowState<Scene>(SAVED_FILTER_ROW_ID, "Saved Filter")
    private var groupsState = HomeRowState<GroupItem>(GROUPS_ROW_ID, "Groups")
    private var discoveryState = HomeRowState<Scene>(DISCOVERY_ROW_ID, "Because You Watched")
    private var unwatchedPerformersState = HomeRowState<Scene>(UNWATCHED_PERFORMERS_ROW_ID, "Unwatched from Favorite Performers")
    private var discoverySource: Scene? = null
    private var pinnedFilter: SavedSceneFilter? = null
    private lateinit var layoutSnapshot: HomeLayoutSettings
    private val contentRows = mutableMapOf<Long, ListRow>()
    private var heroRow: ListRow? = null
    private var serverErrorRow: ListRow? = null
    private var serverErrorActive = false
    private lateinit var shortcutsRow: ListRow
    private lateinit var settingsRow: ListRow

    private var logoView: android.widget.ImageView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        layoutSnapshot = app.prefs.homeLayout
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
                is GroupItem -> startActivity(GroupScenesActivity.intent(requireContext(), item))
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
                is HomeViewAll -> openViewAll(item)
                is String -> {
                    when (item) {
                        "Settings" -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                        "Search" -> startActivity(Intent(requireContext(), SearchActivity::class.java))
                        "Surprise Me" -> playRandomScene()
                        "Favorites" -> startActivity(Intent(requireContext(), FavoritesActivity::class.java))
                        "Library" -> startActivity(Intent(requireContext(), LibraryActivity::class.java))
                        getString(com.sinema.R.string.shortcut_tonight_mix) -> playTonightMix()
                        "Browse Folders" -> startActivity(Intent(requireContext(), BrowseFoldersActivity::class.java))
                        "Groups" -> startActivity(GroupGridActivity.intent(requireContext()))
                        "Galleries" -> startActivity(GalleryGridActivity.intent(requireContext()))
                        "Tags" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.TAG))
                        "Performers" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.PERFORMER))
                        "Studios" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.STUDIO))
                        "Saved Filters" -> startActivity(Intent(requireContext(), SavedFiltersActivity::class.java))
                        "Markers" -> startActivity(Intent(requireContext(), MarkerBrowserActivity::class.java))
                        "Stats" -> startActivity(Intent(requireContext(), StatsActivity::class.java))
                        "Log Out" -> {
                            app.pinVerifiedThisSession = false
                            requireActivity().finishAffinity()
                        }
                        "Retry Connection" -> {
                            loadContent(force = true)
                        }
                        "Refresh" -> {
                            loadContent(force = true)
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

    /** Shortcut row contents: personalization order + fixed session cards. */
    private fun addShortcutItems(shortcutsAdapter: ArrayObjectAdapter) {
        layoutSnapshot.visibleShortcuts().map { it.label }.forEach(shortcutsAdapter::add)
        shortcutsAdapter.add(getString(com.sinema.R.string.shortcut_tonight_mix))
        if (app.prefs.hasPinSet()) shortcutsAdapter.add("Log Out")
    }

    private fun createStaticRows() {
        val shortcutsAdapter = ArrayObjectAdapter(SettingsPresenter())
        addShortcutItems(shortcutsAdapter)
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
        // Discreet toggle while away: rebind visible cards without a reload
        // and re-evaluate hero visibility even when the reload TTL would
        // skip loadContent().
        val rebinded = discreetRebind.onResume { rebindAllCards() }
        if (rebinded) reconcileHomeRows()
        if (layoutSnapshot != app.prefs.homeLayout) {
            applyHomeLayout()
            return
        }
        loadContent()
    }

    private val discreetRebind = DiscreetRebind()

    private fun rebindAllCards() {
        (heroRow?.adapter as? ArrayObjectAdapter)?.rebindCardsRecursively()
        contentRows.values.forEach { row ->
            (row.adapter as? ArrayObjectAdapter)?.rebindCardsRecursively()
        }
    }

    /**
     * Swap to a changed home layout in place: rebuild shortcut contents, drop
     * content rows (their presenters may encode the old card size), then
     * reload. Avoids the full activity recreation flash.
     */
    private fun applyHomeLayout() {
        layoutSnapshot = app.prefs.homeLayout
        val shortcutsAdapter = shortcutsRow.adapter as ArrayObjectAdapter
        shortcutsAdapter.clear()
        addShortcutItems(shortcutsAdapter)
        contentRows.values.forEach(rowsAdapter::remove)
        contentRows.clear()
        heroRow?.let(rowsAdapter::remove)
        heroRow = null
        if (isLoading) {
            // A load is mid-flight against the old layout; loadContent()
            // refuses re-entry, so recreate for a clean slate.
            requireActivity().recreate()
            return
        }
        reconcileHomeRows()
        loadContent(force = true)
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
    private var lastLoadElapsedMs = 0L
    private var tonightMixJob: Job? = null

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

    private fun rowId(row: HomeContentRow): Long = when (row) {
        HomeContentRow.CONTINUE_PLAYING -> CONTINUE_ROW_ID
        HomeContentRow.RECENTLY_PLAYED -> PLAYED_ROW_ID
        HomeContentRow.RECENTLY_ADDED -> RECENT_ROW_ID
        HomeContentRow.FAVORITES -> FAVORITES_ROW_ID
        HomeContentRow.UNWATCHED_FAVORITES -> UNWATCHED_ROW_ID
        HomeContentRow.RECENTLY_RELEASED -> RELEASED_ROW_ID
        HomeContentRow.SAVED_FILTER -> SAVED_FILTER_ROW_ID
        HomeContentRow.GROUPS -> GROUPS_ROW_ID
        HomeContentRow.DISCOVER -> DISCOVERY_ROW_ID
        HomeContentRow.UNWATCHED_PERFORMERS -> UNWATCHED_PERFORMERS_ROW_ID
    }

    /** One button → a seeded ~30-minute queue of unwatched favorites. */
    private fun playTonightMix() {
        // Double-select would stack players and swap the shared queue mid-fetch.
        if (tonightMixJob?.isActive == true) return
        tonightMixJob = lifecycleScope.launch {
            try {
                var candidates = app.api.findFavoriteScenesPaged(
                    page = 1, perPage = 100, sort = "random", direction = "DESC", unwatchedOnly = true
                ).second
                if (candidates.isEmpty()) {
                    candidates = app.api.findRecentScenesPaged(page = 1, perPage = 100)
                        .second.filter { !it.isWatched }
                }
                if (candidates.isEmpty()) {
                    if (isAdded) Toast.makeText(requireContext(), "No unwatched scenes found", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val mix = TonightMix.select(candidates)
                if (mix.isEmpty()) {
                    if (isAdded) Toast.makeText(requireContext(), "Nothing to play", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                SceneIntents.playAll(requireContext(), mix)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Tonight's Mix: ${TonightMix.summary(mix)}", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Tonight's Mix failed", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openViewAll(item: HomeViewAll) {        when (item.row) {
            HomeContentRow.SAVED_FILTER -> pinnedFilter?.let {
                startActivity(SavedFilterScenesActivity.intent(requireContext(), it))
            } ?: Toast.makeText(requireContext(), "Saved filter unavailable", Toast.LENGTH_LONG).show()
            HomeContentRow.GROUPS -> startActivity(GroupGridActivity.intent(requireContext()))
            HomeContentRow.DISCOVER -> discoverySource?.let {
                startActivity(RelatedScenesActivity.intent(requireContext(), it))
            } ?: Toast.makeText(requireContext(), "Recommendations unavailable", Toast.LENGTH_LONG).show()
            else -> startActivity(HomeRowScenesActivity.intent(requireContext(), item.row))
        }
    }

    private fun reconcileContentRow(
        rowType: HomeContentRow,
        state: HomeRowState<out Any>,
        items: List<Any>?
    ) {
        val existing = contentRows[state.id]
        if (!state.visible || !layoutSnapshot.isRowVisible(rowType)) {
            existing?.let(rowsAdapter::remove)
            contentRows.remove(state.id)
            return
        }
        val row = existing ?: ListRow(
            HeaderItem(state.id, state.displayTitle),
            ArrayObjectAdapter(CardPresenter(app.api, layoutSnapshot.cardSize))
        ).also { contentRows[state.id] = it }
        if (row.headerItem?.name != state.displayTitle) {
            row.headerItem = HeaderItem(state.id, state.displayTitle)
            val index = indexOfRow(row)
            if (index >= 0) rowsAdapter.notifyItemRangeChanged(index, 1)
        }
        if (items != null) updateItems(row, items + HomeViewAll(rowType, state.title))
    }

    private fun reconcileHomeRows() {
        // The hero shows large explicit artwork — hidden entirely in discreet
        // mode (unfocused cards hide theirs; the hero is always "unfocused").
        val heroVisible = layoutSnapshot.showHero &&
            layoutSnapshot.isRowVisible(HomeContentRow.CONTINUE_PLAYING) &&
            !app.prefs.discreetMode
        // Persistent retry affordance while the dashboard has no content at
        // all; disappears as soon as any row renders data.
        if (serverErrorActive) {
            if (serverErrorRow == null) {
                serverErrorRow = ListRow(
                    HeaderItem(ERROR_ROW_ID, ""),
                    ArrayObjectAdapter(SettingsPresenter()).apply { add("Retry Connection") }
                )
            }
        } else {
            serverErrorRow?.let(rowsAdapter::remove)
            serverErrorRow = null
        }
        val heroItem = if (heroVisible) {
            continueState.items.orEmpty().firstOrNull()?.let { (scene, resumeSeconds) ->
                HeroItem(scene, (resumeSeconds * 1000).toLong())
            }
        } else null
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

        reconcileContentRow(HomeContentRow.CONTINUE_PLAYING, continueState, continueState.items?.map { it.first })
        reconcileContentRow(HomeContentRow.RECENTLY_PLAYED, recentlyPlayedState, recentlyPlayedState.items)
        reconcileContentRow(HomeContentRow.RECENTLY_ADDED, recentlyAddedState, recentlyAddedState.items)
        reconcileContentRow(HomeContentRow.FAVORITES, favoritesState, favoritesState.items?.take(20))
        reconcileContentRow(HomeContentRow.UNWATCHED_FAVORITES, unwatchedFavoritesState, unwatchedFavoritesState.items)
        reconcileContentRow(HomeContentRow.RECENTLY_RELEASED, recentlyReleasedState, recentlyReleasedState.items)
        reconcileContentRow(HomeContentRow.SAVED_FILTER, savedFilterState, savedFilterState.items)
        reconcileContentRow(HomeContentRow.GROUPS, groupsState, groupsState.items)
        reconcileContentRow(HomeContentRow.DISCOVER, discoveryState, discoveryState.items)
        reconcileContentRow(HomeContentRow.UNWATCHED_PERFORMERS, unwatchedPerformersState, unwatchedPerformersState.items)

        val orderedRows = buildList {
            serverErrorRow?.let(::add)
            heroRow?.let(::add)
            add(shortcutsRow)
            layoutSnapshot.orderedRows().forEach { rowType ->
                contentRows[rowId(rowType)]?.let(::add)
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

    private fun anyContentLoaded(): Boolean = listOf(
        continueState, recentlyPlayedState, recentlyAddedState, favoritesState,
        unwatchedFavoritesState, recentlyReleasedState, savedFilterState,
        groupsState, discoveryState, unwatchedPerformersState
    ).any { !it.items.isNullOrEmpty() }

    private fun loadContent(force: Boolean = false) {
        if (isLoading) return
        // Quick round-trips (settings, detail screens) within the TTL keep
        // the current rows; explicit Refresh and layout changes force.
        if (!shouldReloadHome(lastLoadElapsedMs, android.os.SystemClock.elapsedRealtime(), force)) return
        isLoading = true
        val live = captureFocus()
        val focus = if (live.rowIndex >= 0) live else pausedFocus
        fun enabled(row: HomeContentRow) = layoutSnapshot.isRowVisible(row)
        if (enabled(HomeContentRow.CONTINUE_PLAYING)) continueState = continueState.refreshing()
        if (enabled(HomeContentRow.RECENTLY_PLAYED)) recentlyPlayedState = recentlyPlayedState.refreshing()
        if (enabled(HomeContentRow.RECENTLY_ADDED)) recentlyAddedState = recentlyAddedState.refreshing()
        if (enabled(HomeContentRow.FAVORITES)) favoritesState = favoritesState.refreshing()
        if (enabled(HomeContentRow.UNWATCHED_FAVORITES)) unwatchedFavoritesState = unwatchedFavoritesState.refreshing()
        if (enabled(HomeContentRow.RECENTLY_RELEASED)) recentlyReleasedState = recentlyReleasedState.refreshing()
        if (enabled(HomeContentRow.SAVED_FILTER)) {
            savedFilterState = savedFilterState.copy(
                title = layoutSnapshot.savedFilterName.ifBlank { "Saved Filter" }
            ).refreshing()
            pinnedFilter = null
        }
        if (enabled(HomeContentRow.GROUPS)) groupsState = groupsState.refreshing()
        if (enabled(HomeContentRow.DISCOVER)) {
            discoveryState = discoveryState.copy(title = "Because You Watched").refreshing()
            discoverySource = null
        }
        if (enabled(HomeContentRow.UNWATCHED_PERFORMERS)) {
            unwatchedPerformersState = unwatchedPerformersState.refreshing()
        }
        reconcileHomeRows()
        lifecycleScope.launch {
            try {
                Log.d("Sinema", "loadContent starting")
                var firstError: Exception? = null

                fun noteError(label: String, e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("Sinema", "loadContent $label failed", e)
                    app.noteError("$label: ${e.message ?: "unknown"}")
                    if (firstError == null) firstError = e
                }

                val rows = coroutineScope {
                    val continueJob = async {
                        if (shouldLoadHomeRow(
                                enabled(HomeContentRow.CONTINUE_PLAYING), app.prefs.channelsEnabled, feedsLauncher = true
                            )
                        ) loadRow { app.api.findContinuePlaying() }
                        else RowResult<List<Pair<Scene, Double>>>(emptyList())
                    }
                    val playedJob = async {
                        if (enabled(HomeContentRow.RECENTLY_PLAYED)) loadRow { app.api.findRecentlyPlayed(25) }
                        else RowResult<List<Scene>>(emptyList())
                    }
                    val recentJob = async {
                        if (shouldLoadHomeRow(
                                enabled(HomeContentRow.RECENTLY_ADDED), app.prefs.channelsEnabled, feedsLauncher = true
                            )
                        ) loadRow { app.api.findRecentScenes(25) }
                        else RowResult<List<Scene>>(emptyList())
                    }
                    val favJob = async {
                        if (enabled(HomeContentRow.FAVORITES)) loadRow { app.api.findFavoriteScenes() }
                        else RowResult<List<Scene>>(emptyList())
                    }
                    val unwatchedJob = async {
                        if (enabled(HomeContentRow.UNWATCHED_FAVORITES)) loadRow {
                            app.api.findFavoriteScenesPaged(
                                page = 1, perPage = 25, sort = "updated_at", direction = "DESC", unwatchedOnly = true
                            ).second
                        } else RowResult<List<Scene>>(emptyList())
                    }
                    val releasedJob = async {
                        if (enabled(HomeContentRow.RECENTLY_RELEASED)) loadRow {
                            app.api.findRecentlyReleasedPaged(page = 1, perPage = 25).second
                        } else RowResult<List<Scene>>(emptyList())
                    }
                    val savedJob = async {
                        if (enabled(HomeContentRow.SAVED_FILTER)) loadRow {
                            val id = layoutSnapshot.savedFilterId
                            if (id.isBlank()) throw java.io.IOException("Choose a saved filter in Home Settings")
                            val filter = app.api.findSavedSceneFilter(id)
                                ?: throw java.io.IOException("Saved filter no longer exists")
                            pinnedFilter = filter
                            savedFilterState = savedFilterState.copy(title = filter.name)
                            app.api.findScenesForSavedFilter(filter, page = 1, perPage = 25).second
                        } else RowResult<List<Scene>>(emptyList())
                    }
                    val groupsJob = async {
                        if (enabled(HomeContentRow.GROUPS)) loadRow {
                            app.api.findGroups(page = 1, perPage = 25).second
                        } else RowResult<List<GroupItem>>(emptyList())
                    }
                    val discoveryJob = async {
                        if (enabled(HomeContentRow.DISCOVER)) loadRow {
                            val recent = app.api.findRecentlyPlayed(1).firstOrNull()
                            val full = recent?.let { app.api.findSceneFull(it.id)?.scene }
                            if (full == null) {
                                emptyList()
                            } else {
                                discoverySource = full
                                discoveryState = discoveryState.copy(
                                    title = "Because You Watched · ${full.displayTitle}"
                                )
                                app.api.findRelatedScenes(full).take(25)
                            }
                        } else RowResult<List<Scene>>(emptyList())
                    }
                    val unwatchedPerformersJob = async {
                        if (enabled(HomeContentRow.UNWATCHED_PERFORMERS)) loadRow {
                            app.api.findUnwatchedForFavoritePerformersPaged(page = 1, perPage = 25).second
                        } else RowResult<List<Scene>>(emptyList())
                    }
                    DashboardLoad(
                        continueJob.await(), playedJob.await(), recentJob.await(), favJob.await(),
                        unwatchedJob.await(), releasedJob.await(), savedJob.await(), groupsJob.await(),
                        discoveryJob.await(), unwatchedPerformersJob.await()
                    )
                }

                continueState = applyResult(continueState, rows.continuePlaying)
                recentlyPlayedState = applyResult(recentlyPlayedState, rows.recentlyPlayed)
                recentlyAddedState = applyResult(recentlyAddedState, rows.recentlyAdded)
                favoritesState = applyResult(favoritesState, rows.favorites)
                unwatchedFavoritesState = applyResult(unwatchedFavoritesState, rows.unwatchedFavorites)
                recentlyReleasedState = applyResult(recentlyReleasedState, rows.recentlyReleased)
                savedFilterState = applyResult(savedFilterState, rows.savedFilter)
                groupsState = applyResult(groupsState, rows.groups)
                discoveryState = applyResult(discoveryState, rows.discovery)
                unwatchedPerformersState = applyResult(unwatchedPerformersState, rows.unwatchedPerformers)
                rows.continuePlaying.error?.let { noteError("Continue Playing", it) }
                rows.recentlyPlayed.error?.let { noteError("Recently Played", it) }
                rows.recentlyAdded.error?.let { noteError("Recently Added", it) }
                rows.favorites.error?.let { noteError("Favorites", it) }
                rows.unwatchedFavorites.error?.let { noteError("Unwatched Favorites", it) }
                rows.recentlyReleased.error?.let { noteError("Recently Released", it) }
                rows.savedFilter.error?.let { noteError("Saved Filter", it) }
                rows.groups.error?.let { noteError("Groups", it) }
                rows.discovery.error?.let { noteError("Because You Watched", it) }
                rows.unwatchedPerformers.error?.let { noteError("Unwatched from Favorite Performers", it) }
                serverErrorActive = shouldShowServerErrorRow(firstError != null, anyContentLoaded())
                reconcileHomeRows()

                val continuePairs = rows.continuePlaying.value.orEmpty()
                val recentScenes = rows.recentlyAdded.value.orEmpty()
                Log.d("Sinema", "Rows loaded: ${rowsAdapter.size()}")

                // Sync TV channels (fire-and-forget on appScope). Capture the
                // application context now, while the fragment is attached:
                // appScope outlives this fragment, so calling requireContext()
                // inside the coroutine would throw if the user navigated away
                // before it ran. context may already be null after a long load.
                // A failed row must never sync: both syncs delete existing
                // entries first, so an error-derived empty list would wipe
                // Watch Next / Recently Added on a single network blip.
                val appCtx = context?.applicationContext
                if (appCtx != null) {
                    app.appScope.launch {
                        if (shouldSyncTvChannelRow(rows.continuePlaying)) {
                            com.sinema.util.TvChannels.syncWatchNext(appCtx, continuePairs)
                        }
                        if (shouldSyncTvChannelRow(rows.recentlyAdded)) {
                            com.sinema.util.TvChannels.syncRecentlyAdded(appCtx, recentScenes)
                        }
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
                if (unwatchedFavoritesState.loading) unwatchedFavoritesState = unwatchedFavoritesState.failed(e)
                if (recentlyReleasedState.loading) recentlyReleasedState = recentlyReleasedState.failed(e)
                if (savedFilterState.loading) savedFilterState = savedFilterState.failed(e)
                if (groupsState.loading) groupsState = groupsState.failed(e)
                if (discoveryState.loading) discoveryState = discoveryState.failed(e)
                if (unwatchedPerformersState.loading) unwatchedPerformersState = unwatchedPerformersState.failed(e)
                serverErrorActive = shouldShowServerErrorRow(hadError = true, anyContentLoaded())
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
                // Stamp even failures: a dead server should not be re-hit on
                // every resume either.
                lastLoadElapsedMs = android.os.SystemClock.elapsedRealtime()
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
        private const val UNWATCHED_ROW_ID = 8L
        private const val RELEASED_ROW_ID = 9L
        private const val SAVED_FILTER_ROW_ID = 10L
        private const val GROUPS_ROW_ID = 11L
        private const val DISCOVERY_ROW_ID = 12L
        private const val UNWATCHED_PERFORMERS_ROW_ID = 13L
        // Must stay unique vs. content rows above: rowKey() matches focus
        // restoration by header id.
        private const val ERROR_ROW_ID = 14L
    }
}

internal fun shouldLoadHomeRow(rowVisible: Boolean, channelsEnabled: Boolean, feedsLauncher: Boolean): Boolean =
    rowVisible || (channelsEnabled && feedsLauncher)

/** Reload the home dashboard at most this often on resume (Refresh forces). */
internal const val HOME_RELOAD_TTL_MS = 15_000L

/**
 * The persistent server-error row appears only when a load attempt failed
 * AND no row has content to show — a partial failure keeps existing rows.
 */
internal fun shouldShowServerErrorRow(hadError: Boolean, anyContentLoaded: Boolean): Boolean =
    hadError && !anyContentLoaded

/**
 * Gate for the onResume dashboard reload: skip when the last load is fresher
 * than [HOME_RELOAD_TTL_MS]. Uses elapsedRealtime deltas so wall-clock
 * changes cannot affect it. `lastLoadElapsedMs <= 0` means "never loaded".
 */
internal fun shouldReloadHome(lastLoadElapsedMs: Long, nowElapsedMs: Long, force: Boolean = false): Boolean =
    force || lastLoadElapsedMs <= 0L || nowElapsedMs - lastLoadElapsedMs >= HOME_RELOAD_TTL_MS

/**
 * TV channel sync must only run on a successfully loaded row: both syncs
 * delete their launcher entries before republishing, so an error-derived
 * empty list would wipe channels on a transient failure.
 */
internal fun shouldSyncTvChannelRow(result: RowResult<*>): Boolean =
    result.error == null && result.value != null

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

/** The enabled home-screen row queries, fetched concurrently. */
private class DashboardLoad(
    val continuePlaying: RowResult<List<Pair<Scene, Double>>>,
    val recentlyPlayed: RowResult<List<Scene>>,
    val recentlyAdded: RowResult<List<Scene>>,
    val favorites: RowResult<List<Scene>>,
    val unwatchedFavorites: RowResult<List<Scene>>,
    val recentlyReleased: RowResult<List<Scene>>,
    val savedFilter: RowResult<List<Scene>>,
    val groups: RowResult<List<GroupItem>>,
    val discovery: RowResult<List<Scene>>,
    val unwatchedPerformers: RowResult<List<Scene>>
)
