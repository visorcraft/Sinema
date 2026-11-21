package com.sinema.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.sinema.R
import com.sinema.model.Scene
import com.sinema.model.StarRating

/**
 * Pure formatting and visual-surface helpers shared by home cards, shortcuts,
 * and quick-command chips.
 */
internal object CardVisuals {
    enum class SurfaceTone(
        @get:DrawableRes val backgroundRes: Int,
        @get:ColorRes val iconTintRes: Int
    ) {
        ACCENT(R.drawable.card_shortcut_surface, R.color.card_polish_accent_soft),
        WARM(R.drawable.card_shortcut_warm_surface, R.color.home_polish_command_logout_focused),
        MUTED(R.drawable.card_placeholder_surface, R.color.home_polish_command_icon_unfocused),
        RETRY(R.drawable.card_retry_surface, R.color.home_polish_command_logout_focused)
    }

    /** Duration badge overlaid on scene thumbnails when runtime is known. */
    fun sceneDurationBadge(scene: Scene): String? =
        if (scene.duration > 0.0) scene.formatDuration() else null

    /** Quality badge (4K / 1440p / 1080p / 720p) when scene dimensions are HD+. */
    fun sceneQualityBadge(scene: Scene): String? =
        HeroCard.qualityBadge(scene.width, scene.height)

    /**
     * Concise, scannable subtitle line for scene cards. Surfaces star rating,
     * watch progress / remaining time or watched state, studio or duration
     * fallback, and O-counter without cluttering a single TV line.
     */
    fun sceneMetadataLine(scene: Scene): String = buildList {
        if (scene.starCount > 0) {
            add(StarRating.glyphs(scene.starCount))
        }
        val percent = scene.watchProgressPercent
        val resumeMs = (scene.resumeTime * 1000.0).toLong()
        when {
            percent > 0 -> {
                val remaining = HeroCard.remainingLabel(scene, resumeMs)
                add(if (remaining != null) "$percent% · $remaining" else "$percent%")
            }
            scene.isWatched -> add("Watched")
            !scene.studio?.name.isNullOrBlank() -> add(scene.studio!!.name.trim())
            scene.duration > 0.0 -> add(scene.formatDuration())
        }
        if (scene.oCounter > 0) {
            add("O×${scene.oCounter}")
        }
    }.joinToString("  •  ")

    /** Curated dark gradient surface and icon tint for home shortcut/placeholder cards. */
    fun shortcutToneFor(itemKey: String?): SurfaceTone = when (itemKey) {
        "Retry Connection", "Log Out" -> SurfaceTone.RETRY
        "Favorites", "Surprise Me" -> SurfaceTone.WARM
        "Search", "Library", "Groups", "Galleries", "Browse Folders", "View All" -> SurfaceTone.ACCENT
        else -> SurfaceTone.MUTED
    }

    /** Distinct icon tint for quick-action command pills (warm coral for Log Out on focus). */
    @ColorRes
    fun commandIconTintRes(command: String, focused: Boolean): Int = when {
        !focused -> R.color.home_polish_command_icon_unfocused
        command == HomeCommandRow.LOG_OUT -> R.color.home_polish_command_logout_focused
        else -> R.color.card_polish_accent_soft
    }

    fun applyPlaceholderSurface(image: ImageView, @DrawableRes iconRes: Int, tone: SurfaceTone) {
        image.setImageResource(iconRes)
        image.scaleType = ImageView.ScaleType.CENTER
        image.setBackgroundResource(tone.backgroundRes)
        ImageViewCompat.setImageTintList(
            image,
            ColorStateList.valueOf(ContextCompat.getColor(image.context, tone.iconTintRes))
        )
    }

    fun clearArtworkSurface(image: ImageView) {
        ImageViewCompat.setImageTintList(image, null)
        image.setBackgroundColor(0xFF262D33.toInt())
    }

    fun applyCardFocusTextStyle(view: View, focused: Boolean) {
        val ctx = view.context
        view.findViewById<TextView>(R.id.card_title)?.apply {
            setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (focused) R.color.card_polish_title_focused else R.color.card_polish_title_unfocused
                )
            )
            setTypeface(null, if (focused) Typeface.BOLD else Typeface.NORMAL)
        }
        view.findViewById<TextView>(R.id.card_content)?.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (focused) R.color.card_polish_meta_focused else R.color.card_polish_meta_unfocused
            )
        )
    }
}
