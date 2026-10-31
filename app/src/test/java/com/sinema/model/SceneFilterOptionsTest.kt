package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneFilterOptionsTest {
    @Test
    fun `default filter adds no criteria`() {
        assertEquals(emptyMap<String, Any?>(), SceneFilterOptions().toStashFilter())
        assertEquals("All scenes", SceneFilterOptions().summary)
    }

    @Test
    fun `watched favorites combine criteria`() {
        val filter = SceneFilterOptions(WatchFilter.WATCHED, true).toStashFilter()
        assertEquals("GREATER_THAN", (filter.getValue("play_count") as Map<*, *>)["modifier"])
        assertEquals(0, (filter.getValue("play_count") as Map<*, *>)["value"])
        assertEquals("GREATER_THAN", (filter.getValue("rating100") as Map<*, *>)["modifier"])
    }

    @Test
    fun `unwatched uses zero play count`() {
        val filter = SceneFilterOptions(WatchFilter.UNWATCHED).toStashFilter()
        assertEquals("EQUALS", (filter.getValue("play_count") as Map<*, *>)["modifier"])
        assertEquals(setOf("play_count"), filter.keys)
    }

    @Test
    fun `preference round trip preserves both choices`() {
        val original = SceneFilterOptions(WatchFilter.UNWATCHED, true)
        assertEquals(original, SceneFilterOptions.fromPreferenceValue(original.toPreferenceValue()))
    }

    @Test
    fun `invalid preference returns default filter`() {
        assertEquals(SceneFilterOptions(), SceneFilterOptions.fromPreferenceValue(null))
        assertEquals(SceneFilterOptions(), SceneFilterOptions.fromPreferenceValue("not-a-filter"))
        assertEquals(SceneFilterOptions(), SceneFilterOptions.fromPreferenceValue("WATCHED:not-boolean"))
    }
}
