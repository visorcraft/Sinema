package com.sinema.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import com.sinema.R
import com.sinema.SinemaApp

internal fun Activity.renderGridLoadState(state: GridLoadState, onRetry: () -> Unit) {
    val overlay = findViewById<View>(R.id.grid_state_overlay) ?: return
    val wasVisible = overlay.isVisible
    overlay.animate().cancel()
    if (state.visible) {
        overlay.isVisible = true
        val reduceMotion = runCatching { SinemaApp.instance.prefs.reduceMotion }.getOrDefault(false)
        if (!wasVisible && !reduceMotion) {
            overlay.alpha = 0f
            overlay.animate().alpha(1f).setDuration(150L).start()
        } else {
            overlay.alpha = 1f
        }
    } else {
        overlay.alpha = 1f
        overlay.isVisible = false
    }
    findViewById<View>(R.id.grid_state_progress)?.isVisible = state.showProgress
    findViewById<TextView>(R.id.grid_state_message)?.apply {
        text = state.message
        isVisible = state.message.isNotBlank()
    }
    val retry = findViewById<Button>(R.id.grid_state_retry)
    retry?.isVisible = state.showRetry
    retry?.setOnClickListener { onRetry() }
    if (state.showRetry) retry?.post { retry.requestFocus() }
}
