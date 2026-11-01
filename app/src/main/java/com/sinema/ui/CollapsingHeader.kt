package com.sinema.ui

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * TV "collapsing header": views pinned above a scrolling grid collapse when
 * the user scrolls down (freeing the whole viewport for content rows) and
 * restore once the grid returns to the top. Fixes frozen-top layouts on
 * short TV viewports.
 */
object CollapsingHeader {
    private var listenerKey = Int.MAX_VALUE

    /**
     * @param header the tall header to collapse/expand (e.g. entity_header).
     * @param pinned small views that collapse together with the header
     * (pass nothing to keep a control bar always visible).
     */
    fun bind(grid: RecyclerView, header: View, vararg pinned: View) {
        val collapsing = listOf(header, *pinned)
        var collapsed = false
        fun apply(collapse: Boolean) {
            if (collapse == collapsed) return
            collapsed = collapse
            collapsing.forEach { view ->
                if (collapse) {
                    view.animate()
                        .translationY(-view.height.toFloat())
                        .alpha(0f)
                        .setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { if (collapsed) view.visibility = View.GONE }
                        .start()
                } else {
                    view.visibility = View.VISIBLE
                    view.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }
        // Tag-guard so rebinding the same grid doesn't stack listeners.
        val key = "CollapsingHeader".hashCode()
        if (grid.getTag(listenerKey) != key) {
            grid.setTag(listenerKey, key)
            grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        apply(collapse = true)
                    } else if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                        apply(collapse = false)
                    }
                }
            })
        }
    }
}
