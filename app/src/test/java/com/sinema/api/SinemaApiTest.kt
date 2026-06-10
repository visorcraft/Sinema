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
}
