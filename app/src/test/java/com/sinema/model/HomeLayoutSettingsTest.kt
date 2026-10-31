package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutSettingsTest {

    @Test
    fun `defaults preserve existing home order and hide optional rows`() {
        val settings = HomeLayoutSettings().normalized()

        assertEquals(HomeShortcut.customizable, settings.shortcutOrder)
        assertEquals(HomeContentRow.entries.toList(), settings.rowOrder)
        assertTrue(settings.isRowVisible(HomeContentRow.CONTINUE_PLAYING))
        assertFalse(settings.isRowVisible(HomeContentRow.UNWATCHED_FAVORITES))
        assertFalse(settings.isRowVisible(HomeContentRow.RECENTLY_RELEASED))
        assertFalse(settings.isRowVisible(HomeContentRow.SAVED_FILTER))
        assertEquals(HomeCardSize.COMPACT, settings.cardSize)
        assertTrue(settings.showHero)
    }

    @Test
    fun `normalization removes unknown and duplicate entries while adding missing defaults`() {
        val settings = HomeLayoutSettings(
            shortcutOrder = listOf(HomeShortcut.TAGS, HomeShortcut.TAGS),
            rowOrder = listOf(HomeContentRow.FAVORITES),
            hiddenRows = emptySet()
        ).normalized()

        assertEquals(HomeShortcut.TAGS, settings.shortcutOrder?.first())
        assertEquals(HomeShortcut.customizable.toSet(), settings.shortcutOrder?.toSet())
        assertEquals(HomeContentRow.FAVORITES, settings.rowOrder?.first())
        assertEquals(HomeContentRow.entries.toSet(), settings.rowOrder?.toSet())
        assertTrue(settings.isRowVisible(HomeContentRow.RECENTLY_RELEASED))
    }

    @Test
    fun `moving items clamps at both ends`() {
        val settings = HomeLayoutSettings().normalized()

        assertEquals(settings, settings.moveShortcut(HomeShortcut.SEARCH, -1))
        assertEquals(settings, settings.moveRow(HomeContentRow.SAVED_FILTER, 1))
        assertEquals(
            HomeShortcut.SURPRISE_ME,
            settings.moveShortcut(HomeShortcut.SEARCH, 1).shortcutOrder?.first()
        )
    }

    @Test
    fun `shortcut and row visibility are independent`() {
        val settings = HomeLayoutSettings().normalized()
            .toggleShortcut(HomeShortcut.STATS)
            .toggleRow(HomeContentRow.RECENTLY_ADDED)

        assertFalse(settings.visibleShortcuts().contains(HomeShortcut.STATS))
        assertFalse(settings.isRowVisible(HomeContentRow.RECENTLY_ADDED))
        assertTrue(settings.visibleShortcuts().contains(HomeShortcut.SEARCH))
        assertTrue(settings.isRowVisible(HomeContentRow.FAVORITES))
    }

    @Test
    fun `grouped library keeps primary shortcuts and exposes secondary items`() {
        val settings = HomeLayoutSettings(groupLibrary = true).normalized()

        assertEquals(
            listOf(HomeShortcut.SEARCH, HomeShortcut.SURPRISE_ME, HomeShortcut.FAVORITES, HomeShortcut.LIBRARY),
            settings.visibleShortcuts()
        )
        assertFalse(settings.visibleLibraryShortcuts().contains(HomeShortcut.SEARCH))
        assertTrue(settings.visibleLibraryShortcuts().contains(HomeShortcut.MARKERS))
    }

    @Test
    fun `large artwork dimensions are explicit`() {
        assertEquals(266, HomeCardSize.COMPACT.widthDp)
        assertEquals(158, HomeCardSize.COMPACT.imageHeightDp)
        assertEquals(320, HomeCardSize.LARGE.widthDp)
        assertEquals(270, HomeCardSize.LARGE.heightDp)
        assertEquals(200, HomeCardSize.LARGE.imageHeightDp)
    }
}
