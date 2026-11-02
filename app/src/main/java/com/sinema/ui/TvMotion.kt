package com.sinema.ui

import com.sinema.SinemaApp

internal object TvMotion {
    fun focusZoom(reduceMotion: Boolean): Int = if (reduceMotion) 0 else 1

    fun focusZoom(): Int = focusZoom(SinemaApp.instance.prefs.reduceMotion)
}
