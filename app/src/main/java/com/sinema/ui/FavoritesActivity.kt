package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.SortOption

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

    // MENU-key opens the sort picker; on-device fallback (remotes lacking MENU) is deferred.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            (supportFragmentManager.findFragmentById(R.id.main_frame) as? SceneGridFragment)
                ?.showSortDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

class FavoritesGridFragment : SceneGridFragment() {
    override val gridTitle = "Favorites"
    override val emptyMessage = "No favorites yet"
    override val sortScreenKey = "favorites"
    override val defaultSort: SortOption = SortOption.UPDATED_DESC
    override suspend fun loadItems(): List<Any> =
        app.api.findFavoriteScenes(sort = sort.apiSort(randomSeed), direction = sort.direction)
}
