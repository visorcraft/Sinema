package com.sinema.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sinema.model.PerformerRef
import com.sinema.model.Scene
import com.sinema.model.StudioRef
import com.sinema.model.TagRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Collections

class DiscoveryApiTest {

    @Test
    fun `related scenes use three bounded full metadata queries and rank unique candidates`() {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        withServer(requests) { request ->
            val filter = request.getAsJsonObject("variables").getAsJsonObject("scene_filter")
            val scene = when {
                filter.has("performers") -> sceneJson("1", performers = true)
                filter.has("tags") -> sceneJson("2", tags = true)
                else -> sceneJson("3", studio = true)
            }
            """{"data":{"findScenes":{"count":1,"scenes":[$scene]}}}"""
        }
        val source = Scene(
            id = "source", title = "Source", path = "/data/source.mp4", size = 1,
            duration = 100.0, width = 1920, height = 1080,
            performers = listOf(PerformerRef("p1", "Performer")),
            studio = StudioRef("s1", "Studio"),
            tags = listOf(TagRef("t1", "Tag"))
        )

        val api = SinemaApi(serverUrl, "k")
        val related = runBlocking { api.findRelatedScenes(source) }

        assertEquals(listOf("1", "3", "2"), related.map { it.id })
        assertEquals(3, requests.size)
        requests.forEach { body ->
            val request = JsonParser.parseString(body).asJsonObject
            val filter = request.getAsJsonObject("variables").getAsJsonObject("filter")
            assertEquals(40, filter.get("per_page").asInt)
            assertTrue(request.get("query").asString.contains("performers { id name }"))
            assertTrue(request.get("query").asString.contains("tags { id name }"))
        }
        server.stop(0)
    }

    @Test
    fun `related scenes skip requests when source has no usable metadata`() {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        withServer(requests) { """{"data":{"findScenes":{"count":0,"scenes":[]}}}""" }
        val source = Scene("source", "Source", "/data/source.mp4", 1, 100.0, 1920, 1080)

        val related = runBlocking { SinemaApi(serverUrl, "k").findRelatedScenes(source) }

        assertTrue(related.isEmpty())
        assertTrue(requests.isEmpty())
        server.stop(0)
    }

    @Test
    fun `unwatched favorite performer scenes send both criteria`() {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        withServer(requests) { """{"data":{"findScenes":{"count":0,"scenes":[]}}}""" }

        runBlocking {
            SinemaApi(serverUrl, "k").findUnwatchedForFavoritePerformersPaged(
                page = 2, perPage = 30, sort = "updated_at", direction = "DESC"
            )
        }

        val request = JsonParser.parseString(requests.single()).asJsonObject
        val filter = request.getAsJsonObject("variables").getAsJsonObject("scene_filter")
        assertTrue(filter.get("performer_favorite").asBoolean)
        assertEquals(0, filter.getAsJsonObject("play_count").get("value").asInt)
        assertEquals("EQUALS", filter.getAsJsonObject("play_count").get("modifier").asString)
        val page = request.getAsJsonObject("variables").getAsJsonObject("filter")
        assertEquals(2, page.get("page").asInt)
        assertEquals(30, page.get("per_page").asInt)
        server.stop(0)
    }

    private lateinit var server: com.sun.net.httpserver.HttpServer
    private val serverUrl get() = "http://127.0.0.1:${server.address.port}"

    private fun withServer(
        requests: MutableList<String>,
        response: (JsonObject) -> String
    ) {
        server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/graphql") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests += body
            val payload = response(JsonParser.parseString(body).asJsonObject).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
            exchange.close()
        }
        server.start()
    }

    private fun sceneJson(id: String, performers: Boolean = false, tags: Boolean = false, studio: Boolean = false): String {
        val performerField = if (performers) """"performers":[{"id":"p1","name":"Performer"}]""" else """"performers":[]"""
        val tagField = if (tags) """"tags":[{"id":"t1","name":"Tag"}]""" else """"tags":[]"""
        val studioField = if (studio) """"studio":{"id":"s1","name":"Studio"}""" else """"studio":null"""
        return """{"id":"$id","title":"Scene $id","files":[],$performerField,$tagField,$studioField}"""
    }
}
