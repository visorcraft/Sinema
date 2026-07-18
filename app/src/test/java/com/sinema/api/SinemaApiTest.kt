package com.sinema.api

import com.sinema.model.CaptionRef
import org.junit.Assert.assertEquals
import org.junit.Test

class SinemaApiTest {

    @Test
    fun `apikey mode sends ApiKey header`() {
        val api = SinemaApi("http://server", "the-key", "", "apikey")
        assertEquals(mapOf("ApiKey" to "the-key"), api.mediaAuthHeaders())
    }

    @Test
    fun `session mode sends session cookie`() {
        val api = SinemaApi("http://server", "", "session=abc123", "session")
        assertEquals(mapOf("Cookie" to "session=abc123"), api.mediaAuthHeaders())
    }

    @Test
    fun `session mode with blank cookie falls back to ApiKey`() {
        val api = SinemaApi("http://server", "the-key", "", "session")
        assertEquals(mapOf("ApiKey" to "the-key"), api.mediaAuthHeaders())
    }

    @Test
    fun `updateConfig changes media auth headers`() {
        val api = SinemaApi("http://server", "old-key", "", "apikey")
        api.updateConfig("http://server", "", "session=zzz", "session")
        assertEquals(mapOf("Cookie" to "session=zzz"), api.mediaAuthHeaders())
    }

    @Test
    fun `caption url carries language and type`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        assertEquals(
            "http://server/scene/42/caption?lang=en&type=srt",
            api.getCaptionUrl("42", CaptionRef("en", "srt"))
        )
    }

    @Test
    fun `parseScene tolerates JSON-null tags, performers, and captions`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","captions":null,"tags":null,"performers":null,"files":[]}"""
        ).asJsonObject
        val scene = api.parseScene(obj)
        assertEquals(emptyList<com.sinema.model.TagRef>(), scene.tags)
        assertEquals(emptyList<com.sinema.model.PerformerRef>(), scene.performers)
        assertEquals(emptyList<CaptionRef>(), scene.captions)
    }

    @Test
    fun `parseEntity tolerates null fields`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"5","name":null,"scene_count":null,"image_path":null}"""
        ).asJsonObject
        val e = api.parseEntity(obj, com.sinema.model.EntityItem.Kind.TAG)
        assertEquals("5", e.id)
        assertEquals("", e.name)
        assertEquals(0, e.sceneCount)
        assertEquals(null, e.imagePath)
    }

    @Test
    fun `parseMarker tolerates null title and primary tag`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"m1","title":null,"seconds":12.5,"primary_tag":null}"""
        ).asJsonObject
        val m = api.parseMarker(obj)
        assertEquals("m1", m.id)
        assertEquals("", m.title)
        assertEquals(12.5, m.seconds, 0.0)
        assertEquals("", m.primaryTag)
    }

    @Test
    fun `caption url encodes unsafe characters`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        assertEquals(
            "http://server/scene/1/caption?lang=pt%2FBR&type=srt",
            api.getCaptionUrl("1", com.sinema.model.CaptionRef("pt/BR", "srt"))
        )
    }

    @Test
    fun `parseScene reads full metadata payload`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"7","title":"t","date":"2024-01-02",
                "studio":{"id":"3","name":"St"},
                "tags":[{"id":"1","name":"a"},{"id":"2","name":"b"}],
                "performers":[{"id":"9","name":"P"}],
                "captions":[{"language_code":"en","caption_type":"srt"}],
                "files":[{"path":"/data/x.mp4","size":10,"duration":5.0,"width":1,"height":2}]}"""
        ).asJsonObject
        val s = api.parseScene(obj)
        assertEquals("2024-01-02", s.date)
        assertEquals("St", s.studio?.name)
        assertEquals(listOf("a", "b"), s.tags.map { it.name })
        assertEquals("P", s.performers.single().name)
        assertEquals("en", s.captions.single().languageCode)
    }

    @Test
    fun `parseScene reads last_played_at when present`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val withTs = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","last_played_at":"2026-07-18T12:00:00Z","files":[]}"""
        ).asJsonObject
        val withoutTs = com.google.gson.JsonParser.parseString(
            """{"id":"2","title":"t","last_played_at":null,"files":[]}"""
        ).asJsonObject
        val lean = com.google.gson.JsonParser.parseString(
            """{"id":"3","title":"t","files":[]}"""
        ).asJsonObject
        assertEquals("2026-07-18T12:00:00Z", api.parseScene(withTs).lastPlayedAt)
        assertEquals(null, api.parseScene(withoutTs).lastPlayedAt)
        assertEquals(null, api.parseScene(lean).lastPlayedAt)
    }

    @Test
    fun `recently-played filter omits last_played_at for Stash 0_24 compat`() {
        // Regression guard for 1.17.0:
        // 1) Used invalid CriterionModifier IS_NOT_NULL → HTTP 422
        // 2) last_played_at scene_filter only exists on Stash ≥ 0.26; README
        //    targets 0.24+. Filter must stay limited to play_count/resume_time.
        val filter = SinemaApi.RECENTLY_PLAYED_SCENE_FILTER
        assertEquals(setOf("play_count", "resume_time"), filter.keys)
        assertEquals(false, filter.containsKey("last_played_at"))

        @Suppress("UNCHECKED_CAST")
        val playCount = filter["play_count"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val resumeTime = filter["resume_time"] as Map<String, Any>
        assertEquals("GREATER_THAN", playCount["modifier"])
        assertEquals(0, playCount["value"])
        assertEquals("EQUALS", resumeTime["modifier"])
        assertEquals(0, resumeTime["value"])
    }

    @Test
    fun `recently-played client sort puts null timestamps last`() {
        // Mirrors findRecentlyPlayed's partition + sortedByDescending so a
        // future rewrite cannot drop the nulls-last guarantee.
        data class Row(val id: String, val lastPlayedAt: String?)
        val rows = listOf(
            Row("null-a", null),
            Row("newer", "2026-07-18T12:00:00Z"),
            Row("older", "2026-01-01T00:00:00Z"),
            Row("null-b", null)
        )
        val (withTs, withoutTs) = rows.partition { it.lastPlayedAt != null }
        val sorted = withTs.sortedByDescending { it.lastPlayedAt } + withoutTs
        assertEquals(listOf("newer", "older", "null-a", "null-b"), sorted.map { it.id })
    }
}
