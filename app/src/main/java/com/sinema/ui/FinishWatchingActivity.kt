package com.sinema.ui

import com.sinema.R
import com.sinema.model.Scene

/**
 * "Finish Watching" triage: partially-watched scenes with progress bars.
 * Long-press offers mark-watched / remove-from-continue; bulk actions and
 * Play All work like any scene grid. Near-end scenes are hidden (they count
 * as finished server-side once playback clears the resume point).
 */
class FinishWatchingActivity : GridActivity() {
    override fun createGridFragment(): SceneGridFragment =
        FinishWatchingFragment()
}

class FinishWatchingFragment : SceneGridFragment() {
    override val gridTitle get() = getString(R.string.finish_watching_title)
    override val emptyMessage get() = getString(R.string.finish_watching_empty)
    // Non-paged: near-end filtering breaks GridPageState's empty-batch =
    // end-of-list heuristic, so the fragment scans up to 5 server pages
    // itself (100+ concurrent in-progress scenes is unrealistic).
    override val paged = false
    override val sortScreenKey get() = "finish_watching"

    override suspend fun loadItems(): List<Any> {
        val visible = mutableListOf<Scene>()
        for (serverPage in 1..5) {
            val (_, scenes) = app.api.findPartiallyWatched(
                page = serverPage,
                perPage = pageSize,
                sort = sort.apiSort(randomSeed),
                direction = sort.direction,
                filters = sceneFilter
            )
            // Near-end scenes are effectively finished; hide them from triage.
            visible += scenes.filter { scene ->
                scene.duration <= 0.0 || scene.duration - scene.resumeTime > 30.0
            }
            if (scenes.size < pageSize) break
        }
        return visible
    }
}
