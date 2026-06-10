package com.sinema.api

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
}
