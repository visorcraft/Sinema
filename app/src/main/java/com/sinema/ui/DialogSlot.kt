package com.sinema.ui

import android.app.AlertDialog
import com.sinema.R

/**
 * One managed playback dialog slot: identity-safe dismiss, unified TV dialog
 * chrome, D-pad focus initialization, and lifecycle teardown.
 */
class DialogSlot {
    var dialog: AlertDialog? = null
        internal set

    val isShowing: Boolean get() = dialog?.isShowing == true

    fun show(
        builder: AlertDialog.Builder,
        onDismiss: (() -> Unit)? = null,
        onShow: (AlertDialog) -> Unit = {}
    ): AlertDialog {
        dismiss()
        val created = builder.create()
        created.setOnShowListener {
            created.window?.setBackgroundDrawableResource(R.drawable.playback_polish_dialog_background)
            if (created.listView != null) {
                created.listView.requestFocus()
            } else {
                created.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
            }
            onShow(created)
        }
        if (onDismiss != null) {
            created.setOnDismissListener {
                dismissIf(created)
                onDismiss()
            }
        }
        dialog = created
        created.show()
        return created
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /** Dismiss only if this slot currently holds [d]. */
    fun dismissIf(d: AlertDialog) {
        if (dialog === d) dismiss()
    }
}
