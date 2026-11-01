package com.sinema.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun `play links resolve to scene ids`() {
        assertEquals("42", DeepLinks.playSceneId("/play/42"))
        assertNull(DeepLinks.playSceneId("/play/"))
        assertNull(DeepLinks.playSceneId("/play/a/b"))
        assertNull(DeepLinks.playSceneId(null))
    }

    @Test
    fun `scene links resolve to scene ids and never cross routes`() {
        assertEquals("42", DeepLinks.detailSceneId("/scene/42"))
        assertNull(DeepLinks.detailSceneId("/play/42"))
        assertNull(DeepLinks.detailSceneId("/scene/"))
        assertNull(DeepLinks.detailSceneId("/scene/a/b"))
        assertNull(DeepLinks.detailSceneId(null))
        // Sanity: play parser never claims a scene link and vice versa.
        assertNull(DeepLinks.playSceneId("/scene/42"))
    }
}

class RecentSearchesTest {
    @Test
    fun `recent searches newest first, dedup case-insensitive, capped`() {
        val start = listOf("alpha", "Beta", "gamma")
        assertEquals(listOf("delta", "alpha", "Beta", "gamma"), RecentSearches.updated(start, "delta"))
        // Duplicate re-heads the list with the latest casing.
        assertEquals(
            listOf("BETA", "alpha", "gamma"),
            RecentSearches.updated(listOf("alpha", "Beta", "gamma"), "BETA")
        )
        // Capped at MAX.
        val ten = (1..RecentSearches.MAX).map { "q$it" }
        val updated = RecentSearches.updated(ten, "new")
        assertEquals("new", updated.first())
        assertEquals(RecentSearches.MAX, updated.size)
        assertEquals(false, updated.contains("q10"))
    }

    @Test
    fun `short and blank queries are ignored`() {
        assertEquals(emptyList<String>(), RecentSearches.updated(emptyList(), "a"))
        assertEquals(emptyList<String>(), RecentSearches.updated(emptyList(), "   "))
        // Whitespace trims before the length check: "  x  " stays ignored.
        assertEquals(listOf("keep"), RecentSearches.updated(listOf("keep"), "  x  "))
        assertEquals(listOf("keep"), RecentSearches.updated(listOf("keep"), "  "))
    }
}
