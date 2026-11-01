package com.sinema.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneFilterCompositionTest {
    @Test
    fun `independent criteria retain flat query shape`() {
        val base = mapOf("resume_time" to mapOf("value" to 0, "modifier" to "GREATER_THAN"))
        val additional = mapOf("duration" to mapOf("value" to 600, "modifier" to "LESS_THAN"))
        assertEquals(base + additional, combineSceneFilters(base, additional))
    }

    @Test
    fun `conflicting screen and advanced criteria are both preserved with AND`() {
        val baseTag = mapOf("value" to listOf("screen-tag"), "modifier" to "INCLUDES", "depth" to 0)
        val extraTag = mapOf("value" to listOf("extra-tag"), "modifier" to "EXCLUDES", "depth" to 0)
        val base = mapOf("tags" to baseTag)
        val additional = mapOf(
            "tags" to extraTag,
            "duration" to mapOf("value" to 600, "modifier" to "LESS_THAN")
        )
        val combined = combineSceneFilters(base, additional)
        val child = combined["AND"] as Map<*, *>
        assertEquals(base, child["AND"])
        assertEquals(extraTag, child["tags"])
        assertEquals(additional["duration"], child["duration"])
        assertTrue(!combined.containsKey("tags"))
    }

    @Test
    fun `additional logical criteria cannot replace the base scope`() {
        val base = mapOf("tags" to mapOf("value" to listOf("screen-tag"), "modifier" to "INCLUDES"))
        val additional = mapOf(
            "AND" to mapOf("duration" to mapOf("value" to 600, "modifier" to "LESS_THAN")),
            "tags" to mapOf("value" to listOf("extra-tag"), "modifier" to "EXCLUDES")
        )
        val combined = combineSceneFilters(base, additional).toString()
        assertTrue(combined.contains("screen-tag"))
        assertTrue(combined.contains("extra-tag"))
        assertTrue(combined.contains("600"))
    }

    @Test
    fun `existing logical scope survives composition`() {
        val base = mapOf(
            "AND" to mapOf("play_count" to mapOf("value" to 0, "modifier" to "EQUALS")),
            "performer_favorite" to true
        )
        val additional = mapOf("play_count" to mapOf("value" to 0, "modifier" to "GREATER_THAN"))
        val combined = combineSceneFilters(base, additional)
        assertEquals(base + additional, combined)
        assertEquals(base["AND"], combined["AND"])
        assertEquals(additional["play_count"], combined["play_count"])
    }
}
