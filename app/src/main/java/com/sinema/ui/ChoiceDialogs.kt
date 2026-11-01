package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.PlaybackAspectRatio
import com.sinema.model.PlaybackStreamQuality
import com.sinema.model.SortOption

// Thin wrappers over Dialogs.showChoiceDialog; kept as named objects so
// call sites read intent at a glance.

object SortDialog {
    fun show(context: Context, current: SortOption, onChosen: (SortOption) -> Unit) =
        Dialogs.showChoiceDialog(
            context, "Sort by", SortOption.entries, current, label = { it.label }, onChosen = onChosen
        )
}

object AspectRatioDialog {
    fun show(
        context: Context,
        current: PlaybackAspectRatio,
        onChosen: (PlaybackAspectRatio) -> Unit
    ): AlertDialog =
        Dialogs.showChoiceDialog(
            context, "Aspect ratio", PlaybackAspectRatio.entries, current,
            label = { it.label }, onChosen = onChosen
        )
}

object StreamQualityDialog {
    fun show(
        context: Context,
        current: PlaybackStreamQuality,
        onChosen: (PlaybackStreamQuality) -> Unit
    ): AlertDialog =
        Dialogs.showChoiceDialog(
            context, "Stream quality", PlaybackStreamQuality.entries, current,
            label = { it.label }, onChosen = onChosen
        )
}
