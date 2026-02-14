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
import com.sinema.model.Scene
import kotlinx.coroutines.launch

class FavoritesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, FavoritesGridFragment())
                .commit()
        }
    }
}

class FavoritesGridFragment : VerticalGridSupportFragment() {
    private val app get() = SinemaApp.instance
    private lateinit var gridAdapter: ArrayObjectAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val vPad = (27 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        view.viewTreeObserver.addOnGlobalLayoutListener {
            findGridView(view as? ViewGroup)?.let { grid ->
                if (grid.paddingLeft != hPad) {
                    grid.setPadding(hPad, vPad, hPad, vPad)
                    grid.clipToPadding = false
                }
            }
        }
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
        title = "Favorites"
        badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = 3
        setGridPresenter(gridPresenter)

        gridAdapter = ArrayObjectAdapter(CardPresenter(app.api))
        adapter = gridAdapter

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

        loadFavorites()
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            try {
                val scenes = app.api.findFavoriteScenes()
                gridAdapter.clear()
                scenes.forEach { gridAdapter.add(it) }
                if (scenes.isEmpty()) {
                    Toast.makeText(requireContext(), "No favorites yet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
