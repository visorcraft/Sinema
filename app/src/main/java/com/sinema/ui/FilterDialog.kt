package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.DurationFilter
import com.sinema.model.EntityFilter
import com.sinema.model.EntityItem
import com.sinema.model.PresenceFilter
import com.sinema.model.ReleaseDateFilter
import com.sinema.model.ResolutionFilter
import com.sinema.model.SceneFilterOptions
import com.sinema.model.VideoCodecFilter
import com.sinema.model.WatchFilter

object FilterDialog {
    fun show(
        context: Context,
        current: SceneFilterOptions,
        onChosen: (SceneFilterOptions) -> Unit,
        onSave: ((SceneFilterOptions) -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_scene_filter, null)
        val watchGroup = view.findViewById<RadioGroup>(R.id.filter_watch_group)
        val ratingGroup = view.findViewById<RadioGroup>(R.id.filter_rating_group)
        val resolutionGroup = view.findViewById<RadioGroup>(R.id.filter_resolution_group)
        val markersGroup = view.findViewById<RadioGroup>(R.id.filter_markers_group)
        val captionsGroup = view.findViewById<RadioGroup>(R.id.filter_captions_group)
        val favorites = view.findViewById<CheckBox>(R.id.filter_favorites)
        val chipsScroll = view.findViewById<HorizontalScrollView>(R.id.filter_chips_scroll)
        val chips = view.findViewById<LinearLayout>(R.id.filter_chips)
        val saveButton = view.findViewById<Button>(R.id.filter_save)
        val durationButton = view.findViewById<Button>(R.id.filter_duration)
        val releaseDateButton = view.findViewById<Button>(R.id.filter_release_date)
        val codecButton = view.findViewById<Button>(R.id.filter_codec)
        val performersButton = view.findViewById<Button>(R.id.filter_performers)
        val studiosButton = view.findViewById<Button>(R.id.filter_studios)
        val tagsButton = view.findViewById<Button>(R.id.filter_tags)
        var working = current
        lateinit var renderAll: () -> Unit

        fun renderChips() {
            chips.removeAllViews()
            val specs = mutableListOf<Pair<String, () -> Unit>>()
            if (working.watch != WatchFilter.ALL) specs.add(
                working.watch.label to { working = working.copy(watch = WatchFilter.ALL) }
            )
            if (working.favoritesOnly || working.minStars > 0) {
                val label = if (working.minStars > 0) "★${working.minStars}+" else "Favorites"
                specs.add(label to { working = working.copy(favoritesOnly = false, minStars = 0) })
            }
            if (working.resolution != ResolutionFilter.ANY) specs.add(
                working.resolution.label to { working = working.copy(resolution = ResolutionFilter.ANY) }
            )
            if (working.markers != PresenceFilter.ANY) specs.add(
                "${working.markers.label} markers" to { working = working.copy(markers = PresenceFilter.ANY) }
            )
            if (working.captions != PresenceFilter.ANY) specs.add(
                "${working.captions.label} captions" to { working = working.copy(captions = PresenceFilter.ANY) }
            )
            if (working.duration != DurationFilter.ANY) specs.add(
                working.duration.label to { working = working.copy(duration = DurationFilter.ANY) }
            )
            if (working.releaseDate != ReleaseDateFilter.ANY) specs.add(
                working.releaseDate.label to { working = working.copy(releaseDate = ReleaseDateFilter.ANY) }
            )
            if (working.videoCodec != VideoCodecFilter.ANY) specs.add(
                working.videoCodec.label to { working = working.copy(videoCodec = VideoCodecFilter.ANY) }
            )
            if (working.performers.isActive) specs.add(
                working.performers.summary("Performers") to { working = working.copy(performers = EntityFilter()) }
            )
            if (working.studios.isActive) specs.add(
                working.studios.summary("Studios") to { working = working.copy(studios = EntityFilter()) }
            )
            if (working.tags.isActive) specs.add(
                working.tags.summary("Tags") to { working = working.copy(tags = EntityFilter()) }
            )
            specs.forEachIndexed { index, (label, clear) ->
                val chip = Button(context).apply {
                    text = "$label  ✕"
                    textSize = 13f
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 44)
                    ).apply { marginEnd = dp(context, 8) }
                    setOnClickListener {
                        clear()
                        renderAll()
                        val neighbor = chips.getChildAt(index.coerceAtMost(chips.childCount - 1))
                        (neighbor ?: watchGroup.getChildAt(0))?.requestFocus()
                    }
                }
                chips.addView(chip)
            }
            chipsScroll.visibility = if (specs.isEmpty()) View.GONE else View.VISIBLE
        }

        fun render() {
            watchGroup.check(
                when (working.watch) {
                    WatchFilter.ALL -> R.id.filter_watch_all
                    WatchFilter.WATCHED -> R.id.filter_watch_watched
                    WatchFilter.UNWATCHED -> R.id.filter_watch_unwatched
                }
            )
            favorites.isChecked = working.favoritesOnly
            ratingGroup.check(ratingId(working.minStars))
            resolutionGroup.check(
                when (working.resolution) {
                    ResolutionFilter.ANY -> R.id.filter_resolution_any
                    ResolutionFilter.UNDER_HD -> R.id.filter_resolution_under_hd
                    ResolutionFilter.HD_PLUS -> R.id.filter_resolution_hd
                    ResolutionFilter.FULL_HD_PLUS -> R.id.filter_resolution_full_hd
                    ResolutionFilter.FOUR_K_PLUS -> R.id.filter_resolution_four_k
                }
            )
            markersGroup.check(
                presenceId(R.id.filter_markers_any, R.id.filter_markers_with, R.id.filter_markers_without, working.markers)
            )
            captionsGroup.check(
                presenceId(R.id.filter_captions_any, R.id.filter_captions_with, R.id.filter_captions_without, working.captions)
            )
            durationButton.text = "Duration: ${working.duration.label}"
            releaseDateButton.text = "Release date: ${working.releaseDate.label}"
            codecButton.text = "Video codec: ${working.videoCodec.label}"
            performersButton.text = if (working.performers.isActive)
                working.performers.summary("Performers") else "Performers: Any"
            studiosButton.text = if (working.studios.isActive)
                working.studios.summary("Studios") else "Studios: Any"
            tagsButton.text = if (working.tags.isActive)
                working.tags.summary("Tags") else "Tags: Any"
            renderChips()
        }
        renderAll = { render() }

        watchGroup.setOnCheckedChangeListener { _, id ->
            working = working.copy(
                watch = when (id) {
                    R.id.filter_watch_watched -> WatchFilter.WATCHED
                    R.id.filter_watch_unwatched -> WatchFilter.UNWATCHED
                    else -> WatchFilter.ALL
                }
            )
            renderChips()
        }
        favorites.setOnCheckedChangeListener { _, checked ->
            working = working.copy(favoritesOnly = checked)
            renderChips()
        }
        ratingGroup.setOnCheckedChangeListener { _, id ->
            working = working.copy(minStars = starsFromId(id))
            renderChips()
        }
        resolutionGroup.setOnCheckedChangeListener { _, id ->
            working = working.copy(
                resolution = when (id) {
                    R.id.filter_resolution_under_hd -> ResolutionFilter.UNDER_HD
                    R.id.filter_resolution_hd -> ResolutionFilter.HD_PLUS
                    R.id.filter_resolution_full_hd -> ResolutionFilter.FULL_HD_PLUS
                    R.id.filter_resolution_four_k -> ResolutionFilter.FOUR_K_PLUS
                    else -> ResolutionFilter.ANY
                }
            )
            renderChips()
        }
        markersGroup.setOnCheckedChangeListener { _, id ->
            working = working.copy(
                markers = presenceFromId(id, R.id.filter_markers_with, R.id.filter_markers_without)
            )
            renderChips()
        }
        captionsGroup.setOnCheckedChangeListener { _, id ->
            working = working.copy(
                captions = presenceFromId(id, R.id.filter_captions_with, R.id.filter_captions_without)
            )
            renderChips()
        }

        durationButton.setOnClickListener {
            showChoice(context, "Duration", DurationFilter.entries, working.duration, { it.label }) { chosen ->
                working = working.copy(duration = chosen)
                render()
            }
        }
        releaseDateButton.setOnClickListener {
            showChoice(context, "Release date", ReleaseDateFilter.entries, working.releaseDate, { it.label }) { chosen ->
                working = working.copy(releaseDate = chosen)
                render()
            }
        }
        codecButton.setOnClickListener {
            showChoice(context, "Video codec", VideoCodecFilter.entries, working.videoCodec, { it.label }) { chosen ->
                working = working.copy(videoCodec = chosen)
                render()
            }
        }
        performersButton.setOnClickListener {
            EntityFilterDialog.show(context as FragmentActivity, EntityItem.Kind.PERFORMER, working.performers) { chosen ->
                working = working.copy(performers = chosen)
                render()
            }
        }
        studiosButton.setOnClickListener {
            EntityFilterDialog.show(context as FragmentActivity, EntityItem.Kind.STUDIO, working.studios) { chosen ->
                working = working.copy(studios = chosen)
                render()
            }
        }
        tagsButton.setOnClickListener {
            EntityFilterDialog.show(context as FragmentActivity, EntityItem.Kind.TAG, working.tags) { chosen ->
                working = working.copy(tags = chosen)
                render()
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Filter scenes")
            .setView(view)
            .setPositiveButton("Apply") { _, _ -> onChosen(working) }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear all", null)
            .create()
        saveButton.visibility = if (onSave == null) View.GONE else View.VISIBLE
        saveButton.setOnClickListener {
            onSave?.invoke(working)
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                working = SceneFilterOptions()
                render()
            }
        }
        render()
        dialog.show()
    }

    private fun <T> showChoice(
        context: Context,
        title: String,
        options: List<T>,
        selected: T,
        label: (T) -> String,
        onChosen: (T) -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options.map(label).toTypedArray(), options.indexOf(selected)) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

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
