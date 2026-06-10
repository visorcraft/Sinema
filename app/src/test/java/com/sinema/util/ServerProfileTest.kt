package com.sinema.util

import com.sinema.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerProfileTest {
    @Test
    fun `round trips profile list through json`() {
        val list = listOf(ServerProfile("u1", "A", "http://a", "k", "", "apikey", "", ""))
        assertEquals(list, ProfileCodec.fromJson(ProfileCodec.toJson(list)))
    }

    @Test
    fun `fromJson tolerates garbage`() {
        assertEquals(emptyList<ServerProfile>(), ProfileCodec.fromJson("not json"))
    }

    @Test
    fun `round trips multi-profile list`() {
        val list = listOf(
            ServerProfile("u1", "Home", "http://home", "k1", "", "apikey", "u", "p"),
            ServerProfile("u2", "Office", "http://office", "", "sid=abc", "session", "", "")
        )
        val json = ProfileCodec.toJson(list)
        val decoded = ProfileCodec.fromJson(json)
        assertEquals(2, decoded.size)
        assertEquals("Home", decoded[0].name)
        assertEquals("Office", decoded[1].name)
        assertEquals(list, decoded)
    }

    @Test
    fun `round trips empty list`() {
        assertEquals(emptyList<ServerProfile>(), ProfileCodec.fromJson(ProfileCodec.toJson(emptyList())))
    }

    @Test
    fun `default values work for forward compatibility`() {
        val p = ServerProfile()
        assertNotNull(p.id)
        assertEquals("", p.name)
        assertEquals("apikey", p.authMode)
    }

    @Test
    fun `partial json fills defaults`() {
        val json = """[{"name":"Partial"}]"""
        val decoded = ProfileCodec.fromJson(json)
        assertEquals(1, decoded.size)
        assertEquals("Partial", decoded[0].name)
        assertEquals("", decoded[0].serverUrl)
        assertEquals("apikey", decoded[0].authMode)
    }
}
