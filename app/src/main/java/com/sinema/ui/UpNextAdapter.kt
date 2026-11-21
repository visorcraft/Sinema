package com.sinema.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.util.PlaybackQueue

/**
 * Rows for the Up Next dialog. The now-playing item renders dimmed without
 * controls; upcoming items get jump (row select), move up/down, and remove.
 * The adapter reads the live queue so edits apply immediately.
 */
class UpNextAdapter(
    context: Context,
    private val onQueueChanged: () -> Unit = {},
    private val discreetMode: () -> Boolean = { SinemaApp.instance.prefs.discreetMode },
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
        val current = PlaybackQueue.current()
        val isCurrent = current?.id == item.id
        val model = buildUpNextRowModel(
            item = item,
            position = position,
            totalCount = count,
            isCurrent = isCurrent,
            hasCurrent = current != null,
            discreetMode = discreetMode(),
            nowLabel = row.context.getString(R.string.playback_polish_queue_now),
            watchedLabel = row.context.getString(R.string.playback_polish_queue_watched)
        )
        val content = row.findViewById<View>(R.id.up_next_row_content)
        val badge = row.findViewById<TextView>(R.id.up_next_badge)
        val label = row.findViewById<TextView>(R.id.up_next_label)
        val meta = row.findViewById<TextView>(R.id.up_next_meta)

        badge.text = model.badgeText
        label.text = model.titleText
        meta.text = model.metaText
        content.alpha = if (model.isCurrent) 0.55f else 1f
        content.isClickable = !model.isCurrent
        content.setOnClickListener { if (!model.isCurrent) onJump(item) }

        fun wire(id: Int, enabled: Boolean, action: () -> Unit) {
            val button = row.findViewById<Button>(id)
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.3f
            button.setOnClickListener { if (enabled) action() }
        }
        wire(R.id.up_next_move_up, model.canMoveUp) {
            if (PlaybackQueue.moveRemaining(item.id, -1)) {
                refresh()
                onQueueChanged()
            }
        }
        wire(R.id.up_next_move_down, model.canMoveDown) {
            if (PlaybackQueue.moveRemaining(item.id, 1)) {
                refresh()
                onQueueChanged()
            }
        }
        wire(R.id.up_next_remove, model.canRemove) {
            if (PlaybackQueue.removeRemaining(item.id)) {
                refresh()
                onQueueChanged()
            }
        }
        return row
    }
}
