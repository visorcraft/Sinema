package com.sinema.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.sinema.api.SinemaApi

/**
 * Authenticated image loading: wraps the repeated
 * Glide + GlideAuth four-liner. centerCrop is the common case; callers
 * needing listeners, dontTransform, or centerInside keep custom builders.
 */
fun ImageView.loadAuth(
    api: SinemaApi,
    url: String,
    centerCrop: Boolean = true
) {
    val request = Glide.with(this).load(GlideAuth.url(api, url))
    if (centerCrop) request.centerCrop().into(this) else request.into(this)
}
