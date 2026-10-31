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

    // --- 1.21 additions: resolution, markers, captions, minimum rating ---

    @Test
    fun `full preference round trip preserves every choice`() {
        val original = SceneFilterOptions(
            watch = WatchFilter.WATCHED,
            favoritesOnly = false,
            resolution = ResolutionFilter.FULL_HD_PLUS,
            markers = PresenceFilter.WITH,
            captions = PresenceFilter.WITHOUT,
            minStars = 3
        )
        assertEquals(original, SceneFilterOptions.fromPreferenceValue(original.toPreferenceValue()))
    }

    @Test
    fun `legacy two-part preference parses with defaulted tail`() {
        // 1.20.0 wrote "watch:favoritesOnly" only.
        val parsed = SceneFilterOptions.fromPreferenceValue("UNWATCHED:true")
        assertEquals(SceneFilterOptions(WatchFilter.UNWATCHED, true), parsed)
    }

    @Test
    fun `resolution maps to stash enum criterion`() {
        // Stash comparators are strict on enum rank, so "X or better" is
        // GREATER_THAN on the next-lower tier.
        val hd = SceneFilterOptions(resolution = ResolutionFilter.HD_PLUS).toStashFilter()
        assertEquals(
            mapOf("value" to "WEB_HD", "modifier" to "GREATER_THAN"),
            hd["resolution"]
        )
        val fullHd = SceneFilterOptions(resolution = ResolutionFilter.FULL_HD_PLUS).toStashFilter()
        assertEquals(
            mapOf("value" to "STANDARD_HD", "modifier" to "GREATER_THAN"),
            fullHd["resolution"]
        )
        val fourK = SceneFilterOptions(resolution = ResolutionFilter.FOUR_K_PLUS).toStashFilter()
        assertEquals(
            mapOf("value" to "QUAD_HD", "modifier" to "GREATER_THAN"),
            fourK["resolution"]
        )
        val underHd = SceneFilterOptions(resolution = ResolutionFilter.UNDER_HD).toStashFilter()
        assertEquals("LESS_THAN", (underHd.getValue("resolution") as Map<*, *>)["modifier"])
        assertEquals("STANDARD_HD", (underHd.getValue("resolution") as Map<*, *>)["value"])
    }

    @Test
    fun `has_markers is a plain string criterion`() {
        val with = SceneFilterOptions(markers = PresenceFilter.WITH).toStashFilter()
        assertEquals("true", with["has_markers"])
        val without = SceneFilterOptions(markers = PresenceFilter.WITHOUT).toStashFilter()
        assertEquals("false", without["has_markers"])
        assertEquals(null, SceneFilterOptions().toStashFilter()["has_markers"])
    }

    @Test
    fun `captions map to null-check string criterion`() {
        val with = SceneFilterOptions(captions = PresenceFilter.WITH).toStashFilter()
        assertEquals(
            mapOf("value" to "", "modifier" to "NOT_NULL"),
            with["captions"]
        )
        val without = SceneFilterOptions(captions = PresenceFilter.WITHOUT).toStashFilter()
        assertEquals("IS_NULL", (without.getValue("captions") as Map<*, *>)["modifier"])
    }

    @Test
    fun `minimum stars outrank favorites checkbox for rating criterion`() {
        val filter = SceneFilterOptions(favoritesOnly = true, minStars = 3).toStashFilter()
        // ★3+ means rating100 >= 60 → strict GREATER_THAN 59.
        assertEquals(
            mapOf("value" to 59, "modifier" to "GREATER_THAN"),
            filter["rating100"]
        )
    }

    @Test
    fun `out-of-range stars clamp when parsed from preferences`() {
        val high = SceneFilterOptions.fromPreferenceValue("ALL:false:9:ANY:ANY:ANY")
        assertEquals(5, high.minStars)
        val low = SceneFilterOptions.fromPreferenceValue("ALL:false:-2:ANY:ANY:ANY")
        assertEquals(0, low.minStars)
    }

    @Test
    fun `summary lists every active dimension`() {
        val filter = SceneFilterOptions(
            watch = WatchFilter.UNWATCHED,
            minStars = 2,
            resolution = ResolutionFilter.HD_PLUS,
            markers = PresenceFilter.WITH,
            captions = PresenceFilter.WITHOUT
        )
        val summary = filter.summary
        org.junit.Assert.assertTrue(summary.contains("Unwatched"))
        org.junit.Assert.assertTrue(summary.contains("★2+"))
        org.junit.Assert.assertTrue(summary.contains("720p or better"))
        org.junit.Assert.assertTrue(summary.contains("With markers"))
        org.junit.Assert.assertTrue(summary.contains("Without captions"))
    }
}
