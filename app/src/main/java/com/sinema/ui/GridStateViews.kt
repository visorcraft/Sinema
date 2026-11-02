package com.sinema.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.sinema.R

internal fun Activity.renderGridLoadState(state: GridLoadState, onRetry: () -> Unit) {
    val overlay = findViewById<View>(R.id.grid_state_overlay) ?: return
    overlay.visibility = if (state.visible) View.VISIBLE else View.GONE
    findViewById<View>(R.id.grid_state_progress)?.visibility =
        if (state.showProgress) View.VISIBLE else View.GONE
    findViewById<TextView>(R.id.grid_state_message)?.text = state.message
    val retry = findViewById<Button>(R.id.grid_state_retry)
    retry?.visibility = if (state.showRetry) View.VISIBLE else View.GONE
    retry?.setOnClickListener { onRetry() }
    if (state.showRetry) retry?.post { retry.requestFocus() }
}
