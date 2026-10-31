package com.sinema.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PlaybackControllerContractTest {
    @Test
    fun `quick seek bindings match the included Media3 amount button ids`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_controller.xml")))
        val activity = String(Files.readAllBytes(Paths.get("src/main/java/com/sinema/ui/PlaybackActivity.kt")))

        assertTrue(layout.contains("@layout/exo_player_control_rewind_button"))
        assertTrue(layout.contains("@layout/exo_player_control_ffwd_button"))
        assertTrue(activity.contains("R.id.exo_rew_with_amount"))
        assertTrue(activity.contains("R.id.exo_ffwd_with_amount"))
    }

    @Test
    fun `marker navigation uses custom ids so Media3 cannot disable the buttons`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_controller.xml")))
        val activity = String(Files.readAllBytes(Paths.get("src/main/java/com/sinema/ui/PlaybackActivity.kt")))

        // exo_prev/exo_next are owned by PlayerControlView, which disables them
        // whenever the player has no previous/next media item — dead buttons
        // for Sinema's single-item playback. Marker nav must use custom ids.
        assertFalse(layout.contains("@id/exo_prev"))
        assertFalse(layout.contains("@id/exo_next"))
        assertTrue(layout.contains("@+id/playback_prev_marker_button"))
        assertTrue(layout.contains("@+id/playback_next_marker_button"))
        assertTrue(activity.contains("R.id.playback_prev_marker_button"))
        assertTrue(activity.contains("R.id.playback_next_marker_button"))
    }

    @Test
    fun `controller exposes the up next queue chip`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_controller.xml")))
        val activity = String(Files.readAllBytes(Paths.get("src/main/java/com/sinema/ui/PlaybackActivity.kt")))

        assertTrue(layout.contains("@+id/playback_queue_chip"))
        assertTrue(activity.contains("R.id.playback_queue_chip"))
        assertTrue(activity.contains("showUpNextDialog"))
    }
}
