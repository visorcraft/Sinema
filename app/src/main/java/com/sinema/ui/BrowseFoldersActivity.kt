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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.FolderItem
import com.sinema.util.FolderHelper
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class BrowseFoldersActivity : FragmentActivity() {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (24 * density).toInt()
        val vPad = (20 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
    }

    private fun findGridView(vg: ViewGroup?): androidx.leanback.widget.VerticalGridView? {
        if (vg == null) return null
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)
            if (child is androidx.leanback.widget.VerticalGridView) return child
            if (child is ViewGroup) findGridView(child)?.let { return it }
        }
        return null
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

                // Lazily enrich each folder card, bounded so we don't flood
                // the server. Each card is replaced in place as its data
                // arrives; a per-folder failure just leaves the placeholder.
                val gate = Semaphore(8)
                folders.forEachIndexed { index, (name, fullPath) ->
                    launch {
                        gate.withPermit {
                            try {
                                val sceneCount = app.api.getSceneCountForPath(fullPath)
                                val imageCount = app.api.getImageCountForPath(fullPath)
                                val firstSceneId = app.api.getFirstSceneIdForPath(fullPath)
                                val firstImageId = if (firstSceneId == null)
                                    app.api.getFirstImageIdForPath(fullPath) else null
                                val hasFavs = app.api.hasFavoritesInPath(fullPath)
                                val enriched = FolderItem(
                                    name = name,
                                    fullPath = fullPath,
                                    isFolder = true,
                                    childCount = sceneCount + imageCount,
                                    firstSceneId = firstSceneId,
                                    firstImageId = firstImageId,
                                    hasFavorites = hasFavs
                                )
                                // Replace only if the card is still the same
                                // folder (loose files appended below shift
                                // nothing before them, but guard anyway).
                                if (index < gridAdapter.size() &&
                                    (gridAdapter.get(index) as? FolderItem)?.fullPath == fullPath) {
                                    gridAdapter.replace(index, enriched)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Non-fatal: keep the name-only placeholder.
                            }
                        }
                    }
                }

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
}
