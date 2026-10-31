package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.RadioGroup
import com.sinema.R
import com.sinema.model.PresenceFilter
import com.sinema.model.ResolutionFilter
import com.sinema.model.SceneFilterOptions
import com.sinema.model.WatchFilter

object FilterDialog {
    fun show(context: Context, current: SceneFilterOptions, onChosen: (SceneFilterOptions) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_scene_filter, null)
        val watchGroup = view.findViewById<RadioGroup>(R.id.filter_watch_group)
        val ratingGroup = view.findViewById<RadioGroup>(R.id.filter_rating_group)
        val resolutionGroup = view.findViewById<RadioGroup>(R.id.filter_resolution_group)
        val markersGroup = view.findViewById<RadioGroup>(R.id.filter_markers_group)
        val captionsGroup = view.findViewById<RadioGroup>(R.id.filter_captions_group)
        val favorites = view.findViewById<CheckBox>(R.id.filter_favorites)

        watchGroup.check(
            when (current.watch) {
                WatchFilter.ALL -> R.id.filter_watch_all
                WatchFilter.WATCHED -> R.id.filter_watch_watched
                WatchFilter.UNWATCHED -> R.id.filter_watch_unwatched
            }
        )
        favorites.isChecked = current.favoritesOnly
        ratingGroup.check(ratingId(current.minStars))
        resolutionGroup.check(
            when (current.resolution) {
                ResolutionFilter.ANY -> R.id.filter_resolution_any
                ResolutionFilter.UNDER_HD -> R.id.filter_resolution_under_hd
                ResolutionFilter.HD_PLUS -> R.id.filter_resolution_hd
                ResolutionFilter.FULL_HD_PLUS -> R.id.filter_resolution_full_hd
                ResolutionFilter.FOUR_K_PLUS -> R.id.filter_resolution_four_k
            }
        )
        markersGroup.check(presenceId(R.id.filter_markers_any, R.id.filter_markers_with, R.id.filter_markers_without, current.markers))
        captionsGroup.check(presenceId(R.id.filter_captions_any, R.id.filter_captions_with, R.id.filter_captions_without, current.captions))

        AlertDialog.Builder(context)
            .setTitle("Filter scenes")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val watch = when (watchGroup.checkedRadioButtonId) {
                    R.id.filter_watch_watched -> WatchFilter.WATCHED
                    R.id.filter_watch_unwatched -> WatchFilter.UNWATCHED
                    else -> WatchFilter.ALL
                }
                val minStars = starsFromId(ratingGroup.checkedRadioButtonId)
                val resolution = when (resolutionGroup.checkedRadioButtonId) {
                    R.id.filter_resolution_under_hd -> ResolutionFilter.UNDER_HD
                    R.id.filter_resolution_hd -> ResolutionFilter.HD_PLUS
                    R.id.filter_resolution_full_hd -> ResolutionFilter.FULL_HD_PLUS
                    R.id.filter_resolution_four_k -> ResolutionFilter.FOUR_K_PLUS
                    else -> ResolutionFilter.ANY
                }
                val markers = presenceFromId(
                    markersGroup.checkedRadioButtonId,
                    R.id.filter_markers_with, R.id.filter_markers_without
                )
                val captions = presenceFromId(
                    captionsGroup.checkedRadioButtonId,
                    R.id.filter_captions_with, R.id.filter_captions_without
                )
                onChosen(
                    SceneFilterOptions(
                        watch = watch,
                        favoritesOnly = favorites.isChecked,
                        resolution = resolution,
                        markers = markers,
                        captions = captions,
                        minStars = minStars
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ratingId(stars: Int): Int = when (stars) {
        1 -> R.id.filter_rating_1
        2 -> R.id.filter_rating_2
        3 -> R.id.filter_rating_3
        4 -> R.id.filter_rating_4
        5 -> R.id.filter_rating_5
        else -> R.id.filter_rating_0
    }

    private fun starsFromId(id: Int): Int = when (id) {
        R.id.filter_rating_1 -> 1
        R.id.filter_rating_2 -> 2
        R.id.filter_rating_3 -> 3
        R.id.filter_rating_4 -> 4
        R.id.filter_rating_5 -> 5
        else -> 0
    }

    private fun presenceId(anyId: Int, withId: Int, withoutId: Int, value: PresenceFilter): Int =
        when (value) {
            PresenceFilter.ANY -> anyId
            PresenceFilter.WITH -> withId
            PresenceFilter.WITHOUT -> withoutId
        }

    private fun presenceFromId(id: Int, withId: Int, withoutId: Int): PresenceFilter =
        when (id) {
            withId -> PresenceFilter.WITH
            withoutId -> PresenceFilter.WITHOUT
            else -> PresenceFilter.ANY
        }
}
