package com.sinema.util

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import androidx.tvprovider.media.tv.TvContractCompat
import com.sinema.SinemaApp
import com.sinema.api.testScene
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
class TvChannelsIsolationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        RecordingProvider.reset()
        registerProviders()
        SinemaApp.instance.prefs.channelsEnabled = true
        SinemaApp.instance.prefs.activeProfileId = "profile-a"
    }

    @Test
    fun `stale syncs do not read delete or insert launcher content`() {
        SinemaApp.instance.prefs.activeProfileId = "profile-b"
        val scene = testScene("9")

        TvChannels.syncWatchNext(context, listOf(scene to 10.0), "profile-a")
        TvChannels.syncRecentlyAdded(context, listOf(scene), "profile-a")

        assertEquals(0, RecordingProvider.queries)
        assertEquals(emptyList<Long>(), RecordingProvider.deletes)
        assertEquals(emptyList<ContentValues>(), RecordingProvider.inserts)
    }

    @Test
    fun `clear removes legacy and namespaced sinema entries only`() {
        RecordingProvider.watchRows[1L] = "sinema:legacy"
        RecordingProvider.watchRows[2L] = "other:scene"
        RecordingProvider.watchRows[3L] = "sinema:profile-a:new"

        TvChannels.clearAll(context)

        assertEquals(setOf(1L, 3L), RecordingProvider.deletes.toSet())
        assertEquals(mapOf(2L to "other:scene"), RecordingProvider.watchRows)
    }

    @Test
    fun `matching watch next sync writes profile namespaced ids`() {
        val scene = testScene("9")
        TvChannels.syncWatchNext(context, listOf(scene to 10.0), "profile-a")

        assertEquals(1, RecordingProvider.inserts.size)
        assertEquals(
            "sinema:profile-a:9",
            RecordingProvider.inserts.single().getAsString(
                TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
            )
        )
    }

    private fun registerProviders() {
        Robolectric.buildContentProvider(RecordingProvider::class.java)
            .create(TvContractCompat.WatchNextPrograms.CONTENT_URI.authority).get()
        Robolectric.buildContentProvider(RecordingProvider::class.java)
            .create(TvContractCompat.Channels.CONTENT_URI.authority).get()
    }

    class RecordingProvider : ContentProvider() {
        companion object {
            val watchRows = mutableMapOf<Long, String>()
            val deletes = mutableListOf<Long>()
            val inserts = mutableListOf<ContentValues>()
            var queries = 0

            fun reset() {
                watchRows.clear()
                deletes.clear()
                inserts.clear()
                queries = 0
            }
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            queries++
            return if (uri.authority == TvContractCompat.WatchNextPrograms.CONTENT_URI.authority) {
                MatrixCursor(arrayOf(
                    BaseColumns._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                )).apply {
                    watchRows.forEach { (id, providerId) -> addRow(listOf(id, providerId)) }
                }
            } else {
                MatrixCursor(arrayOf(BaseColumns._ID, TvContractCompat.Channels.COLUMN_DISPLAY_NAME))
            }
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val id = ContentUris.parseId(uri)
            deletes += id
            watchRows.remove(id)
            return 1
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserts += ContentValues(values)
            return ContentUris.withAppendedId(uri, inserts.size.toLong())
        }

        override fun getType(uri: Uri): String? = null
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }
}
