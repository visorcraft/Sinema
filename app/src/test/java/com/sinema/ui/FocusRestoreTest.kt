package com.sinema.ui

import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusRestoreTest {

    private fun scene(id: String) = Scene(id = id, title = "t$id", path = "/v/$id.mp4",
        size = 1L, duration = 1.0, width = 1, height = 1)

    @Test
    fun `no previous focus means no restore`() {
        assertEquals(-1, FocusRestore.positionAfterReload(null, -1, listOf("a", "b")))
    }

    @Test
    fun `empty list means no restore`() {
        assertEquals(-1, FocusRestore.positionAfterReload("a", 0, emptyList()))
    }

    @Test
    fun `same item is found after reorder`() {
        val keys = listOf("x", "y", "z")
        assertEquals(2, FocusRestore.positionAfterReload("z", 0, keys))
    }

    @Test
    fun `removed item falls back to same index`() {
        val keys = listOf("a", "b", "c", "d")
        assertEquals(2, FocusRestore.positionAfterReload("gone", 2, keys))
    }

    @Test
    fun `removed last item clamps to new last index`() {
        val keys = listOf("a", "b")
        assertEquals(1, FocusRestore.positionAfterReload("gone", 3, keys))
    }

    @Test
    fun `scene key uses id not title`() {
        val a = scene("42")
        val sameTitleDifferentId = scene("99")
        assertEquals(FocusRestore.keyOf(a), FocusRestore.keyOf(a.copy(title = "renamed")))
        assertEquals(false, FocusRestore.keyOf(a) == FocusRestore.keyOf(sameTitleDifferentId))
    }

    @Test
    fun `string items have stable keys`() {
        assertEquals(FocusRestore.keyOf("Settings"), FocusRestore.keyOf("Settings"))
        assertEquals(false, FocusRestore.keyOf("Settings") == FocusRestore.keyOf("Search"))
    }
}
