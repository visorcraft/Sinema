package com.sinema.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
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
import com.sinema.model.StarRating
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

    /** Holds the last-bound scene so discreet mode can re-render on focus. */
    private class SceneHolder(view: View) : ViewHolder(view) {
        var scene: Scene? = null
        var focused: Boolean = false
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        val density = parent.resources.displayMetrics.density
        view.layoutParams.width = (cardSize.widthDp * density).toInt()
        view.layoutParams.height = (cardSize.heightDp * density).toInt()
        (view.findViewById<ImageView>(R.id.card_image).parent as View).layoutParams.height =
            (cardSize.imageHeightDp * density).toInt()
        return SceneHolder(view).also { holder ->
            view.tag = holder
            view.setOnFocusChangeListener { _, hasFocus ->
                holder.focused = hasFocus
                // No-op for holders currently bound to non-scene items.
                if (holder.scene != null) applySceneDisplay(holder)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        // Reset holder state every bind: recycled views must not leak the
        // previous scene or its focus state into the new item.
        (viewHolder as? SceneHolder)?.let { holder ->
            holder.scene = item as? Scene
            holder.focused = view.hasFocus()
        }
        // Restored per bind; the scene branch may hide it again for discreet.
        image.visibility = View.VISIBLE
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<TextView>(R.id.card_heart)
        val checkmark = view.findViewById<TextView>(R.id.card_checkmark)
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.card_progress)
        // Long-press OK on a focused card opens per-card actions (grids only).
        // Lambda parameter must not be named — it would shadow the bound item.
        view.setOnLongClickListener(
            onItemLongClick?.let { hook ->
                android.view.View.OnLongClickListener { _ -> item?.let(hook); true }
            }
        )
        when (item) {
            is Scene -> {
                val url = api.getScreenshotUrl(item.id)
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(0xFF333333.toInt())
                Glide.with(view.context)
                    .load(GlideAuth.url(api, url))
                    .centerCrop()
                    .into(image)
                val holder = viewHolder as? SceneHolder ?: return
                holder.scene = item
                applySceneDisplay(holder)
            }
            is HomeViewAll -> {
                title.text = item.actionLabel
                content.text = item.title
                heart?.visibility = View.GONE
                checkmark?.visibility = View.GONE
                progress?.visibility = View.GONE
                Glide.with(view.context).clear(image)
                image.setImageResource(android.R.drawable.ic_menu_more)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            is GroupItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                heart?.visibility = View.GONE
                checkmark?.visibility = View.GONE
                progress?.visibility = View.GONE
                if (item.frontImagePath != null) {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.setBackgroundColor(0xFF333333.toInt())
                    Glide.with(view.context)
                        .load(GlideAuth.url(api, item.frontImagePath))
                        .centerCrop()
                        .into(image)
                } else {
                    Glide.with(view.context).clear(image)
                    image.setImageResource(android.R.drawable.ic_menu_agenda)
                    image.scaleType = ImageView.ScaleType.CENTER
                    image.setBackgroundColor(0xFF444444.toInt())
                }
            }
            is GalleryItem -> {
                title.text = item.displayTitle
                content.text = "${item.imageCount} " + if (item.imageCount == 1) "image" else "images"
                heart?.visibility = if ((item.rating100 ?: 0) > 0) View.VISIBLE else View.GONE
                checkmark?.visibility = View.GONE
                progress?.visibility = View.GONE
                if (item.coverImageId != null) {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.setBackgroundColor(0xFF333333.toInt())
                    Glide.with(view.context)
                        .load(GlideAuth.url(api, api.getImageThumbnailUrl(item.coverImageId)))
                        .centerCrop()
                        .into(image)
                } else {
                    Glide.with(view.context).clear(image)
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                    image.scaleType = ImageView.ScaleType.CENTER
                    image.setBackgroundColor(0xFF444444.toInt())
                }
            }
            is EntityItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                heart?.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                checkmark?.visibility = View.GONE
                progress?.visibility = View.GONE
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(0xFF333333.toInt())
                // Prefer the list payload's image_path; newer Stash schemas
                // can omit it, so fall back to the direct image endpoint.
                val imageUrl = item.imagePath ?: api.entityImageUrl(item.kind, item.id)
                Glide.with(view.context)
                    .load(GlideAuth.url(api, imageUrl))
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_agenda)
                    .into(image)
            }
        }
    }

    /**
     * Scene text/overlays/artwork in one place so discreet mode can re-render
     * on focus change: unfocused cards show a neutral label, no overlays, and
     * hidden artwork; focused cards reveal everything.
     */
    private fun applySceneDisplay(holder: SceneHolder) {
        val scene = holder.scene ?: return
        val view = holder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<TextView>(R.id.card_heart)
        val checkmark = view.findViewById<TextView>(R.id.card_checkmark)
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.card_progress)
        val hide = com.sinema.SinemaApp.instance.prefs.discreetMode && !holder.focused
        if (hide) {
            title.text = DiscreetMode.neutralTitle(scene.id)
            content.text = "•••"
            heart?.visibility = View.GONE
            checkmark?.visibility = View.GONE
            progress?.visibility = View.GONE
            image.visibility = View.INVISIBLE
            return
        }
        title.text = scene.displayTitle
        content.text = buildString {
            if (scene.starCount > 0) {
                append(StarRating.glyphs(scene.starCount))
                append("  ")
            }
            append(scene.formatDuration())
            if (scene.oCounter > 0) {
                append("  O×")
                append(scene.oCounter)
            }
        }
        heart?.visibility = if (scene.isFavorite) View.VISIBLE else View.GONE
        checkmark?.visibility = if (scene.isWatched) View.VISIBLE else View.GONE
        val percent = scene.watchProgressPercent
        progress?.let { bar ->
            if (percent > 0) {
                bar.progress = percent
                bar.contentDescription = view.context.getString(R.string.accessibility_watch_progress, percent)
                bar.visibility = View.VISIBLE
            } else {
                bar.visibility = View.GONE
            }
        }
        image.visibility = View.VISIBLE
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder as? SceneHolder)?.scene = null
        val image = viewHolder.view.findViewById<ImageView>(R.id.card_image)
        image.setImageDrawable(null)
    }
}

class FolderCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<ImageView>(R.id.card_heart)
        when (item) {
            is FolderItem -> {
                // Show/hide heart
                heart?.visibility = if (item.hasFavorites) View.VISIBLE else View.GONE

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
                        image.scaleType = ImageView.ScaleType.CENTER_CROP
                        image.setBackgroundColor(0xFF333333.toInt())
                        Glide.with(view.context)
                            .load(GlideAuth.url(SinemaApp.instance.api, thumbUrl))
                            .centerCrop()
                            .into(image)
                    } else {
                        Glide.with(view.context).clear(image)
                        image.setImageResource(android.R.drawable.ic_menu_agenda)
                        image.scaleType = ImageView.ScaleType.CENTER
                        image.setBackgroundColor(0xFF444444.toInt())
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
                        else -> {
                            content.text = ""
                            null
                        }
                    }
                    if (thumbUrl != null) {
                        image.scaleType = ImageView.ScaleType.CENTER_CROP
                        image.setBackgroundColor(0xFF333333.toInt())
                        Glide.with(view.context)
                            .load(GlideAuth.url(SinemaApp.instance.api, thumbUrl))
                            .centerCrop()
                            .into(image)
                    }
                }
            }
            is ImageItem -> {
                title.text = item.title.ifBlank { item.filename }
                content.text = if (item.width > 0 && item.height > 0)
                    "${item.width} × ${item.height}" else "Picture"
                heart?.visibility = if ((item.rating100 ?: 0) > 0) View.VISIBLE else View.GONE
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(0xFF333333.toInt())
                val api = SinemaApp.instance.api
                Glide.with(view.context)
                    .load(GlideAuth.url(api, api.getImageThumbnailUrl(item.id)))
                    .centerCrop()
                    .into(image)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val image = viewHolder.view.findViewById<ImageView>(R.id.card_image)
        image.setImageDrawable(null)
    }
}

class SettingsPresenter(private val onUp: (() -> Unit)? = null) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
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
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val title = viewHolder.view.findViewById<TextView>(com.sinema.R.id.card_title)
        val content = viewHolder.view.findViewById<TextView>(com.sinema.R.id.card_content)
        val image = viewHolder.view.findViewById<ImageView>(com.sinema.R.id.card_image)
        val ctx = viewHolder.view.context
        // Fixed cards added with localized labels (Library/Home entries) must
        // match by resource, never by literal string identity.
        fun res(id: Int) = ctx.getString(id)
        when (item as? String) {
            res(com.sinema.R.string.shortcut_tonight_mix) -> {
                title.text = res(com.sinema.R.string.tonight_mix_card_title)
                content.text = res(com.sinema.R.string.tonight_mix_card_sub)
                image.setImageResource(android.R.drawable.ic_menu_day)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            res(com.sinema.R.string.library_card_finish_watching) -> {
                title.text = res(com.sinema.R.string.library_card_finish_watching)
                content.text = res(com.sinema.R.string.library_card_finish_watching_sub)
                image.setImageResource(android.R.drawable.ic_media_ff)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            res(com.sinema.R.string.library_card_untagged) -> {
                title.text = res(com.sinema.R.string.library_card_untagged)
                content.text = res(com.sinema.R.string.library_card_untagged_sub)
                image.setImageResource(android.R.drawable.ic_menu_edit)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            res(com.sinema.R.string.library_card_server) -> {
                title.text = res(com.sinema.R.string.library_card_server)
                content.text = res(com.sinema.R.string.library_card_server_sub)
                image.setImageResource(android.R.drawable.ic_menu_info_details)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Retry Connection" -> {
                title.text = res(com.sinema.R.string.retry_connection)
                content.text = res(com.sinema.R.string.retry_connection_sub)
                image.setImageResource(android.R.drawable.ic_menu_rotate)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Search" -> {
                title.text = "🔍 Search"
                content.text = "Find scenes"
                image.setImageResource(android.R.drawable.ic_menu_search)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Surprise Me" -> {
                title.text = "🎲 Surprise Me"
                content.text = "Play a random scene"
                image.setImageResource(android.R.drawable.ic_media_play)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Refresh" -> {
                title.text = "🔄 Refresh"
                content.text = "Reload content"
                image.setImageResource(android.R.drawable.ic_popup_sync)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "About" -> {
                title.text = "ℹ️ About"
                content.text = "Version info"
                image.setImageResource(android.R.drawable.ic_menu_info_details)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Favorites" -> {
                title.text = "❤️ Favorites"
                content.text = "Rated scenes"
                image.setImageResource(android.R.drawable.btn_star_big_on)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Browse Folders" -> {
                title.text = "📁 Browse Folders"
                content.text = "All folders"
                image.setImageResource(android.R.drawable.ic_menu_agenda)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Groups" -> {
                title.text = "🎞️ Groups"
                content.text = "Collections of scenes"
                image.setImageResource(android.R.drawable.ic_menu_agenda)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Galleries" -> {
                title.text = "🖼️ Galleries"
                content.text = "Collections of images"
                image.setImageResource(android.R.drawable.ic_menu_gallery)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Library" -> {
                title.text = "🎬 Library"
                content.text = "Browse your collection"
                image.setImageResource(android.R.drawable.ic_menu_agenda)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Log Out" -> {
                title.text = "🔒 Log Out"
                content.text = "Lock app"
                image.setImageResource(android.R.drawable.ic_lock_lock)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Tags" -> {
                title.text = "🏷️ Tags"
                content.text = "Browse by tag"
                image.setImageResource(android.R.drawable.ic_menu_agenda)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Performers" -> {
                title.text = "👤 Performers"
                content.text = "Browse by performer"
                image.setImageResource(android.R.drawable.ic_menu_agenda)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Studios" -> {
                title.text = "🎬 Studios"
                content.text = "Browse by studio"
                image.setImageResource(android.R.drawable.ic_menu_agenda)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Saved Filters" -> {
                title.text = "⭐ Saved Filters"
                content.text = "Filters saved in Stash"
                image.setImageResource(android.R.drawable.ic_menu_sort_by_size)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Markers" -> {
                title.text = "📑 Markers"
                content.text = "Browse all chapters"
                image.setImageResource(android.R.drawable.ic_menu_compass)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            "Stats" -> {
                title.text = "📊 Stats"
                content.text = "Library and watch stats"
                image.setImageResource(android.R.drawable.ic_menu_sort_by_size)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            else -> {
                title.text = "⚙️ Settings"
                content.text = "Server configuration"
                image.setImageResource(android.R.drawable.ic_menu_preferences)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.setOnKeyListener(null)
    }
}
