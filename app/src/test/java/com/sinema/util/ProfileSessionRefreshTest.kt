package com.sinema.util

import com.sinema.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSessionRefreshTest {
    private val profileA = ServerProfile(id = "a", name = "A", sessionCookie = "old-a")
    private val profileB = ServerProfile(id = "b", name = "B", sessionCookie = "old-b")

    @Test
    fun inactiveOriginUpdatesOnlyItsOwnProfile() {
        val result = routeSessionRefresh(
            profiles = listOf(profileA, profileB),
            activeProfileId = "b",
            originatingProfileId = "a",
            cookie = "new-a"
        )

        assertEquals("new-a", result.profiles.single { it.id == "a" }.sessionCookie)
        assertEquals("old-b", result.profiles.single { it.id == "b" }.sessionCookie)
        assertFalse(result.updateActiveCookie)
    }

    @Test
    fun activeOriginAlsoUpdatesLegacyActiveCookie() {
        val result = routeSessionRefresh(
            profiles = listOf(profileA, profileB),
            activeProfileId = "a",
            originatingProfileId = "a",
            cookie = "new-a"
        )

        assertEquals("new-a", result.profiles.single { it.id == "a" }.sessionCookie)
        assertTrue(result.updateActiveCookie)
    }

    @Test
    fun unknownOriginDoesNotInventAProfileOrChangeActiveCookie() {
        val result = routeSessionRefresh(
            profiles = listOf(profileA, profileB),
            activeProfileId = "b",
            originatingProfileId = "removed",
            cookie = "orphaned"
        )

        assertEquals(listOf(profileA, profileB), result.profiles)
        assertFalse(result.updateActiveCookie)
    }
}
