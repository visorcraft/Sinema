package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
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
}

class SinemaSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private val app get() = SinemaApp.instance
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var searchJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val vPad = (27 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        lrp.shadowEnabled = false
        rowsAdapter = ArrayObjectAdapter(lrp)
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Scene) {
                val intent = Intent(requireContext(), SceneDetailActivity::class.java)
                intent.putExtra("scene_id", item.id)
                intent.putExtra("scene_path", item.path)
                intent.putExtra("scene_duration", item.duration)
                intent.putExtra("scene_size", item.size)
                intent.putExtra("scene_width", item.width)
                intent.putExtra("scene_height", item.height)
                intent.putExtra("scene_play_count", item.playCount)
                intent.putExtra("scene_rating100", item.rating100 ?: -1)
                intent.putExtra("scene_title", item.title)
                startActivity(intent)
            }
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
        searchJob?.cancel()
        if (query.length < 2) {
            rowsAdapter.clear()
            return
        }
        searchJob = lifecycleScope.launch {
            delay(300)
            try {
                val (count, scenes) = app.api.searchScenes(query)
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
