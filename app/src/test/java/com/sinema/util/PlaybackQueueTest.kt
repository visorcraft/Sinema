package com.sinema.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackQueueTest {
    private fun item(id: String) = PlaybackQueue.Item(id, "Scene $id", 60.0)

    @After
    fun tearDown() {
        PlaybackQueue.clear()
    }

    @Test
    fun `start seeds queue and next walks it to exhaustion`() {
        PlaybackQueue.start(listOf(item("a"), item("b"), item("c")), startAt = 0)

        assertTrue(PlaybackQueue.isActive)
        assertEquals("a", PlaybackQueue.current()?.id)
        assertEquals(listOf("b", "c"), PlaybackQueue.remaining().map { it.id })
        assertEquals("b", PlaybackQueue.next()?.id)
        assertEquals("c", PlaybackQueue.next()?.id)
        assertNull(PlaybackQueue.next())
        assertFalse(PlaybackQueue.isActive)
    }

    @Test
    fun `startAt begins mid queue and remaining skips consumed items`() {
        PlaybackQueue.start(listOf(item("a"), item("b"), item("c")), startAt = 1)

        assertEquals("b", PlaybackQueue.current()?.id)
        assertEquals(listOf("c"), PlaybackQueue.remaining().map { it.id })
        assertEquals(1, PlaybackQueue.remainingCount())
    }

    @Test
    fun `jumpTo plays the selected item and consumes skipped ones`() {
        PlaybackQueue.start(listOf(item("a"), item("b"), item("c"), item("d")), startAt = 0)

        assertEquals("c", PlaybackQueue.jumpTo(1)?.id)
        assertEquals(listOf("d"), PlaybackQueue.remaining().map { it.id })
        assertNull(PlaybackQueue.jumpTo(99))
    }

    @Test
    fun `shuffleRemaining keeps current item first and randomizes the rest`() {
        val items = (0 until 12).map { item("s$it") }
        PlaybackQueue.start(items, startAt = 0)
        PlaybackQueue.next() // current = s1

        assertTrue(PlaybackQueue.shuffleRemaining(Random(42)))

        assertEquals("s1", PlaybackQueue.current()?.id)
        val remaining = PlaybackQueue.remaining().map { it.id }.toSet()
        assertEquals((2 until 12).map { "s$it" }.toSet(), remaining)

        // Shuffling again is fine while more than one item remains; it only
        // declines when the queue is down to at most one upcoming scene.
        assertTrue(PlaybackQueue.shuffleRemaining(Random(1)))
        PlaybackQueue.jumpTo(PlaybackQueue.remainingCount() - 1)
        assertFalse(PlaybackQueue.shuffleRemaining())
    }

    @Test
    fun `jumpToItem resolves by id regardless of live queue position`() {
        PlaybackQueue.start(listOf(item("a"), item("b"), item("c"), item("d")), startAt = 0)

        // Snapshot assumed index 1 = "b", but the queue advanced first.
        PlaybackQueue.next()
        assertEquals("c", PlaybackQueue.jumpToItem("c")?.id)
        assertEquals(listOf("d"), PlaybackQueue.remaining().map { it.id })

        assertNull(PlaybackQueue.jumpToItem("c")) // already current
        assertNull(PlaybackQueue.jumpToItem("a")) // already consumed
        assertNull(PlaybackQueue.jumpToItem("zzz")) // unknown
    }

    @Test
    fun `filterRemainingUnwatched keeps current and drops watched remainder`() {
        val items = listOf(
            PlaybackQueue.Item("cur", "Now", 60.0, playCount = 0),
            PlaybackQueue.Item("w1", "Watched 1", 60.0, playCount = 2),
            PlaybackQueue.Item("u1", "Unwatched 1", 60.0, playCount = 0),
            PlaybackQueue.Item("w2", "Watched 2", 60.0, playCount = 1),
            PlaybackQueue.Item("u2", "Unwatched 2", 60.0)
        )
        PlaybackQueue.start(items, startAt = 0)

        assertTrue(PlaybackQueue.filterRemainingUnwatched())

        assertEquals("cur", PlaybackQueue.current()?.id)
        assertEquals(listOf("u1", "u2"), PlaybackQueue.remaining().map { it.id })
        // Nothing left to drop on a second pass.
        assertFalse(PlaybackQueue.filterRemainingUnwatched())
    }

    @Test
    fun `filterRemainingUnwatched on all-unwatched queue is a no-op`() {
        PlaybackQueue.start(
            listOf(item("a"), item("b")),
            startAt = 0
        )

        assertFalse(PlaybackQueue.filterRemainingUnwatched())
        assertEquals(listOf("b"), PlaybackQueue.remaining().map { it.id })
    }

    @Test
    fun `empty queue is inert`() {
        PlaybackQueue.start(emptyList(), startAt = 0)

        assertFalse(PlaybackQueue.isActive)
        assertNull(PlaybackQueue.current())
        assertTrue(PlaybackQueue.remaining().isEmpty())
        assertNull(PlaybackQueue.jumpTo(0))
    }

    @Test
    fun `clear resets everything`() {
        PlaybackQueue.start(listOf(item("a"), item("b")), startAt = 0)
        PlaybackQueue.clear()

        assertFalse(PlaybackQueue.isActive)
        assertTrue(PlaybackQueue.remaining().isEmpty())
    }
}
