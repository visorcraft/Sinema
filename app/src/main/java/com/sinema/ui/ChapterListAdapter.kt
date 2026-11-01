package com.sinema.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.api.SinemaApi
import com.sinema.util.GlideAuth
import com.sinema.util.TimeFormat

/**
 * Rows for the playback Chapters dialog: marker frame thumbnail plus title
 * and timestamp. Thumbnails reuse the authenticated marker screenshot
 * endpoint (Stash serves the scene frame at any timestamp); Glide cancels
 * superseded requests on recycled rows automatically.
 */
class ChapterListAdapter(
    context: Context,
    private val api: SinemaApi,
    private val sceneId: String,
    markers: List<Pair<String, Double>>
) : ArrayAdapter<Pair<String, Double>>(context, R.layout.item_chapter, markers) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        val marker = getItem(position) ?: return row
        row.findViewById<TextView>(R.id.chapter_title).text = marker.first
        row.findViewById<TextView>(R.id.chapter_time).text = TimeFormat.formatSeconds(marker.second)
        val image = row.findViewById<ImageView>(R.id.chapter_thumb)
        Glide.with(image)
            .load(GlideAuth.url(api, api.getMarkerScreenshotUrl(sceneId, marker.second)))
            .centerCrop()
            .into(image)
        return row
    }
}
