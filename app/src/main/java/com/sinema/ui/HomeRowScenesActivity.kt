package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.sinema.R
import com.sinema.model.HomeContentRow

class HomeRowScenesActivity : GridActivity() {
    override fun createGridFragment(): SceneGridFragment =
        HomeRowScenesFragment.create(
intent.getStringExtra(EXTRA_ROW) ?: HomeContentRow.RECENTLY_ADDED.name
)

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
    override val supportsAlphaJump get() = row != HomeContentRow.UNWATCHED_PERFORMERS

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> {
        val sortField = sort.apiSort(randomSeed)
        val direction = sort.direction
        val prefix = alphaRegex()
        val field = GridNavigation.alphaField(sort)
        val result = when (row) {
            HomeContentRow.CONTINUE_PLAYING -> app.api.findContinuePlayingPaged(
                page, perPage, sortField, direction, sceneFilter, prefix, field
            )
            HomeContentRow.RECENTLY_PLAYED -> app.api.findRecentlyPlayedPaged(
                page, perPage, sortField, direction, sceneFilter, prefix, field
            )
            HomeContentRow.RECENTLY_ADDED -> app.api.findRecentScenesPaged(
                page, perPage, sortField, direction, sceneFilter, prefix, field
            )
            HomeContentRow.FAVORITES -> app.api.findFavoriteScenesPaged(
                page, perPage, sortField, direction, unwatchedOnly = false, filters = sceneFilter,
                pathPrefixRegex = prefix, alphaField = field
            )
            HomeContentRow.UNWATCHED_FAVORITES -> app.api.findFavoriteScenesPaged(
                page, perPage, sortField, direction, unwatchedOnly = true, filters = sceneFilter,
                pathPrefixRegex = prefix, alphaField = field
            )
            HomeContentRow.RECENTLY_RELEASED -> app.api.findRecentlyReleasedPaged(
                page, perPage, sortField, direction, sceneFilter, prefix, field
            )
            HomeContentRow.SAVED_FILTER -> throw IllegalArgumentException("Saved filters use SavedFilterScenesActivity")
            HomeContentRow.GROUPS -> throw IllegalArgumentException("Groups use GroupGridActivity")
            HomeContentRow.DISCOVER -> throw IllegalArgumentException("Discovery uses RelatedScenesActivity")
            HomeContentRow.UNWATCHED_PERFORMERS -> app.api.findUnwatchedForFavoritePerformersPaged(
                page, perPage, sortField, direction, sceneFilter
            )
        }
        return result.first to result.second
    }

    companion object {
        fun create(rowName: String) = HomeRowScenesFragment().apply {
            arguments = Bundle().apply { putString(HomeRowScenesActivity.EXTRA_ROW, rowName) }
        }
    }
}
