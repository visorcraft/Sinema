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

    private class ContentHolder(view: View) : ViewHolder(view) {
        var item: Any? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        val density = parent.resources.displayMetrics.density
        view.layoutParams.width = (cardSize.widthDp * density).toInt()
        view.layoutParams.height = (cardSize.heightDp * density).toInt()
        (view.findViewById<ImageView>(R.id.card_image).parent as View).layoutParams.height =
            (cardSize.imageHeightDp * density).toInt()
        return ContentHolder(view).also { holder ->
            view.tag = holder
            view.setOnFocusChangeListener { _, _ ->
                holder.item?.let { onBindViewHolder(holder, it) }
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
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.card_progress)
        view.setOnLongClickListener(
            onItemLongClick?.let { hook ->
                android.view.View.OnLongClickListener { _ -> item?.let(hook); true }
            }
        )

        val identity = item?.let(::discreetIdentity)
        if (SinemaApp.instance.prefs.discreetMode && !view.hasFocus() && identity != null) {
            Glide.with(view.context.applicationContext).clear(image)
            image.setImageDrawable(null)
            image.visibility = View.INVISIBLE
            title.text = DiscreetMode.neutralTitle(identity.first, identity.second)
            content.text = "•••"
            heart.visibility = View.GONE
            checkmark.visibility = View.GONE
            progress.visibility = View.GONE
            progress.contentDescription = null
            return
        }

        image.visibility = View.VISIBLE
        heart.visibility = View.GONE
        checkmark.visibility = View.GONE
        progress.visibility = View.GONE
        progress.contentDescription = null
        when (item) {
            is Scene -> {
                title.text = item.displayTitle
                content.text = buildString {
                    if (item.starCount > 0) {
                        append(StarRating.glyphs(item.starCount))
                        append("  ")
                    }
                    append(item.formatDuration())
                    if (item.oCounter > 0) {
                        append("  O×")
                        append(item.oCounter)
                    }
                }
                heart.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                checkmark.visibility = if (item.isWatched) View.VISIBLE else View.GONE
                val percent = item.watchProgressPercent
                if (percent > 0) {
                    progress.progress = percent
                    progress.contentDescription = view.context.getString(
                        R.string.accessibility_watch_progress, percent
                    )
                    progress.visibility = View.VISIBLE
                }
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(0xFF333333.toInt())
                Glide.with(view.context.applicationContext)
                    .load(GlideAuth.url(api, api.getScreenshotUrl(item.id)))
                    .centerCrop()
                    .into(image)
            }
            is HomeViewAll -> {
                title.text = item.actionLabel
                content.text = item.title
                Glide.with(view.context.applicationContext).clear(image)
                image.setImageResource(android.R.drawable.ic_menu_more)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setBackgroundColor(0xFF444444.toInt())
            }
            is GroupItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                if (item.frontImagePath != null) {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.setBackgroundColor(0xFF333333.toInt())
                    Glide.with(view.context.applicationContext)
                        .load(GlideAuth.url(api, item.frontImagePath))
                        .centerCrop()
                        .into(image)
                } else {
                    Glide.with(view.context.applicationContext).clear(image)
                    image.setImageResource(android.R.drawable.ic_menu_agenda)
                    image.scaleType = ImageView.ScaleType.CENTER
                    image.setBackgroundColor(0xFF444444.toInt())
                }
            }
            is GalleryItem -> {
                title.text = item.displayTitle
                content.text = "${item.imageCount} " + if (item.imageCount == 1) "image" else "images"
                heart.visibility = if ((item.rating100 ?: 0) > 0) View.VISIBLE else View.GONE
                if (item.coverImageId != null) {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.setBackgroundColor(0xFF333333.toInt())
                    Glide.with(view.context.applicationContext)
                        .load(GlideAuth.url(api, api.getImageThumbnailUrl(item.coverImageId)))
                        .centerCrop()
                        .into(image)
                } else {
                    Glide.with(view.context.applicationContext).clear(image)
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                    image.scaleType = ImageView.ScaleType.CENTER
                    image.setBackgroundColor(0xFF444444.toInt())
                }
            }
            is EntityItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                heart.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(0xFF333333.toInt())
                val imageUrl = item.imagePath ?: api.entityImageUrl(item.kind, item.id)
                Glide.with(view.context.applicationContext)
                    .load(GlideAuth.url(api, imageUrl))
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_agenda)
                    .into(image)
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
        view.clearFocus()
        val image = view.findViewById<ImageView>(R.id.card_image)
        Glide.with(view.context.applicationContext).clear(image)
        image.setImageDrawable(null)
        image.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.card_title).text = ""
        view.findViewById<TextView>(R.id.card_content).text = ""
        view.findViewById<View>(R.id.card_heart).visibility = View.GONE
        view.findViewById<View>(R.id.card_checkmark).visibility = View.GONE
        view.findViewById<android.widget.ProgressBar>(R.id.card_progress).apply {
            visibility = View.GONE
            contentDescription = null
        }
    }
}

class FolderCardPresenter : Presenter() {
    private class FolderHolder(view: View) : ViewHolder(view) {
        var item: Any? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return FolderHolder(view).also { holder ->
            view.setOnFocusChangeListener { _, _ ->
                holder.item?.let { onBindViewHolder(holder, it) }
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
        val identity = when (item) {
            is FolderItem -> (if (item.isFolder) "Folder" else if (item.scene != null) "Scene" else "Image") to
                item.fullPath.ifBlank { item.name }
            is ImageItem -> "Image" to item.id
            else -> null
        }
        if (SinemaApp.instance.prefs.discreetMode && !view.hasFocus() && identity != null) {
            Glide.with(view.context.applicationContext).clear(image)
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
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setBackgroundColor(0xFF333333.toInt())
        Glide.with(image.context.applicationContext)
            .load(GlideAuth.url(SinemaApp.instance.api, url))
            .centerCrop()
            .into(image)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as FolderHolder
        holder.item = null
        val view = holder.view
        view.clearFocus()
        val image = view.findViewById<ImageView>(R.id.card_image)
        Glide.with(view.context.applicationContext).clear(image)
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
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (onUp != null) {
            viewHolder.view.setOnKeyListener { _, keyCode, event ->
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
            viewHolder.view.setOnKeyListener(null)
        }
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
