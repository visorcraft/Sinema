package com.sinema.util

import com.sinema.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueuePlanningTest {
    private fun scene(id: String) = Scene(
        id = id,
        title = "Scene $id",
        path = "/$id.mp4",
        size = 1L,
        duration = 60.0,
        width = 1,
        height = 1
    )

    @Test
    fun `plain plan keeps order and honors startAt`() {
        val scenes = listOf(scene("a"), scene("b"), scene("c"))

        val fromTop = QueuePlanning.plan(scenes)
        assertEquals(listOf("a", "b", "c"), fromTop.ordered.map { it.id })
        assertFalse(fromTop.capped)

        val fromMiddle = QueuePlanning.plan(scenes, startAt = 1)
        assertEquals(listOf("b", "c"), fromMiddle.ordered.map { it.id })

        assertEquals(listOf("c"), QueuePlanning.plan(scenes, startAt = 99).ordered.map { it.id })
    }

    @Test
    fun `shuffle plan is seeded deterministic and covers every scene`() {
        val scenes = (0 until 20).map { scene("s$it") }

        val first = QueuePlanning.plan(scenes, shuffle = true, random = Random(7))
        val second = QueuePlanning.plan(scenes, shuffle = true, random = Random(7))

        assertEquals(first.ordered.map { it.id }, second.ordered.map { it.id })
        assertEquals(scenes.map { it.id }.toSet(), first.ordered.map { it.id }.toSet())
    }

    @Test
    fun `plan caps the queue and flags it`() {
        val scenes = (0 until QueuePlanning.MAX_QUEUE + 5).map { scene("s$it") }

        val plan = QueuePlanning.plan(scenes)

        assertTrue(plan.capped)
        assertEquals(QueuePlanning.MAX_QUEUE, plan.ordered.size)
        assertEquals((0 until QueuePlanning.MAX_QUEUE).map { "s$it" }, plan.ordered.map { it.id })
    }

    @Test
    fun `empty scenes produce empty plan`() {
        val plan = QueuePlanning.plan(emptyList(), startAt = 3, shuffle = true)

        assertTrue(plan.ordered.isEmpty())
        assertFalse(plan.capped)
    }

    @Test
    fun `queue items carry display metadata for the up next list`() {
        val blank = scene("a").copy(title = "", path = "/folder/nice file.mp4")

        val items = QueuePlanning.toQueueItems(listOf(blank, scene("b")))

        assertEquals("nice file.mp4", items[0].title)
        assertEquals(60.0, items[0].durationSec, 0.0)
        assertEquals("b", items[1].id)
    }
}
