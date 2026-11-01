package com.sinema.api

import com.sinema.model.DurationFilter
import com.sinema.model.SavedSceneFilter
import com.sinema.model.SceneFilterOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SavedFilterMutationApiTest {
    @Test
    fun `saveSceneFilter sends mode criteria and sort`() {
        val body = AtomicReference("")
        val server = MutationServer.respond(
            """{"data":{"saveFilter":{"id":"9","name":"Short HEVC",
                "find_filter":{"sort":"duration","direction":"DESC"},
                "object_filter":{"duration":{"value":600,"modifier":"LESS_THAN"}}}}}""",
            body
        )
        try {
            val api = SinemaApi(server, "k")
            val saved = kotlinx.coroutines.runBlocking {
                api.saveSceneFilter(
                    name = "Short HEVC",
                    options = SceneFilterOptions(duration = DurationFilter.UNDER_10),
                    sort = "duration",
                    direction = "DESC"
                )
            }
            assertEquals("9", saved.id)
            assertEquals("Short HEVC", saved.name)
            val json = body.get()
            assertTrue(json.contains("\"mode\":\"SCENES\""))
            assertTrue(json.contains("\"sort\":\"duration\""))
            assertTrue(json.contains("\"direction\":\"DESC\""))
            assertTrue(json.contains("\"duration\""))
            assertTrue(json.contains("\"value\":600"))
            assertTrue(json.contains("\"modifier\":\"LESS_THAN\""))
        } finally {
            MutationServer.stop()
        }
    }

    @Test
    fun `renameSavedSceneFilter preserves id raw criteria and sort`() {
        val body = AtomicReference("")
        val server = MutationServer.respond(
            """{"data":{"saveFilter":{"id":"4","name":"Renamed",
                "find_filter":{"sort":"rating100","direction":"DESC"},
                "object_filter":{"rating100":{"value":0,"modifier":"GREATER_THAN"}}}}}""",
            body
        )
        try {
            val original = SavedSceneFilter(
                id = "4",
                name = "Old",
                sceneFilter = mapOf("rating100" to mapOf("value" to 0, "modifier" to "GREATER_THAN")),
                sort = "rating100",
                direction = "DESC"
            )
            val renamed = kotlinx.coroutines.runBlocking {
                SinemaApi(server, "k").renameSavedSceneFilter(original, "Renamed")
            }
            assertEquals("Renamed", renamed.name)
            val json = body.get()
            assertTrue(json.contains("\"id\":\"4\""))
            assertTrue(json.contains("\"name\":\"Renamed\""))
            assertTrue(json.contains("\"rating100\""))
            assertTrue(json.contains("\"sort\":\"rating100\""))
        } finally {
            MutationServer.stop()
        }
    }

    @Test
    fun `rename preserves absent sort and web ui options`() {
        val body = AtomicReference("")
        val server = MutationServer.respond(
            """{"data":{"saveFilter":{"id":"5","name":"Renamed","find_filter":null,
                "object_filter":{},"ui_options":{"view":"wall"}}}}""",
            body
        )
        try {
            val original = SavedSceneFilter(
                id = "5",
                name = "Old",
                sceneFilter = emptyMap(),
                sort = null,
                direction = null,
                uiOptions = mapOf("view" to "wall")
            )
            val renamed = kotlinx.coroutines.runBlocking {
                SinemaApi(server, "k").renameSavedSceneFilter(original, "Renamed")
            }
            assertEquals(mapOf("view" to "wall"), renamed.uiOptions)
            val input = com.google.gson.JsonParser.parseString(body.get()).asJsonObject
                .getAsJsonObject("variables").getAsJsonObject("input")
            org.junit.Assert.assertFalse(input.has("find_filter"))
            assertEquals("wall", input.getAsJsonObject("ui_options").get("view").asString)
        } finally {
            MutationServer.stop()
        }
    }

    @Test
    fun `destroySavedSceneFilter sends id`() {
        val body = AtomicReference("")
        val server = MutationServer.respond("""{"data":{"destroySavedFilter":true}}""", body)
        try {
            kotlinx.coroutines.runBlocking { SinemaApi(server, "k").destroySavedSceneFilter("4") }
            assertTrue(body.get().contains("destroySavedFilter(input:"))
            assertTrue(body.get().contains("\"id\":\"4\""))
        } finally {
            MutationServer.stop()
        }
    }
}

private object MutationServer {
    private var server: com.sun.net.httpserver.HttpServer? = null

    fun respond(response: String, body: AtomicReference<String>): String {
        val created = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        created.createContext("/graphql") { exchange ->
            body.set(exchange.requestBody.bufferedReader().readText())
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        created.start()
        server = created
        return "http://127.0.0.1:${created.address.port}"
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}
