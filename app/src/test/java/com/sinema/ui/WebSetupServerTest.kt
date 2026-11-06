package com.sinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSetupServerTest {
    @Test
    fun getServesSetupFormWithEscapedServerUrl() {
        startServer(serverUrl = "http://stash:9999/\"unsafe\"").use { server ->
            val response = request(server, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")

            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(response.contains("http://stash:9999/&quot;unsafe&quot;"))
            assertTrue(response.contains("name=\"setupToken\" value=\"token\""))
        }
    }

    @Test
    fun validPostDecodesCredentialsAndCompletesOnce() {
        val completions = CountDownLatch(1)
        var received: Pair<String, String>? = null
        startServer(onComplete = { url, key ->
            received = url to key
            completions.countDown()
        }).use { server ->
            val body = form("serverUrl" to "http://stash:9999/", "apiKey" to "a+b&c", "setupToken" to "token")
            val response = request(server, "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body")

            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(completions.await(2, TimeUnit.SECONDS))
            assertEquals("http://stash:9999" to "a+b&c", received)
        }
    }

    @Test
    fun invalidTokenIsForbiddenWithoutCompletion() {
        var completed = false
        startServer(onComplete = { _, _ -> completed = true }).use { server ->
            val body = form("serverUrl" to "http://stash", "apiKey" to "key", "setupToken" to "wrong")
            val response = request(server, "POST / HTTP/1.1\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body")

            assertTrue(response.startsWith("HTTP/1.1 403"))
            assertFalse(completed)
        }
    }

    @Test
    fun negativeContentLengthIsRejected() {
        startServer().use { server ->
            val response = request(server, "POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n")
            assertTrue(response.startsWith("HTTP/1.1 400"))
        }
    }

    @Test
    fun oversizedContentLengthIsRejectedBeforeBody() {
        startServer().use { server ->
            val response = request(server, "POST / HTTP/1.1\r\nContent-Length: 16385\r\n\r\n")
            assertTrue(response.startsWith("HTTP/1.1 400"))
        }
    }

    @Test
    fun pendingConnectionsAreBoundedAndExcessClientsAreClosed() {
        startServer().use { server ->
            val clients = (1..8).map {
                Socket(InetAddress.getLoopbackAddress(), server.localPort)
            }
            val rejected = Socket(InetAddress.getLoopbackAddress(), server.localPort)
            try {
                rejected.soTimeout = 2_000
                assertEquals(-1, rejected.getInputStream().read())
            } finally {
                (clients + rejected).forEach { client -> runCatching { client.close() } }
            }
        }
    }

    @Test
    fun closeClosesListeningSocket() {
        val server = startServer()
        val listeningSocket = WebSetupServer::class.java.getDeclaredField("socket")
            .apply { isAccessible = true }
            .get(server) as ServerSocket
        assertFalse(listeningSocket.isClosed)

        server.close()

        assertTrue(listeningSocket.isClosed)
    }

    private fun startServer(
        serverUrl: String = "http://stash:9999",
        onComplete: (String, String) -> Unit = { _, _ -> }
    ): WebSetupServer = WebSetupServer.start(
        serverUrl = serverUrl,
        setupToken = "token",
        onComplete = onComplete,
        port = 0,
        bindAddress = InetAddress.getLoopbackAddress()
    )

    private fun request(server: WebSetupServer, rawRequest: String): String {
        Socket(InetAddress.getLoopbackAddress(), server.localPort).use { socket ->
            socket.soTimeout = 2_000
            val output = socket.getOutputStream()
            output.write(rawRequest.toByteArray(Charsets.UTF_8))
            output.flush()
            socket.shutdownOutput()
            return socket.getInputStream().bufferedReader().readText()
        }
    }

    private fun form(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }
}
