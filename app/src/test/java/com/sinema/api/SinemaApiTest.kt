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
    fun `parseScene tolerates JSON-null captions`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","captions":null,"tags":null,"performers":null,"files":[]}"""
        ).asJsonObject
        val scene = api.parseSceneForTest(obj)
        assertEquals(emptyList<CaptionRef>(), scene.captions)
    }

    @Test
    fun `parseScene tolerates JSON-null tags`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","captions":null,"tags":null,"performers":null,"files":[]}"""
        ).asJsonObject
        val scene = api.parseSceneForTest(obj)
        assertEquals(emptyList<com.sinema.model.TagRef>(), scene.tags)
    }

    @Test
    fun `parseScene tolerates JSON-null performers`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","captions":null,"tags":null,"performers":null,"files":[]}"""
        ).asJsonObject
        val scene = api.parseSceneForTest(obj)
        assertEquals(emptyList<com.sinema.model.PerformerRef>(), scene.performers)
    }
}
