package com.sinema.util

import com.sinema.model.Scene
import com.sinema.model.StudioRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure mapping for Android TV global-search suggestion rows (see
 * GlobalSearchProvider). Column order must match SUGGESTION_COLUMNS minus _ID:
 * TEXT_1, TEXT_2, CARD_IMAGE, PRODUCTION_YEAR, DURATION, INTENT_DATA_ID, CONTENT_TYPE.
 */
class GlobalSearchSuggestionsTest {

    private fun scene(
        id: String = "42",
        title: String = "Title",
        path: String = "/data/folder/file.mp4",
        duration: Double = 90.0,
        date: String? = "2021-06-15",
        studio: StudioRef? = StudioRef("3", "Studio X")
    ) = Scene(
        id = id, title = title, path = path, size = 1L, duration = duration,
        width = 1920, height = 1080, date = date, studio = studio
    )

    @Test
    fun `row carries title studio year duration and id`() {
        val row = GlobalSearchSuggestions.build(scene(), "http://srv:6969")
        assertEquals("Title", row[0])
        assertEquals("Studio X", row[1])
        assertEquals("http://srv:6969/scene/42/screenshot", row[2])
        assertEquals(2021, row[3])
        assertEquals(90_000L, row[4])
        assertEquals("42", row[5])
        assertEquals("video/mp4", row[6])
    }

    @Test
    fun `rows never embed credentials`() {
        // The provider is exported; suggestion rows must not leak the API key
        // (or any secret) to other apps while global search is enabled.
        val row = GlobalSearchSuggestions.build(scene(), "http://srv:6969")
        row.forEach { value ->
            assertFalse(value.toString().contains("apikey"))
        }
    }

    @Test
    fun `untitled scenes fall back to filename`() {
        val row = GlobalSearchSuggestions.build(scene(title = "", studio = null), "http://srv")
        assertEquals("file.mp4", row[0])
        // No studio → date is the subtitle.
        assertEquals("2021-06-15", row[1])
    }

    @Test
    fun `missing metadata degrades to zeros`() {
        val row = GlobalSearchSuggestions.build(scene(date = null, studio = null), "http://srv")
        assertEquals("", row[1])
        assertEquals(0, row[3])
        // Full row arity stays stable for the MatrixCursor.
        assertEquals(7, row.size)
        assertTrue(row.all { it != null })
    }
}
