package com.sinema.util

import com.sinema.api.testScene

import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TonightMixTest {

    private fun scene(id: String, durationSec: Double) = testScene(
        id = id,
        durationSec = durationSec
    )


    @Test
    fun `mix fills the time budget and keeps the scene that crosses it`() {
        val candidates = (1..20).map { scene("s$it", 300.0) } // 5 min each

        val mix = TonightMix.select(candidates, targetMinutes = 30, random = Random(1))

        // 6 x 5min = 30min exactly; 5 scenes (25min) would end short.
        assertEquals(6, mix.size)
        assertEquals(1800.0, mix.sumOf { it.duration }, 0.0)
        assertTrue(mix.size <= candidates.size)
    }

    @Test
    fun `mix is seeded deterministic and covers no duplicates`() {
        val candidates = (1..50).map { scene("s$it", 120.0) }

        val a = TonightMix.select(candidates, targetMinutes = 20, random = Random(42))
        val b = TonightMix.select(candidates, targetMinutes = 20, random = Random(42))

        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(a.size, a.map { it.id }.toSet().size)
    }

    @Test
    fun `degenerate inputs stay safe`() {
        assertEquals(emptyList<Scene>(), TonightMix.select(emptyList()))
        // Only zero-duration scenes: exactly one becomes the mix.
        val weird = listOf(scene("a", 0.0), scene("b", 0.0))
        assertEquals(1, TonightMix.select(weird, random = Random(3)).size)
        // Summary rounding floor.
        assertEquals("1 scene · ~1 min", TonightMix.summary(listOf(scene("a", 30.0))))
        assertEquals("Nothing to play", TonightMix.summary(emptyList()))
    }
}

class DiscreetModeTest {
    @Test
    fun `neutral titles are stable and numbered`() {
        val a = DiscreetMode.neutralTitle("scene-42")
        assertEquals(a, DiscreetMode.neutralTitle("scene-42"))
        assertTrue(a.matches(Regex("Scene \\d{1,4}")))
        assertEquals("Scene 0", DiscreetMode.neutralTitle(""))
    }
}
