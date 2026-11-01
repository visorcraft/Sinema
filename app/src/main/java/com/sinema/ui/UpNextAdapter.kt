package com.sinema.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import com.sinema.R
import com.sinema.util.PlaybackQueue
import com.sinema.util.TimeFormat

/**
 * Rows for the Up Next dialog. The now-playing item renders dimmed without
 * controls; upcoming items get jump (row select), move up/down, and remove.
 * The adapter reads the live queue so edits apply immediately.
 */
class UpNextAdapter(
    context: android.content.Context,
    private val onJump: (PlaybackQueue.Item) -> Unit
) : ArrayAdapter<PlaybackQueue.Item>(context, R.layout.item_up_next, mutableListOf()) {

    fun refresh() {
        val current = PlaybackQueue.current()
        val rows = listOfNotNull(current) + PlaybackQueue.remaining()
        clear()
        addAll(rows)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_up_next, parent, false)
        val item = getItem(position) ?: return row
        val isCurrent = PlaybackQueue.current()?.id == item.id
        val label = row.findViewById<TextView>(R.id.up_next_label)
        val prefix = if (isCurrent) "▶ " else ""
        label.text = prefix + "${TimeFormat.formatSeconds(item.durationSec)}  ${item.title}"
        label.alpha = if (isCurrent) 0.5f else 1f
        label.setOnClickListener { if (!isCurrent) onJump(item) }
        fun wire(id: Int, enabled: Boolean, action: () -> Unit) {
            val button = row.findViewById<Button>(id)
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.3f
            button.setOnClickListener { if (enabled) action() }
        }
        wire(R.id.up_next_move_up, !isCurrent && position > (if (PlaybackQueue.current() != null) 1 else 0)) {
            if (PlaybackQueue.moveRemaining(item.id, -1)) refresh()
        }
        wire(R.id.up_next_move_down, !isCurrent) {
            if (PlaybackQueue.moveRemaining(item.id, 1)) refresh()
        }
        wire(R.id.up_next_remove, !isCurrent) {
            if (PlaybackQueue.removeRemaining(item.id)) refresh()
        }
        return row
    }
}
