package com.sinema.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSaveQueueTest {
    @Test
    fun savesExecuteInCaptureOrderEvenWhenTheFirstIsSlow() = runBlocking {
        val queue = PlaybackSaveQueue(this)
        val calls = mutableListOf<String>()

        queue.enqueue {
            delay(100)
            calls += "first"
        }
        queue.enqueue {
            calls += "second"
        }
        queue.close()
        queue.join()

        assertEquals(listOf("first", "second"), calls)
    }
}
