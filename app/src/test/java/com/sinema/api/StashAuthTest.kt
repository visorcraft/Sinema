package com.sinema.api

import java.io.IOException
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.sinema.SinemaApp::class)
class StashAuthTest {
    @Test
    fun `login maps all outcomes`() {
        assertEquals(LoginResult.InvalidCredentials, loginFor(status = 401))
        assertEquals(LoginResult.MissingCookie, loginFor(status = 302))
        assertEquals(
            LoginResult.MissingCookie,
            loginFor(status = 302, headers = mapOf("Set-Cookie" to "session=; Path=/")
        ))
        assertEquals(LoginResult.HttpFailure(500), loginFor(status = 500))
        assertEquals(
            LoginResult.Success("session=abc"),
            loginFor(status = 302, headers = mapOf("Set-Cookie" to "session=abc; Path=/"))
        )
        assertTrue(loginFor(status = 200, closedServer = true) is LoginResult.TransportFailure)
        listOf(200, 302, 303, 307, 308).forEach { status ->
            assertTrue(
                loginFor(status = status, headers = mapOf("Set-Cookie" to "session=abc; Path=/"))
                    is LoginResult.Success
            )
        }
    }

    @Test
    fun `generate api key uses cookie mutation and gson parsing`() {
        val responses = listOf(SequencedResponse("""{"data":{"generateAPIKey":"key"}}"""))
        var requestCookie: String? = null
        withGraphqlServer(
            responses,
            onGraphql = { requestCookie = it },
            configureSinemaApp = true
        ) { _, requests ->
            val serverUrl = com.sinema.SinemaApp.instance.prefs.serverUrl
            val client = SinemaApi(serverUrl, "", allowSelfSigned = true).client
            val key = StashAuth.generateApiKey(client, serverUrl, "session=abc")

            assertEquals("key", key)
            assertEquals("session=abc", requestCookie)
            assertTrue(requests().single().contains("generateAPIKey(input: {})"))
        }
    }

    @Test
    fun `generate api key rejects actionable response failures`() {
        assertGenerateFailure(SequencedResponse("", status = 500))
        assertGenerateFailure(SequencedResponse("""{"errors":[{"message":"denied"}]}"""))
        assertGenerateFailure(SequencedResponse("not-json"))
        assertGenerateFailure(SequencedResponse("""{"data":{}}"""))
        assertGenerateFailure(SequencedResponse("""{"data":{"generateAPIKey":""}}"""))
        assertGenerateFailure(SequencedResponse("""{"data":{"generateAPIKey":null}}"""))
    }

    private fun loginFor(
        status: Int,
        headers: Map<String, String> = emptyMap(),
        closedServer: Boolean = false
    ): LoginResult {
        if (closedServer) {
            return StashAuth.login(OkHttpClient(), "http://127.0.0.1:1", "user", "pass")
        }
        val responses = listOf(SequencedResponse("""{"data":{}}"""))
        var result: LoginResult? = null
        withGraphqlServer(
            responses,
            onLogin = { SequencedResponse("", status = status, headers = headers) },
            configureSinemaApp = true
        ) { _, _ ->
            val serverUrl = com.sinema.SinemaApp.instance.prefs.serverUrl
            result = StashAuth.login(SinemaApi(serverUrl, "", allowSelfSigned = true).client, serverUrl, "user", "pass")
        }
        return result!!
    }

    private fun assertGenerateFailure(response: SequencedResponse) {
        withGraphqlServer(listOf(response), configureSinemaApp = true) { _, _ ->
            val serverUrl = com.sinema.SinemaApp.instance.prefs.serverUrl
            assertThrows(IOException::class.java) {
                StashAuth.generateApiKey(SinemaApi(serverUrl, "").client, serverUrl, "session=abc")
            }
        }
    }
}
