package com.sinema.ui

import com.sinema.R
import com.sinema.model.SortOption

class FavoritesActivity : GridActivity() {
    override fun createGridFragment(): SceneGridFragment =
        FavoritesGridFragment()
}

class FavoritesGridFragment : SceneGridFragment() {
    override val gridTitle = "Favorites"
    override val emptyMessage = "No favorites yet"
    override val sortScreenKey = "favorites"
    override val defaultSort: SortOption = SortOption.UPDATED_DESC
    private val resumeReloadGate = GridResumeReloadGate()

    override fun onResume() {
        super.onResume()
        if (resumeReloadGate.onResume()) reload()
    }

    override suspend fun loadItems(): List<Any> =
        app.api.findFavoriteScenes(sort = sort.apiSort(randomSeed), direction = sort.direction)
}
