package com.sinema.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionTest {
    @Test
    fun `session codec round trips queue and position`() {
        val session = PlaybackSession(
            profileId = "profile",
            sceneId = "b",
            title = "Scene B",
            positionMs = 12_000,
            queueItems = listOf(
                PlaybackQueue.Item("a", "A", 60.0),
                PlaybackQueue.Item("b", "B", 60.0)
            ),
            queueIndex = 1
        )

        assertEquals(session, PlaybackSessionCodec.decode(PlaybackSessionCodec.encode(session)))
    }

    @Test
    fun `malformed json is rejected`() {
        assertNull(PlaybackSessionCodec.decode("not json"))
    }

    @Test
    fun `incomplete session metadata is rejected`() {
        val json = """{"version":1,"session":{"profileId":"p","sceneId":"s","positionMs":0,"queueItems":[],"queueIndex":-1}}"""

        assertNull(PlaybackSessionCodec.decode(json))
    }

    @Test
    fun `incomplete queue metadata is rejected`() {
        val json = """{"version":1,"session":{"profileId":"p","sceneId":"s","title":"Scene","positionMs":5000,"queueItems":[{"id":"s","durationSec":60.0,"playCount":0}],"queueIndex":0}}"""

        assertNull(PlaybackSessionCodec.decode(json))
    }

    @Test
    fun `session without a scene is rejected`() {
        val session = PlaybackSession("profile", "", "Blank", 0L, emptyList(), -1)

        assertNull(PlaybackSessionCodec.decode(PlaybackSessionCodec.encode(session)))
    }

    @Test
    fun `invalid queue index is rejected`() {
        val session = PlaybackSession(
            "profile", "b", "Scene B", 0L,
            listOf(PlaybackQueue.Item("b", "B", 60.0)), 4
        )

        assertNull(PlaybackSessionCodec.decode(PlaybackSessionCodec.encode(session)))
    }

    @Test
    fun `restore requires matching profile and unlocked private session`() {
        val session = PlaybackSession("profile", "scene", "Scene", 12_000L, emptyList(), -1)

        assertTrue(PlaybackSessionRestore.isEligible(session, "profile", hasPin = false, pinVerified = false, incognito = false))
        assertFalse(PlaybackSessionRestore.isEligible(session, "other", hasPin = false, pinVerified = false, incognito = false))
        assertFalse(PlaybackSessionRestore.isEligible(session, "profile", hasPin = true, pinVerified = false, incognito = false))
        assertFalse(PlaybackSessionRestore.isEligible(session, "profile", hasPin = false, pinVerified = false, incognito = true))
        assertFalse(PlaybackSessionRestore.isEligible(null, "profile", hasPin = false, pinVerified = false, incognito = false))
    }

    @Test
    fun `zero position single scene is not a restorable interruption`() {
        val session = PlaybackSession("profile", "scene", "Scene", 0L, emptyList(), -1)

        assertFalse(PlaybackSessionRestore.isEligible(session, "profile", hasPin = false, pinVerified = false, incognito = false))
    }

    @Test
    fun `persistence uses the same five second floor as watch history`() {
        assertFalse(PlaybackSessionRestore.shouldPersist(4_999L))
        assertTrue(PlaybackSessionRestore.shouldPersist(5_000L))
    }
}
