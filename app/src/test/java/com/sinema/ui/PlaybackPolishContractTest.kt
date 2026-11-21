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

    @Test
    fun `controller uses polished HUD surfaces and player views show buffering`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_controller.xml")))
        val surface = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_player_surface.xml")))
        val texture = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_player_texture.xml")))

        assertTrue(layout.contains("@drawable/playback_polish_top_bar"))
        assertTrue(layout.contains("@drawable/playback_polish_bottom_bar"))
        assertTrue(layout.contains("@drawable/playback_polish_center_controls_bg"))
        assertTrue(layout.contains("@drawable/playback_polish_control_bg"))
        assertTrue(layout.contains("@drawable/playback_polish_icon_button_bg"))
        assertTrue(surface.contains("app:show_buffering=\"when_playing\""))
        assertTrue(texture.contains("app:show_buffering=\"when_playing\""))
    }
}
