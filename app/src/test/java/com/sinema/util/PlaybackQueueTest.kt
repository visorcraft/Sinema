package com.sinema.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {
    @Test
    fun `next walks the queue and returns null at the end`() {
        PlaybackQueue.start(listOf("1", "2", "3"), startAt = 0)
        assertEquals("2", PlaybackQueue.next())
        assertEquals("3", PlaybackQueue.next())
        assertNull(PlaybackQueue.next())
    }

    @Test
    fun `clear deactivates the queue`() {
        PlaybackQueue.start(listOf("1", "2"), startAt = 0)
        PlaybackQueue.clear()
        assertNull(PlaybackQueue.next())
    }

    @Test
    fun `start mid-list resumes from that position`() {
        PlaybackQueue.start(listOf("a", "b", "c"), startAt = 1)
        assertEquals("c", PlaybackQueue.next())
    }

    @Test
    fun `start at last item, next returns null`() {
        PlaybackQueue.start(listOf("a", "b"), startAt = 1)
        assertNull(PlaybackQueue.next())
    }

    @Test
    fun `start beyond last index clamps to last, next returns null`() {
        PlaybackQueue.start(listOf("a", "b"), startAt = 99)
        assertNull(PlaybackQueue.next())
    }
}
