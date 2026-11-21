package com.sinema.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.sinema.R
import com.sinema.api.SinemaApi
import com.sinema.model.Scene
import com.sinema.model.StarRating
import com.sinema.util.GlideAuth
import com.sinema.util.TimeFormat
import kotlin.math.ceil

/** The highlighted continue-playing scene shown at the top of the home screen. */
data class HeroItem(val scene: Scene, val resumeMs: Long)

object HeroCard {
    /**
     * Action line under the hero title. Resuming shows the position; a hero
     * without a resume point just plays from the start.
     */
    fun actionLabel(resumeMs: Long): String =
        if (resumeMs > 0) "▶ RESUME  ${TimeFormat.formatMs(resumeMs)}" else "▶ PLAY"

    /** Eyebrow label distinguishing resumed playback from a fresh featured pick. */
    fun eyebrowLabel(resumeMs: Long): String =
        if (resumeMs > 0) "CONTINUE WATCHING" else "FEATURED"

    /** Concise video quality tag derived from pixel dimensions, or null when unknown/SD. */
    fun qualityBadge(width: Int, height: Int): String? {
        val longEdge = maxOf(width, height)
        val shortEdge = minOf(width, height)
        return when {
            shortEdge >= 2160 || longEdge >= 3840 -> "4K"
            shortEdge >= 1440 || longEdge >= 2560 -> "1440p"
            shortEdge >= 1080 || longEdge >= 1920 -> "1080p"
            shortEdge >= 720 || longEdge >= 1280 -> "720p"
            else -> null
        }
    }

    /**
     * Resume progress 0..100 for the hero progress bar. Uses [resumeMs] directly
     * (falling back to [Scene.resumeTime]) and hides noise below 2% or at/above 98%.
     */
    fun progressPercent(scene: Scene, resumeMs: Long): Int {
        if (scene.duration <= 0.0) return 0
        val effectiveSec = if (resumeMs > 0L) resumeMs / 1000.0 else scene.resumeTime
        if (effectiveSec <= 0.0) return 0
        val fraction = effectiveSec / scene.duration
        if (fraction < 0.02 || fraction >= 0.98) return 0
        return (fraction * 100.0).toInt().coerceIn(1, 99)
    }

    /** Human-readable remaining watch time for resumed heroes, or null when not applicable. */
    fun remainingLabel(scene: Scene, resumeMs: Long): String? {
        if (resumeMs <= 0L || scene.duration <= 0.0) return null
        val remainingSec = scene.duration - (resumeMs / 1000.0)
        if (remainingSec <= 0.0) return null
        if (remainingSec < 60.0) return "<1m left"
        val totalMinutes = ceil(remainingSec / 60.0).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m left"
            hours > 0 -> "${hours}h left"
            else -> "${minutes}m left"
        }
    }

    /**
     * Composes a crisp, non-duplicative hero metadata line: optional stars,
     * duration, remaining time when resuming, and studio (omitting filename
     * when it already serves as [Scene.displayTitle]).
     */
    fun metaLine(scene: Scene, resumeMs: Long): String = buildList {
        if (scene.starCount > 0) add(StarRating.glyphs(scene.starCount))
        if (scene.duration > 0.0) add(scene.formatDuration())
        remainingLabel(scene, resumeMs)?.let(::add)
        val studioName = scene.studio?.name?.trim().orEmpty()
        when {
            studioName.isNotEmpty() -> add(studioName)
            scene.title.isNotBlank() && scene.filename.isNotBlank() -> add(scene.filename)
        }
    }.joinToString("  •  ")

    fun accessibilitySummary(scene: Scene, resumeMs: Long): String = buildList {
        add(scene.displayTitle)
        add(actionLabel(resumeMs).removePrefix("▶ ").trim())
        val meta = metaLine(scene, resumeMs)
        if (meta.isNotBlank()) add(meta)
    }.joinToString(", ")
}

/** Wide single-focus card: screenshot backdrop + eyebrow/title/meta/progress/CTA strip. */
class HeroPresenter(
    private val api: SinemaApi,
    private val onDown: () -> Unit = {}
) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_hero_card, parent, false)
        TvMotion.clipRoundedCornersPx(
            view,
            view.resources.getDimension(R.dimen.home_polish_hero_corner_radius)
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is HeroItem) return
        val view = viewHolder.view
        val actionView = view.findViewById<TextView>(R.id.hero_action)
        val applyHeroFocus: (Boolean, Boolean) -> Unit = { focused, animate ->
            TvMotion.applyFocusMotion(view, TvMotion.FocusRole.HERO, focused, animate = animate)
            val actionColorRes = if (focused) {
                R.color.home_polish_hero_cta_text_focused
            } else {
                R.color.home_polish_hero_cta_text_unfocused
            }
            actionView.setTextColor(ContextCompat.getColor(view.context, actionColorRes))
        }
        applyHeroFocus(view.hasFocus(), false)
        view.setOnFocusChangeListener { _, hasFocus -> applyHeroFocus(hasFocus, true) }

        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
            ) {
                onDown()
                true
            } else {
                false
            }
        }

        val eyebrow = view.findViewById<TextView>(R.id.hero_eyebrow)
        eyebrow.text = view.context.getString(
            if (item.resumeMs > 0) R.string.home_polish_hero_continue_watching
            else R.string.home_polish_hero_featured
        )

        val badge = view.findViewById<TextView>(R.id.hero_badge)
        val quality = HeroCard.qualityBadge(item.scene.width, item.scene.height)
        if (quality != null) {
            badge.text = quality
            badge.visibility = View.VISIBLE
        } else {
            badge.text = ""
            badge.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.hero_title).text = item.scene.displayTitle
        val metaText = HeroCard.metaLine(item.scene, item.resumeMs)
        view.findViewById<TextView>(R.id.hero_meta).apply {
            text = metaText
            visibility = if (metaText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        val progressBar = view.findViewById<ProgressBar>(R.id.hero_progress)
        val percent = HeroCard.progressPercent(item.scene, item.resumeMs)
        if (percent > 0) {
            progressBar.progress = percent
            progressBar.contentDescription = view.context.getString(
                R.string.accessibility_watch_progress, percent
            )
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.progress = 0
            progressBar.contentDescription = null
            progressBar.visibility = View.GONE
        }

        actionView.text = HeroCard.actionLabel(item.resumeMs)
        view.contentDescription = HeroCard.accessibilitySummary(item.scene, item.resumeMs)

        val image = view.findViewById<ImageView>(R.id.hero_image)
        val fadeMs = TvMotion.imageCrossfadeMs()
        val request = Glide.with(view.context.applicationContext)
            .load(GlideAuth.url(api, api.getScreenshotUrl(item.scene.id)))
            .centerCrop()
            .let { if (fadeMs > 0) it.transition(DrawableTransitionOptions.withCrossFade(fadeMs)) else it }
        request.into(image)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val view = viewHolder.view
        TvMotion.resetFocusMotion(view)
        view.setOnFocusChangeListener(null)
        view.setOnKeyListener(null)
        view.contentDescription = null
        val image = view.findViewById<ImageView>(R.id.hero_image)
        Glide.with(view.context.applicationContext).clear(image)
        image.setImageDrawable(null)
        view.findViewById<TextView>(R.id.hero_badge).apply {
            text = ""
            visibility = View.GONE
        }
        view.findViewById<ProgressBar>(R.id.hero_progress).apply {
            progress = 0
            contentDescription = null
            visibility = View.GONE
        }
    }
}
