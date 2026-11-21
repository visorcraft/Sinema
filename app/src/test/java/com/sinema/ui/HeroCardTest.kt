package com.sinema.ui

import com.sinema.api.testScene
import com.sinema.model.StudioRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroCardTest {

    @Test
    fun `resume hero shows the resume position`() {
        assertEquals("▶ RESUME  0:34", HeroCard.actionLabel(34_125))
        assertEquals("▶ RESUME  34:12", HeroCard.actionLabel(2_052_900))
        assertEquals("▶ RESUME  1:02:03", HeroCard.actionLabel(3_723_000))
    }

    @Test
    fun `hero without resume offers plain play`() {
        assertEquals("▶ PLAY", HeroCard.actionLabel(0))
        assertEquals("▶ PLAY", HeroCard.actionLabel(-5))
    }

    @Test
    fun `labels never leak formatting whitespace`() {
        assertTrue(HeroCard.actionLabel(90_000).isNotBlank())
        assertTrue(!HeroCard.actionLabel(0).contains("RESUME"))
    }

    @Test
    fun `eyebrow distinguishes continue watching from featured`() {
        assertEquals("CONTINUE WATCHING", HeroCard.eyebrowLabel(15_000L))
        assertEquals("FEATURED", HeroCard.eyebrowLabel(0L))
    }

    @Test
    fun `quality badge classifies common TV resolutions cleanly`() {
        assertEquals("4K", HeroCard.qualityBadge(3840, 2160))
        assertEquals("1440p", HeroCard.qualityBadge(2560, 1440))
        assertEquals("1080p", HeroCard.qualityBadge(1920, 1080))
        assertEquals("720p", HeroCard.qualityBadge(1280, 720))
        assertNull(HeroCard.qualityBadge(640, 480))
        assertNull(HeroCard.qualityBadge(0, 0))
    }

    @Test
    fun `progress percent honors resume position and suppresses edge noise`() {
        val scene = testScene(id = "s1").copy(duration = 1000.0)
        assertEquals(0, HeroCard.progressPercent(scene, 0L))
        assertEquals(0, HeroCard.progressPercent(scene, 10_000L)) // 1% (<2%)
        assertEquals(45, HeroCard.progressPercent(scene, 450_000L))
        assertEquals(0, HeroCard.progressPercent(scene, 990_000L)) // 99% (>=98%)
    }

    @Test
    fun `remaining label formats hours minutes and under a minute`() {
        val scene = testScene(id = "s1").copy(duration = 4500.0) // 75m
        assertEquals("1h 10m left", HeroCard.remainingLabel(scene, 300_000L)) // 70m left
        assertEquals("10m left", HeroCard.remainingLabel(scene, 3_900_000L)) // 10m left
        assertEquals("<1m left", HeroCard.remainingLabel(scene, 4_470_000L)) // 30s left
        assertNull(HeroCard.remainingLabel(scene, 0L))
    }

    @Test
    fun `meta line includes stars duration remaining and studio without duplicating filename`() {
        val titled = testScene(id = "s1", title = "Sunset Drive", rating100 = 80)
            .copy(duration = 1800.0, studio = StudioRef("st1", "Cinema Lab"))
        val titledMeta = HeroCard.metaLine(titled, 600_000L)
        assertTrue(titledMeta.contains("★★★★"))
        assertTrue(titledMeta.contains("30:00"))
        assertTrue(titledMeta.contains("20m left"))
        assertTrue(titledMeta.contains("Cinema Lab"))

        val untitled = testScene(id = "s2", title = "")
            .copy(duration = 600.0, path = "/data/only_filename.mp4", studio = null)
        val untitledMeta = HeroCard.metaLine(untitled, 0L)
        assertFalse(
            "Untitled scene should not repeat filename in meta when title already displays it",
            untitledMeta.contains("only_filename.mp4")
        )
    }
}
