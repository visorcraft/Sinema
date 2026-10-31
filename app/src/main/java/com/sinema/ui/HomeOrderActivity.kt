package com.sinema.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.sinema.SinemaApp
import com.sinema.model.HomeContentRow
import com.sinema.model.HomeShortcut

/**
 * Reorder / show / hide home shortcuts and content rows.
 *
 * Row views are created once per item and only re-attached in the current
 * order, so D-pad focus survives Move Up/Down and Hide/Show; focus is also
 * restored by view tag after reordering.
 */
class HomeOrderActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private val shortcutsMode get() = intent.getStringExtra(EXTRA_MODE) == MODE_SHORTCUTS

    private lateinit var listLayout: LinearLayout
    private val rowViews = LinkedHashMap<String, RowViews>()

    private class RowViews(
        val item: Item,
        val label: TextView,
        val buttonRow: LinearLayout,
        val upButton: Button,
        val downButton: Button,
        val toggleButton: Button
    )

    /** Either a shortcut or a content row, keyed for view identity. */
    private sealed interface Item {
        val name: String
        val displayLabel: String

        data class Shortcut(val shortcut: HomeShortcut) : Item {
            override val name get() = "shortcut:${shortcut.name}"
            override val displayLabel get() = shortcut.label
        }

        data class Row(val row: HomeContentRow) : Item {
            override val name get() = "row:${row.name}"
            override val displayLabel get() = row.label
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        outer.addView(TextView(this).apply {
            text = if (shortcutsMode) "Customize Shortcuts" else "Customize Content Rows"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 20)
        })
        listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(listLayout)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(outer)
        })
        render()
    }

    private fun currentItemList(): List<Item> {
        val settings = app.prefs.homeLayout
        return if (shortcutsMode) {
            settings.shortcutOrder.orEmpty().map { Item.Shortcut(it) }
        } else {
            settings.rowOrder.orEmpty().map { Item.Row(it) }
        }
    }

    private fun render() {
        val settings = app.prefs.homeLayout
        val items = currentItemList()
        val focusedTag = (currentFocus as? Button)?.tag as? String

        listLayout.removeAllViews()
        items.forEach { item ->
            val views = rowViews.getOrPut(item.name) { buildRowViews(item) }
            val visible = when (item) {
                is Item.Shortcut -> item.shortcut !in settings.hiddenShortcuts.orEmpty()
                is Item.Row -> item.row !in settings.hiddenRows.orEmpty()
            }
            val labelText =
                if (item is Item.Row && item.row == HomeContentRow.SAVED_FILTER &&
                    settings.savedFilterName.isNotBlank()
                ) "${item.row.label}: ${settings.savedFilterName}" else item.displayLabel
            views.label.text = "$labelText — ${if (visible) "Shown" else "Hidden"}"
            views.toggleButton.text = if (visible) "Hide" else "Show"
            listLayout.addView(views.label)
            listLayout.addView(views.buttonRow)
        }

        if (focusedTag != null) listLayout.findViewWithTag<Button>(focusedTag)?.requestFocus()
    }

    private fun buildRowViews(item: Item): RowViews {
        val label = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 16, 0, 4)
        }
        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(tag: String, text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            this.tag = tag
            isFocusable = true
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setOnFocusChangeListener { _, focused ->
                setBackgroundColor(if (focused) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener { action() }
        }
        val up = button("up:${item.name}", "Move Up") { move(item, -1) }
        val down = button("down:${item.name}", "Move Down") { move(item, 1) }
        val toggle = button("toggle:${item.name}", "Hide") { toggle(item) }
        listOf(up, down, toggle).forEach { btn ->
            buttonRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
                bottomMargin = 8
            })
        }
        return RowViews(item, label, buttonRow, up, down, toggle)
    }

    private fun move(item: Item, delta: Int) {
        app.prefs.homeLayout = when (item) {
            is Item.Shortcut -> app.prefs.homeLayout.moveShortcut(item.shortcut, delta)
            is Item.Row -> app.prefs.homeLayout.moveRow(item.row, delta)
        }
        render()
    }

    private fun toggle(item: Item) {
        app.prefs.homeLayout = when (item) {
            is Item.Shortcut -> app.prefs.homeLayout.toggleShortcut(item.shortcut)
            is Item.Row -> app.prefs.homeLayout.toggleRow(item.row)
        }
        render()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SHORTCUTS = "shortcuts"
        const val MODE_ROWS = "rows"
    }
}
