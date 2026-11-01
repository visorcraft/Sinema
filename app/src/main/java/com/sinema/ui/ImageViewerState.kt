package com.sinema.ui

import java.util.Random

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val ZOOM_STEP = 0.25f
private val slideshowDelays = longArrayOf(3_000L, 5_000L, 10_000L, 30_000L)

enum class ImageDisplayMode { FIT, FILL }

data class ImageViewerState(
    val position: Int = 0,
    val zoom: Float = MIN_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val displayMode: ImageDisplayMode = ImageDisplayMode.FIT
) {
    fun zoomIn(): ImageViewerState = copy(zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM))

    fun zoomOut(): ImageViewerState {
        val target = (zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM)
        return if (target == MIN_ZOOM) resetTransform() else copy(zoom = target)
    }

    fun pan(dx: Float, dy: Float, viewportWidth: Int, viewportHeight: Int): ImageViewerState {
        if (zoom <= MIN_ZOOM) return resetTransform()
        val maxX = (zoom - 1f) * viewportWidth / 2f
        val maxY = (zoom - 1f) * viewportHeight / 2f
        return copy(
            panX = (panX + dx).coerceIn(-maxX, maxX),
            panY = (panY + dy).coerceIn(-maxY, maxY)
        )
    }

    fun resetTransform(): ImageViewerState = copy(zoom = MIN_ZOOM, panX = 0f, panY = 0f)

    fun toggleDisplayMode(): ImageViewerState = copy(
        displayMode = if (displayMode == ImageDisplayMode.FIT) ImageDisplayMode.FILL else ImageDisplayMode.FIT
    ).resetTransform()

    fun next(size: Int): ImageViewerState =
        if (position < size - 1) copy(position = position + 1).resetTransform() else this

    fun previous(size: Int): ImageViewerState =
        if (size > 0 && position > 0) copy(position = position - 1).resetTransform() else this
}

fun shuffledImageOrder(size: Int, currentIndex: Int, seed: Long = 0L): List<Int> {
    if (size <= 0) return emptyList()
    if (size == 1) return listOf(0)
    val current = currentIndex.coerceIn(0, size - 1)
    val remaining = (0 until size).filter { it != current }.toMutableList()
    remaining.shuffle(Random(seed))
    return remaining.apply { add(current, current) }
}

fun nextSlideshowDelay(currentMs: Long): Long {
    val index = slideshowDelays.indexOf(currentMs)
    return if (index < 0 || index == slideshowDelays.lastIndex) slideshowDelays.first()
    else slideshowDelays[index + 1]
}
