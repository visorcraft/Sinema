package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.api.SinemaApi
import com.sinema.model.EntityItem
import com.sinema.model.MarkerSortOption
import com.sinema.model.SceneMarkerItem
import com.sinema.util.GlideAuth
import com.sinema.util.SceneIntents
import kotlinx.coroutines.launch

/**
 * Library-wide scene markers ("chapters") with tag filtering. Selecting a
 * marker jumps straight into playback at its timestamp.
 */
class MarkerBrowserActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = MarkerBrowserFragment()
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

class MarkerBrowserFragment : SceneGridFragment() {
    override val gridTitle = "Scene Markers"
    override val emptyMessage = "No scene markers on this server"
    override val paged = true
    override val canFilter = true

    // canFilter here means the *tag* filter; the quick watch/favorites
    // toggles drive SceneFilterOptions, which marker queries never apply —
    // they would reload the identical list.
    override val canQuickFilter = false

    private var sortOption: MarkerSortOption = MarkerSortOption.CREATED_DESC
    private var tagId: String? = null
    private var tagName: String? = null
    private var searchQuery: String = ""

    override val filterLabel: String
        get() = tagName ?: "All tags"

    override val canSearch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        sortOption = MarkerSortOption.fromName(app.prefs.sortFor("marker_browser"))
        savedInstanceState?.let { state ->
            tagId = state.getString("marker_tag_id")
            tagName = state.getString("marker_tag_name")
            searchQuery = state.getString("marker_search").orEmpty()
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("marker_tag_id", tagId)
        outState.putString("marker_tag_name", tagName)
        outState.putString("marker_search", searchQuery)
    }

    override fun createCardPresenter(): Presenter = MarkerCardPresenter(app.api)

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findSceneMarkers(
            page = page,
            perPage = perPage,
            sort = sortOption.apiSort(randomSeed),
            direction = sortOption.direction,
            tagId = tagId,
            searchQuery = searchQuery
        )

    /** Text search across marker titles (FindFilterType.q). */
    override fun showSearchDialog() {
        Dialogs.showTextSearchDialog(
            requireContext(),
            title = "Search markers",
            initialQuery = searchQuery,
            hint = "Marker or scene title",
            onClear = {
                searchQuery = ""
                reload()
            }
        ) { query ->
            searchQuery = query
            reload()
        }
    }

    /** Markers use their own sort fields, so the shared SortDialog is bypassed. */
    override fun showSortDialog() {
        Dialogs.showChoiceDialog(
            requireContext(), "Sort markers by", MarkerSortOption.entries, sortOption,
            label = { it.label }
        ) { chosen ->
            sortOption = chosen
            app.prefs.setSortFor("marker_browser", chosen.name)
            reload()
        }
    }

    override fun showGridMenu() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Markers")
            .setItems(arrayOf("Sort by…", "Filter by tag…")) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> showSortDialog()
                    1 -> showTagFilterDialog()
                }
            }
            .show()
    }

    override fun showFilterDialog() = showTagFilterDialog()

    private fun showTagFilterDialog() {
        lifecycleScope.launch {
            try {
                val tags = loadAllTags()
                if (!isAdded) return@launch
                val names = listOf("All tags") + tags.map { it.name }
                // Checked row follows the active filter ("All tags" = index 0).
                val checked = tags.indexOfFirst { it.id == tagId }.let { if (it >= 0) it + 1 else 0 }
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Filter by tag")
                    .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                        dialog.dismiss()
                        if (which == 0) {
                            tagId = null
                            tagName = null
                        } else {
                            val tag = tags[which - 1]
                            tagId = tag.id
                            tagName = tag.name
                        }
                        activity?.refreshGridControls()
                        reload()
                    }
                    .show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sinema", "Tag filter load failed", e)
                if (isAdded) {
                    android.widget.Toast.makeText(
                        requireContext(), "Could not load tags: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Pages through every tag so long-tail tags stay selectable; capped as a
     * guard against pathologically huge libraries.
     */
    private suspend fun loadAllTags(): List<EntityItem> = app.api.findAllTags()

    override fun onItemClicked(item: Any) {
        if (item is SceneMarkerItem) {
            startActivity(
                SceneIntents.playback(
                    requireContext(), item.sceneId,
                    resumeMs = (item.seconds * 1000).toLong(),
                    width = item.sceneWidth, height = item.sceneHeight
                )
            )
        }
    }
}

/** Marker card: the scene screenshot at the marker's timestamp. */
class MarkerCardPresenter(private val api: SinemaApi) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is SceneMarkerItem) return
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.card_title).text = item.displayTitle
        view.findViewById<TextView>(R.id.card_content).text =
            "${item.sceneTitle.ifBlank { "Scene" }}  •  ${com.sinema.util.TimeFormat.formatSeconds(item.seconds)}"
        view.findViewById<View>(R.id.card_heart).visibility = View.GONE
        view.findViewById<View>(R.id.card_checkmark).visibility = View.GONE
        view.findViewById<View>(R.id.card_progress).visibility = View.GONE
        val image = view.findViewById<ImageView>(R.id.card_image)
        // Stash serves the scene screenshot at any timestamp — exactly the
        // marker's frame.
        val url = api.getMarkerScreenshotUrl(item.sceneId, item.seconds)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setBackgroundColor(0xFF333333.toInt())
        com.bumptech.glide.Glide.with(view.context)
            .load(GlideAuth.url(api, url))
            .centerCrop()
            .into(image)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.findViewById<ImageView>(R.id.card_image).setImageDrawable(null)
    }
}
