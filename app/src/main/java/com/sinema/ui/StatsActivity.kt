package com.sinema.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import com.sinema.model.EntityPlayCount
import com.sinema.model.Scene
import com.sinema.model.ScenePlayStats
import com.sinema.model.StatsSummary
import com.sinema.util.SceneIntents
import com.sinema.util.TasteInsights
import com.sinema.util.TimeFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Library stats: server totals plus most-watched scenes, performers, tags,
 * and studios aggregated from the top-played scenes.
 */
class StatsActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, StatsFragment())
                .commitNowAllowingStateLoss()
        }
    }
}

class StatsFragment : RowsSupportFragment() {
    private val app get() = SinemaApp.instance
    private val discreetRebind = DiscreetRebind()

    override fun onResume() {
        super.onResume()
        discreetRebind.onResume {
            (adapter as? ArrayObjectAdapter)?.rebindCardsRecursively()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val topPad = (27 * density).toInt()
        view.setPadding(hPad, topPad, hPad, topPad)
        (view as? ViewGroup)?.clipToPadding = false

        val lrp = ListRowPresenter(TvMotion.focusZoom(), false)
        lrp.shadowEnabled = false
        adapter = ArrayObjectAdapter(lrp)
        loadStats()
    }

    private fun loadStats() {
        val rows = adapter as? ArrayObjectAdapter
        val hasContent = (rows?.size() ?: 0) > 0
        activity?.renderGridLoadState(GridLoadState.loading(hasContent)) { loadStats() }
        launchSafely(
            label = "Stats load",
            onError = { e ->
                if (!isAdded) return@launchSafely
                val message = UiErrorMessage.forException(e)
                if ((rows?.size() ?: 0) > 0) {
                    android.widget.Toast.makeText(
                        requireContext(), message, android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    activity?.renderGridLoadState(
                        GridLoadState.failed(hasContent = false, message = message)
                    ) { loadStats() }
                }
            }
        ) {
            val (stats, topScenes) = coroutineScope {
                val statsJob = async { app.api.getStats() }
                val scenesJob = async { app.api.findTopPlayedScenes() }
                Pair(statsJob.await(), scenesJob.await())
            }
            if (!isAdded) return@launchSafely
            rows?.clear()
            bindRows(stats, topScenes)
            activity?.renderGridLoadState(
                GridLoadState.loaded(rows?.size() ?: 0, "No library stats available")
            ) { loadStats() }
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

        // Taste trends: play-share inside the last 14 days vs everything
        // before, from the same most-played snapshot (no extra queries).
        val cutoff = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000))
        val recent = TasteInsights.recentScenes(topScenes, cutoff)
        TasteInsights.summary(recent)?.let { text ->
            val summaryAdapter = ArrayObjectAdapter(StatCardPresenter())
            summaryAdapter.add(StatCard(text, "Your last two weeks"))
            rows.add(ListRow(HeaderItem("Taste trends"), summaryAdapter))
        }
        TasteInsights.trends(topScenes, cutoff).takeIf { it.isNotEmpty() }?.let { trends ->
            val trendAdapter = ArrayObjectAdapter(StatCardPresenter())
            trends.forEach { trend ->
                val arrow = when {
                    trend.delta > 0 -> " ▲"
                    trend.delta < 0 -> " ▼"
                    else -> ""
                }
                trendAdapter.add(StatCard("${trend.scenes} scenes$arrow", trend.label))
            }
            rows.add(ListRow(HeaderItem("In your recent plays"), trendAdapter))
        }

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

internal enum class StatTrendTone { NEUTRAL, UP, DOWN }

internal fun statTrendToneFor(value: String): StatTrendTone = when {
    value.contains("▲") -> StatTrendTone.UP
    value.contains("▼") -> StatTrendTone.DOWN
    else -> StatTrendTone.NEUTRAL
}

internal fun entityKindBadgeLabel(kind: EntityItem.Kind): String = when (kind) {
    EntityItem.Kind.PERFORMER -> "Performer"
    EntityItem.Kind.TAG -> "Tag"
    EntityItem.Kind.STUDIO -> "Studio"
}

/** Big-number card for the Library summary and taste-trend rows. */
class StatCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.stats_stat_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is StatCard) return
        val view = viewHolder.view
        val context = view.context
        val tone = statTrendToneFor(item.value)
        val valueColor = when (tone) {
            StatTrendTone.UP -> ContextCompat.getColor(context, R.color.screens_polish_trend_up)
            StatTrendTone.DOWN -> ContextCompat.getColor(context, R.color.screens_polish_trend_down)
            StatTrendTone.NEUTRAL -> ContextCompat.getColor(context, android.R.color.white)
        }
        val accentColor = when (tone) {
            StatTrendTone.UP -> ContextCompat.getColor(context, R.color.screens_polish_trend_up)
            StatTrendTone.DOWN -> ContextCompat.getColor(context, R.color.screens_polish_trend_down)
            StatTrendTone.NEUTRAL -> ContextCompat.getColor(context, R.color.screens_polish_accent)
        }
        view.findViewById<View>(R.id.stats_card_accent).setBackgroundColor(accentColor)
        view.findViewById<TextView>(R.id.card_title).apply {
            text = item.value
            setTextColor(valueColor)
        }
        view.findViewById<TextView>(R.id.card_content).text = item.label
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}

/** Entity card whose content line shows kind badge and aggregated plays. */
class EntityPlayCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.stats_entity_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is EntityPlayCount) return
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.stats_entity_kind).text = entityKindBadgeLabel(item.item.kind)
        view.findViewById<TextView>(R.id.stats_entity_name).text = item.item.name
        view.findViewById<TextView>(R.id.stats_entity_plays).text =
            "${item.plays} ${if (item.plays == 1) "play" else "plays"}"
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
