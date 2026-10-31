package com.sinema.ui

import android.view.KeyEvent
import android.view.View
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.sinema.R

interface SortableScreen {
    fun showSortDialog()
    fun showGridMenu() = showSortDialog()
    fun showFilterDialog() {}
    fun showSearchDialog() {}
    val canFilter: Boolean get() = false
    val canSearch: Boolean get() = false
    val filterLabel: String get() = "All scenes"
}

fun FragmentActivity.handleSortMenuKey(keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_MENU) return false
    (supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen)?.showGridMenu()
    return true
}

fun FragmentActivity.bindGridControls(screen: SortableScreen) {
    findViewById<Button>(R.id.btn_grid_sort)?.setOnClickListener { screen.showSortDialog() }
    findViewById<Button>(R.id.btn_grid_filter)?.apply {
        visibility = if (screen.canFilter) View.VISIBLE else View.GONE
        text = "Filter: ${screen.filterLabel}"
        setOnClickListener { screen.showFilterDialog() }
    }
    findViewById<Button>(R.id.btn_grid_search)?.apply {
        visibility = if (screen.canSearch) View.VISIBLE else View.GONE
        setOnClickListener { screen.showSearchDialog() }
    }
}

fun FragmentActivity.refreshGridControls() {
    val screen = supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen ?: return
    findViewById<Button>(R.id.btn_grid_filter)?.text = "Filter: ${screen.filterLabel}"
}
