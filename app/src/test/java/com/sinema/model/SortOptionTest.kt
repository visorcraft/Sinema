package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SortOptionTest {
    @Test
    fun `random sort embeds seed for stable pagination`() {
        assertEquals("random_42", SortOption.RANDOM.apiSort(42))
    }

    @Test
    fun `non-random sort ignores seed`() {
        assertEquals("created_at", SortOption.ADDED_DESC.apiSort(42))
    }

    @Test
    fun `fromName falls back to default on unknown value`() {
        assertEquals(SortOption.PATH_ASC, SortOption.fromName("garbage"))
    }
}
