package com.sinema.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import com.sinema.model.HomeCardSize
import com.sinema.model.HomeContentRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class HomeSettingsActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "Home Settings"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        })
        layout.addView(TextView(this).apply {
            text = "Personalize shortcuts, rows, and artwork. Changes apply when you return home."
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 12, 0, 12)
        })

        val settings = app.prefs.homeLayout
        addButton(layout, "Artwork: ${settings.cardSize.name}") {
            app.prefs.homeLayout = settings.copy(
                cardSize = if (settings.cardSize == HomeCardSize.COMPACT) HomeCardSize.LARGE else HomeCardSize.COMPACT
            )
            render()
        }
        addButton(layout, "Hero banner: ${if (settings.showHero) "ON" else "OFF"}") {
            app.prefs.homeLayout = settings.copy(showHero = !settings.showHero)
            render()
        }
        addButton(layout, "Group library shortcuts: ${if (settings.groupLibrary) "ON" else "OFF"}") {
            app.prefs.homeLayout = settings.copy(groupLibrary = !settings.groupLibrary)
            render()
        }
        addButton(layout, "Customize shortcuts") {
            startActivity(Intent(this, HomeOrderActivity::class.java).putExtra(HomeOrderActivity.EXTRA_MODE, HomeOrderActivity.MODE_SHORTCUTS))
        }
        addButton(layout, "Customize content rows") {
            startActivity(Intent(this, HomeOrderActivity::class.java).putExtra(HomeOrderActivity.EXTRA_MODE, HomeOrderActivity.MODE_ROWS))
        }
        val filterLabel = settings.savedFilterName.ifBlank { "None" }
        addButton(layout, "Saved Filter home row: $filterLabel") { chooseSavedFilter() }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        })
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isFocusable = true
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setOnFocusChangeListener { _, focused ->
                setBackgroundColor(if (focused) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 })
    }

    private fun chooseSavedFilter() {
        lifecycleScope.launch {
            try {
                val filters = app.api.findSavedSceneFilters()
                if (isFinishing || isDestroyed) return@launch
                if (filters.isEmpty()) {
                    Toast.makeText(this@HomeSettingsActivity, "No saved filters on this server", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val labels = listOf("None (hide home row)") + filters.map { it.name }
                AlertDialog.Builder(this@HomeSettingsActivity)
                    .setTitle("Saved Filter home row")
                    .setItems(labels.toTypedArray()) { _, which ->
                        val settings = app.prefs.homeLayout
                        app.prefs.homeLayout = if (which == 0) {
                            settings.copy(
                                savedFilterId = "",
                                savedFilterName = "",
                                hiddenRows = settings.hiddenRows.orEmpty() + HomeContentRow.SAVED_FILTER
                            )
                        } else {
                            val filter = filters[which - 1]
                            settings.copy(
                                savedFilterId = filter.id,
                                savedFilterName = filter.name,
                                hiddenRows = settings.hiddenRows.orEmpty() - HomeContentRow.SAVED_FILTER
                            )
                        }
                        render()
                    }
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "Failed to load saved filters", e)
                Toast.makeText(this@HomeSettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
