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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

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
                    } else if (item.scene != null) {
                        val scene = item.scene
                        val intent = Intent(requireContext(), SceneDetailActivity::class.java)
                        intent.putExtra("scene_id", scene.id)
                        intent.putExtra("scene_path", scene.path)
                        intent.putExtra("scene_duration", scene.duration)
                        intent.putExtra("scene_size", scene.size)
                        intent.putExtra("scene_width", scene.width)
                        intent.putExtra("scene_height", scene.height)
                        intent.putExtra("scene_play_count", scene.playCount)
                        intent.putExtra("scene_rating100", scene.rating100 ?: -1)
                        intent.putExtra("scene_title", scene.title)
                        startActivity(intent)
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

                // Get scene counts for all folders in parallel
                val countJobs = folders.map { (name, fullPath) ->
                    async {
                        val sceneCount = app.api.getSceneCountForPath(fullPath)
                        val firstSceneId = app.api.getFirstSceneIdForPath(fullPath)
                        val hasFavs = app.api.hasFavoritesInPath(fullPath)
                        FolderItem(
                            name = name,
                            fullPath = fullPath,
                            isFolder = true,
                            childCount = sceneCount,
                            scene = null,
                            firstSceneId = firstSceneId,
                            hasFavorites = hasFavs
                        )
                    }
                }
                val folderItems = countJobs.awaitAll()
                folderItems.forEach { gridAdapter.add(it) }

                // Get loose files directly in /data (not in subfolders)
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

                val totalItems = folderItems.size + looseFiles.size
                title = "Browse Folders ($totalItems items)"
                badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)
                if (gridAdapter.size() == 0) {
                    Toast.makeText(requireContext(), "No folders found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
