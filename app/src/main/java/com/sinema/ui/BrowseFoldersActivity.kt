package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.FolderItem
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class BrowseFoldersActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, BrowseFoldersGridFragment())
                .commit()
        }
    }
}

class BrowseFoldersGridFragment : VerticalGridSupportFragment() {
    private val app get() = SinemaApp.instance
    private lateinit var gridAdapter: ArrayObjectAdapter
    private val discreetRebind = DiscreetRebind()
    private val enrichGate = Semaphore(4)
    private val enrichedPaths = mutableSetOf<String>()

    override fun onResume() {
        super.onResume()
        discreetRebind.onResume { gridAdapter.rebindCardsRecursively() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (24 * density).toInt()
        val vPad = (20 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Browse Folders"
        badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false)
        gridPresenter.numberOfColumns = 4
        setGridPresenter(gridPresenter)

        gridAdapter = ArrayObjectAdapter(FolderCardPresenter())
        adapter = gridAdapter

        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is FolderItem) scheduleFolderEnrichment(item)
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is FolderItem -> {
                    if (item.isFolder) {
                        val intent = Intent(requireContext(), FolderBrowseActivity::class.java)
                        intent.putExtra("path", item.fullPath)
                        startActivity(intent)
                    } else if (item.image != null) {
                        val imageItems = (0 until gridAdapter.size())
                            .mapNotNull { (gridAdapter.get(it) as? FolderItem)?.image }
                        val index = imageItems.indexOfFirst { it.id == item.image.id }
                        startActivity(ImageViewActivity.intentFor(requireContext(), imageItems, index))
                    } else if (item.scene != null) {
                        startActivity(SceneIntents.detail(requireContext(), item.scene))
                    }
                }
            }
        }

        loadRootFolders()
    }

    private fun loadRootFolders() {
        lifecycleScope.launch {
            try {
                val (_, folders) = app.api.findTopLevelFoldersPaged(1, 500)
                gridAdapter.clear()

                // Show every folder immediately (name only) so the list is
                // usable in ~1s. Counts, thumbnails, and favorite state are
                // filled in lazily below — fetching them all up front fans
                // out thousands of queries and the old awaitAll() barrier
                // left the grid blank until every one finished.
                folders.forEach { (name, fullPath) ->
                    gridAdapter.add(FolderItem(name = name, fullPath = fullPath, isFolder = true))
                }
                title = "Browse Folders (${folders.size} folders)"
                badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)
                if (gridAdapter.size() > 0) scheduleFolderEnrichment(gridAdapter.get(0) as FolderItem)

                // Loose files and pictures directly in /data, appended after
                // the folders (videos first, then pictures).
                val (_, looseFiles) = app.api.findScenesInFolderDirect("/data", 1, 500)
                looseFiles.forEach { scene ->
                    gridAdapter.add(FolderItem(
                        name = scene.filename,
                        fullPath = scene.path,
                        isFolder = false,
                        childCount = 0,
                        scene = scene,
                        firstSceneId = scene.id
                    ))
                }
                val (_, looseImages) = app.api.findImagesInFolderDirect("/data", 1, 500)
                looseImages.forEach { img ->
                    gridAdapter.add(FolderItem(
                        name = img.filename,
                        fullPath = img.path,
                        isFolder = false,
                        childCount = 0,
                        image = img
                    ))
                }

                val total = folders.size + looseFiles.size + looseImages.size
                title = "Browse Folders ($total items)"
                if (total == 0) {
                    Toast.makeText(requireContext(), "No folders found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e // back-press cancels the scope; not a real error
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Counts and thumbnails for the folders around the focused card, not the whole tree. */
    private fun scheduleFolderEnrichment(focused: FolderItem) {
        val center = (0 until gridAdapter.size()).firstOrNull { index ->
            (gridAdapter.get(index) as? FolderItem)?.fullPath == focused.fullPath
        } ?: return
        val from = (center - 2).coerceAtLeast(0)
        val to = (center + ENRICH_WINDOW).coerceAtMost(gridAdapter.size())
        for (index in from until to) {
            val item = gridAdapter.get(index) as? FolderItem ?: continue
            if (!item.isFolder || !enrichedPaths.add(item.fullPath)) continue
            val path = item.fullPath
            lifecycleScope.launch {
                enrichGate.withPermit {
                    try {
                        val summary = app.api.summarizeFolder(path)
                        val slot = (0 until gridAdapter.size()).firstOrNull { candidate ->
                            (gridAdapter.get(candidate) as? FolderItem)?.fullPath == path
                        } ?: return@withPermit
                        val current = gridAdapter.get(slot) as? FolderItem ?: return@withPermit
                        gridAdapter.replace(slot, current.copy(
                            childCount = summary.sceneCount + summary.imageCount,
                            firstSceneId = summary.firstSceneId,
                            firstImageId = summary.firstImageId,
                            hasFavorites = summary.hasFavorites
                        ))
                    } catch (e: CancellationException) {
                        enrichedPaths.remove(path)
                        throw e
                    } catch (_: Exception) {
                        // Keep the name-only card. A later visit can try again.
                        enrichedPaths.remove(path)
                    }
                }
            }
        }
    }

    companion object {
        private const val ENRICH_WINDOW = 18
    }
}
