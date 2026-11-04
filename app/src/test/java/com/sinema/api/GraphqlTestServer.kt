package com.sinema.api

import com.google.gson.Gson
import com.sinema.SinemaApp
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
    val executor = Executors.newFixedThreadPool(8)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = executor
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
        executor.shutdown()
        check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "GraphQL test executor did not stop" }
    }
}

/** One canned reply per request, in order; repeats the last one when exhausted. */
class SequencedResponse(
    val body: String,
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap()
)

/**
 * [withGraphqlServer] variant for tests that need different replies per
 * request (fallback chains, pagination) or request-dependent bodies.
 * [responder] takes the captured request body and returns the reply; it is
 * used when present, otherwise the next [SequencedResponse] is sent.
 */
fun withGraphqlServer(
    responses: List<SequencedResponse>,
    responder: ((requestBody: String) -> SequencedResponse)? = null,
    onLogin: (() -> SequencedResponse)? = null,
    onGraphql: ((String?) -> Unit)? = null,
    configureSinemaApp: Boolean = false,
    block: (api: SinemaApi, requests: () -> List<String>) -> Unit
) {
    val captured = java.util.Collections.synchronizedList(mutableListOf<String>())
    val index = java.util.concurrent.atomic.AtomicInteger(0)
    val executor = Executors.newFixedThreadPool(8)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = executor
    server.createContext("/graphql") { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        captured.add(body)
        onGraphql?.invoke(exchange.requestHeaders.getFirst("Cookie"))
        val reply = responder?.invoke(body)
            ?: responses[index.getAndIncrement().coerceAtMost(responses.size - 1)]
        val bytes = reply.body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        reply.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.createContext("/login") { exchange ->
        exchange.requestBody.bufferedReader().readText()
        val reply = onLogin?.invoke() ?: SequencedResponse("")
        val bytes = reply.body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        reply.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
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
        block(SinemaApi(serverUrl, "k")) { java.util.ArrayList(captured) }
    } finally {
        server.stop(0)
        executor.shutdown()
        check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "GraphQL test executor did not stop" }
    }
}

fun testGraphqlErrorResponse(message: String): String =
    Gson().toJson(mapOf("errors" to listOf(mapOf("message" to message))))
