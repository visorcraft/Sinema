package com.sinema.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.api.SinemaApi
import com.sinema.model.Scene
import com.sinema.util.GlideAuth
import com.sinema.util.TimeFormat

/** The highlighted continue-playing scene shown at the top of the home screen. */
data class HeroItem(val scene: Scene, val resumeMs: Long)

object HeroCard {
    /**
     * Action line under the hero title. Resuming shows the position; a hero
     * without a resume point just plays from the start.
     */
    fun actionLabel(resumeMs: Long): String =
        if (resumeMs > 0) "▶ RESUME  ${TimeFormat.formatMs(resumeMs)}" else "▶ PLAY"
}

/** Wide single-focus card: screenshot backdrop + title/meta/action strip. */
class HeroPresenter(private val api: SinemaApi) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_hero_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is HeroItem) return
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.hero_title).text = item.scene.displayTitle
        view.findViewById<TextView>(R.id.hero_meta).text =
            "${item.scene.formatDuration()}  •  ${item.scene.studio?.name ?: item.scene.filename}"
        view.findViewById<TextView>(R.id.hero_action).text = HeroCard.actionLabel(item.resumeMs)
        val image = view.findViewById<ImageView>(R.id.hero_image)
        Glide.with(view.context)
            .load(GlideAuth.url(api, api.getScreenshotUrl(item.scene.id)))
            .centerCrop()
            .into(image)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.findViewById<ImageView>(R.id.hero_image).setImageDrawable(null)
    }
}
