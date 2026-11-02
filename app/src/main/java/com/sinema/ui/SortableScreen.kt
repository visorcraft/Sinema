package com.sinema.ui

import android.view.KeyEvent
import android.view.View
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.WatchFilter

interface SortableScreen {
    fun showSortDialog()
    fun showGridMenu() = showSortDialog()
    fun showFilterDialog() {}
    fun showSearchDialog() {}
    val canFilter: Boolean get() = false
    val canSearch: Boolean get() = false
    val filterLabel: String get() = "All scenes"

    /** Quick watch/favorites toggles on the grid control bar. */
    val canQuickFilter: Boolean get() = false
    val quickWatchLabel: String get() = WatchFilter.ALL.label
    val quickFavoritesLabel: String get() = "♡ Favorites"
    fun toggleQuickWatch() {}
    fun toggleQuickFavorites() {}
    fun pageBy(direction: Int): Boolean = false
    fun scrollToTop(): Boolean = false
}

fun FragmentActivity.handleSortMenuKey(keyCode: Int): Boolean {
    val screen = supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen
    return when (keyCode) {
        KeyEvent.KEYCODE_MENU -> {
            screen?.showGridMenu()
            true
        }
        KeyEvent.KEYCODE_PAGE_UP -> screen?.pageBy(-1) == true
        KeyEvent.KEYCODE_PAGE_DOWN -> screen?.pageBy(1) == true
        else -> false
    }
}

fun FragmentActivity.bindGridControls(screen: SortableScreen) {
    findViewById<Button>(R.id.btn_grid_sort)?.setOnClickListener { screen.showSortDialog() }
    findViewById<Button>(R.id.btn_grid_filter)?.apply {
        visibility = if (screen.canFilter) View.VISIBLE else View.GONE
        text = "Filter: ${screen.filterLabel}"
        setOnClickListener { screen.showFilterDialog() }
    }
    findViewById<Button>(R.id.btn_grid_watch)?.apply {
        visibility = if (screen.canQuickFilter) View.VISIBLE else View.GONE
        text = screen.quickWatchLabel
        setOnClickListener {
            screen.toggleQuickWatch()
            refreshGridControls()
        }
    }
    findViewById<Button>(R.id.btn_grid_fav)?.apply {
        visibility = if (screen.canQuickFilter) View.VISIBLE else View.GONE
        text = screen.quickFavoritesLabel
        setOnClickListener {
            screen.toggleQuickFavorites()
            refreshGridControls()
        }
    }
    findViewById<Button>(R.id.btn_grid_search)?.apply {
        visibility = if (screen.canSearch) View.VISIBLE else View.GONE
        setOnClickListener { screen.showSearchDialog() }
    }
}

fun FragmentActivity.refreshGridControls() {
    val screen = supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen ?: return
    findViewById<Button>(R.id.btn_grid_filter)?.text = "Filter: ${screen.filterLabel}"
    findViewById<Button>(R.id.btn_grid_watch)?.text = screen.quickWatchLabel
    findViewById<Button>(R.id.btn_grid_fav)?.text = screen.quickFavoritesLabel
}
