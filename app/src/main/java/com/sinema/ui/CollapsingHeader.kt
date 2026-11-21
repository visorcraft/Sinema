package com.sinema.ui

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.sinema.SinemaApp

/**
 * TV "collapsing header": views pinned above a scrolling grid collapse when
 * the user scrolls down (freeing the whole viewport for content rows) and
 * restore once the grid returns to the top or an explicit expand is triggered.
 * Respects the Reduce Motion preference by snapping without translation/fade.
 */
object CollapsingHeader {
    private const val LISTENER_KEY = Int.MAX_VALUE
    private const val EXPAND_ACTION_KEY = Int.MAX_VALUE - 1
    private const val ANIM_DURATION_MS = 200L

    internal fun nextCollapsedState(
        currentlyCollapsed: Boolean,
        dy: Int,
        canScrollUp: Boolean
    ): Boolean = when {
        dy > 0 -> true
        dy < 0 && !canScrollUp -> false
        else -> currentlyCollapsed
    }

    /**
     * @param header the tall header to collapse/expand (e.g. entity_header).
     * @param pinned small views that collapse together with the header
     * (pass nothing to keep a control bar always visible).
     */
    fun bind(grid: RecyclerView, header: View, vararg pinned: View) {
        val collapsing = listOf(header, *pinned)
        var collapsed = false

        fun apply(collapse: Boolean, force: Boolean = false) {
            if (collapse == collapsed && !force) return
            collapsed = collapse
            val reduceMotion = runCatching { SinemaApp.instance.prefs.reduceMotion }.getOrDefault(false)
            collapsing.forEach { view ->
                view.animate().cancel()
                if (reduceMotion) {
                    view.translationY = 0f
                    view.alpha = if (collapse) 0f else 1f
                    view.visibility = if (collapse) View.GONE else View.VISIBLE
                } else if (collapse) {
                    val shift = if (view.height > 0) -view.height.toFloat() else 0f
                    view.animate()
                        .translationY(shift)
                        .alpha(0f)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            if (collapsed) {
                                view.visibility = View.GONE
                                view.translationY = 0f
                            }
                        }
                        .start()
                } else {
                    view.visibility = View.VISIBLE
                    view.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }

        grid.setTag(EXPAND_ACTION_KEY, Runnable { apply(collapse = false, force = true) })

        // Tag-guard so rebinding the same grid doesn't stack listeners.
        val key = "CollapsingHeader".hashCode()
        if (grid.getTag(LISTENER_KEY) != key) {
            grid.setTag(LISTENER_KEY, key)
            grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val target = nextCollapsedState(
                        currentlyCollapsed = collapsed,
                        dy = dy,
                        canScrollUp = recyclerView.canScrollVertically(-1)
                    )
                    apply(collapse = target)
                }
            })
        }
    }

    /** Restores a collapsed header (for example, after Back to Top). */
    fun expand(grid: RecyclerView) {
        (grid.getTag(EXPAND_ACTION_KEY) as? Runnable)?.run()
    }
}
