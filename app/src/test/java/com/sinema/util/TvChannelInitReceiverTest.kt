package com.sinema.util

import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat
import com.sinema.SinemaApp
import com.sinema.api.testSceneListResponse
import com.sinema.api.withGraphqlServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class TvChannelInitReceiverTest {
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
    }

    @Test
    fun `profile switch during receiver fetch suppresses old profile sync`() {
        val responseGate = CountDownLatch(1)
        withGraphqlServer(
            response = testSceneListResponse(),
            responseGate = responseGate,
            configureSinemaApp = true
        ) { _, requestBody ->
            try {
                context.applicationInfo.uid = android.os.Binder.getCallingUid()
                TvChannelInitReceiver().onReceive(
                    context,
                    Intent(TvContractCompat.ACTION_INITIALIZE_PROGRAMS)
                )
                awaitUntil("first receiver request") { requestBody().isNotBlank() }

                SinemaApp.instance.prefs.activeProfileId = "profile-b"
                responseGate.countDown()
                awaitUntil("second receiver request") { requestBody().contains("created_at") }
                Thread.sleep(100)

                assertEquals(0, TvChannelsIsolationTest.RecordingProvider.queries)
                assertEquals(
                    emptyList<android.content.ContentValues>(),
                    TvChannelsIsolationTest.RecordingProvider.inserts
                )
            } finally {
                responseGate.countDown()
            }
        }
    }

    private fun awaitUntil(label: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("$label was not observed within two seconds", condition())
    }
}
