package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import com.sinema.model.SortOption
import com.sinema.util.SceneIntents
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, SinemaSearchFragment())
                .commit()
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

    override fun showSortDialog() {
        SortDialog.show(requireContext(), sort) { chosen ->
            sort = chosen
            app.prefs.setSortFor("search", chosen.name)
            Toast.makeText(requireContext(), "Sorted by ${chosen.label}", Toast.LENGTH_SHORT).show()
            // Re-run the current query with the new sort; if blank, just persist the sort.
            if (currentQuery.length >= 2) search(currentQuery)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val density = resources.displayMetrics.density
        val hPad = (36 * density).toInt()
        val vPad = (20 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sort = SortOption.fromName(app.prefs.sortFor("search"))
        val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        lrp.shadowEnabled = false
        rowsAdapter = ArrayObjectAdapter(lrp)
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Scene) startActivity(SceneIntents.detail(requireContext(), item))
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

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
            return
        }
        searchJob = lifecycleScope.launch {
            delay(300)
            try {
                val (count, scenes) = app.api.searchScenes(
                    query,
                    sort = sort.apiSort(randomSeed),
                    direction = sort.direction
                )
                rowsAdapter.clear()
                // Chunk into rows of 3 for a vertical-scroll grid feel
                val chunks = scenes.chunked(3)
                chunks.forEachIndexed { index, chunk ->
                    val listAdapter = ArrayObjectAdapter(CardPresenter(app.api))
                    chunk.forEach { listAdapter.add(it) }
                    val header = if (index == 0) HeaderItem("$count results") else HeaderItem("")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (_: Exception) {}
        }
    }
}
