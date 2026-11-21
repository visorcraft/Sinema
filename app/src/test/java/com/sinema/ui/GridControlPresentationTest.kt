package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GridControlPresentationTest {
    @Test
    fun `grid controls share static focused background and bold labels`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_grid.xml")))
        val ids = listOf("btn_group_resume", "btn_grid_filter", "btn_grid_watch", "btn_grid_fav", "btn_grid_sort", "btn_grid_search")
        ids.forEach { assertTrue(layout.contains("@+id/$it")) }
        assertEquals(6, Regex("@drawable/grid_control_background").findAll(layout).count())
        assertTrue(layout.contains("android:textStyle=\"bold\""))
    }
}
