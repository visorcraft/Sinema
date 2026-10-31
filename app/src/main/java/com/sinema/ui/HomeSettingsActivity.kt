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
    private lateinit var artworkButton: Button
    private lateinit var heroButton: Button
    private lateinit var groupLibraryButton: Button
    private lateinit var filterButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        update()
    }

    /**
     * Buttons read prefs inside their click lambdas and only labels are
     * refreshed in [update], so D-pad focus survives every toggle.
     */
    private fun buildContentView(): ScrollView {
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

        artworkButton = addButton(layout, "Artwork") {
            app.prefs.homeLayout = app.prefs.homeLayout.copy(
                cardSize = if (app.prefs.homeLayout.cardSize == HomeCardSize.COMPACT) {
                    HomeCardSize.LARGE
                } else {
                    HomeCardSize.COMPACT
                }
            )
            update()
        }
        heroButton = addButton(layout, "Hero banner") {
            app.prefs.homeLayout = app.prefs.homeLayout.copy(showHero = !app.prefs.homeLayout.showHero)
            update()
        }
        groupLibraryButton = addButton(layout, "Group library shortcuts") {
            app.prefs.homeLayout = app.prefs.homeLayout.copy(groupLibrary = !app.prefs.homeLayout.groupLibrary)
            update()
        }
        addButton(layout, "Customize shortcuts") {
            startActivity(
                Intent(this, HomeOrderActivity::class.java)
                    .putExtra(HomeOrderActivity.EXTRA_MODE, HomeOrderActivity.MODE_SHORTCUTS)
            )
        }
        addButton(layout, "Customize content rows") {
            startActivity(
                Intent(this, HomeOrderActivity::class.java)
                    .putExtra(HomeOrderActivity.EXTRA_MODE, HomeOrderActivity.MODE_ROWS)
            )
        }
        filterButton = addButton(layout, "Saved Filter home row") { chooseSavedFilter() }

        return ScrollView(this).apply {
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        }
    }

    private fun update() {
        val settings = app.prefs.homeLayout
        artworkButton.text = "Artwork: ${settings.cardSize.name}"
        heroButton.text = "Hero banner: ${if (settings.showHero) "ON" else "OFF"}"
        groupLibraryButton.text = "Group library shortcuts: ${if (settings.groupLibrary) "ON" else "OFF"}"
        filterButton.text = "Saved Filter home row: ${settings.savedFilterName.ifBlank { "None" }}"
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
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
        return button
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
                        update()
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
