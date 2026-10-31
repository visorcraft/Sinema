package com.sinema.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityPlayCount
import com.sinema.model.EntityItem
import com.sinema.model.Scene
import com.sinema.model.ScenePlayStats
import com.sinema.model.StatsSummary
import com.sinema.util.SceneIntents
import com.sinema.util.TimeFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Library stats: server totals plus most-watched scenes, performers, tags,
 * and studios aggregated from the top-played scenes.
 */
class StatsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, StatsFragment())
                .commitNowAllowingStateLoss()
        }
    }
}

class StatsFragment : RowsSupportFragment() {
    private val app get() = SinemaApp.instance

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val topPad = (27 * density).toInt()
        view.setPadding(hPad, topPad, hPad, topPad)
        (view as? ViewGroup)?.clipToPadding = false

        val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        lrp.shadowEnabled = false
        adapter = ArrayObjectAdapter(lrp)
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val (stats, topScenes) = coroutineScope {
                    val statsJob = async { app.api.getStats() }
                    val scenesJob = async { app.api.findTopPlayedScenes() }
                    Pair(statsJob.await(), scenesJob.await())
                }
                bindRows(stats, topScenes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "Stats load failed", e)
                android.widget.Toast.makeText(
                    requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun bindRows(stats: StatsSummary, topScenes: List<Scene>) {
        val rows = adapter as ArrayObjectAdapter

        val summary = ArrayObjectAdapter(StatCardPresenter())
        summary.add(StatCard(TimeFormat.formatSeconds(stats.totalPlayDurationSeconds), "Total watch time"))
        summary.add(StatCard("${stats.totalPlayCount}", "Total plays"))
        summary.add(StatCard("${stats.sceneCount}", "Scenes"))
        summary.add(StatCard("${stats.scenesPlayed}", "Scenes played"))
        summary.add(StatCard("${stats.performerCount}", "Performers"))
        summary.add(StatCard("${stats.tagCount}", "Tags"))
        summary.add(StatCard("${stats.studioCount}", "Studios"))
        rows.add(ListRow(HeaderItem("Library"), summary))

        if (topScenes.isNotEmpty()) {
            val sceneAdapter = ArrayObjectAdapter(CardPresenter(app.api))
            topScenes.take(20).forEach { sceneAdapter.add(it) }
            rows.add(ListRow(HeaderItem("Most played scenes"), sceneAdapter))
        }

        // Rankings are aggregated from the 200 most-played scenes, not the
        // whole library — say so in the row headers.
        val scope = " (200 most played scenes)"
        addEntityRow("Most watched performers$scope", ScenePlayStats.topPerformers(topScenes))
        addEntityRow("Most watched tags$scope", ScenePlayStats.topTags(topScenes))
        addEntityRow("Most watched studios$scope", ScenePlayStats.topStudios(topScenes))

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Scene -> startActivity(SceneIntents.detail(requireContext(), item))
                is EntityPlayCount -> startActivity(
                    EntityScenesActivity.intent(requireContext(), item.item)
                )
            }
        }
    }

    private fun addEntityRow(header: String, entries: List<EntityPlayCount>) {
        if (entries.isEmpty()) return
        val rows = adapter as ArrayObjectAdapter
        val adapterOut = ArrayObjectAdapter(EntityPlayCardPresenter())
        entries.forEach { adapterOut.add(it) }
        rows.add(ListRow(HeaderItem(header), adapterOut))
    }
}

data class StatCard(val value: String, val label: String)

/** Big-number card for the Library summary row. */
class StatCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is StatCard) return
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.card_title).apply {
            text = item.value
            textSize = 26f
        }
        view.findViewById<TextView>(R.id.card_content).text = item.label
        view.findViewById<ImageView>(R.id.card_image).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundColor(0xFF444444.toInt())
        }
        view.findViewById<View>(R.id.card_heart).visibility = View.GONE
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}

/** Entity card whose content line shows aggregated plays. */
class EntityPlayCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is EntityPlayCount) return
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.card_title).text = item.item.name
        view.findViewById<TextView>(R.id.card_content).text =
            "${item.plays} ${if (item.plays == 1) "play" else "plays"}"
        view.findViewById<View>(R.id.card_heart).visibility = View.GONE
        view.findViewById<View>(R.id.card_checkmark).visibility = View.GONE
        view.findViewById<View>(R.id.card_progress).visibility = View.GONE
        val image = view.findViewById<ImageView>(R.id.card_image)
        image.setImageResource(android.R.drawable.ic_menu_agenda)
        image.scaleType = ImageView.ScaleType.CENTER
        image.setBackgroundColor(0xFF444444.toInt())
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
