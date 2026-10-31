package com.sinema.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Regression guard for the dashboard's core guarantee: one failed Stash row
 * query must not cancel or blank its sibling rows (see loadRow in
 * MainActivity.kt and the 1.17.0 blanking bug it prevents).
 */
class LoadRowTest {

    @Test
    fun `success carries the value`() = runBlocking {
        val result = loadRow { 42 }
        assertEquals(42, result.value)
        assertEquals(null, result.error)
    }

    @Test
    fun `failure is captured as error`() = runBlocking {
        val result = loadRow<String> { throw IOException("boom") }
        assertEquals(null, result.value)
        assertEquals("boom", result.error?.message)
    }

    @Test
    fun `failing row does not cancel sibling rows`() = runBlocking {
        coroutineScope {
            val good = async { loadRow { delay(200); "ok" } }
            val bad = async { loadRow<String> { throw IOException("boom") } }
            // Await the failing row first: if its exception could escape the
            // child and fail the scope, the sibling await below would throw.
            assertEquals("boom", bad.await().error?.message)
            assertEquals("ok", good.await().value)
        }
    }

    @Test
    fun `cancellation propagates instead of being swallowed`() = runBlocking {
        val job = launch {
            loadRow<String> { throw kotlinx.coroutines.CancellationException("stop") }
        }
        job.join()
        assertTrue(job.isCancelled)
    }
}
