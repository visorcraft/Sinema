package com.sinema.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    // Voice search: Leanback shows a mic button in the search bar once a
    // SpeechRecognitionCallback is registered. SearchSupportFragment has no
    // recognizeSpeech() launcher (only the old SearchFragment does), so the
    // callback starts the recognizer intent itself and the result launcher
    // feeds the transcript back into the query field. The system recognizer
    // needs RECORD_AUDIO, requested on demand the first time the mic is used.
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
        // setSearchQuery(Intent, submit) reads the recognizer's
        // EXTRA_RESULTS transcript itself.
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { setSearchQuery(it, true) }
        }
    }

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
        // and removes the pending callback, so recognition only starts when
        // the mic button is actually pressed.
        setSearchQuery("", false)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Scene) startActivity(SceneIntents.detail(requireContext(), item))
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
