package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCategoryTest {
    @Test
    fun `categories have a predictable remote friendly order`() {
        assertEquals(
            listOf("Server", "Home", "Playback", "Privacy", "System"),
            SettingsCategory.entries.map { it.title }
        )
    }
}
