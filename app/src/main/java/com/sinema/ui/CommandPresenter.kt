package com.sinema.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.leanback.widget.Presenter
import com.sinema.R

class CommandPresenter(
    hasPin: Boolean,
    private val onDown: () -> Unit = {}
) : Presenter() {
    var hasPin: Boolean = hasPin
        internal set

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
        val titleView = view.findViewById<TextView>(R.id.command_title)
        val iconView = view.findViewById<ImageView>(R.id.command_icon)
        titleView.text = command
        val icon = when (command) {
            HomeCommandRow.LOG_OUT -> android.R.drawable.ic_lock_lock
            HomeCommandRow.REFRESH -> android.R.drawable.ic_popup_sync
            HomeCommandRow.SETTINGS -> android.R.drawable.ic_menu_preferences
            HomeCommandRow.ABOUT -> android.R.drawable.ic_menu_info_details
            else -> android.R.drawable.ic_menu_info_details
        }
        iconView.setImageResource(icon)

        val applyStyle: (Boolean, Boolean) -> Unit = { focused, animate ->
            TvMotion.applyFocusMotion(view, TvMotion.FocusRole.COMMAND, focused, animate = animate)
            val iconColor = ContextCompat.getColor(
                view.context,
                CardVisuals.commandIconTintRes(command, focused)
            )
            ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(iconColor))
            titleView.setTextColor(
                ContextCompat.getColor(
                    view.context,
                    if (focused) R.color.card_polish_title_focused else R.color.card_polish_title_unfocused
                )
            )
            titleView.setTypeface(null, if (focused) Typeface.BOLD else Typeface.NORMAL)
        }
        applyStyle(view.hasFocus(), false)
        view.setOnFocusChangeListener { _, hasFocus -> applyStyle(hasFocus, true) }

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
        val view = viewHolder.view
        TvMotion.resetFocusMotion(view)
        view.setOnFocusChangeListener(null)
        view.setOnKeyListener(null)
        ImageViewCompat.setImageTintList(view.findViewById(R.id.command_icon), null)
    }
}
