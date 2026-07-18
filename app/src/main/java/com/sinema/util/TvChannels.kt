package com.sinema.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene

object TvChannels {
    private const val CHANNEL_NAME = "Sinema — Recently Added"
    private const val MAX_WATCH_NEXT = 10
    private const val MAX_RECENT = 20

    fun syncWatchNext(context: Context, continuePairs: List<Pair<Scene, Double>>) {
        val app = SinemaApp.instance
        if (!app.prefs.channelsEnabled) return
        try {
            val helper = PreviewChannelHelper(context)
            val resolver = context.contentResolver
            val uri = TvContractCompat.WatchNextPrograms.CONTENT_URI
            // Delete only our own Watch Next entries (prefix with "sinema:")
            val internalIdCol = TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
            resolver.query(uri, null, null, null, null)?.use { c: Cursor ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                    val internalId = c.getString(c.getColumnIndexOrThrow(internalIdCol))
                    if (internalId?.startsWith("sinema:") == true) {
                        resolver.delete(ContentUris.withAppendedId(uri, id), null, null)
                    }
                }
            }

            continuePairs.take(MAX_WATCH_NEXT).forEach { (scene, resumeSec) ->
                val intentUri = Uri.parse("com.sinema://app/scene/${scene.id}")
                val builder = WatchNextProgram.Builder()
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
                    .setTitle(scene.title)
                    .setDurationMillis((scene.duration * 1000).toInt())
                    .setLastPlaybackPositionMillis((resumeSec * 1000).toInt())
                    .setIntentUri(intentUri)
                    .setInternalProviderId("sinema:${scene.id}")

                helper.publishWatchNextProgram(builder.build())
            }
        } catch (e: Exception) {
            Log.e("Sinema", "Failed to sync Watch Next", e)
        }
    }

    fun syncRecentlyAdded(context: Context, scenes: List<Scene>) {
        val app = SinemaApp.instance
        if (!app.prefs.channelsEnabled) return
        try {
            val helper = PreviewChannelHelper(context)
            val channelUri = findOrCreateChannel(context, helper)
            if (channelUri == null) {
                Log.w("Sinema", "Could not create/find channel")
                return
            }

            val channelId = ContentUris.parseId(channelUri)
            val resolver = context.contentResolver
            val programsUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)

            resolver.query(programsUri, null, null, null, null)?.use { c: Cursor ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                    resolver.delete(ContentUris.withAppendedId(programsUri, id), null, null)
                }
            }

            scenes.take(MAX_RECENT).forEach { scene ->
                val intentUri = Uri.parse("com.sinema://app/scene/${scene.id}")
                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
                    .setTitle(scene.title)
                    .setDurationMillis((scene.duration * 1000).toInt())
                    .setIntentUri(intentUri)
                    .setInternalProviderId("sinema:${scene.id}")

                helper.publishPreviewProgram(builder.build())
            }
        } catch (e: Exception) {
            Log.e("Sinema", "Failed to sync Recently Added channel", e)
        }
    }

    fun clearAll(context: Context) {
        try {
            val resolver = context.contentResolver
            val watchUri = TvContractCompat.WatchNextPrograms.CONTENT_URI
            val internalIdCol = TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
            resolver.query(watchUri, null, null, null, null)?.use { c: Cursor ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                    val internalId = c.getString(c.getColumnIndexOrThrow(internalIdCol))
                    if (internalId?.startsWith("sinema:") == true) {
                        resolver.delete(ContentUris.withAppendedId(watchUri, id), null, null)
                    }
                }
            }

            val channelsUri = TvContractCompat.Channels.CONTENT_URI
            resolver.query(channelsUri, null, null, null, null)?.use { c: Cursor ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                    val name = c.getString(c.getColumnIndexOrThrow(TvContractCompat.Channels.COLUMN_DISPLAY_NAME))
                    if (name == CHANNEL_NAME) {
                        val programsUri = TvContractCompat.buildPreviewProgramsUriForChannel(id)
                        resolver.query(programsUri, null, null, null, null)?.use { pc: Cursor ->
                            while (pc.moveToNext()) {
                                val pid = pc.getLong(pc.getColumnIndexOrThrow(BaseColumns._ID))
                                resolver.delete(ContentUris.withAppendedId(programsUri, pid), null, null)
                            }
                        }
                        resolver.delete(ContentUris.withAppendedId(channelsUri, id), null, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Sinema", "Failed to clear TV channels", e)
        }
    }

    private fun findOrCreateChannel(context: Context, helper: PreviewChannelHelper): Uri? {
        val resolver = context.contentResolver
        val uri = TvContractCompat.Channels.CONTENT_URI
        resolver.query(uri, null, null, null, null)?.use { c: Cursor ->
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow(TvContractCompat.Channels.COLUMN_DISPLAY_NAME))
                if (name == CHANNEL_NAME) {
                    val id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                    return ContentUris.withAppendedId(uri, id)
                }
            }
        }

        val icon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        val channel = PreviewChannel.Builder()
            .setDisplayName(CHANNEL_NAME)
            .setAppLinkIntentUri(Uri.parse("com.sinema://app"))
            .apply { if (icon != null) setLogo(icon) }
            .build()
        val channelId = helper.publishDefaultChannel(channel)

        if (channelId > 0) {
            TvContractCompat.requestChannelBrowsable(context, channelId)
            return ContentUris.withAppendedId(TvContractCompat.Channels.CONTENT_URI, channelId)
        }
        return null
    }
}
