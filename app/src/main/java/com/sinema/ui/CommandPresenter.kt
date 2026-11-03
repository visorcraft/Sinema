package com.sinema.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.sinema.R

class CommandPresenter(
    private val hasPin: Boolean,
    private val onDown: () -> Unit = {}
) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_command_card, parent, false)
        view.id = View.generateViewId()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val command = item as? String ?: return
        val view = viewHolder.view
        val first = if (hasPin) HomeCommandRow.LOG_OUT else HomeCommandRow.REFRESH
        view.nextFocusLeftId = if (command == first) view.id else View.NO_ID
        view.nextFocusRightId = if (command == HomeCommandRow.ABOUT) view.id else View.NO_ID
        view.contentDescription = if (command == HomeCommandRow.LOG_OUT) {
            view.context.getString(R.string.accessibility_log_out)
        } else {
            command
        }
        view.findViewById<TextView>(R.id.command_title).text = command
        val icon = when (command) {
            HomeCommandRow.LOG_OUT -> android.R.drawable.ic_lock_lock
            HomeCommandRow.REFRESH -> android.R.drawable.ic_popup_sync
            HomeCommandRow.SETTINGS -> android.R.drawable.ic_menu_preferences
            HomeCommandRow.ABOUT -> android.R.drawable.ic_menu_info_details
            else -> android.R.drawable.ic_menu_info_details
        }
        view.findViewById<ImageView>(R.id.command_icon).setImageResource(icon)
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                onDown()
                true
            } else {
                false
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.setOnKeyListener(null)
    }
}
