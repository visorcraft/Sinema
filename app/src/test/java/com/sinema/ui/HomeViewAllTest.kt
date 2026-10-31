package com.sinema.ui

import com.sinema.model.HomeContentRow
import com.sinema.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeViewAllTest {

    @Test
    fun `view all cards have stable row identity and useful labels`() {
        val card = HomeViewAll(HomeContentRow.RECENTLY_ADDED, "Recently Added")

        assertEquals("view_all:RECENTLY_ADDED", card.key)
        assertEquals("View All", card.actionLabel)
        assertEquals("Recently Added", card.title)
        assertNotEquals(card.key, HomeViewAll(HomeContentRow.FAVORITES, "Favorites").key)
    }

    @Test
    fun `home rows map to their native default sorts`() {
        assertEquals(SortOption.LAST_PLAYED_DESC, HomeContentRow.CONTINUE_PLAYING.defaultSort)
        assertEquals(SortOption.LAST_PLAYED_DESC, HomeContentRow.RECENTLY_PLAYED.defaultSort)
        assertEquals(SortOption.ADDED_DESC, HomeContentRow.RECENTLY_ADDED.defaultSort)
        assertEquals(SortOption.UPDATED_DESC, HomeContentRow.FAVORITES.defaultSort)
        assertEquals(SortOption.UPDATED_DESC, HomeContentRow.UNWATCHED_FAVORITES.defaultSort)
        assertEquals(SortOption.DATE_DESC, HomeContentRow.RECENTLY_RELEASED.defaultSort)
    }
}
