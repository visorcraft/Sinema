package com.sinema.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.tvprovider.media.tv.TvContractCompat
import com.sinema.SinemaApp
import com.sinema.util.TvChannelsIsolationTest
import org.junit.Assert.assertFalse
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

object ServerTransitionTestSupport {
    val context: Context get() = RuntimeEnvironment.getApplication()

    fun reset() {
        TvChannelsIsolationTest.RecordingProvider.reset()
        Robolectric.buildContentProvider(TvChannelsIsolationTest.RecordingProvider::class.java)
            .create(TvContractCompat.WatchNextPrograms.CONTENT_URI.authority).get()
        Robolectric.buildContentProvider(TvChannelsIsolationTest.RecordingProvider::class.java)
            .create(TvContractCompat.Channels.CONTENT_URI.authority).get()
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
        SinemaApp.instance.prefs.globalSearchEnabled = false
        seed()
    }

    fun seed() {
        TvChannelsIsolationTest.RecordingProvider.watchRows[1L] = "sinema:legacy"
    }

    fun assertCleared() {
        assertFalse(
            TvChannelsIsolationTest.RecordingProvider.watchRows.values.any {
                it.startsWith("sinema:")
            }
        )
    }

    fun button(root: View, text: String): Button = views(root)
        .filterIsInstance<Button>()
        .single { it.text.toString().startsWith(text) }

    fun buttons(root: View, text: String): List<Button> = views(root)
        .filterIsInstance<Button>()
        .filter { it.text.toString() == text }

    fun views(root: View): List<View> = buildList {
        fun add(view: View) {
            this@buildList.add(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) add(view.getChildAt(index))
            }
        }
        add(root)
    }
}
