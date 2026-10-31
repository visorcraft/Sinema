package com.sinema.util

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.sinema.SinemaApp
import kotlinx.coroutines.runBlocking

/**
 * Serves scene suggestions to the Android TV system search dialog. The
 * component ships DISABLED in the manifest and is only turned on from
 * Settings → Global Search (opt-in, privacy-first). Suggestion clicks fire
 * a VIEW intent for com.sinema://app/scene/<id>, handled by DeepLinkActivity
 * (which enforces the PIN gate).
 */
class GlobalSearchProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (context == null) return null
        val app = SinemaApp.instance
        // Defense in depth: the component is only enabled when the user opted
        // in, but re-check the pref so a stale component state cannot leak data.
        if (!app.prefs.globalSearchEnabled || !app.prefs.isConfigured) return null

        val query = selectionArgs?.firstOrNull()
            ?.takeIf { uri.lastPathSegment == SearchManager.SUGGEST_URI_PATH_QUERY }
            ?.takeIf { it.isNotBlank() } // system search never sends blanks; defensive
            ?: return null

        // System search expects fast answers; the suggestions client enforces
        // its own short total call timeout (OkHttp callTimeout) because
        // coroutine cancellation cannot interrupt a blocking execute().
        val scenes = try {
            runBlocking { app.api.searchScenesForSuggestions(query, perPage = 8) }
        } catch (e: Exception) {
            android.util.Log.w("Sinema", "Global search suggestion query failed", e)
            null
        } ?: return null

        val cursor = MatrixCursor(SUGGESTION_COLUMNS)
        scenes.forEachIndexed { index, scene ->
            cursor.addRow(
                listOf(index.toLong()) + GlobalSearchSuggestions.build(scene, app.prefs.serverUrl)
            )
        }
        return cursor
    }

    companion object {
        val SUGGESTION_COLUMNS: Array<String> = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_DURATION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE
        )
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

/**
 * Pure scene → suggestion-row mapper (no Android types) so it can be unit
 * tested. Returns the row without the leading _ID column; the provider
 * prepends it to match [GlobalSearchProvider.SUGGESTION_COLUMNS].
 *
 * SECURITY: rows must never carry credentials. The card image is the plain
 * screenshot URL — it renders on servers that allow unauthenticated media
 * access and simply yields no artwork otherwise. Embedding the API key
 * would hand it to any app querying this (necessarily exported) provider.
 */
object GlobalSearchSuggestions {
    fun build(scene: com.sinema.model.Scene, serverUrl: String): List<Any?> = listOf(
        scene.displayTitle,
        scene.studio?.name ?: scene.date ?: "",
        "${serverUrl.trimEnd('/')}/scene/${scene.id}/screenshot",
        scene.date?.substringBefore('-')?.toIntOrNull() ?: 0,
        (scene.duration * 1000).toLong(),
        scene.id,
        "video/mp4"
    )
}
