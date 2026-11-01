package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSettingsDraftTest {
    @Test
    fun `unchanged values are not dirty`() {
        val draft = ServerSettingsDraft("http://stash:6969", "api-key")
        assertFalse(draft.hasChanges)
    }

    @Test
    fun `surrounding whitespace is normalized without becoming a change`() {
        val draft = ServerSettingsDraft("http://stash:6969", "api-key")
        draft.url = "  http://stash:6969  "
        draft.apiKey = "  api-key  "
        assertFalse(draft.hasChanges)
        assertEquals("http://stash:6969", draft.normalizedUrl())
        assertEquals("api-key", draft.normalizedApiKey())
    }

    @Test
    fun `url edit is dirty`() {
        val draft = ServerSettingsDraft("http://stash:6969", "api-key")
        draft.url = "http://other:6969"
        assertTrue(draft.hasChanges)
    }

    @Test
    fun `api key edit is dirty`() {
        val draft = ServerSettingsDraft("http://stash:6969", "api-key")
        draft.apiKey = "new-key"
        assertTrue(draft.hasChanges)
    }

    @Test
    fun `reverting an edit clears the dirty state`() {
        val draft = ServerSettingsDraft("http://stash:6969", "api-key")
        draft.url = "http://other:6969"
        draft.url = "http://stash:6969"
        assertFalse(draft.hasChanges)
    }
}
