package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitySortOptionTest {
    @Test
    fun `entity sorts map to Stash fields`() {
        assertEquals("scenes_count", EntitySortOption.COUNT_DESC.field)
        assertEquals("DESC", EntitySortOption.COUNT_DESC.direction)
        assertEquals("name", EntitySortOption.NAME_ASC.field)
        assertEquals("ASC", EntitySortOption.NAME_ASC.direction)
        assertEquals("DESC", EntitySortOption.NAME_DESC.direction)
    }

    @Test
    fun `saved entity sort falls back to scene count`() {
        assertEquals(EntitySortOption.NAME_ASC, EntitySortOption.fromName("NAME_ASC"))
        assertEquals(EntitySortOption.COUNT_DESC, EntitySortOption.fromName("removed-sort"))
        assertEquals(EntitySortOption.COUNT_DESC, EntitySortOption.fromName(null))
    }
}
