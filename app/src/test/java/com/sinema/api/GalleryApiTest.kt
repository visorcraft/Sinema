package com.sinema.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class GalleryApiTest {
    @Test
    fun `parseGallery reads cover metadata and tolerates nulls`() {
        val api = SinemaApi("http://server", "k")
        val full = JsonParser.parseString(
            """{"id":"7","title":"Holiday","image_count":42,"cover":{"id":"91"},
                "date":"2026-10-01","details":"  Photos  ","photographer":"A. Artist",
                "studio":{"name":"Studio"},"rating100":80}"""
        ).asJsonObject
        val gallery = api.parseGallery(full)
        assertEquals("7", gallery.id)
        assertEquals("Holiday", gallery.displayTitle)
        assertEquals(42, gallery.imageCount)
        assertEquals("91", gallery.coverImageId)
        assertEquals("Photos", gallery.details)
        assertEquals("A. Artist", gallery.photographer)
        assertEquals("Studio", gallery.studioName)
        assertEquals(80, gallery.rating100)

        val sparse = api.parseGallery(JsonParser.parseString(
            """{"id":"8","title":null,"image_count":null,"cover":null,"studio":null}"""
        ).asJsonObject)
        assertEquals("Gallery 8", sparse.displayTitle)
        assertEquals(0, sparse.imageCount)
        assertNull(sparse.coverImageId)
        assertNull(sparse.details)
    }

    @Test
    fun `findGalleries sends paging search and sort`() {
        val body = AtomicReference("")
        val server = TestServers.graphql(
            """{"data":{"findGalleries":{"count":1,"galleries":[
                {"id":"7","title":"Holiday","image_count":42,"cover":{"id":"91"}}
            ]}}}""",
            body
        )
        try {
            val api = SinemaApi(server, "k")
            val result = kotlinx.coroutines.runBlocking {
                api.findGalleries(page = 2, perPage = 50, searchTerm = "holiday", sort = "title", direction = "ASC")
            }
            assertEquals(1, result.first)
            assertEquals("Holiday", result.second.single().displayTitle)
            assertTrue(body.get().contains("\"page\":2"))
            assertTrue(body.get().contains("\"per_page\":50"))
            assertTrue(body.get().contains("\"q\":\"holiday\""))
            assertTrue(body.get().contains("\"sort\":\"title\""))
        } finally {
            TestServers.stop()
        }
    }

    @Test
    fun `findGallery requests and parses one gallery`() {
        val body = AtomicReference("")
        val server = TestServers.graphql(
            """{"data":{"findGallery":{"id":"7","title":"Holiday","image_count":42,"cover":{"id":"91"}}}}""",
            body
        )
        try {
            val api = SinemaApi(server, "k")
            val gallery = kotlinx.coroutines.runBlocking { api.findGallery("7") }
            assertEquals("Holiday", gallery?.displayTitle)
            assertTrue(body.get().contains("findGallery(id:"))
            assertTrue(body.get().contains("\"id\":\"7\""))
        } finally {
            TestServers.stop()
        }
    }

    @Test
    fun `findImagesForGallery sends gallery criterion`() {
        val body = AtomicReference("")
        val server = TestServers.graphql(
            """{"data":{"findImages":{"count":1,"images":[
                {"id":"11","title":"One","visual_files":[{"path":"/data/one.jpg","size":12,"width":100,"height":200}]}
            ]}}}""",
            body
        )
        try {
            val api = SinemaApi(server, "k")
            val result = kotlinx.coroutines.runBlocking {
                api.findImagesForGallery("7", page = 3, perPage = 200)
            }
            assertEquals("11", result.second.single().id)
            assertEquals("/data/one.jpg", result.second.single().path)
            assertEquals(100, result.second.single().width)
            assertEquals(200, result.second.single().height)
            assertTrue(body.get().contains("\"galleries\""))
            assertTrue(body.get().contains("\"value\":[\"7\"]"))
            assertTrue(body.get().contains("\"modifier\":\"INCLUDES\""))
            assertTrue(body.get().contains("\"page\":3"))
            assertTrue(body.get().contains("... on VideoFile"))
        } finally {
            TestServers.stop()
        }
    }
}

private object TestServers {
    private var server: com.sun.net.httpserver.HttpServer? = null

    fun graphql(response: String, body: AtomicReference<String>): String {
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
