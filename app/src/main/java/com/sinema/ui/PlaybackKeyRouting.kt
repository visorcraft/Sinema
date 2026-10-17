package com.sinema.ui

object PlaybackKeyRouting {
    fun shouldSeekToAdjacentMarker(
        controllerVisible: Boolean,
        hasMarkers: Boolean,
        playerSurfaceFocused: Boolean
    ): Boolean = controllerVisible && hasMarkers && playerSurfaceFocused
}
