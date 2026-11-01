package com.sinema.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import com.sinema.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Power-user server dashboard: Stash version, library totals, running jobs,
 * and the most recent errors recorded by the app.
 */
class ServerDashboardActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "Server"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 20)
        })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(content)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        })
        load()
    }

    private fun load() {
        content.removeAllViews()
        addRow("Loading…", "")
        lifecycleScope.launch {
            try {
                val (version, stats, jobs) = coroutineScope {
                    val v = async { app.api.getServerVersion() }
                    val s = async { app.api.getStats() }
                    val j = async { app.api.getJobQueue() }
                    Triple(v.await(), s.await(), j.await())
                }
                if (isFinishing || isDestroyed) return@launch
                content.removeAllViews()
                addRow("Stash version", version)
                addRow("Scenes", "${stats.sceneCount}  (${TimeFormat.formatSeconds(stats.scenesDurationSeconds)} total)")
                addRow("Performers · Tags · Studios",
                    "${stats.performerCount} · ${stats.tagCount} · ${stats.studioCount}")
                addRow("Plays", "${stats.totalPlayCount}  (${TimeFormat.formatSeconds(stats.totalPlayDurationSeconds)} watched)")
                when {
                    jobs == null -> addRow("Jobs", "Job queue not supported by this server")
                    jobs.isEmpty() -> addRow("Jobs", "Idle")
                    else -> {
                        addHeader("Running jobs")
                        jobs.take(10).forEach { addRow("•", it) }
                    }
                }
                val errors = app.recentErrors.toList().asReversed()
                if (errors.isNotEmpty()) {
                    addHeader("Recent errors")
                    errors.take(10).forEach { addRow("•", it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@launch
                content.removeAllViews()
                addRow("Error", e.message ?: "unknown")
            }
        }
    }

    private fun addHeader(label: String) {
        content.addView(TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 4)
        })
    }

    private fun addRow(label: String, value: String) {
        content.addView(TextView(this).apply {
            text = if (label.isBlank()) value else "$label: $value"
            textSize = 15f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 4, 0, 4)
            // Focusable rows give the ScrollView a D-pad scroll path.
            isFocusable = true
            setOnFocusChangeListener { v, focused ->
                v.setBackgroundColor(if (focused) 0xFF22303C.toInt() else 0x00000000)
            }
        })
    }
}
