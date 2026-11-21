package com.sinema.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.runBlocking

class SceneDestroyMutationApiTest {
    @Test
    fun `destroyScene deletes scene and original file`() {
        withGraphqlServer("""{"data":{"sceneDestroy":true}}""") { api, body ->
            runBlocking { api.destroyScene("scene-7") }

            val request = JsonParser.parseString(body()).asJsonObject
            assertTrue(request.get("query").asString.contains("sceneDestroy(input:"))
            val input = request.getAsJsonObject("variables").getAsJsonObject("input")
            assertEquals("scene-7", input.get("id").asString)
            assertTrue(input.get("delete_file").asBoolean)
        }
    }

    @Test
    fun `destroyScene rejects false result`() {
        withGraphqlServer("""{"data":{"sceneDestroy":false}}""") { api, _ ->
            val error = assertThrows(IOException::class.java) {
                runBlocking { api.destroyScene("scene-7") }
            }
            assertEquals("Stash could not delete the scene", error.message)
        }
    }
}
