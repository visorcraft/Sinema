package com.sinema.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared fake GraphQL server for all API tests: fixed response, captures
 * the last request body. Extracted from SinemaApiTest so every test file
 * can use it.
 */
fun withGraphqlServer(
    response: String,
    block: (api: SinemaApi, requestBody: () -> String) -> Unit
) {
    val captured = AtomicReference("")
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/graphql") { exchange ->
        captured.set(exchange.requestBody.bufferedReader().readText())
        val bytes = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.start()
    try {
        block(SinemaApi("http://127.0.0.1:${server.address.port}", "k")) { captured.get() }
    } finally {
        server.stop(0)
    }
}
