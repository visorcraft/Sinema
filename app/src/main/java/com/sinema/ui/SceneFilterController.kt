package com.sinema.ui

import com.sinema.model.SceneFilterOptions
import com.sinema.util.Prefs
import com.sinema.model.cycled
import com.sinema.model.effectiveFavorites
import com.sinema.model.toggledQuickFavorites

/**
 * Shared scene-filter state for the three screens that implement
 * SortableScreen directly (grids, folder browse, search): quick
 * watch/favorites toggles, label rendering, persistence, and reload.
 */
class SceneFilterController(
    private val prefs: Prefs,
    private val screenKey: String,
    initial: SceneFilterOptions,
    private val refreshControls: () -> Unit = {},
    private val reload: () -> Unit
) {
    var value: SceneFilterOptions = initial
        private set

    val filterLabel: String get() = value.summary
    val watchLabel: String get() = value.watch.label
    val favoritesLabel: String
        get() = if (value.effectiveFavorites()) "♥ Favorites ✓" else "♡ Favorites"

    fun toggleWatch() {
        value = value.copy(watch = value.watch.cycled())
        persist()
        reload()
    }

    fun toggleFavorites() {
        value = value.toggledQuickFavorites()
        persist()
        reload()
    }

    fun apply(chosen: SceneFilterOptions) {
        value = chosen
        persist()
        refreshControls()
        reload()
    }

    private fun persist() {
        prefs.setSceneFilterFor(screenKey, value)
    }
}
