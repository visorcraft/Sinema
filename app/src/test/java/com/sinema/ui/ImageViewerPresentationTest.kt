package com.sinema.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ImageViewerPresentationTest {
    @Test
    fun `viewer controls have rounded scrim and readable status text`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_image_view.xml")))
        assertTrue(layout.contains("@drawable/image_viewer_controls_background"))
        assertTrue(layout.contains("android:textSize=\"18sp\""))
        assertTrue(layout.contains("android:textSize=\"14sp\""))
        assertTrue(layout.contains("@+id/image_controls"))
    }
}
