package com.sinema.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import com.sinema.model.GroupItem
import com.sinema.model.Scene
import com.sinema.model.SceneFilterOptions
import com.sinema.model.SortOption
import com.sinema.model.cycled
import com.sinema.model.effectiveFavorites
import com.sinema.model.toggledQuickFavorites
import com.sinema.util.SceneIntents
import com.sinema.util.RecentSearches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = SinemaSearchFragment()
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

class SinemaSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider, SortableScreen {
    private val app get() = SinemaApp.instance
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var searchJob: Job? = null

    private var sort: SortOption = SortOption.PATH_ASC
    private val randomSeed: Int = (0..99_999_999).random()
    private var currentQuery: String = ""

    override val canFilter = true
    override val filterLabel get() = filterController.value.summary

    // Quick watch/favorites toggles, shared semantics with SceneGridFragment.
    override val canQuickFilter = true
    override val quickWatchLabel: String get() = filterController.watchLabel
    override val quickFavoritesLabel: String get() = filterController.favoritesLabel

    private val filterController = SceneFilterController(
        prefs = app.prefs,
        screenKey = "search",
        initial = app.prefs.sceneFilterFor("search"),
        refreshControls = { activity?.refreshGridControls() },
        reload = { }
    )
    private val sceneFilter get() = filterController.value

    private fun runSearch() {
        if (currentQuery.length >= 2) search(currentQuery)
    }

    override fun toggleQuickWatch() {
        filterController.toggleWatch()
        runSearch()
    }

    override fun toggleQuickFavorites() {
        filterController.toggleFavorites()
        runSearch()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognizer()
        } else {
            context?.let {
                Toast.makeText(it, "Voice search needs microphone permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val speechResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { setSearchQuery(it, true) }
        }
    }

    override fun showSortDialog() {
        SortDialog.show(requireContext(), sort) { chosen ->
            sort = chosen
            app.prefs.setSortFor("search", chosen.name)
            Toast.makeText(requireContext(), "Sorted by ${chosen.label}", Toast.LENGTH_SHORT).show()
            if (currentQuery.length >= 2) search(currentQuery)
        }
    }

    override fun showFilterDialog() {
        FilterDialog.show(
            requireContext(),
            filterController.value,
            onChosen = { chosen ->
                filterController.apply(chosen)
                runSearch()
            },
            onSave = { chosen ->
                SavedFilterActions.promptSave(
                    requireActivity(), chosen, sort.apiSort(randomSeed), sort.direction
                )
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val density = resources.displayMetrics.density
        val hPad = (36 * density).toInt()
        val vPad = (20 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        activity?.refreshGridControls()
        val overlay = activity?.findViewById<View>(R.id.grid_state_overlay)
        val searchBar = view.findViewById<androidx.leanback.widget.SearchBar>(
            androidx.leanback.R.id.lb_search_bar
        )
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val params = overlay?.layoutParams as? android.widget.FrameLayout.LayoutParams
            if (params != null) {
                val barLocation = IntArray(2)
                val parentLocation = IntArray(2)
                searchBar.getLocationInWindow(barLocation)
                (overlay.parent as View).getLocationInWindow(parentLocation)
                val topMargin = (barLocation[1] + searchBar.height - parentLocation[1]).coerceAtLeast(0)
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    overlay.layoutParams = params
                }
            }
        }
    }

    private val discreetRebind = DiscreetRebind()

    override fun onResume() {
        super.onResume()
        discreetRebind.onResume { rowsAdapter.rebindCardsRecursively() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sort = SortOption.fromName(app.prefs.sortFor("search"))
        val lrp = ListRowPresenter(TvMotion.focusZoom(), false)
        rowsAdapter = ArrayObjectAdapter(lrp)
        setSearchResultProvider(this)
        setSpeechRecognitionCallback {
            val ctx = context ?: return@setSpeechRecognitionCallback
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeechRecognizer()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        // A fresh SearchSupportFragment auto-starts speech recognition ~300ms
        // after the result provider registers (mStartRecognitionRunnable while
        // mAutoStartRecognition is true), which would pop the mic permission
        // dialog just for opening Search. Setting any query clears the flag
        // and removes the pending callback. When launched from system search
        // (GlobalSearchActivity) the forwarded query is submitted instead.
        val prefill = if (savedInstanceState == null)
            requireActivity().intent?.getStringExtra(GlobalSearchActivity.EXTRA_PREFILL_QUERY)
        else null
        if (!prefill.isNullOrBlank()) {
            setSearchQuery(prefill, true)
        } else {
            setSearchQuery("", false)
        }
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Scene -> startActivity(SceneIntents.detail(requireContext(), item))
                is EntityItem -> startActivity(EntityScenesActivity.intent(requireContext(), item))
                is GroupItem -> startActivity(GroupScenesActivity.intent(requireContext(), item))
                is String -> when (item) {
                    RecentSearches.CLEAR_ACTION -> {
                        app.prefs.clearRecentSearches()
                        renderRecentSearches()
                    }
                    else -> setSearchQuery(item, true)
                }
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    private fun startSpeechRecognizer() {
        try {
            speechResultLauncher.launch(getRecognizerIntent())
        } catch (e: android.content.ActivityNotFoundException) {
            context?.let {
                Toast.makeText(it, "Voice recognition is not available on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onQueryTextChange(newQuery: String): Boolean {
        search(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        search(query)
        return true
    }

    private fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()
        if (query.length < 2) {
            rowsAdapter.clear()
            renderRecentSearches()
            return
        }
        activity?.renderGridLoadState(GridLoadState.loading(rowsAdapter.size() > 0)) {
            search(currentQuery)
        }
        searchJob = lifecycleScope.launch {
            delay(300)
            try {
                val results = coroutineScope {
                    val scenes = async {
                        app.api.searchScenes(
                            query,
                            sort = sort.apiSort(randomSeed),
                            direction = sort.direction,
                            filters = sceneFilter
                        )
                    }
                    val tags = async {
                        app.api.findEntities(EntityItem.Kind.TAG, perPage = 20, searchTerm = query, sort = "name", direction = "ASC")
                    }
                    val performers = async {
                        app.api.findEntities(EntityItem.Kind.PERFORMER, perPage = 20, searchTerm = query, sort = "name", direction = "ASC")
                    }
                    val studios = async {
                        app.api.findEntities(EntityItem.Kind.STUDIO, perPage = 20, searchTerm = query, sort = "name", direction = "ASC")
                    }
                    val groups = async {
                        app.api.findGroups(perPage = 20, searchTerm = query, sort = "name", direction = "ASC")
                    }
                    SearchResults(scenes.await(), tags.await(), performers.await(), studios.await(), groups.await())
                }
                // Only queries that produced a result render become recents,
                // and never while incognito.
                if (!app.prefs.incognitoMode) app.prefs.recordRecentSearch(query)
                renderResults(results)
                activity?.renderGridLoadState(
                    GridLoadState.loaded(rowsAdapter.size(), "No results for “$query”")
                ) { search(currentQuery) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Search failed for query $query", e)
                val message = UiErrorMessage.forException(e)
                if (rowsAdapter.size() > 0) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                } else {
                    activity?.renderGridLoadState(GridLoadState.failed(false, message)) {
                        search(currentQuery)
                    }
                }
            }
        }
    }

    private fun renderResults(results: SearchResults) {
        rowsAdapter.clear()
        val (sceneCount, scenes) = results.scenes
        scenes.chunked(3).forEachIndexed { index, chunk ->
            val listAdapter = ArrayObjectAdapter(CardPresenter(app.api))
            chunk.forEach { listAdapter.add(it) }
            val header = if (index == 0) HeaderItem("Scenes ($sceneCount)") else HeaderItem("")
            rowsAdapter.add(ListRow(header, listAdapter))
        }
        addCardRow("Tags", results.tags)
        addCardRow("Performers", results.performers)
        addCardRow("Studios", results.studios)
        addCardRow("Groups", results.groups)
    }

    /** Empty-query home: re-runnable recent searches plus a clear action. */
    private fun renderRecentSearches() {
        rowsAdapter.clear()
        val recents = app.prefs.recentSearches
        if (recents.isNotEmpty()) {
            val listAdapter = ArrayObjectAdapter(RecentSearchPresenter())
            recents.forEach { listAdapter.add(it) }
            listAdapter.add(RecentSearches.CLEAR_ACTION)
            rowsAdapter.add(ListRow(HeaderItem("Recent searches"), listAdapter))
        }
        activity?.renderGridLoadState(
            GridLoadState.loaded(rowsAdapter.size(), "Search scenes, tags, performers, studios, and Groups")
        ) { search(currentQuery) }
    }

    private fun addCardRow(label: String, result: Pair<Int, List<Any>>) {
        val (count, items) = result
        if (items.isEmpty()) return
        val listAdapter = ArrayObjectAdapter(CardPresenter(app.api))
        items.forEach { listAdapter.add(it) }
        rowsAdapter.add(ListRow(HeaderItem("$label ($count)"), listAdapter))
    }

    private data class SearchResults(
        val scenes: Pair<Int, List<Scene>>,
        val tags: Pair<Int, List<EntityItem>>,
        val performers: Pair<Int, List<EntityItem>>,
        val studios: Pair<Int, List<EntityItem>>,
        val groups: Pair<Int, List<GroupItem>>
    )
}

/** Card for one recent search query (or the clear-all action). */
class RecentSearchPresenter : androidx.leanback.widget.Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(com.sinema.R.layout.view_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val query = item as? String ?: return
        val view = viewHolder.view
        val image = view.findViewById<android.widget.ImageView>(com.sinema.R.id.card_image)
        view.findViewById<android.widget.TextView>(com.sinema.R.id.card_title).text =
            if (query == RecentSearches.CLEAR_ACTION) "Clear" else "🔍 $query"
        view.findViewById<android.widget.TextView>(com.sinema.R.id.card_content).text =
            if (query == RecentSearches.CLEAR_ACTION) "Remove all recent searches" else "Run this search again"
        view.findViewById<android.view.View>(com.sinema.R.id.card_heart).visibility = android.view.View.GONE
        view.findViewById<android.view.View>(com.sinema.R.id.card_checkmark).visibility = android.view.View.GONE
        view.findViewById<android.view.View>(com.sinema.R.id.card_progress).visibility = android.view.View.GONE
        image.setImageResource(
            if (query == RecentSearches.CLEAR_ACTION) android.R.drawable.ic_menu_close_clear_cancel
            else android.R.drawable.ic_menu_search
        )
        image.scaleType = android.widget.ImageView.ScaleType.CENTER
        image.setBackgroundColor(0xFF444444.toInt())
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
