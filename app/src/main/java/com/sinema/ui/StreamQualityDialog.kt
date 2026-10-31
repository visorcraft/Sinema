package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.PlaybackStreamQuality

object StreamQualityDialog {
    fun show(
        context: Context,
        current: PlaybackStreamQuality,
        onChosen: (PlaybackStreamQuality) -> Unit
    ): AlertDialog {
        val options = PlaybackStreamQuality.entries
        return AlertDialog.Builder(context)
            .setTitle("Stream quality")
            .setSingleChoiceItems(
                options.map { it.label }.toTypedArray(),
                options.indexOf(current)
            ) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .show()
    }
}
