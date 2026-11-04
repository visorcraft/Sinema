package com.sinema.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvProviderIdentityTest {
    @Test
    fun `new ids contain profile and scene`() {
        assertEquals("sinema:profile-a:scene-9", providerId("profile-a", "scene-9"))
    }

    @Test
    fun `cleanup accepts legacy and namespaced sinema ids only`() {
        assertTrue(isSinemaProviderId("sinema:scene-9"))
        assertTrue(isSinemaProviderId("sinema:profile-a:scene-9"))
        assertFalse(isSinemaProviderId("other:scene-9"))
        assertFalse(isSinemaProviderId(null))
    }

    @Test
    fun `stale identity is rejected`() {
        assertTrue(isActiveProfile("profile-a", "profile-a"))
        assertFalse(isActiveProfile("profile-a", "profile-b"))
        assertFalse(isActiveProfile("", "profile-b"))
    }

    @Test
    fun `active server requires matching profile and normalized url`() {
        assertTrue(isActiveServer("profile-a", "http://server-a/", "profile-a", "http://server-a"))
        assertFalse(isActiveServer("profile-a", "http://server-a", "profile-b", "http://server-a"))
        assertFalse(isActiveServer("profile-a", "http://server-a", "profile-a", "http://server-b"))
    }
}
