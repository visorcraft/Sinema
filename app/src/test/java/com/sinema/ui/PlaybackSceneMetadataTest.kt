package com.sinema.ui

import com.sinema.api.testScene
import com.sinema.model.SceneDetails
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackSceneMetadataTest {
    @Test
    fun `metadata clears before null result`() = runBlocking {
        val events = mutableListOf<String>()
        loadSceneMetadataForSwitch(
            clear = { events += "clear" },
            fetch = { events += "fetch"; null },
            apply = { events += "apply" }
        )
        assertEquals(listOf("clear", "fetch"), events)
    }

    @Test
    fun `metadata stays cleared on failure`() {
        val events = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            runBlocking {
                loadSceneMetadataForSwitch(
                    clear = { events += "clear" },
                    fetch = { events += "fetch"; throw IOException("offline") },
                    apply = { events += "apply" }
                )
            }
        }
        assertEquals(listOf("clear", "fetch"), events)
    }

    @Test
    fun `successful metadata applies only after clear`() = runBlocking {
        val details = SceneDetails(testScene("next"), 0.0, emptyList())
        val events = mutableListOf<String>()
        loadSceneMetadataForSwitch(
            clear = { events += "clear" },
            fetch = { details },
            apply = { events += "apply:${it.scene.id}" }
        )
        assertEquals(listOf("clear", "apply:next"), events)
    }

    @Test
    fun `cancellation propagates after clear without apply`() {
        val events = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                loadSceneMetadataForSwitch(
                    clear = { events += "clear" },
                    fetch = { throw CancellationException("cancelled") },
                    apply = { events += "apply" }
                )
            }
        }
        assertEquals(listOf("clear"), events)
    }
}
