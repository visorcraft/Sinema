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

    @Test
    fun `negative seed is clamped to digits-only suffix`() {
        assertEquals("random_0", SortOption.RANDOM.apiSort(-123))
    }

    @Test
    fun `fromName handles null and empty as default`() {
        assertEquals(SortOption.PATH_ASC, SortOption.fromName(null))
        assertEquals(SortOption.PATH_ASC, SortOption.fromName(""))
    }

    @Test
    fun `fromName round trips a real entry`() {
        assertEquals(SortOption.RANDOM, SortOption.fromName("RANDOM"))
    }
}
