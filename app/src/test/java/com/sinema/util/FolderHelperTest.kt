package com.sinema.util

import com.sinema.model.ImageItem
import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderHelperTest {

    private fun scene(id: String, path: String) = Scene(
        id = id, title = "", path = path, size = 0, duration = 0.0, width = 0, height = 0
    )

    private fun image(id: String, path: String) = ImageItem(
        id = id, title = "", path = path
    )

    @Test
    fun `folders first then videos then pictures`() {
        val scenes = listOf(
            scene("s1", "/data/root/zz-video.mp4"),
            scene("s2", "/data/root/aa-video.mp4")
        )
        val images = listOf(
            image("i1", "/data/root/aa-picture.jpg"),
            image("i2", "/data/root/zz-picture.jpg")
        )
        val items = FolderHelper.buildFolderContents(scenes, images, "/data/root")

        // Videos preserve API input order (z before a); images stay alphabetical.
        assertEquals(
            listOf("zz-video.mp4", "aa-video.mp4", "aa-picture.jpg", "zz-picture.jpg"),
            items.map { it.name }
        )
        assertTrue(items.take(2).all { it.scene != null })
        assertTrue(items.drop(2).all { it.image != null })
    }

    @Test
    fun `video order from API is preserved so sort picker takes effect`() {
        // Simulate the API returning scenes in reverse-alpha order (e.g. sort=title DESC).
        // buildFolderContents must NOT re-sort them; the caller owns the order.
        val scenes = listOf(
            scene("s1", "/data/root/z.mp4"),
            scene("s2", "/data/root/a.mp4")
        )
        val items = FolderHelper.buildFolderContents(scenes, emptyList(), "/data/root")

        assertEquals(listOf("z.mp4", "a.mp4"), items.map { it.name })
    }

    @Test
    fun `mixed folders videos and pictures are grouped and sorted`() {
        val scenes = listOf(
            scene("s1", "/data/root/video.mp4"),
            scene("s2", "/data/root/bbb/nested.mp4")
        )
        val images = listOf(
            image("i1", "/data/root/picture.jpg"),
            image("i2", "/data/root/aaa/nested.jpg")
        )
        val items = FolderHelper.buildFolderContents(scenes, images, "/data/root")

        assertEquals(listOf("aaa", "bbb", "video.mp4", "picture.jpg"), items.map { it.name })
        assertTrue(items[0].isFolder)
        assertTrue(items[1].isFolder)
    }

    @Test
    fun `subfolder containing only pictures is not empty`() {
        // The "empty folder" bug: a folder whose nested subfolders hold only
        // pictures must still list those subfolders.
        val images = listOf(
            image("i1", "/data/root/photos/2023/a.jpg"),
            image("i2", "/data/root/photos/2023/b.jpg"),
            image("i3", "/data/root/photos/c.jpg")
        )
        val items = FolderHelper.buildFolderContents(emptyList(), images, "/data/root")

        assertEquals(1, items.size)
        val folder = items[0]
        assertTrue(folder.isFolder)
        assertEquals("photos", folder.name)
        assertEquals("/data/root/photos", folder.fullPath)
        assertEquals(3, folder.childCount)
        assertNotNull(folder.firstImageId)
    }

    @Test
    fun `subfolder count includes both videos and pictures`() {
        val scenes = listOf(scene("s1", "/data/root/sub/v.mp4"))
        val images = listOf(image("i1", "/data/root/sub/p.jpg"))
        val items = FolderHelper.buildFolderContents(scenes, images, "/data/root")

        assertEquals(1, items.size)
        assertEquals(2, items[0].childCount)
        assertEquals("s1", items[0].firstSceneId)
        assertEquals("i1", items[0].firstImageId)
    }

    @Test
    fun `trailing slash on current path is normalized`() {
        val images = listOf(image("i1", "/data/root/p.jpg"))
        val items = FolderHelper.buildFolderContents(emptyList(), images, "/data/root/")

        assertEquals(1, items.size)
        assertEquals("p.jpg", items[0].name)
        assertNotNull(items[0].image)
    }

    @Test
    fun `sibling folders with shared prefix are not merged`() {
        // "/data/Movies" must not swallow files from "/data/Movies2"
        val scenes = listOf(scene("s1", "/data/Movies2/v.mp4"))
        val items = FolderHelper.buildFolderContents(scenes, emptyList(), "/data/Movies")
        assertEquals(0, items.size)
    }
}
