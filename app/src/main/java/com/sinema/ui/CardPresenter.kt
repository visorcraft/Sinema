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
import com.sinema.model.HomeCardSize
import com.sinema.model.Scene
import com.sinema.model.StarRating
import com.sinema.util.GlideAuth

class CardPresenter(
    private val api: SinemaApi,
    private val cardSize: HomeCardSize = HomeCardSize.COMPACT
) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        val density = parent.resources.displayMetrics.density
        view.layoutParams.width = (cardSize.widthDp * density).toInt()
        view.layoutParams.height = (cardSize.heightDp * density).toInt()
        (view.findViewById<ImageView>(R.id.card_image).parent as View).layoutParams.height =
            (cardSize.imageHeightDp * density).toInt()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<TextView>(R.id.card_heart)
        val checkmark = view.findViewById<TextView>(R.id.card_checkmark)
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.card_progress)
        when (item) {
            is Scene -> {
                title.text = item.displayTitle
                content.text = if (item.starCount > 0)
                    "${StarRating.glyphs(item.starCount)}  ${item.formatDuration()}"
                else item.formatDuration()
                heart?.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                checkmark?.visibility = if (item.isWatched) View.VISIBLE else View.GONE
                val percent = item.watchProgressPercent
                progress?.let { bar ->
                    if (percent > 0) {
                        bar.progress = percent
                        bar.visibility = View.VISIBLE
                    } else {
                        bar.visibility = View.GONE
                    }
                }
                val url = api.getScreenshotUrl(item.id)
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setBackgroundColor(0xFF333333.toInt())
                Glide.with(view.context)
                    .load(GlideAuth.url(api, url))
                    .centerCrop()
                    .into(image)
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
            is EntityItem -> {
                title.text = item.name
                content.text = "${item.sceneCount} " + if (item.sceneCount == 1) "scene" else "scenes"
                heart?.visibility = View.GONE
                checkmark?.visibility = View.GONE
                progress?.visibility = View.GONE
                if (item.imagePath != null) {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.setBackgroundColor(0xFF333333.toInt())
                    Glide.with(view.context)
                        .load(GlideAuth.url(api, item.imagePath))
                        .centerCrop()
                        .into(image)
                } else {
                    Glide.with(view.context).clear(image)
                    image.setImageResource(android.R.drawable.ic_menu_agenda)
                    image.scaleType = ImageView.ScaleType.CENTER
                    image.setBackgroundColor(0xFF444444.toInt())
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
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
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val image = viewHolder.view.findViewById<ImageView>(R.id.card_image)
        image.setImageDrawable(null)
    }
}

class SettingsPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val title = viewHolder.view.findViewById<TextView>(R.id.card_title)
        val content = viewHolder.view.findViewById<TextView>(R.id.card_content)
        val image = viewHolder.view.findViewById<ImageView>(R.id.card_image)
        when (item as? String) {
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

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
