package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDraftRetentionTest {
    @Test
    fun `first draft is created for the active profile`() {
        assertTrue(shouldResetServerSettingsDraft(null, "profile-a"))
    }

    @Test
    fun `recreation keeps a draft for the same profile`() {
        assertFalse(shouldResetServerSettingsDraft("profile-a", "profile-a"))
    }

    @Test
    fun `active profile change resets the old credentials draft`() {
        assertTrue(shouldResetServerSettingsDraft("profile-a", "profile-b"))
    }
}
