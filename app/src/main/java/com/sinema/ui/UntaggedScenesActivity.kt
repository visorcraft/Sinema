package com.sinema.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.sinema.R

/**
 * Curation backlog: scenes with no tags and no performers. Long-press a card
 * to assign tags; the scene leaves the grid once it is no longer an orphan.
 */
class UntaggedScenesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = UntaggedScenesFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, fragment)
                .commit()
            bindGridControls(fragment)
        } else {
            (supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen)
                ?.let { bindGridControls(it) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)
}

class UntaggedScenesFragment : SceneGridFragment() {
    override val gridTitle = "Untagged Scenes"
    override val emptyMessage = "No untagged scenes — library is curated 🎉"
    override val paged = true
    override val sortScreenKey get() = "untagged"
    override val canAssignTags = true

    override suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findUntaggedScenes(
            page = page,
            perPage = perPage,
            sort = sort.apiSort(randomSeed),
            direction = sort.direction
        )
}
