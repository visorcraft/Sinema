package com.sinema.util

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sinema.api.SinemaApi

object GlideAuth {
    /** Wraps a media URL with the auth headers required by the active auth mode. */
    fun url(api: SinemaApi, url: String): GlideUrl {
        val headers = LazyHeaders.Builder()
        api.mediaAuthHeaders().forEach { (name, value) -> headers.addHeader(name, value) }
        return GlideUrl(url, headers.build())
    }
}
