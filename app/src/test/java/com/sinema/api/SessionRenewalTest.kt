package com.sinema.api

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.sinema.SinemaApp::class)
class SessionRenewalTest {
    @Test
    fun `concurrent 401 responses renew once per api instance`() {
        val first401 = CountDownLatch(2)
        val logins = AtomicInteger(0)
        val callbacks = AtomicInteger(0)
        val retryCookies = mutableSetOf<String>()
        val responses = listOf(
            SequencedResponse("""{"errors":[{"message":"unauthorized"}]}""", 401),
            SequencedResponse("""{"data":{"findScene":null}}""")
        )
        val requestCount = AtomicInteger(0)
        withGraphqlServer(
            responses,
            responder = {
                if (requestCount.incrementAndGet() <= 2) {
                    first401.countDown()
                    first401.await(2, TimeUnit.SECONDS)
                    responses[0]
                } else {
                    responses[1]
                }
            },
            onLogin = {
                logins.incrementAndGet()
                SequencedResponse("", headers = mapOf("Set-Cookie" to "session=refreshed; Path=/"))
            },
            onGraphql = { cookie ->
                cookie?.takeIf { it.contains("session=refreshed") }?.let { retryCookies += it }
            },
            configureSinemaApp = true
        ) { _, requests ->
            val api = SinemaApi(
                com.sinema.SinemaApp.instance.prefs.serverUrl,
                "",
                "session=expired",
                "session"
            ).apply {
                stashUsername = "user"
                stashPassword = "pass"
                onSessionRefreshed = { callbacks.incrementAndGet() }
            }
            val results = kotlinx.coroutines.runBlocking {
                (1..2).map { async(Dispatchers.IO) { api.findSceneFull("1") } }.awaitAll()
            }

            assertEquals(2, results.size)
            assertEquals(1, logins.get())
            assertEquals(1, callbacks.get())
            assertEquals(4, requests().size)
            org.junit.Assert.assertTrue(retryCookies.isNotEmpty())
            org.junit.Assert.assertTrue(retryCookies.all { it == "session=refreshed" })
        }
    }

    @Test
    fun `no credentials means no login and original 401`() {
        val logins = AtomicInteger(0)
        val responses = listOf(SequencedResponse("""{"errors":[{"message":"unauthorized"}]}""", 401))
        withGraphqlServer(
            responses,
            onLogin = { logins.incrementAndGet(); SequencedResponse("") },
            configureSinemaApp = true
        ) { _, requests ->
            val api = SinemaApi(
                com.sinema.SinemaApp.instance.prefs.serverUrl,
                "",
                "session=expired",
                "session"
            )
            val error = assertUnauthorized(api)
            org.junit.Assert.assertTrue(error.message.orEmpty().contains("unauthorized"))
            assertEquals(0, logins.get())
            assertEquals(1, requests().size)
        }
    }

    @Test
    fun `failed renewal returns original 401 without retry`() {
        val callbacks = AtomicInteger(0)
        val responses = listOf(SequencedResponse("""{"errors":[{"message":"unauthorized"}]}""", 401))
        withGraphqlServer(
            responses,
            onLogin = { SequencedResponse("", status = 401) },
            configureSinemaApp = true
        ) { _, requests ->
            val api = sessionApi().apply { onSessionRefreshed = { callbacks.incrementAndGet() } }
            val error = assertUnauthorized(api)
            org.junit.Assert.assertTrue(error.message.orEmpty().contains("unauthorized"))
            assertEquals(0, callbacks.get())
            assertEquals(1, requests().size)
        }
    }

    @Test
    fun `each request retries at most once`() {
        val responses = listOf(SequencedResponse("""{"errors":[{"message":"unauthorized"}]}""", 401))
        withGraphqlServer(
            responses,
            onLogin = { SequencedResponse("", headers = mapOf("Set-Cookie" to "session=refreshed; Path=/")) },
            configureSinemaApp = true
        ) { _, requests ->
            val error = assertUnauthorized(sessionApi())
            org.junit.Assert.assertTrue(error.message.orEmpty().contains("unauthorized"))
            assertEquals(2, requests().size)
        }
    }

    @Test
    fun `separate api instances renew independently`() {
        val first401 = CountDownLatch(2)
        val logins = AtomicInteger(0)
        val callbacks = AtomicInteger(0)
        val requestCount = AtomicInteger(0)
        val responses = listOf(
            SequencedResponse("""{"errors":[{"message":"unauthorized"}]}""", 401),
            SequencedResponse("""{"data":{"findScene":null}}""")
        )
        withGraphqlServer(
            responses,
            responder = {
                if (requestCount.incrementAndGet() <= 2) {
                    first401.countDown()
                    first401.await(2, TimeUnit.SECONDS)
                    responses[0]
                } else responses[1]
            },
            onLogin = {
                logins.incrementAndGet()
                SequencedResponse("", headers = mapOf("Set-Cookie" to "session=refreshed; Path=/"))
            },
            configureSinemaApp = true
        ) { _, requests ->
            val apis = (1..2).map { sessionApi().apply { onSessionRefreshed = { callbacks.incrementAndGet() } } }
            kotlinx.coroutines.runBlocking {
                apis.map { async(Dispatchers.IO) { it.findSceneFull("1") } }.awaitAll()
            }
            assertEquals(2, logins.get())
            assertEquals(2, callbacks.get())
            assertEquals(4, requests().size)
        }
    }

    @Test
    fun `api key 401 never attempts login`() {
        val logins = AtomicInteger(0)
        val responses = listOf(SequencedResponse("""{"errors":[{"message":"unauthorized"}]}""", 401))
        withGraphqlServer(
            responses,
            onLogin = { logins.incrementAndGet(); SequencedResponse("") },
            configureSinemaApp = true
        ) { _, requests ->
            val api = SinemaApi(com.sinema.SinemaApp.instance.prefs.serverUrl, "key")
            val error = assertUnauthorized(api)
            org.junit.Assert.assertTrue(error.message.orEmpty().contains("unauthorized"))
            assertEquals(0, logins.get())
            assertEquals(1, requests().size)
        }
    }

    private fun sessionApi() = SinemaApi(
        com.sinema.SinemaApp.instance.prefs.serverUrl,
        "",
        "session=expired",
        "session"
    ).apply {
        stashUsername = "user"
        stashPassword = "pass"
    }

    private fun assertUnauthorized(api: SinemaApi): java.io.IOException =
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { api.findSceneFull("1") }
        }
}
