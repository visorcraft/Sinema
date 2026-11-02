package com.sinema.api

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.sinema.api.SinemaApi

/**
 * Card image binding with authenticated URL, centerCrop, and a fallback
 * placeholder when the server has no image. Consolidates the artwork
 * branches across card presenters.
 */
fun ImageView.bindCardImage(api: SinemaApi, url: String?, fallbackRes: Int) {
    val usableUrl = url ?: return run {
        setImageResource(fallbackRes)
        scaleType = android.widget.ImageView.ScaleType.CENTER
    }
    Glide.with(this)
        .load(com.sinema.util.GlideAuth.url(api, usableUrl))
        .centerCrop()
        .error(fallbackRes)
        .into(this)
}
