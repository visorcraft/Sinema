package com.sinema.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PlaybackPolishContractTest {
    @Test
    fun `controller uses stronger scrim and readable timestamps`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_controller.xml")))
        assertTrue(layout.contains("@drawable/playback_polish_scrim"))
        assertTrue(layout.contains("@drawable/playback_polish_time_background"))
        assertTrue(layout.contains("android:id=\"@id/exo_progress\""))
        assertTrue(layout.contains("android:textSize=\"22sp\""))
    }
}
