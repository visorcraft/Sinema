package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.FolderItem
import com.sinema.util.FolderHelper
import kotlinx.coroutines.launch

class FolderBrowseActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val path = intent.getStringExtra("path") ?: "/data"
        if (savedInstanceState == null) {
            val fragment = FolderGridFragment().apply {
                arguments = Bundle().apply { putString("path", path) }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, fragment)
                .commit()
        }
    }
}

class FolderGridFragment : VerticalGridSupportFragment() {
    private val app get() = SinemaApp.instance
    private lateinit var gridAdapter: ArrayObjectAdapter
    private var currentPath = "/data"

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
            if (child is ViewGroup) {
                findGridView(child)?.let { return it }
            }
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPath = arguments?.getString("path") ?: "/data"
        title = currentPath
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

        loadFolder()
    }

    private fun loadFolder() {
        lifecycleScope.launch {
            try {
                val (_, scenes) = app.api.findScenesByPath(currentPath, 1, 1000)
                val items = FolderHelper.buildFolderContents(scenes, currentPath)
                gridAdapter.clear()
                items.forEach { gridAdapter.add(it) }
                if (items.isEmpty()) {
                    Toast.makeText(requireContext(), "Empty folder", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
