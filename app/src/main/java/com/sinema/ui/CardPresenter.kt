package com.sinema.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.model.FolderItem
import com.sinema.model.Scene

class CardPresenter(private val api: SinemaApi) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val view = viewHolder.view
        val image = view.findViewById<ImageView>(R.id.card_image)
        val title = view.findViewById<TextView>(R.id.card_title)
        val content = view.findViewById<TextView>(R.id.card_content)
        val heart = view.findViewById<TextView>(R.id.card_heart)
        val checkmark = view.findViewById<TextView>(R.id.card_checkmark)
        when (item) {
            is Scene -> {
                title.text = item.filename
                content.text = item.formatDuration()
                heart?.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
                checkmark?.visibility = if (item.isWatched) View.VISIBLE else View.GONE
                val url = api.getScreenshotUrl(item.id)
                val glideUrl = GlideUrl(url, LazyHeaders.Builder()
                    .addHeader("ApiKey", SinemaApp.instance.prefs.apiKey)
                    .build())
                Glide.with(view.context)
                    .load(glideUrl)
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

class FolderCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
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
                    if (item.firstSceneId != null) {
                        val url = SinemaApp.instance.api.getScreenshotUrl(item.firstSceneId)
                        val glideUrl = GlideUrl(url, LazyHeaders.Builder()
                            .addHeader("ApiKey", SinemaApp.instance.prefs.apiKey)
                            .build())
                        Glide.with(view.context)
                            .load(glideUrl)
                            .centerCrop()
                            .into(image)
                    } else {
                        image.setImageResource(android.R.drawable.ic_menu_agenda)
                        image.scaleType = ImageView.ScaleType.CENTER
                        image.setBackgroundColor(0xFF444444.toInt())
                    }
                } else {
                    title.text = item.name
                    content.text = item.scene?.formatDuration() ?: ""
                    if (item.scene != null) {
                        val url = SinemaApp.instance.api.getScreenshotUrl(item.scene.id)
                        val glideUrl = GlideUrl(url, LazyHeaders.Builder()
                            .addHeader("ApiKey", SinemaApp.instance.prefs.apiKey)
                            .build())
                        Glide.with(view.context)
                            .load(glideUrl)
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

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
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
            "Log Out" -> {
                title.text = "🔒 Log Out"
                content.text = "Lock app"
                image.setImageResource(android.R.drawable.ic_lock_lock)
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

class SearchCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_folder_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val title = viewHolder.view.findViewById<TextView>(R.id.card_title)
        val content = viewHolder.view.findViewById<TextView>(R.id.card_content)
        val image = viewHolder.view.findViewById<ImageView>(R.id.card_image)
        title.text = "🔍 Search"
        content.text = "Find scenes"
        image.setImageResource(android.R.drawable.ic_menu_search)
        image.scaleType = ImageView.ScaleType.CENTER
        image.setBackgroundColor(0xFF444444.toInt())
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
