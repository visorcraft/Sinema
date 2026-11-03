package com.sinema.ui

import com.sinema.R

/**
 * Curation backlog: scenes with no tags and no performers. Long-press a card
 * to assign tags; the scene leaves the grid once it is no longer an orphan.
 */
class UntaggedScenesActivity : GridActivity() {
    override fun createGridFragment(): SceneGridFragment =
        UntaggedScenesFragment()
}

class UntaggedScenesFragment : SceneGridFragment() {
    override val gridTitle get() = getString(R.string.untagged_title)
    override val emptyMessage get() = getString(R.string.untagged_empty)
    override val paged = true
    override val sortScreenKey get() = "untagged"
    override val canAssignTags = true

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findUntaggedScenes(
            page = page,
            perPage = perPage,
            sort = sort.apiSort(randomSeed),
            direction = sort.direction
        )
}
