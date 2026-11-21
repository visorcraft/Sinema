package com.sinema.ui

import android.app.Activity
import android.widget.Button
import com.sinema.R
import com.sinema.model.SceneFilterOptions
import com.sinema.model.WatchFilter
import com.sinema.model.effectiveFavorites

/**
 * Pure visual-state model for grid toolbar pills so active filters, toggles,
 * and search queries display a clear accented state even when unfocused.
 */
internal data class GridControlVisualState(
    val filterActive: Boolean = false,
    val watchActive: Boolean = false,
    val favoritesActive: Boolean = false,
    val searchQuery: String = ""
) {
    val searchActive: Boolean get() = searchQuery.isNotBlank()

    fun searchButtonLabel(): String =
        if (searchActive) "Search: $searchQuery" else "Search"

    companion object {
        fun fromSceneFilter(
            options: SceneFilterOptions,
            searchQuery: String = ""
        ): GridControlVisualState = GridControlVisualState(
            filterActive = options != SceneFilterOptions(),
            watchActive = options.watch != WatchFilter.ALL,
            favoritesActive = options.effectiveFavorites(),
            searchQuery = searchQuery.trim()
        )
    }
}

internal fun Activity.applyGridControlVisuals(state: GridControlVisualState) {
    findViewById<Button>(R.id.btn_grid_filter)?.isActivated = state.filterActive
    findViewById<Button>(R.id.btn_grid_watch)?.isActivated = state.watchActive
    findViewById<Button>(R.id.btn_grid_fav)?.isActivated = state.favoritesActive
    findViewById<Button>(R.id.btn_grid_search)?.apply {
        isActivated = state.searchActive
        text = state.searchButtonLabel()
    }
}
