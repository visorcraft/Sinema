package com.sinema.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.model.EntityItem
import com.sinema.model.FolderItem
import com.sinema.model.GalleryItem
import com.sinema.model.GroupItem
import com.sinema.model.HomeCardSize
import com.sinema.model.ImageItem
import com.sinema.model.Scene
import com.sinema.util.DiscreetMode
import com.sinema.util.GlideAuth

class CardPresenter(
    private val api: SinemaApi,
    private val cardSize: HomeCardSize = HomeCardSize.COMPACT,
    private val onItemLongClick: ((Any) -> Unit)? = null
) : Presenter() {
    companion object {
        /**
         * Bumped whenever discreet mode flips. Screens compare their last
         * seen generation on resume and rebind visible cards so already-bound
         * rows switch to/from neutral display without a data reload.
         */
        @Volatile var discreetGeneration: Int = 0
    }

    private class ContentHolder(view: View) : ViewHolder(view) {
        var item: Any? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        val density = parent.resources.displayMetrics.density
        view.layoutParams.width = (cardSize.widthDp * density).toInt()
        view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.minimumHeight = (cardSize.heightDp * density).toInt()
        val artworkFrame = view.findViewById<View>(R.id.card_artwork_frame)
            ?: (view.findViewById<ImageView>(R.id.card_image).parent as View)
        artworkFrame.layoutParams.height = (cardSize.imageHeightDp * density).toInt()
        TvMotion.clipRoundedCornersPx(
            artworkFrame,
            view.resources.getDimension(R.dimen.card_polish_artwork_radius)
        )
        return ContentHolder(view).also { holder ->
            view.tag = holder
            view.setOnFocusChangeListener { v, hasFocus ->
                TvMotion.applyFocusMotion(v, TvMotion.FocusRole.CARD, hasFocus)
                CardVisuals.applyCardFocusTextStyle(v, hasFocus)
                if (SinemaApp.instance.prefs.discreetMode) {
                    holder.item?.let { onBindViewHolder(holder, it) }
                }
            }
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = viewHolder as ContentHolder
        holder.item = item
        val view = holder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<TextView>(R.id.card_heart)
        val checkmark = view.findViewById<TextView>(R.id.card_checkmark)
        val progress = view.findViewById<ProgressBar>(R.id.card_progress)
        val scrim = view.findViewById<View?>(R.id.card_artwork_scrim)
        val durationBadge = view.findViewById<TextView?>(R.id.card_duration_badge)
        val qualityBadge = view.findViewById<TextView?>(R.id.card_quality_badge)

        TvMotion.applyFocusMotion(view, TvMotion.FocusRole.CARD, view.hasFocus(), animate = false)
        CardVisuals.applyCardFocusTextStyle(view, view.hasFocus())

        view.setOnLongClickListener(
            onItemLongClick?.let { hook ->
                View.OnLongClickListener { _ -> item?.let(hook); true }
            }
        )

        val identity = item?.let(::discreetIdentity)
        if (SinemaApp.instance.prefs.discreetMode && !view.hasFocus() && identity != null) {
            Glide.with(view.context.applicationContext).clear(image)
            CardVisuals.clearArtworkSurface(image)
            image.setImageDrawable(null)
            image.visibility = View.INVISIBLE
            title.text = DiscreetMode.neutralTitle(identity.first, identity.second)
            content.text = "•••"
            hideAllOverlays(heart, checkmark, progress, scrim, durationBadge, qualityBadge)
            return
        }

        image.visibility = View.VISIBLE
        hideAllOverlays(heart, checkmark, progress, scrim, durationBadge, qualityBadge)
        when (item) {
            is Scene -> {
                title.text = item.displayTitle
                content.text = CardVisuals.sceneMetadataLine(item)
                heart.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                checkmark.visibility = if (item.isWatched) View.VISIBLE else View.GONE

                val durationText = CardVisuals.sceneDurationBadge(item)
                if (durationBadge != null && durationText != null) {
                    durationBadge.text = durationText
                    durationBadge.visibility = View.VISIBLE
                }

                val qualityText = CardVisuals.sceneQualityBadge(item)
                if (qualityBadge != null && qualityText != null) {
                    qualityBadge.text = qualityText
                    qualityBadge.visibility = View.VISIBLE
                }

                val percent = item.watchProgressPercent
                if (percent > 0) {
                    progress.progress = percent
                    progress.contentDescription = view.context.getString(
                        R.string.accessibility_watch_progress, percent
                    )
                    progress.visibility = View.VISIBLE
                }
                applyBottomBadgeClearance(view, hasProgress = percent > 0, checkmark, durationBadge)

                val hasBottomOverlay = percent > 0 || item.isWatched || durationText != null
                scrim?.visibility = if (hasBottomOverlay) View.VISIBLE else View.GONE

                loadAuthenticatedArtwork(image, api.getScreenshotUrl(item.id))
            }
            is HomeViewAll -> {
                title.text = item.actionLabel
                content.text = item.title
                Glide.with(view.context.applicationContext).clear(image)
                CardVisuals.applyPlaceholderSurface(
                    image,
                    android.R.drawable.ic_menu_more,
                    CardVisuals.SurfaceTone.ACCENT
                )
            }
            is GroupItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                if (item.frontImagePath != null) {
                    loadAuthenticatedArtwork(image, item.frontImagePath)
                } else {
                    Glide.with(view.context.applicationContext).clear(image)
                    CardVisuals.applyPlaceholderSurface(
                        image,
                        android.R.drawable.ic_menu_agenda,
                        CardVisuals.SurfaceTone.ACCENT
                    )
                }
            }
            is GalleryItem -> {
                title.text = item.displayTitle
                content.text = "${item.imageCount} " + if (item.imageCount == 1) "image" else "images"
                heart.visibility = if ((item.rating100 ?: 0) > 0) View.VISIBLE else View.GONE
                if (item.coverImageId != null) {
                    loadAuthenticatedArtwork(image, api.getImageThumbnailUrl(item.coverImageId))
                } else {
                    Glide.with(view.context.applicationContext).clear(image)
                    CardVisuals.applyPlaceholderSurface(
                        image,
                        android.R.drawable.ic_menu_gallery,
                        CardVisuals.SurfaceTone.ACCENT
                    )
                }
            }
            is EntityItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                heart.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                val imageUrl = item.imagePath ?: api.entityImageUrl(item.kind, item.id)
                loadAuthenticatedArtwork(image, imageUrl, errorRes = android.R.drawable.ic_menu_agenda)
            }
        }
    }

    private fun loadAuthenticatedArtwork(image: ImageView, url: String, errorRes: Int? = null) {
        CardVisuals.clearArtworkSurface(image)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val fadeMs = TvMotion.imageCrossfadeMs()
        val request = Glide.with(image.context.applicationContext)
            .load(GlideAuth.url(api, url))
            .centerCrop()
            .let { if (errorRes != null) it.error(errorRes) else it }
            .let { if (fadeMs > 0) it.transition(DrawableTransitionOptions.withCrossFade(fadeMs)) else it }
        request.into(image)
    }

    private fun hideAllOverlays(
        heart: View,
        checkmark: View,
        progress: ProgressBar,
        scrim: View?,
        durationBadge: TextView?,
        qualityBadge: TextView?
    ) {
        heart.visibility = View.GONE
        checkmark.visibility = View.GONE
        progress.visibility = View.GONE
        progress.contentDescription = null
        scrim?.visibility = View.GONE
        durationBadge?.apply {
            text = ""
            visibility = View.GONE
        }
        qualityBadge?.apply {
            text = ""
            visibility = View.GONE
        }
    }

    private fun applyBottomBadgeClearance(
        cardView: View,
        hasProgress: Boolean,
        vararg badges: View?
    ) {
        val bottomMargin = cardView.resources.getDimensionPixelSize(
            if (hasProgress) R.dimen.card_polish_badge_margin_above_progress
            else R.dimen.card_polish_badge_margin
        )
        badges.forEach { badge ->
            val lp = badge?.layoutParams as? FrameLayout.LayoutParams ?: return@forEach
            if (lp.bottomMargin != bottomMargin) {
                lp.bottomMargin = bottomMargin
                badge.layoutParams = lp
            }
        }
    }

    private fun discreetIdentity(item: Any): Pair<String, String>? = when (item) {
        is Scene -> "Scene" to item.id
        is GroupItem -> "Group" to item.id
        is GalleryItem -> "Gallery" to item.id
        is EntityItem -> "Entity" to item.id
        else -> null
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as ContentHolder
        holder.item = null
        val view = holder.view
        TvMotion.resetFocusMotion(view)
        view.clearFocus()
        CardVisuals.applyCardFocusTextStyle(view, focused = false)
        val image = view.findViewById<ImageView>(R.id.card_image)
        Glide.with(view.context.applicationContext).clear(image)
        CardVisuals.clearArtworkSurface(image)
        image.setImageDrawable(null)
        image.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.card_title).text = ""
        view.findViewById<TextView>(R.id.card_content).text = ""
        val checkmark = view.findViewById<View>(R.id.card_checkmark)
        val durationBadge = view.findViewById<TextView?>(R.id.card_duration_badge)
        hideAllOverlays(
            view.findViewById(R.id.card_heart),
            checkmark,
            view.findViewById(R.id.card_progress),
            view.findViewById(R.id.card_artwork_scrim),
            durationBadge,
            view.findViewById(R.id.card_quality_badge)
        )
        applyBottomBadgeClearance(view, hasProgress = false, checkmark, durationBadge)
    }
}

class FolderCardPresenter : Presenter() {
    private class FolderHolder(view: View) : ViewHolder(view) {
        var item: Any? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        view.findViewById<View?>(R.id.card_artwork_frame)?.let {
            TvMotion.clipRoundedCornersPx(it, view.resources.getDimension(R.dimen.card_polish_artwork_radius))
        }
        return FolderHolder(view).also { holder ->
            view.setOnFocusChangeListener { v, hasFocus ->
                TvMotion.applyFocusMotion(v, TvMotion.FocusRole.CARD, hasFocus)
                CardVisuals.applyCardFocusTextStyle(v, hasFocus)
                if (SinemaApp.instance.prefs.discreetMode) {
                    holder.item?.let { onBindViewHolder(holder, it) }
                }
            }
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = viewHolder as FolderHolder
        holder.item = item
        val view = holder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<ImageView>(R.id.card_heart)

        TvMotion.applyFocusMotion(view, TvMotion.FocusRole.CARD, view.hasFocus(), animate = false)
        CardVisuals.applyCardFocusTextStyle(view, view.hasFocus())

        val identity = when (item) {
            is FolderItem -> (if (item.isFolder) "Folder" else if (item.scene != null) "Scene" else "Image") to
                item.fullPath.ifBlank { item.name }
            is ImageItem -> "Image" to item.id
            else -> null
        }
        if (SinemaApp.instance.prefs.discreetMode && !view.hasFocus() && identity != null) {
            Glide.with(view.context.applicationContext).clear(image)
            CardVisuals.clearArtworkSurface(image)
            image.setImageDrawable(null)
            image.visibility = View.INVISIBLE
            title.text = DiscreetMode.neutralTitle(identity.first, identity.second)
            content.text = "•••"
            heart.visibility = View.GONE
            return
        }

        image.visibility = View.VISIBLE
        heart.visibility = View.GONE
        content.text = ""
        Glide.with(view.context.applicationContext).clear(image)
        CardVisuals.clearArtworkSurface(image)
        image.setImageDrawable(null)
        when (item) {
            is FolderItem -> {
                heart.visibility = if (item.hasFavorites) View.VISIBLE else View.GONE
                if (item.isFolder) {
                    title.text = "\uD83D\uDCC1 ${item.name}"
                    val itemWord = if (item.childCount == 1) "item" else "items"
                    content.text = "${item.childCount} $itemWord"
                    val thumbUrl = when {
                        item.firstSceneId != null -> SinemaApp.instance.api.getScreenshotUrl(item.firstSceneId)
                        item.firstImageId != null -> SinemaApp.instance.api.getImageThumbnailUrl(item.firstImageId)
                        else -> null
                    }
                    if (thumbUrl != null) {
                        loadArtwork(image, thumbUrl)
                    } else {
                        CardVisuals.applyPlaceholderSurface(
                            image,
                            android.R.drawable.ic_menu_agenda,
                            CardVisuals.SurfaceTone.MUTED
                        )
                    }
                } else {
                    title.text = item.name
                    val thumbUrl = when {
                        item.image != null -> {
                            content.text = if (item.image.width > 0 && item.image.height > 0)
                                "${item.image.width} × ${item.image.height}" else "Picture"
                            SinemaApp.instance.api.getImageThumbnailUrl(item.image.id)
                        }
                        item.scene != null -> {
                            content.text = item.scene.formatDuration()
                            SinemaApp.instance.api.getScreenshotUrl(item.scene.id)
                        }
                        else -> null
                    }
                    thumbUrl?.let { loadArtwork(image, it) }
                }
            }
            is ImageItem -> {
                title.text = item.title.ifBlank { item.filename }
                content.text = if (item.width > 0 && item.height > 0)
                    "${item.width} × ${item.height}" else "Picture"
                heart.visibility = if ((item.rating100 ?: 0) > 0) View.VISIBLE else View.GONE
                val api = SinemaApp.instance.api
                loadArtwork(image, api.getImageThumbnailUrl(item.id))
            }
        }
    }

    private fun loadArtwork(image: ImageView, url: String) {
        CardVisuals.clearArtworkSurface(image)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val fadeMs = TvMotion.imageCrossfadeMs()
        val request = Glide.with(image.context.applicationContext)
            .load(GlideAuth.url(SinemaApp.instance.api, url))
            .centerCrop()
            .let { if (fadeMs > 0) it.transition(DrawableTransitionOptions.withCrossFade(fadeMs)) else it }
        request.into(image)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as FolderHolder
        holder.item = null
        val view = holder.view
        TvMotion.resetFocusMotion(view)
        view.clearFocus()
        CardVisuals.applyCardFocusTextStyle(view, focused = false)
        val image = view.findViewById<ImageView>(R.id.card_image)
        Glide.with(view.context.applicationContext).clear(image)
        CardVisuals.clearArtworkSurface(image)
        image.setImageDrawable(null)
        image.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.card_title).text = ""
        view.findViewById<TextView>(R.id.card_content).text = ""
        view.findViewById<View>(R.id.card_heart).visibility = View.GONE
    }
}

class SettingsPresenter(private val onUp: (() -> Unit)? = null) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        view.findViewById<View?>(R.id.card_artwork_frame)?.let {
            TvMotion.clipRoundedCornersPx(it, view.resources.getDimension(R.dimen.card_polish_artwork_radius))
        }
        view.setOnFocusChangeListener { v, hasFocus ->
            TvMotion.applyFocusMotion(v, TvMotion.FocusRole.CARD, hasFocus)
            CardVisuals.applyCardFocusTextStyle(v, hasFocus)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view
        TvMotion.applyFocusMotion(view, TvMotion.FocusRole.CARD, view.hasFocus(), animate = false)
        CardVisuals.applyCardFocusTextStyle(view, view.hasFocus())

        if (onUp != null) {
            view.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                ) {
                    onUp.invoke()
                    true
                } else {
                    false
                }
            }
        } else {
            view.setOnKeyListener(null)
        }
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val image = view.findViewById<ImageView>(R.id.card_image)
        val ctx = view.context
        val key = item as? String
        // Fixed cards added with localized labels (Library/Home entries) must
        // match by resource, never by literal string identity.
        fun res(id: Int) = ctx.getString(id)
        val (iconRes, tone) = when (key) {
            res(R.string.shortcut_tonight_mix) -> {
                title.text = res(R.string.tonight_mix_card_title)
                content.text = res(R.string.tonight_mix_card_sub)
                android.R.drawable.ic_menu_day to CardVisuals.SurfaceTone.WARM
            }
            res(R.string.library_card_finish_watching) -> {
                title.text = res(R.string.library_card_finish_watching)
                content.text = res(R.string.library_card_finish_watching_sub)
                android.R.drawable.ic_media_ff to CardVisuals.SurfaceTone.ACCENT
            }
            res(R.string.library_card_untagged) -> {
                title.text = res(R.string.library_card_untagged)
                content.text = res(R.string.library_card_untagged_sub)
                android.R.drawable.ic_menu_edit to CardVisuals.SurfaceTone.MUTED
            }
            res(R.string.library_card_server) -> {
                title.text = res(R.string.library_card_server)
                content.text = res(R.string.library_card_server_sub)
                android.R.drawable.ic_menu_info_details to CardVisuals.SurfaceTone.MUTED
            }
            "Retry Connection" -> {
                title.text = res(R.string.retry_connection)
                content.text = res(R.string.retry_connection_sub)
                android.R.drawable.ic_menu_rotate to CardVisuals.SurfaceTone.RETRY
            }
            "Search" -> {
                title.text = "🔍 Search"
                content.text = "Find scenes"
                android.R.drawable.ic_menu_search to CardVisuals.shortcutToneFor(key)
            }
            "Surprise Me" -> {
                title.text = "🎲 Surprise Me"
                content.text = "Play a random scene"
                android.R.drawable.ic_media_play to CardVisuals.shortcutToneFor(key)
            }
            "Refresh" -> {
                title.text = "🔄 Refresh"
                content.text = "Reload content"
                android.R.drawable.ic_popup_sync to CardVisuals.shortcutToneFor(key)
            }
            "About" -> {
                title.text = "ℹ️ About"
                content.text = "Version info"
                android.R.drawable.ic_menu_info_details to CardVisuals.shortcutToneFor(key)
            }
            "Favorites" -> {
                title.text = "❤️ Favorites"
                content.text = "Rated scenes"
                android.R.drawable.btn_star_big_on to CardVisuals.shortcutToneFor(key)
            }
            "Browse Folders" -> {
                title.text = "📁 Browse Folders"
                content.text = "All folders"
                android.R.drawable.ic_menu_agenda to CardVisuals.shortcutToneFor(key)
            }
            "Groups" -> {
                title.text = "🎞️ Groups"
                content.text = "Collections of scenes"
                android.R.drawable.ic_menu_agenda to CardVisuals.shortcutToneFor(key)
            }
            "Galleries" -> {
                title.text = "🖼️ Galleries"
                content.text = "Collections of images"
                android.R.drawable.ic_menu_gallery to CardVisuals.shortcutToneFor(key)
            }
            "Library" -> {
                title.text = "🎬 Library"
                content.text = "Browse your collection"
                android.R.drawable.ic_menu_agenda to CardVisuals.shortcutToneFor(key)
            }
            "Log Out" -> {
                title.text = "🔒 Log Out"
                content.text = "Lock app"
                android.R.drawable.ic_lock_lock to CardVisuals.shortcutToneFor(key)
            }
            "Tags" -> {
                title.text = "🏷️ Tags"
                content.text = "Browse by tag"
                android.R.drawable.ic_menu_agenda to CardVisuals.shortcutToneFor(key)
            }
            "Performers" -> {
                title.text = "👤 Performers"
                content.text = "Browse by performer"
                android.R.drawable.ic_menu_agenda to CardVisuals.shortcutToneFor(key)
            }
            "Studios" -> {
                title.text = "🎬 Studios"
                content.text = "Browse by studio"
                android.R.drawable.ic_menu_agenda to CardVisuals.shortcutToneFor(key)
            }
            "Saved Filters" -> {
                title.text = "⭐ Saved Filters"
                content.text = "Filters saved in Stash"
                android.R.drawable.ic_menu_sort_by_size to CardVisuals.shortcutToneFor(key)
            }
            "Markers" -> {
                title.text = "📑 Markers"
                content.text = "Browse all chapters"
                android.R.drawable.ic_menu_compass to CardVisuals.shortcutToneFor(key)
            }
            "Stats" -> {
                title.text = "📊 Stats"
                content.text = "Library and watch stats"
                android.R.drawable.ic_menu_sort_by_size to CardVisuals.shortcutToneFor(key)
            }
            else -> {
                title.text = "⚙️ Settings"
                content.text = "Server configuration"
                android.R.drawable.ic_menu_preferences to CardVisuals.SurfaceTone.MUTED
            }
        }
        CardVisuals.applyPlaceholderSurface(image, iconRes, tone)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val view = viewHolder.view
        TvMotion.resetFocusMotion(view)
        view.setOnKeyListener(null)
    }
}
