package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSortOptionTest {
    @Test
    fun `sort options use Stash server field names`() {
        assertEquals("scenes_count", GroupSortOption.COUNT_DESC.field)
        assertEquals("name", GroupSortOption.NAME_ASC.field)
        assertEquals("updated_at", GroupSortOption.UPDATED_DESC.field)
    }
}
