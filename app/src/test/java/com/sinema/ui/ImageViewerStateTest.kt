package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerStateTest {
    @Test
    fun `zoom is bounded and navigation resets transform`() {
        val zoomed = ImageViewerState(position = 0, zoom = 4.75f, panX = 20f, panY = -10f)
        assertEquals(5f, zoomed.zoomIn().zoom, 0f)
        assertEquals(1f, ImageViewerState(zoom = 1f).zoomOut().zoom, 0f)
        val next = zoomed.next(3)
        assertEquals(1, next.position)
        assertEquals(1f, next.zoom, 0f)
        assertEquals(0f, next.panX, 0f)
        assertEquals(0f, next.panY, 0f)
        val last = next.next(3)
        assertEquals(2, last.position)
        assertEquals(last, last.next(3))
        assertEquals(0, next.previous(3).position)
        assertEquals(ImageViewerState(position = 0), ImageViewerState(position = 0).previous(3))
    }

    @Test
    fun `pan is bounded by zoomed viewport and reset restores fit`() {
        val state = ImageViewerState(zoom = 2f).pan(10_000f, -10_000f, 1000, 500)
        assertEquals(500f, state.panX, 0f)
        assertEquals(-250f, state.panY, 0f)
        assertEquals(ImageViewerState(), state.resetTransform())
        assertEquals(ImageViewerState(), ImageViewerState().pan(20f, 20f, 1000, 500))
    }

    @Test
    fun `fill toggle resets transform`() {
        val filled = ImageViewerState(zoom = 3f, panX = 50f).toggleDisplayMode()
        assertEquals(ImageDisplayMode.FILL, filled.displayMode)
        assertEquals(1f, filled.zoom, 0f)
        assertEquals(0f, filled.panX, 0f)
        assertEquals(ImageDisplayMode.FIT, filled.toggleDisplayMode().displayMode)
    }

    @Test
    fun `shuffle retains current position and every image once`() {
        val order = shuffledImageOrder(size = 100, currentIndex = 42, seed = 7L)
        assertEquals(100, order.size)
        assertEquals(42, order[42])
        assertEquals((0 until 100).toSet(), order.toSet())
        assertTrue(order != (0 until 100).toList())
        assertEquals(listOf(0), shuffledImageOrder(1, 0))
        assertEquals(emptyList<Int>(), shuffledImageOrder(0, 0))
    }

    @Test
    fun `slideshow delay cycles through supported values`() {
        assertEquals(5_000L, nextSlideshowDelay(3_000L))
        assertEquals(10_000L, nextSlideshowDelay(5_000L))
        assertEquals(30_000L, nextSlideshowDelay(10_000L))
        assertEquals(3_000L, nextSlideshowDelay(30_000L))
        assertEquals(3_000L, nextSlideshowDelay(1L))
    }
}
