package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Watch-progress bar math for scene cards (see Scene.watchProgressPercent).
 */
class SceneWatchProgressTest {

    private fun scene(resumeTime: Double, duration: Double) = Scene(
        id = "1",
        title = "t",
        path = "/data/a.mp4",
        size = 1L,
        duration = duration,
        width = 1920,
        height = 1080,
        resumeTime = resumeTime
    )

    @Test
    fun `no progress without resume time or duration`() {
        assertEquals(0, scene(resumeTime = 0.0, duration = 100.0).watchProgressPercent)
        assertEquals(0, scene(resumeTime = 30.0, duration = 0.0).watchProgressPercent)
    }

    @Test
    fun `progress reflects resume fraction`() {
        assertEquals(30, scene(resumeTime = 30.0, duration = 100.0).watchProgressPercent)
        assertEquals(50, scene(resumeTime = 500.0, duration = 1000.0).watchProgressPercent)
    }

    @Test
    fun `barely started scene shows no bar`() {
        assertEquals(0, scene(resumeTime = 1.0, duration = 100.0).watchProgressPercent)
    }

    @Test
    fun `nearly finished scene shows no bar`() {
        assertEquals(0, scene(resumeTime = 99.5, duration = 100.0).watchProgressPercent)
        assertEquals(0, scene(resumeTime = 98.0, duration = 100.0).watchProgressPercent)
    }

    @Test
    fun `fraction floors to a whole percent when shown`() {
        assertEquals(2, scene(resumeTime = 2.5, duration = 100.0).watchProgressPercent)
        assertEquals(97, scene(resumeTime = 97.0, duration = 100.0).watchProgressPercent)
    }
}
