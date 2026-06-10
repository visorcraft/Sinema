package com.sinema.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Shared vertical-grid screen: overscan padding, card presenter, click-to-detail,
 * and error handling. Subclasses provide a title and an item loader.
 */
abstract class SceneGridFragment : VerticalGridSupportFragment() {
    protected val app get() = SinemaApp.instance
    protected lateinit var gridAdapter: ArrayObjectAdapter

    protected open val columns = 3
    protected abstract val gridTitle: String
    protected abstract val emptyMessage: String
    protected abstract suspend fun loadItems(): List<Any>

    /** Subclasses with non-Scene items override to handle their own clicks. */
    protected open fun onItemClicked(item: Any) {
        if (item is Scene) startActivity(SceneIntents.detail(requireContext(), item))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = gridTitle
        badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = columns
        setGridPresenter(gridPresenter)
        gridAdapter = ArrayObjectAdapter(CardPresenter(app.api))
        adapter = gridAdapter
        setOnItemViewClickedListener { _, item, _, _ -> item?.let { onItemClicked(it) } }
        reload()
    }

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

    protected fun reload() {
        lifecycleScope.launch {
            try {
                val items = loadItems()
                gridAdapter.clear()
                items.forEach { gridAdapter.add(it) }
                if (items.isEmpty()) {
                    Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e // back-press cancels the scope; not a real error
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
}
