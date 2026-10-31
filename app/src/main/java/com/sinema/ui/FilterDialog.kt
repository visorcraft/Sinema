package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.RadioGroup
import com.sinema.R
import com.sinema.model.SceneFilterOptions
import com.sinema.model.WatchFilter

object FilterDialog {
    fun show(context: Context, current: SceneFilterOptions, onChosen: (SceneFilterOptions) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_scene_filter, null)
        val watchGroup = view.findViewById<RadioGroup>(R.id.filter_watch_group)
        val favorites = view.findViewById<CheckBox>(R.id.filter_favorites)
        watchGroup.check(
            when (current.watch) {
                WatchFilter.ALL -> R.id.filter_watch_all
                WatchFilter.WATCHED -> R.id.filter_watch_watched
                WatchFilter.UNWATCHED -> R.id.filter_watch_unwatched
            }
        )
        favorites.isChecked = current.favoritesOnly

        AlertDialog.Builder(context)
            .setTitle("Filter scenes")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val watch = when (watchGroup.checkedRadioButtonId) {
                    R.id.filter_watch_watched -> WatchFilter.WATCHED
                    R.id.filter_watch_unwatched -> WatchFilter.UNWATCHED
                    else -> WatchFilter.ALL
                }
                onChosen(SceneFilterOptions(watch, favorites.isChecked))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
