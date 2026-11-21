package com.sinema.ui

import com.sinema.model.GalleryItem
import com.sinema.model.ImageItem
import com.sinema.model.SavedSceneFilter
import com.sinema.model.Scene
import com.sinema.model.SceneMarkerItem
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

    @Test
    fun `gallery image marker and saved filter keys stay stable across field edits`() {
        val gallery = GalleryItem(id = "g1", title = "Old", imageCount = 3)
        assertEquals(FocusRestore.keyOf(gallery), FocusRestore.keyOf(gallery.copy(title = "New", imageCount = 9)))

        val image = ImageItem(id = "img1", title = "A", path = "/a.jpg")
        assertEquals(FocusRestore.keyOf(image), FocusRestore.keyOf(image.copy(title = "B", rating100 = 100)))

        val marker = SceneMarkerItem("m1", "Intro", 12.0, "s1", "Scene", 1920, 1080, "t1", "Tag")
        assertEquals(FocusRestore.keyOf(marker), FocusRestore.keyOf(marker.copy(title = "Renamed")))

        val filter = SavedSceneFilter(id = "f1", name = "Favorites")
        assertEquals(FocusRestore.keyOf(filter), FocusRestore.keyOf(filter.copy(name = "Top Favorites")))
    }
}
