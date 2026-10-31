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

class HomeOrderActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private val shortcuts get() = intent.getStringExtra(EXTRA_MODE) == MODE_SHORTCUTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val settings = app.prefs.homeLayout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        layout.addView(TextView(this).apply {
            text = if (shortcuts) "Customize Shortcuts" else "Customize Content Rows"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 20)
        })
        if (shortcuts) {
            settings.shortcutOrder.orEmpty().forEach { shortcut ->
                addItem(
                    parent = layout,
                    label = shortcut.label,
                    visible = shortcut !in settings.hiddenShortcuts.orEmpty(),
                    moveUp = { app.prefs.homeLayout = settings.moveShortcut(shortcut, -1); render() },
                    moveDown = { app.prefs.homeLayout = settings.moveShortcut(shortcut, 1); render() },
                    toggle = { app.prefs.homeLayout = settings.toggleShortcut(shortcut); render() }
                )
            }
        } else {
            settings.rowOrder.orEmpty().forEach { row ->
                addItem(
                    parent = layout,
                    label = if (row == HomeContentRow.SAVED_FILTER && settings.savedFilterName.isNotBlank())
                        "${row.label}: ${settings.savedFilterName}" else row.label,
                    visible = row !in settings.hiddenRows.orEmpty(),
                    moveUp = { app.prefs.homeLayout = settings.moveRow(row, -1); render() },
                    moveDown = { app.prefs.homeLayout = settings.moveRow(row, 1); render() },
                    toggle = { app.prefs.homeLayout = settings.toggleRow(row); render() }
                )
            }
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        })
    }

    private fun addItem(
        parent: LinearLayout,
        label: String,
        visible: Boolean,
        moveUp: () -> Unit,
        moveDown: () -> Unit,
        toggle: () -> Unit
    ) {
        parent.addView(TextView(this).apply {
            text = "$label — ${if (visible) "Shown" else "Hidden"}"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 16, 0, 4)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(buttons, "Move Up", moveUp)
        addButton(buttons, "Move Down", moveDown)
        addButton(buttons, if (visible) "Hide" else "Show", toggle)
        parent.addView(buttons)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            isFocusable = true
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setOnFocusChangeListener { _, focused ->
                setBackgroundColor(if (focused) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 8
            bottomMargin = 8
        })
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SHORTCUTS = "shortcuts"
        const val MODE_ROWS = "rows"
    }
}
