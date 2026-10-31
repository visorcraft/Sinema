package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneDisplayTitleTest {
    private fun scene(title: String) = Scene(
        id = "1",
        title = title,
        path = "/data/folder/Filename.mkv",
        size = 1L,
        duration = 60.0,
        width = 1920,
        height = 1080
    )

    @Test
    fun `nonblank Stash title is preferred`() {
        assertEquals("Friendly title", scene("Friendly title").displayTitle)
    }

    @Test
    fun `blank title falls back to filename`() {
        assertEquals("Filename.mkv", scene("").displayTitle)
        assertEquals("Filename.mkv", scene("   ").displayTitle)
    }
}
