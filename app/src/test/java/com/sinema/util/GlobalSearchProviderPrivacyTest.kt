package com.sinema.util

import android.app.SearchManager
import android.net.Uri
import com.sinema.SinemaApp
import com.sinema.api.SequencedResponse
import com.sinema.api.testScene
import com.sinema.api.testSceneListResponse
import com.sinema.api.withGraphqlServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class GlobalSearchProviderPrivacyTest {
    private val app get() = SinemaApp.instance
    private lateinit var provider: GlobalSearchProvider
    private val uri = Uri.parse(
        "content://com.sinema.globalsearch/${SearchManager.SUGGEST_URI_PATH_QUERY}"
    )

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(GlobalSearchProvider::class.java).create().get()
        app.prefs.globalSearchEnabled = true
        app.prefs.serverUrl = "http://server"
        app.prefs.apiKey = "key"
        app.prefs.sessionCookie = ""
        app.pinVerifiedThisSession = false
        app.decoySession = false
        app.prefs.removePin()
    }

    @Test
    fun `locked and decoy queries never reach the server`() {
        withGraphqlServer(
            responses = listOf(SequencedResponse(testSceneListResponse(testScene(id = "secret")))),
            configureSinemaApp = true
        ) { _, requests ->
            app.prefs.setPinHash("1234")
            app.pinVerifiedThisSession = false
            assertNull(query())

            app.decoySession = true
            app.pinVerifiedThisSession = true
            assertNull(query())

            assertEquals(emptyList<String>(), requests())
        }
    }

    @Test
    fun `all enabled configuration pin and decoy states follow the gate`() {
        val cases = listOf(
            GateCase(enabled = false, configured = true, pin = false, verified = false, decoy = false, allowed = false),
            GateCase(enabled = true, configured = false, pin = false, verified = false, decoy = false, allowed = false),
            GateCase(enabled = true, configured = true, pin = true, verified = false, decoy = false, allowed = false),
            GateCase(enabled = true, configured = true, pin = true, verified = true, decoy = false, allowed = true),
            GateCase(enabled = true, configured = true, pin = false, verified = false, decoy = false, allowed = true),
            GateCase(enabled = true, configured = true, pin = true, verified = true, decoy = true, allowed = false)
        )
        cases.forEach(::assertGate)
    }

    private fun assertGate(case: GateCase) {
        withGraphqlServer(
            responses = listOf(SequencedResponse(testSceneListResponse(testScene(id = "secret")))),
            configureSinemaApp = true
        ) { _, requests ->
            app.prefs.globalSearchEnabled = case.enabled
            app.prefs.apiKey = if (case.configured) "key" else ""
            app.prefs.sessionCookie = ""
            app.prefs.removePin()
            if (case.pin) app.prefs.setPinHash("1234")
            app.pinVerifiedThisSession = case.verified
            app.decoySession = case.decoy

            val cursor = query()
            if (case.allowed) {
                assertEquals(case.toString(), 1, cursor?.count)
                assertEquals(case.toString(), 1, requests().size)
            } else {
                assertNull(case.toString(), cursor)
                assertEquals(case.toString(), emptyList<String>(), requests())
            }
            cursor?.close()
        }
    }

    private fun query() = provider.query(uri, null, null, arrayOf("secret"), null)

    private data class GateCase(
        val enabled: Boolean,
        val configured: Boolean,
        val pin: Boolean,
        val verified: Boolean,
        val decoy: Boolean,
        val allowed: Boolean
    )
}
