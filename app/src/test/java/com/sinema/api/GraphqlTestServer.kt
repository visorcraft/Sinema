package com.sinema.api

import com.google.gson.Gson
import com.sinema.SinemaApp
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared fake GraphQL server for all API tests: fixed response, captures
 * the last request body. Extracted from SinemaApiTest so every test file
 * can use it.
 */
fun withGraphqlServer(
    response: String,
    responseGate: CountDownLatch? = null,
    status: Int = 200,
    configureSinemaApp: Boolean = false,
    block: (api: SinemaApi, requestBody: () -> String) -> Unit
) {
    val captured = AtomicReference("")
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/graphql") { exchange ->
        captured.set(exchange.requestBody.bufferedReader().readText())
        responseGate?.await(5, TimeUnit.SECONDS)
        val bytes = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.start()
    try {
        val serverUrl = "http://127.0.0.1:${server.address.port}"
        if (configureSinemaApp) {
            SinemaApp.instance.prefs.serverUrl = serverUrl
            SinemaApp.instance.refreshApi()
        }
        block(SinemaApi(serverUrl, "k")) { captured.get() }
    } finally {
        server.stop(0)
    }
}

fun testGraphqlErrorResponse(message: String): String =
    Gson().toJson(mapOf("errors" to listOf(mapOf("message" to message))))
