package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.HomeContentRow
import com.sinema.model.SortOption

class HomeRowScenesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        if (savedInstanceState == null) {
            val fragment = HomeRowScenesFragment.create(
                intent.getStringExtra(EXTRA_ROW) ?: HomeContentRow.RECENTLY_ADDED.name
            )
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

    companion object {
        const val EXTRA_ROW = "home_row"

        fun intent(context: Context, row: HomeContentRow): Intent {
            require(row != HomeContentRow.SAVED_FILTER) { "Saved filters use SavedFilterScenesActivity" }
            return Intent(context, HomeRowScenesActivity::class.java).putExtra(EXTRA_ROW, row.name)
        }
    }
}

class HomeRowScenesFragment : SceneGridFragment() {
    private val row: HomeContentRow by lazy {
        HomeContentRow.valueOf(requireArguments().getString(HomeRowScenesActivity.EXTRA_ROW) ?: HomeContentRow.RECENTLY_ADDED.name)
    }

    override val gridTitle get() = row.label
    override val emptyMessage get() = "No scenes in ${row.label}"
    override val paged = true
    override val sortScreenKey get() = "home_row_${row.name}"
    override val defaultSort get() = row.defaultSort

    override suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        val sortField = sort.apiSort(randomSeed)
        val direction = sort.direction
        val result = when (row) {
            HomeContentRow.CONTINUE_PLAYING -> app.api.findContinuePlayingPaged(
                page, perPage, sortField, direction, sceneFilter
            )
            HomeContentRow.RECENTLY_PLAYED -> app.api.findRecentlyPlayedPaged(
                page, perPage, sortField, direction, sceneFilter
            )
            HomeContentRow.RECENTLY_ADDED -> app.api.findRecentScenesPaged(
                page, perPage, sortField, direction, sceneFilter
            )
            HomeContentRow.FAVORITES -> app.api.findFavoriteScenesPaged(
                page, perPage, sortField, direction, unwatchedOnly = false, filters = sceneFilter
            )
            HomeContentRow.UNWATCHED_FAVORITES -> app.api.findFavoriteScenesPaged(
                page, perPage, sortField, direction, unwatchedOnly = true, filters = sceneFilter
            )
            HomeContentRow.RECENTLY_RELEASED -> app.api.findRecentlyReleasedPaged(
                page, perPage, sortField, direction, sceneFilter
            )
            HomeContentRow.SAVED_FILTER -> throw IllegalArgumentException("Saved filters use SavedFilterScenesActivity")
        }
        return result.first to result.second
    }

    companion object {
        fun create(rowName: String) = HomeRowScenesFragment().apply {
            arguments = Bundle().apply { putString(HomeRowScenesActivity.EXTRA_ROW, rowName) }
        }
    }
}
