package com.sinema.ui

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
    fun `seek presets synchronize ExoPlayer visible increments`() {
        val activity = String(Files.readAllBytes(Paths.get("src/main/java/com/sinema/ui/PlaybackActivity.kt")))

        assertTrue(activity.contains("setSeekBackIncrementMs(seekPreset.backwardMs)"))
        assertTrue(activity.contains("setSeekForwardIncrementMs(seekPreset.forwardMs)"))
        assertTrue(activity.contains("applySeekPreset(exo)"))
    }
}
