package com.sinema.ui

import com.sinema.model.SortOption
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class GridNavigationTest {
    @Test
    fun `range uses one-based visible positions`() {
        assertEquals("101–150 of 1,842", GridNavigation.positionLabel(100, 149, 1842, Locale.US))
    }

    @Test
    fun `range accepts a localized template`() {
        assertEquals(
            "101 | 150 | 1,842",
            GridNavigation.positionLabel(100, 149, 1842, Locale.US, "%1\$s | %2\$s | %3\$s")
        )
    }

    @Test
    fun `empty and negative ranges are hidden`() {
        assertEquals("", GridNavigation.positionLabel(-1, -1, 0))
    }

    @Test
    fun `title sort filters titles while path and chronological sorts filter paths`() {
        assertEquals("title", GridNavigation.alphaField(SortOption.TITLE_ASC))
        assertEquals("title", GridNavigation.alphaField(SortOption.TITLE_DESC))
        assertEquals("path", GridNavigation.alphaField(SortOption.PATH_ASC))
        assertEquals("path", GridNavigation.alphaField(SortOption.ADDED_DESC))
    }
}
