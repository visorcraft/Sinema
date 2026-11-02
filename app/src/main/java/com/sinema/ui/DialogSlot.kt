package com.sinema.ui

import android.app.AlertDialog

/**
 * One managed playback dialog slot: identity-safe dismiss, show-time
 * tracking, and lifecycle teardown. Replaces the seven nullable
 * AlertDialog fields plus their repeated dismiss/null/identity dances.
 */
class DialogSlot {
    var dialog: AlertDialog? = null
        internal set

    val isShowing: Boolean get() = dialog?.isShowing == true

    fun show(builder: AlertDialog.Builder) {
        dismiss()
        dialog = builder.show()
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
