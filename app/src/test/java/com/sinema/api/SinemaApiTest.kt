package com.sinema.api

import com.sinema.model.CaptionRef
import org.junit.Assert.assertEquals
import org.junit.Test

class SinemaApiTest {

    @Test
    fun `apikey mode sends ApiKey header`() {
        val api = SinemaApi("http://server", "the-key", "", "apikey")
        assertEquals(mapOf("ApiKey" to "the-key"), api.mediaAuthHeaders())
    }

    @Test
    fun `session mode sends session cookie`() {
        val api = SinemaApi("http://server", "", "session=abc123", "session")
        assertEquals(mapOf("Cookie" to "session=abc123"), api.mediaAuthHeaders())
    }

    @Test
    fun `session mode with blank cookie falls back to ApiKey`() {
        val api = SinemaApi("http://server", "the-key", "", "session")
        assertEquals(mapOf("ApiKey" to "the-key"), api.mediaAuthHeaders())
    }

    @Test
    fun `updateConfig changes media auth headers`() {
        val api = SinemaApi("http://server", "old-key", "", "apikey")
        api.updateConfig("http://server", "", "session=zzz", "session")
        assertEquals(mapOf("Cookie" to "session=zzz"), api.mediaAuthHeaders())
    }

    @Test
    fun `caption url carries language and type`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        assertEquals(
            "http://server/scene/42/caption?lang=en&type=srt",
            api.getCaptionUrl("42", CaptionRef("en", "srt"))
        )
    }

    @Test
    fun `parseScene tolerates JSON-null tags, performers, and captions`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","captions":null,"tags":null,"performers":null,"files":[]}"""
        ).asJsonObject
        val scene = api.parseScene(obj)
        assertEquals(emptyList<com.sinema.model.TagRef>(), scene.tags)
        assertEquals(emptyList<com.sinema.model.PerformerRef>(), scene.performers)
        assertEquals(emptyList<CaptionRef>(), scene.captions)
    }

    @Test
    fun `parseEntity tolerates null fields`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"5","name":null,"scene_count":null,"image_path":null}"""
        ).asJsonObject
        val e = api.parseEntity(obj, com.sinema.model.EntityItem.Kind.TAG)
        assertEquals("5", e.id)
        assertEquals("", e.name)
        assertEquals(0, e.sceneCount)
        assertEquals(null, e.imagePath)
    }

    @Test
    fun `parseEntityHeader reads performer payload with tags`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"7","name":"Performer Name","scene_count":12,
                "image_path":"http://server/performer/7/image",
                "tags":[{"id":"1","name":"blonde"},{"id":"2","name":"new"}]}"""
        ).asJsonObject
        val h = api.parseEntityHeader(obj, com.sinema.model.EntityItem.Kind.PERFORMER)
        assertEquals("7", h.id)
        assertEquals("Performer Name", h.name)
        assertEquals(12, h.sceneCount)
        assertEquals("http://server/performer/7/image", h.imagePath)
        assertEquals(listOf("blonde", "new"), h.tags.map { it.name })
    }

    @Test
    fun `parseEntityHeader tolerates null fields and missing tags`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"8","name":null,"scene_count":null,"image_path":null}"""
        ).asJsonObject
        val h = api.parseEntityHeader(obj, com.sinema.model.EntityItem.Kind.STUDIO)
        assertEquals("8", h.id)
        assertEquals("", h.name)
        assertEquals(0, h.sceneCount)
        assertEquals(null, h.imagePath)
        assertEquals(emptyList<com.sinema.model.TagRef>(), h.tags)
    }

    @Test
    fun `parseMarker tolerates null title and primary tag`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"m1","title":null,"seconds":12.5,"primary_tag":null}"""
        ).asJsonObject
        val m = api.parseMarker(obj)
        assertEquals("m1", m.id)
        assertEquals("", m.title)
        assertEquals(12.5, m.seconds, 0.0)
        assertEquals("", m.primaryTag)
    }

    @Test
    fun `caption url encodes unsafe characters`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        assertEquals(
            "http://server/scene/1/caption?lang=pt%2FBR&type=srt",
            api.getCaptionUrl("1", com.sinema.model.CaptionRef("pt/BR", "srt"))
        )
    }

    @Test
    fun `parseScene reads full metadata payload`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"7","title":"t","date":"2024-01-02",
                "studio":{"id":"3","name":"St"},
                "tags":[{"id":"1","name":"a"},{"id":"2","name":"b"}],
                "performers":[{"id":"9","name":"P"}],
                "captions":[{"language_code":"en","caption_type":"srt"}],
                "files":[{"path":"/data/x.mp4","size":10,"duration":5.0,"width":1,"height":2}]}"""
        ).asJsonObject
        val s = api.parseScene(obj)
        assertEquals("2024-01-02", s.date)
        assertEquals("St", s.studio?.name)
        assertEquals(listOf("a", "b"), s.tags.map { it.name })
        assertEquals("P", s.performers.single().name)
        assertEquals("en", s.captions.single().languageCode)
    }

    @Test
    fun `parseScene reads last_played_at when present`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val withTs = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","last_played_at":"2026-07-18T12:00:00Z","files":[]}"""
        ).asJsonObject
        val withoutTs = com.google.gson.JsonParser.parseString(
            """{"id":"2","title":"t","last_played_at":null,"files":[]}"""
        ).asJsonObject
        val lean = com.google.gson.JsonParser.parseString(
            """{"id":"3","title":"t","files":[]}"""
        ).asJsonObject
        assertEquals("2026-07-18T12:00:00Z", api.parseScene(withTs).lastPlayedAt)
        assertEquals(null, api.parseScene(withoutTs).lastPlayedAt)
        assertEquals(null, api.parseScene(lean).lastPlayedAt)
    }

    @Test
    fun `scene list fields include resume_time for card progress`() {
        // CardPresenter's watch-progress bar depends on list queries shipping
        // resume_time; guard the shared field list so a refactor cannot
        // silently drop it.
        org.junit.Assert.assertTrue(
            "SCENE_FIELDS must select resume_time: ${SinemaApi.SCENE_FIELDS}",
            Regex("\\bresume_time\\b").containsMatchIn(SinemaApi.SCENE_FIELDS)
        )
    }

    @Test
    fun `full scene fields include details and o_counter`() {
        org.junit.Assert.assertTrue(SinemaApi.SCENE_FIELDS_FULL.contains(" details"))
        org.junit.Assert.assertTrue(SinemaApi.SCENE_FIELDS_FULL.contains(" o_counter"))
    }

    @Test
    fun `parseScene reads resume_time when present`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val withResume = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","resume_time":42.5,"files":[]}"""
        ).asJsonObject
        val nullResume = com.google.gson.JsonParser.parseString(
            """{"id":"2","title":"t","resume_time":null,"files":[]}"""
        ).asJsonObject
        val lean = com.google.gson.JsonParser.parseString(
            """{"id":"3","title":"t","files":[]}"""
        ).asJsonObject
        assertEquals(42.5, api.parseScene(withResume).resumeTime, 0.0)
        assertEquals(0.0, api.parseScene(nullResume).resumeTime, 0.0)
        assertEquals(0.0, api.parseScene(lean).resumeTime, 0.0)
    }

    @Test
    fun `parseSavedSceneFilter reads object_filter and find_filter`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"3","name":"Rated HD",
                "find_filter":{"sort":"rating","direction":"DESC"},
                "object_filter":{"rating100":{"value":40,"modifier":"GREATER_THAN"},
                                 "resolution":{"value":"STANDARD_HD","modifier":"GREATER_THAN"}}}"""
        ).asJsonObject
        val f = api.parseSavedSceneFilter(obj)
        assertEquals("3", f.id)
        assertEquals("Rated HD", f.name)
        assertEquals("rating", f.sort)
        assertEquals("DESC", f.direction)
        @Suppress("UNCHECKED_CAST")
        val rating = f.sceneFilter?.get("rating100") as Map<String, Any>
        assertEquals(40.0, rating["value"])
        assertEquals("GREATER_THAN", rating["modifier"])
        assertEquals("STANDARD_HD", (f.sceneFilter?.get("resolution") as Map<*, *>)["value"])
    }

    @Test
    fun `parseSavedSceneFilter tolerates nulls and missing sections`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"1","name":"F","find_filter":null,"object_filter":null}"""
        ).asJsonObject
        val f = api.parseSavedSceneFilter(obj)
        assertEquals(null, f.sceneFilter)
        assertEquals(null, f.sort)
        assertEquals(null, f.direction)
    }

    @Test
    fun `findSavedSceneFilters requests scene mode and parses list`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findSavedFilters":[
                {"id":"3","name":"Rated HD",
                 "find_filter":{"sort":"rating","direction":"DESC"},
                 "object_filter":{"rating100":{"value":40,"modifier":"GREATER_THAN"}}},
                {"id":"4","name":"Tagged"}
            ]}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val filters = kotlinx.coroutines.runBlocking { api.findSavedSceneFilters() }
            assertEquals(2, filters.size)
            assertEquals("Rated HD", filters[0].name)
            assertEquals("rating", filters[0].sort)
            assertEquals(40.0, (filters[0].sceneFilter?.get("rating100") as Map<*, *>)["value"])
            assertEquals(null, filters[1].sceneFilter)
            org.junit.Assert.assertTrue(
                "Expected SCENES mode in query: ${requestBody.get()}",
                requestBody.get().contains("findSavedFilters(mode: SCENES)")
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `parseScene reads details and o_counter`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val full = com.google.gson.JsonParser.parseString(
            """{"id":"1","title":"t","details":"  A synopsis.\nSecond line.  ","o_counter":3,"files":[]}"""
        ).asJsonObject
        val nulls = com.google.gson.JsonParser.parseString(
            """{"id":"2","title":"t","details":null,"o_counter":null,"files":[]}"""
        ).asJsonObject
        val scene = api.parseScene(full)
        assertEquals("A synopsis.\nSecond line.", scene.details)
        assertEquals(3, scene.oCounter)
        assertEquals(null, api.parseScene(nulls).details)
        assertEquals(0, api.parseScene(nulls).oCounter)
    }

    @Test
    fun `findRandomScene requests one random scene and parses it`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findScenes":{"count":9,"scenes":[
                {"id":"9","title":"r","play_count":1,"rating100":null,"resume_time":10.0,
                 "files":[{"path":"/data/r.mp4","size":10,"duration":100.0,"width":1920,"height":1080}]}
            ]}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val scene = kotlinx.coroutines.runBlocking { api.findRandomScene() }
            org.junit.Assert.assertNotNull(scene)
            assertEquals("9", scene!!.id)
            assertEquals("/data/r.mp4", scene.path)
            assertEquals(10.0, scene.resumeTime, 0.0)
            org.junit.Assert.assertTrue(
                "Expected random sort in request body: ${requestBody.get()}",
                requestBody.get().contains("\"sort\":\"random\"")
            )
            org.junit.Assert.assertTrue(
                "Expected per_page 1 in request body: ${requestBody.get()}",
                requestBody.get().contains("\"per_page\":1")
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `findEntityHeader requests performer with tags and parses response`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findPerformer":{
                "id":"7","name":"Performer Name","scene_count":12,
                "image_path":"http://127.0.0.1/performer/7/image",
                "tags":[{"id":"1","name":"blonde"},{"id":"2","name":"new"}]
            }}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val header = kotlinx.coroutines.runBlocking {
                api.findEntityHeader(com.sinema.model.EntityItem.Kind.PERFORMER, "7")
            }
            org.junit.Assert.assertNotNull(header)
            assertEquals("Performer Name", header!!.name)
            assertEquals(12, header.sceneCount)
            assertEquals(listOf("blonde", "new"), header.tags.map { it.name })
            val body = requestBody.get()
            org.junit.Assert.assertTrue("Expected findPerformer query: $body", body.contains("findPerformer"))
            org.junit.Assert.assertTrue("Expected tags selection for performer: $body", body.contains("tags { id name }"))
            org.junit.Assert.assertTrue("Expected id variable: $body", body.contains("\"id\":\"7\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `searchScenesForSuggestions bounds itself to the suggestion client`() {
        // Request-shape contract: q term + per_page + no scene_filter, and a
        // parseable scene list straight from the bounded suggestions client.
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findScenes":{"count":1,"scenes":[
                {"id":"5","title":"match","play_count":0,"rating100":null,"resume_time":0.0,
                 "files":[{"path":"/data/m.mp4","size":9,"duration":10.0,"width":1,"height":2}]}
            ]}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val scenes = kotlinx.coroutines.runBlocking { api.searchScenesForSuggestions("mat", perPage = 8) }
            assertEquals(1, scenes.size)
            assertEquals("match", scenes[0].title)
            val body = requestBody.get()
            org.junit.Assert.assertTrue(body.contains("\"q\":\"mat\""))
            org.junit.Assert.assertTrue(body.contains("\"per_page\":8"))
            org.junit.Assert.assertFalse("Suggestions must not send scene_filter", body.contains("scene_filter"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `parseSceneMarker reads marker with scene metadata`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"m1","title":"Intro", "seconds":42.5,
                "primary_tag":{"id":"9","name":"favorite"},
                "scene":{"id":"7","title":"Scene Title",
                         "files":[{"path":"/data/s.mp4","size":5,"duration":90.0,"width":1920,"height":1080}]}}"""
        ).asJsonObject
        val m = api.parseSceneMarker(obj)
        assertEquals("m1", m.id)
        assertEquals("Intro", m.title)
        assertEquals(42.5, m.seconds, 0.0)
        assertEquals("7", m.sceneId)
        assertEquals("Scene Title", m.sceneTitle)
        assertEquals(1920, m.sceneWidth)
        assertEquals(1080, m.sceneHeight)
        assertEquals("9", m.primaryTagId)
        assertEquals("favorite", m.primaryTagName)
        assertEquals("Intro", m.displayTitle)
    }

    @Test
    fun `parseSceneMarker falls back to primary tag and tolerates nulls`() {
        val api = SinemaApi("http://server", "k", "", "apikey")
        val obj = com.google.gson.JsonParser.parseString(
            """{"id":"m2","title":null,"seconds":null,
                "primary_tag":{"id":"3","name":"Tag Name"},
                "scene":{"id":"8","title":null,"files":[]}}"""
        ).asJsonObject
        val m = api.parseSceneMarker(obj)
        assertEquals("", m.title)
        assertEquals(0.0, m.seconds, 0.0)
        assertEquals("Tag Name", m.displayTitle)
        assertEquals("", m.sceneTitle)
        assertEquals(0, m.sceneWidth)
    }

    @Test
    fun `findSceneMarkers sends tag filter and parses list`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findSceneMarkers":{"count":2,"scene_markers":[
                {"id":"m1","title":"Intro","seconds":10.0,
                 "primary_tag":{"id":"9","name":"t1"},
                 "scene":{"id":"7","title":"S","files":[{"path":"/a.mp4","size":1,"duration":60.0,"width":1,"height":2}]}},
                {"id":"m2","title":"","seconds":20.0,
                 "primary_tag":{"id":"9","name":"t1"},
                 "scene":{"id":"8","title":"S2","files":[]}}
            ]}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val (count, markers) = kotlinx.coroutines.runBlocking {
                api.findSceneMarkers(page = 1, perPage = 50, sort = "created_at", direction = "DESC", tagId = "9")
            }
            assertEquals(2, count)
            assertEquals(2, markers.size)
            assertEquals("Intro", markers[0].displayTitle)
            assertEquals("t1", markers[1].displayTitle) // blank title → primary tag
            // Exact wire contract: hierarchical tags criterion under the
            // marker_filter variable (maps to $marker_filter in the query),
            // page/sort in the find filter.
            val body = com.google.gson.JsonParser.parseString(requestBody.get()).asJsonObject
            val variables = body.getAsJsonObject("variables")
            assertEquals(1, variables.get("filter").asJsonObject.get("page").asInt)
            assertEquals("created_at", variables.get("filter").asJsonObject.get("sort").asString)
            val markerFilter = variables.getAsJsonObject("marker_filter")
            val tags = markerFilter.getAsJsonObject("tags")
            assertEquals("INCLUDES", tags.get("modifier").asString)
            assertEquals(0, tags.get("depth").asInt)
            assertEquals(1, tags.getAsJsonArray("value").size())
            assertEquals("9", tags.getAsJsonArray("value")[0].asString)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `findTopPlayedScenes requests play count sort and full fields`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findScenes":{"count":1,"scenes":[
                {"id":"5","title":"top","play_count":9,"rating100":null,"resume_time":0.0,
                 "date":"2020-01-01","details":null,"o_counter":0,
                 "studio":{"id":"2","name":"St"},"tags":[],"performers":[],"captions":[],
                 "files":[{"path":"/data/t.mp4","size":9,"duration":10.0,"width":1920,"height":1080}]}
            ]}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val scenes = kotlinx.coroutines.runBlocking { api.findTopPlayedScenes() }
            assertEquals(1, scenes.size)
            assertEquals("top", scenes[0].title)
            assertEquals("St", scenes[0].studio?.name)
            val variables = com.google.gson.JsonParser.parseString(requestBody.get())
                .asJsonObject.getAsJsonObject("variables")
            assertEquals("play_count", variables.get("filter").asJsonObject.get("sort").asString)
            assertEquals("DESC", variables.get("filter").asJsonObject.get("direction").asString)
            assertEquals(200, variables.get("filter").asJsonObject.get("per_page").asInt)
            val playCount = variables.getAsJsonObject("scene_filter").getAsJsonObject("play_count")
            assertEquals("GREATER_THAN", playCount.get("modifier").asString)
            assertEquals(0, playCount.get("value").asInt)
            org.junit.Assert.assertTrue(
                "Expected full metadata fields in query: ${requestBody.get()}",
                requestBody.get().contains("performers") && requestBody.get().contains("o_counter")
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `getStats parses the server stats payload`() {
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            val response = """{"data":{"stats":{
                "scene_count":120,"scenes_size":300.5,"scenes_duration":432000.0,
                "image_count":10,"performer_count":7,"studio_count":3,"tag_count":40,
                "total_o_count":11,"total_play_duration":5400.0,
                "total_play_count":77,"scenes_played":55}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val stats = kotlinx.coroutines.runBlocking { api.getStats() }
            assertEquals(120, stats.sceneCount)
            assertEquals(7, stats.performerCount)
            assertEquals(40, stats.tagCount)
            assertEquals(3, stats.studioCount)
            assertEquals(5400.0, stats.totalPlayDurationSeconds, 0.0)
            assertEquals(77, stats.totalPlayCount)
            assertEquals(55, stats.scenesPlayed)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `recently-played filter omits last_played_at for Stash 0_24 compat`() {
        // Regression guard for 1.17.0:
        // 1) Used invalid CriterionModifier IS_NOT_NULL → HTTP 422
        // 2) last_played_at scene_filter only exists on Stash ≥ 0.26; README
        //    targets 0.24+. Filter must stay limited to play_count/resume_time.
        val filter = SinemaApi.RECENTLY_PLAYED_SCENE_FILTER
        assertEquals(setOf("play_count", "resume_time"), filter.keys)
        assertEquals(false, filter.containsKey("last_played_at"))

        @Suppress("UNCHECKED_CAST")
        val playCount = filter["play_count"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val resumeTime = filter["resume_time"] as Map<String, Any>
        assertEquals("GREATER_THAN", playCount["modifier"])
        assertEquals(0, playCount["value"])
        assertEquals("EQUALS", resumeTime["modifier"])
        assertEquals(0, resumeTime["value"])
    }

    @Test
    fun `recently-played client sort puts null timestamps last`() {
        // Mirrors findRecentlyPlayed's partition + sortedByDescending so a
        // future rewrite cannot drop the nulls-last guarantee.
        data class Row(val id: String, val lastPlayedAt: String?)
        val rows = listOf(
            Row("null-a", null),
            Row("newer", "2026-07-18T12:00:00Z"),
            Row("older", "2026-01-01T00:00:00Z"),
            Row("null-b", null)
        )
        val (withTs, withoutTs) = rows.partition { it.lastPlayedAt != null }
        val sorted = withTs.sortedByDescending { it.lastPlayedAt } + withoutTs
        assertEquals(listOf("newer", "older", "null-a", "null-b"), sorted.map { it.id })
    }

    @Test
    fun `unfavorite sends explicit null rating`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"sceneUpdate":{"id":"42","rating100":100}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            kotlinx.coroutines.runBlocking { api.setSceneRating("42", null) }
            org.junit.Assert.assertTrue(
                "Expected rating100:null in mutation body: ${requestBody.get()}",
                requestBody.get().contains("\"rating100\":null")
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `entity search sends query sort direction and page for every kind`() {
        val requestBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBodies.add(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val cases = listOf(
                Triple(com.sinema.model.EntityItem.Kind.TAG, "findTags", "tags"),
                Triple(com.sinema.model.EntityItem.Kind.PERFORMER, "findPerformers", "performers"),
                Triple(com.sinema.model.EntityItem.Kind.STUDIO, "findStudios", "studios")
            )
            kotlinx.coroutines.runBlocking {
                cases.forEach { (kind, _, _) ->
                    api.findEntities(
                        kind,
                        page = 3,
                        perPage = 50,
                        searchTerm = "space",
                        sort = "name",
                        direction = "ASC"
                    )
                }
            }
            assertEquals(3, requestBodies.size)
            cases.forEachIndexed { index, (_, queryName, listKey) ->
                val body = requestBodies[index]
                org.junit.Assert.assertTrue(body, body.contains(queryName))
                org.junit.Assert.assertTrue(body, body.contains("$listKey { id name"))
                org.junit.Assert.assertTrue(body, body.contains("\"page\":3"))
                org.junit.Assert.assertTrue(body, body.contains("\"per_page\":50"))
                org.junit.Assert.assertTrue(body, body.contains("\"q\":\"space\""))
                org.junit.Assert.assertTrue(body, body.contains("\"sort\":\"name\""))
                org.junit.Assert.assertTrue(body, body.contains("\"direction\":\"ASC\""))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `scene search combines watched and favorite filters`() {
        val requestBody = java.util.concurrent.atomic.AtomicReference("")
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findScenes":{"count":0,"scenes":[]}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            kotlinx.coroutines.runBlocking {
                api.searchScenes(
                    "space",
                    filters = com.sinema.model.SceneFilterOptions(
                        com.sinema.model.WatchFilter.UNWATCHED,
                        favoritesOnly = true
                    )
                )
            }
            val body = requestBody.get()
            org.junit.Assert.assertTrue(body, body.contains("\"q\":\"space\""))
            org.junit.Assert.assertTrue(body, body.contains("\"play_count\":{\"value\":0,\"modifier\":\"EQUALS\"}"))
            org.junit.Assert.assertTrue(body, body.contains("\"rating100\":{\"value\":0,\"modifier\":\"GREATER_THAN\"}"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `entity and folder constraints survive user filters`() {
        val requestBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 0), 0
        )
        server.createContext("/graphql") { exchange ->
            requestBodies.add(exchange.requestBody.bufferedReader().readText())
            val response = """{"data":{"findScenes":{"count":0,"scenes":[]}}}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val api = SinemaApi("http://127.0.0.1:${server.address.port}", "k")
            val filters = com.sinema.model.SceneFilterOptions(
                com.sinema.model.WatchFilter.WATCHED,
                favoritesOnly = true
            )
            kotlinx.coroutines.runBlocking {
                api.findScenesForEntity(
                    com.sinema.model.EntityItem.Kind.TAG,
                    "tag-1",
                    filters = filters
                )
                api.findScenesByPath("/data/Foo - Bar", filters = filters)
            }
            assertEquals(2, requestBodies.size)
            val entityBody = requestBodies[0]
            org.junit.Assert.assertTrue(entityBody, entityBody.contains("\"tags\""))
            org.junit.Assert.assertTrue(entityBody, entityBody.contains("\"play_count\""))
            org.junit.Assert.assertTrue(entityBody, entityBody.contains("\"rating100\""))
            val folderBody = requestBodies[1]
            org.junit.Assert.assertTrue(folderBody, folderBody.contains("\"path\""))
            org.junit.Assert.assertTrue(folderBody, folderBody.contains("Foo - Bar"))
            org.junit.Assert.assertTrue(folderBody, folderBody.contains("\"play_count\""))
            org.junit.Assert.assertTrue(folderBody, folderBody.contains("\"rating100\""))
        } finally {
            server.stop(0)
        }
    }
}
