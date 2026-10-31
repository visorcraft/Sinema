package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StarRatingTest {

    @Test
    fun `stars convert to rating100`() {
        assertEquals(null, StarRating.toRating100(0))
        assertEquals(20, StarRating.toRating100(1))
        assertEquals(60, StarRating.toRating100(3))
        assertEquals(100, StarRating.toRating100(5))
        assertEquals(null, StarRating.toRating100(6))
        assertEquals(null, StarRating.toRating100(-1))
    }

    @Test
    fun `rating100 converts back to stars`() {
        assertEquals(0, StarRating.fromRating100(null))
        assertEquals(0, StarRating.fromRating100(0))
        assertEquals(1, StarRating.fromRating100(1))
        assertEquals(1, StarRating.fromRating100(20))
        assertEquals(3, StarRating.fromRating100(59))
        assertEquals(3, StarRating.fromRating100(60))
        assertEquals(5, StarRating.fromRating100(100))
        assertEquals(5, StarRating.fromRating100(120))
    }

    @Test
    fun `round trip through rating100 keeps star count`() {
        (1..StarRating.MAX_STARS).forEach { stars ->
            assertEquals(stars, StarRating.fromRating100(StarRating.toRating100(stars)))
        }
    }

    @Test
    fun `glyphs render filled stars only`() {
        assertEquals("", StarRating.glyphs(0))
        assertEquals("★", StarRating.glyphs(1))
        assertEquals("★★★", StarRating.glyphs(3))
        assertEquals("", StarRating.glyphs(StarRating.MAX_STARS + 1))
    }

    @Test
    fun `scene starCount follows rating`() {
        fun scene(rating: Int?) = Scene(
            id = "1", title = "t", path = "/data/a.mp4", size = 1L,
            duration = 10.0, width = 1920, height = 1080, rating100 = rating
        )
        assertEquals(0, scene(null).starCount)
        assertEquals(0, scene(0).starCount)
        assertEquals(4, scene(80).starCount)
        assertEquals(5, scene(100).starCount)
        // Any rating > 0 keeps favorite semantics intact.
        assertEquals(true, scene(20).isFavorite)
    }
}
