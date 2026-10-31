package com.sinema.ui

import com.sinema.model.GroupItem
import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GroupPlaybackTest {

    @Test
    fun `empty group has no resume index`() {
        assertEquals(-1, GroupPlayback.resumeIndex(emptyList()))
    }

    @Test
    fun `partially watched scene is resumed before unwatched scenes`() {
        val scenes = listOf(
            scene("1", playCount = 0),
            scene("2", playCount = 0),
            scene("3", playCount = 1, resumeTime = 42.0),
            scene("4", playCount = 0, resumeTime = 10.0)
        )
        assertEquals(2, GroupPlayback.resumeIndex(scenes))
    }

    @Test
    fun `first unwatched scene is next when nothing is partially watched`() {
        val scenes = listOf(
            scene("1", playCount = 2),
            scene("2", playCount = 0),
            scene("3", playCount = 0)
        )
        assertEquals(1, GroupPlayback.resumeIndex(scenes))
    }

    @Test
    fun `fully watched group starts at the beginning`() {
        val scenes = listOf(scene("1", playCount = 1), scene("2", playCount = 3))
        assertEquals(0, GroupPlayback.resumeIndex(scenes))
    }

    @Test
    fun `resume position uses saved seconds only for meaningful progress`() {
        assertEquals(42_000L, GroupPlayback.resumePositionMs(scene("1", playCount = 1, resumeTime = 42.0)))
        assertEquals(0L, GroupPlayback.resumePositionMs(scene("2", playCount = 0, resumeTime = 2.0)))
    }

    @Test
    fun `group focus key uses stable id`() {
        val group = GroupItem(id = "7", name = "Before", sceneCount = 2)
        assertEquals("group:7", FocusRestore.keyOf(group))
        assertEquals(FocusRestore.keyOf(group), FocusRestore.keyOf(group.copy(name = "After")))
        assertNotEquals(FocusRestore.keyOf(group), FocusRestore.keyOf(group.copy(id = "8")))
    }

    private fun scene(id: String, playCount: Int, resumeTime: Double = 0.0) = Scene(
        id = id,
        title = "Scene $id",
        path = "/data/$id.mp4",
        size = 1,
        duration = 100.0,
        width = 1920,
        height = 1080,
        playCount = playCount,
        resumeTime = resumeTime
    )
}
