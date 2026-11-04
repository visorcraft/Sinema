package com.sinema

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class RecentErrorsConcurrencyTest {
    @Test
    fun `concurrent error writes produce bounded safe snapshots`() {
        val app = SinemaApp.instance
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writers = (0 until 2).map { worker ->
                executor.submit {
                    start.await()
                    repeat(500) { index -> app.noteError("error-$worker-$index") }
                }
            }
            start.countDown()
            repeat(1_000) { app.recentErrors.size }
            writers.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertTrue(app.recentErrors.size <= 20)
    }
}
