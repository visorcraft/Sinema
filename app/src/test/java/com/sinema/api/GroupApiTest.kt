package com.sinema.api

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GroupApiTest {

    @Test
    fun `parseGroup reads artwork metadata and tolerates nulls`() {
        val api = SinemaApi("http://server", "k")
        val full = JsonParser.parseString(
            """{"id":"7","name":"Collection","scene_count":3,
                "front_image_path":"http://server/group/7/front",
                "back_image_path":"http://server/group/7/back",
                "date":"2024-03-02","director":"Director","synopsis":" Synopsis ",
                "studio":{"name":"Studio"}}"""
        ).asJsonObject
        val nulls = JsonParser.parseString(
            """{"id":"8","name":null,"scene_count":null,"front_image_path":null,
                "back_image_path":null,"date":null,"director":null,"synopsis":null,"studio":null}"""
        ).asJsonObject

        val group = api.parseGroup(full)
        assertEquals("7", group.id)
        assertEquals("Collection", group.name)
        assertEquals(3, group.sceneCount)
        assertEquals("http://server/group/7/front", group.frontImagePath)
        assertEquals("http://server/group/7/back", group.backImagePath)
        assertEquals("2024-03-02", group.date)
        assertEquals("Director", group.director)
        assertEquals("Synopsis", group.synopsis)
        assertEquals("Studio", group.studioName)

        val empty = api.parseGroup(nulls)
        assertEquals("8", empty.id)
        assertEquals("", empty.name)
        assertEquals(0, empty.sceneCount)
        assertNull(empty.frontImagePath)
        assertNull(empty.backImagePath)
        assertNull(empty.date)
        assertNull(empty.director)
        assertNull(empty.synopsis)
        assertNull(empty.studioName)
    }

    @Test
    fun `findGroups sends paging search and sort and parses results`() {
        val requests = mutableListOf<String>()
        withServer(
            listOf("""{"data":{"findGroups":{"count":1,"groups":[
                {"id":"7","name":"Collection","scene_count":2,"front_image_path":"front"}
            ]}}}"""), requests
        ) { api ->
            val (count, groups) = api.findGroups(page = 2, perPage = 30, searchTerm = "coll", sort = "name", direction = "ASC")
            assertEquals(1, count)
            assertEquals(listOf("7"), groups.map { it.id })
        }

        val body = JsonParser.parseString(requests.single()).asJsonObject
        assertTrue(body.get("query").asString.contains("findGroups"))
        val filter = body.getAsJsonObject("variables").getAsJsonObject("filter")
        assertEquals(2, filter.get("page").asInt)
        assertEquals(30, filter.get("per_page").asInt)
        assertEquals("coll", filter.get("q").asString)
        assertEquals("name", filter.get("sort").asString)
        assertEquals("ASC", filter.get("direction").asString)
    }

    @Test
    fun `findGroups falls back to legacy Movies when Groups schema is missing`() {
        val requests = mutableListOf<String>()
        withServer(
            listOf(
                """{"errors":[{"message":"Cannot query field 'findGroups' on type 'Query'"}]}""",
                """{"data":{"findMovies":{"count":1,"movies":[
                    {"id":"m1","name":"Legacy","scene_count":4}
                ]}}}"""
            ), requests,
            statusCodes = listOf(422, 200)
        ) { api ->
            val (count, groups) = api.findGroups()
            assertEquals(1, count)
            assertEquals("m1", groups.single().id)
            assertEquals(4, groups.single().sceneCount)
        }

        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("findGroups"))
        assertTrue(requests[1].contains("findMovies"))
    }

    @Test
    fun `findGroups does not retry unrelated GraphQL errors`() {
        val requests = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            runBlocking {
                withServer(
                    listOf("""{"errors":[{"message":"Permission denied"}]}"""), requests
                ) { api -> api.findGroups() }
            }
        }
        assertEquals(1, requests.size)
    }

    @Test
    fun `findGroups tolerates a null result`() {
        val requests = mutableListOf<String>()
        withServer(listOf("""{"data":{"findGroups":null}}"""), requests) { api ->
            val (count, groups) = api.findGroups()
            assertEquals(0, count)
            assertTrue(groups.isEmpty())
        }
    }

    @Test
    fun `findGroupDetails preserves Stash scene order and falls back to Movie`() {
        val requests = mutableListOf<String>()
        withServer(
            listOf(
                """{"errors":[{"message":"Cannot query field 'findGroup' on type 'Query'"}]}""",
                """{"data":{"findMovie":{"id":"m1","name":"Legacy","scene_count":2,
                    "scenes":[
                        {"id":"20","title":"Second file","files":[]},
                        {"id":"10","title":"First file","files":[]}
                    ]}}}"""
            ), requests
        ) { api ->
            val details = api.findGroupDetails("m1")
            assertEquals("Legacy", details?.group?.name)
            assertEquals(listOf("20", "10"), details?.scenes?.map { it.id })
        }

        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("findGroup("))
        assertTrue(requests[1].contains("findMovie("))
    }

    private fun withServer(
        responses: List<String>,
        requests: MutableList<String>,
        statusCodes: List<Int> = List(responses.size) { 200 },
        block: suspend (SinemaApi) -> Unit
    ) {
        withGraphqlServer(
            responses.mapIndexed { i, body -> SequencedResponse(body, statusCodes[i]) }
        ) { api, captured ->
            try {
                kotlinx.coroutines.runBlocking { block(api) }
            } finally {
                requests.addAll(captured())
            }
        }
    }
}
