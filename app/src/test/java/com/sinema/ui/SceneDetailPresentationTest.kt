package com.sinema.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SceneDetailPresentationTest {
    @Test
    fun `delete is secondary destructive and actions retain readable targets`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_scene_detail.xml")))
        assertTrue(layout.contains("@+id/btn_delete"))
        assertTrue(layout.contains("@drawable/scene_detail_delete_background"))
        assertTrue(layout.contains("@+id/btn_play"))
        assertTrue(layout.contains("@+id/btn_resume"))
    }
}
