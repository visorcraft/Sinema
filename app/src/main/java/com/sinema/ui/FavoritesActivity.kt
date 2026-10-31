package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.SortOption

class FavoritesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = FavoritesGridFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, fragment)
                .commit()
            bindGridControls(fragment)
        } else {
            (supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen)
                ?.let { bindGridControls(it) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)
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
