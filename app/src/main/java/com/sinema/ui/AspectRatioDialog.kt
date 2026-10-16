package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.PlaybackAspectRatio

object AspectRatioDialog {
    fun show(
        context: Context,
        current: PlaybackAspectRatio,
        onChosen: (PlaybackAspectRatio) -> Unit
    ): AlertDialog {
        val options = PlaybackAspectRatio.entries
        return AlertDialog.Builder(context)
            .setTitle("Aspect ratio")
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
