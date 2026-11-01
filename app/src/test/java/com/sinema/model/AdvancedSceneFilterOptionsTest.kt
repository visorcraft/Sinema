package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSceneFilterOptionsTest {
    @Test
    fun `duration ranges map to seconds`() {
        assertEquals(
            mapOf("value" to 600, "modifier" to "LESS_THAN"),
            SceneFilterOptions(duration = DurationFilter.UNDER_10).toStashFilter()["duration"]
        )
        assertEquals(
            mapOf("value" to 600, "value2" to 1800, "modifier" to "BETWEEN"),
            SceneFilterOptions(duration = DurationFilter.TEN_TO_THIRTY).toStashFilter()["duration"]
        )
        assertEquals(
            mapOf("value" to 1800, "value2" to 3600, "modifier" to "BETWEEN"),
            SceneFilterOptions(duration = DurationFilter.THIRTY_TO_SIXTY).toStashFilter()["duration"]
        )
        assertEquals(
            mapOf("value" to 3600, "modifier" to "GREATER_THAN"),
            SceneFilterOptions(duration = DurationFilter.OVER_60).toStashFilter()["duration"]
        )
    }

    @Test
    fun `release dates are relative to the supplied reference date`() {
        val recent = SceneFilterOptions(releaseDate = ReleaseDateFilter.PAST_30_DAYS)
            .toStashFilter("2026-10-31")["date"]
        assertEquals(mapOf("value" to "2026-10-01", "modifier" to "GREATER_THAN"), recent)
        val year = SceneFilterOptions(releaseDate = ReleaseDateFilter.PAST_YEAR)
            .toStashFilter("2026-10-31")["date"]
        assertEquals(mapOf("value" to "2025-10-31", "modifier" to "GREATER_THAN"), year)
        val older = SceneFilterOptions(releaseDate = ReleaseDateFilter.OLDER_THAN_YEAR)
            .toStashFilter("2026-10-31")["date"]
        assertEquals(mapOf("value" to "2025-10-31", "modifier" to "LESS_THAN"), older)
    }

    @Test
    fun `video codecs map to exact stash values`() {
        assertEquals("h264", codecValue(VideoCodecFilter.H264))
        assertEquals("hevc", codecValue(VideoCodecFilter.HEVC))
        assertEquals("vp9", codecValue(VideoCodecFilter.VP9))
        assertEquals("av1", codecValue(VideoCodecFilter.AV1))
    }

    private fun codecValue(codec: VideoCodecFilter): Any? =
        (SceneFilterOptions(videoCodec = codec).toStashFilter().getValue("video_codec") as Map<*, *>)["value"]

    @Test
    fun `entity filters include and exclude multiple ids`() {
        val selected = listOf(EntitySelection("1", "One"), EntitySelection("2", "Two"))
        val filter = SceneFilterOptions(
            performers = EntityFilter(EntityFilterMode.INCLUDE, selected),
            tags = EntityFilter(EntityFilterMode.EXCLUDE, selected),
            studios = EntityFilter(EntityFilterMode.INCLUDE, selected.take(1))
        ).toStashFilter()
        assertEquals(
            mapOf("value" to listOf("1", "2"), "modifier" to "INCLUDES"),
            filter["performers"]
        )
        assertEquals(
            mapOf("value" to listOf("1", "2"), "modifier" to "EXCLUDES", "depth" to 0),
            filter["tags"]
        )
        assertEquals(
            mapOf("value" to listOf("1"), "modifier" to "INCLUDES", "depth" to 0),
            filter["studios"]
        )
    }

    @Test
    fun `empty or any entity selection adds no criterion`() {
        assertTrue(SceneFilterOptions(performers = EntityFilter(EntityFilterMode.INCLUDE)).toStashFilter().isEmpty())
        assertTrue(SceneFilterOptions(tags = EntityFilter(EntityFilterMode.ANY,
            listOf(EntitySelection("1", "One")))).toStashFilter().isEmpty())
    }

    @Test
    fun `advanced filters combine with existing criteria`() {
        val filter = SceneFilterOptions(
            watch = WatchFilter.UNWATCHED,
            resolution = ResolutionFilter.FULL_HD_PLUS,
            duration = DurationFilter.OVER_60,
            videoCodec = VideoCodecFilter.HEVC
        ).toStashFilter()
        assertEquals(setOf("play_count", "resolution", "duration", "video_codec"), filter.keys)
    }

    @Test
    fun `json preference round trip preserves all advanced fields`() {
        val original = SceneFilterOptions(
            watch = WatchFilter.WATCHED,
            favoritesOnly = true,
            resolution = ResolutionFilter.HD_PLUS,
            markers = PresenceFilter.WITHOUT,
            captions = PresenceFilter.WITH,
            minStars = 4,
            duration = DurationFilter.TEN_TO_THIRTY,
            releaseDate = ReleaseDateFilter.PAST_YEAR,
            videoCodec = VideoCodecFilter.AV1,
            performers = EntityFilter(EntityFilterMode.INCLUDE,
                listOf(EntitySelection("p:1", "Performer: One"))),
            studios = EntityFilter(EntityFilterMode.EXCLUDE,
                listOf(EntitySelection("s,1", "Studio, One"))),
            tags = EntityFilter(EntityFilterMode.INCLUDE,
                listOf(EntitySelection("t:1", "Tag: One"), EntitySelection("t2", "Two")))
        )
        assertEquals(original, SceneFilterOptions.fromPreferenceValue(original.toPreferenceValue()))
    }

    @Test
    fun `malformed and partial json preferences are safe`() {
        assertEquals(SceneFilterOptions(), SceneFilterOptions.fromPreferenceValue("{"))
        val partial = SceneFilterOptions.fromPreferenceValue("""{"watch":"UNWATCHED","duration":"OVER_60"}""")
        assertEquals(SceneFilterOptions(watch = WatchFilter.UNWATCHED, duration = DurationFilter.OVER_60), partial)
        assertEquals(SceneFilterOptions(), SceneFilterOptions.fromPreferenceValue("""{"watch":7,"performers":"bad"}"""))
    }

    @Test
    fun `summary includes advanced dimensions and entity names`() {
        val summary = SceneFilterOptions(
            duration = DurationFilter.OVER_60,
            releaseDate = ReleaseDateFilter.PAST_30_DAYS,
            videoCodec = VideoCodecFilter.HEVC,
            performers = EntityFilter(EntityFilterMode.INCLUDE,
                listOf(EntitySelection("1", "One"), EntitySelection("2", "Two"))),
            tags = EntityFilter(EntityFilterMode.EXCLUDE, listOf(EntitySelection("3", "Three")))
        ).summary
        assertTrue(summary.contains("Over 60 min"))
        assertTrue(summary.contains("Last 30 days"))
        assertTrue(summary.contains("HEVC"))
        assertTrue(summary.contains("Performers: One +1"))
        assertTrue(summary.contains("Exclude tags: Three"))
    }
}
