package com.sinema.util

import android.content.Context
import androidx.tvprovider.media.tv.TvContractCompat
import com.sinema.SinemaApp
import com.sinema.api.testScene
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SystemIntegrationsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        TvChannelsIsolationTest.RecordingProvider.reset()
        Robolectric.buildContentProvider(TvChannelsIsolationTest.RecordingProvider::class.java)
            .create(TvContractCompat.WatchNextPrograms.CONTENT_URI.authority).get()
        Robolectric.buildContentProvider(TvChannelsIsolationTest.RecordingProvider::class.java)
            .create(TvContractCompat.Channels.CONTENT_URI.authority).get()
        SinemaApp.instance.prefs.channelsEnabled = true
        SinemaApp.instance.prefs.activeProfileId = "profile-a"
        SinemaApp.instance.prefs.serverUrl = "http://server-a"
        TvChannelsIsolationTest.RecordingProvider.watchRows[1L] = "sinema:legacy"
    }

    @Test
    fun `profile mutation and clear hold lock against stale sync`() {
        assertAtomicTransition(activeProfileId = "profile-b", serverUrl = "http://server-b")
    }

    @Test
    fun `same profile endpoint mutation and clear hold lock against stale sync`() {
        assertAtomicTransition(activeProfileId = "profile-a", serverUrl = "http://server-b")
    }

    private fun assertAtomicTransition(activeProfileId: String, serverUrl: String) {
        val mutationEntered = CountDownLatch(1)
        val finishMutation = CountDownLatch(1)
        val transition = Thread {
            SystemIntegrations.onActiveServerChanged(context) {
                mutationEntered.countDown()
                assertTrue(finishMutation.await(2, TimeUnit.SECONDS))
                SinemaApp.instance.prefs.activeProfileId = activeProfileId
                SinemaApp.instance.prefs.serverUrl = serverUrl
            }
        }
        transition.start()
        assertTrue(mutationEntered.await(2, TimeUnit.SECONDS))
        val staleSync = Thread {
            TvChannels.syncWatchNext(
                context,
                listOf(testScene("old") to 10.0),
                expectedProfileId = "profile-a",
                expectedServerUrl = "http://server-a"
            )
        }
        staleSync.start()
        finishMutation.countDown()
        transition.join(2_000)
        staleSync.join(2_000)

        assertTrue(TvChannelsIsolationTest.RecordingProvider.inserts.isEmpty())
        assertTrue(TvChannelsIsolationTest.RecordingProvider.watchRows.isEmpty())
        assertEquals(activeProfileId, SinemaApp.instance.prefs.activeProfileId)
        assertEquals(serverUrl, SinemaApp.instance.prefs.serverUrl)
    }
}
