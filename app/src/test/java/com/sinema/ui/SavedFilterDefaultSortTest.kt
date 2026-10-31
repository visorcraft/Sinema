package com.sinema.ui

import com.sinema.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Default-sort mapping for saved filter grids. A saved filter's sort field
 * AND direction must carry into the grid (e.g. path/DESC → Name Z-A); field
 * match is the fallback, then Name A-Z. (Construction-time safety of
 * SavedFilterScenesFragment.create — arguments attached after construction —
 * is enforced by defaultSort being a getter, not an initializer.)
 */
class SavedFilterDefaultSortTest {

    @Test
    fun `default sort matches field and direction`() {
        assertEquals(SortOption.PATH_DESC, savedFilterDefaultSort("path", "DESC"))
        assertEquals(SortOption.ADDED_DESC, savedFilterDefaultSort("created_at", "DESC"))
        assertEquals(SortOption.PATH_ASC, savedFilterDefaultSort("path", "ASC"))
    }

    @Test
    fun `unknown direction falls back to a field match`() {
        assertEquals(SortOption.RATING_DESC, savedFilterDefaultSort("rating", "SIDEWAYS"))
    }

    @Test
    fun `unknown or missing sort falls back to name ascending`() {
        assertEquals(SortOption.PATH_ASC, savedFilterDefaultSort("mystery_field", "DESC"))
        assertEquals(SortOption.PATH_ASC, savedFilterDefaultSort(null, null))
        assertEquals(SortOption.PATH_ASC, savedFilterDefaultSort("", ""))
    }
}
