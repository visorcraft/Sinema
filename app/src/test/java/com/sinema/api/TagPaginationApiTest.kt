package com.sinema.api

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class TagPaginationApiTest {
    @Test
    fun findAllTagsStopsAfterTheReportedCount() {
        val requests = AtomicInteger(0)
        withTagServer(
            count = 3,
            requests = requests,
            tagsForPage = { page -> if (page == 1) listOf("a", "b") else listOf("c") }
        ) { api ->
            val tags = runBlocking { api.findAllTags(perPage = 2) }

            assertEquals(listOf("a", "b", "c"), tags.map { it.id })
            assertEquals(2, requests.get())
        }
    }

    @Test
    fun findAllTagsHonorsThePageCap() {
        val requests = AtomicInteger(0)
        withTagServer(
            count = 100,
            requests = requests,
            tagsForPage = { page -> listOf("tag-$page") }
        ) { api ->
            val tags = runBlocking { api.findAllTags(perPage = 1, maxPages = 1) }

            assertEquals(listOf("tag-1"), tags.map { it.id })
            assertEquals(1, requests.get())
        }
    }

    private fun withTagServer(
        count: Int,
        requests: AtomicInteger,
        tagsForPage: (Int) -> List<String>,
        block: (SinemaApi) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/graphql") { exchange ->
            val request = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
            val page = request.getAsJsonObject("variables").getAsJsonObject("filter").get("page").asInt
            requests.incrementAndGet()
            val tags = tagsForPage(page).joinToString(",") { id ->
                """{"id":"$id","name":"Tag $id","scene_count":1,"image_path":null,"rating100":null}"""
            }
            val response = """{"data":{"findTags":{"count":$count,"tags":[$tags]}}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            block(SinemaApi("http://127.0.0.1:${server.address.port}", "k"))
        } finally {
            server.stop(0)
        }
    }
}
