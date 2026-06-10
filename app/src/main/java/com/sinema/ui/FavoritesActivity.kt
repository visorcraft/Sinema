package com.sinema.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.sinema.R

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
}

class FavoritesGridFragment : SceneGridFragment() {
    override val gridTitle = "Favorites"
    override val emptyMessage = "No favorites yet"
    override suspend fun loadItems(): List<Any> = app.api.findFavoriteScenes()
}
