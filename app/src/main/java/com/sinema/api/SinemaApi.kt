package com.sinema.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sinema.model.Scene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SinemaApi(
    private var serverUrl: String,
    private var apiKey: String,
    private var sessionCookie: String = "",
    private var authMode: String = "apikey"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    var stashUsername: String = ""
    var stashPassword: String = ""
    var onSessionRefreshed: ((String) -> Unit)? = null

    fun updateConfig(url: String, key: String, cookie: String = "", mode: String = "apikey") {
        serverUrl = url.trimEnd('/')
        apiKey = key
        sessionCookie = cookie
        authMode = mode
    }

    fun getScreenshotUrl(sceneId: String): String = "$serverUrl/scene/$sceneId/screenshot"
    fun getStreamUrl(sceneId: String): String = "$serverUrl/scene/$sceneId/stream"

    private suspend fun graphql(query: String, variables: Map<String, Any?> = emptyMap()): JsonObject {
        return withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("query" to query, "variables" to variables))

            val first = executeGraphql(body)

            if (authMode == "session" && first.code == 401) {
                val refreshed = reLogin()
                if (!refreshed.isNullOrBlank()) {
                    sessionCookie = refreshed
                    onSessionRefreshed?.invoke(refreshed)
                    val retry = executeGraphql(body)
                    return@withContext parseGraphqlResponse(retry.code, retry.body)
                }
            }

            parseGraphqlResponse(first.code, first.body)
        }
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun executeGraphql(body: String): HttpResult {
        val requestBuilder = Request.Builder()
            .url("$serverUrl/graphql")
            .addHeader("Content-Type", "application/json")

        if (authMode == "session" && sessionCookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", sessionCookie)
        } else {
            requestBuilder.addHeader("ApiKey", apiKey)
        }

        val request = requestBuilder
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            return HttpResult(response.code, response.body?.string() ?: "{}")
        }
    }

    private fun parseGraphqlResponse(code: Int, body: String): JsonObject {
        if (code !in 200..299) throw IOException("Server error $code")

        val obj = gson.fromJson(body, JsonObject::class.java)
        if (obj.has("errors") && obj.getAsJsonArray("errors").size() > 0) {
            val first = obj.getAsJsonArray("errors")[0].asJsonObject
            val msg = first.get("message")?.asString ?: "GraphQL error"
            throw IOException(msg)
        }
        return obj
    }

    private fun reLogin(): String? {
        if (stashUsername.isBlank() || stashPassword.isBlank()) return null
        return try {
            val loginClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()

            val loginBody = FormBody.Builder()
                .add("username", stashUsername)
                .add("password", stashPassword)
                .build()

            val loginRequest = Request.Builder()
                .url("$serverUrl/login")
                .post(loginBody)
                .build()

            loginClient.newCall(loginRequest).execute().use { response ->
                if (response.code !in listOf(200, 302, 303, 307, 308)) return null
                val sessionCookie = response.headers("Set-Cookie").firstOrNull { it.contains("session") } ?: return null
                sessionCookie.substringBefore(';')
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findAllScenes(page: Int = 1, perPage: Int = 100): Pair<Int, List<Scene>> {
        val query = """
            query(${"$"}filter: FindFilterType) {
                findScenes(filter: ${"$"}filter) {
                    count
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("page" to page, "per_page" to perPage, "sort" to "path", "direction" to "ASC")
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data").getAsJsonObject("findScenes")
        val count = data.get("count").asInt
        val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
        return Pair(count, scenes)
    }

    suspend fun findScenesByPath(pathPrefix: String, page: Int = 1, perPage: Int = 100): Pair<Int, List<Scene>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    count
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("page" to page, "per_page" to perPage, "sort" to "path", "direction" to "ASC"),
            "scene_filter" to mapOf("path" to mapOf("value" to pathPrefix, "modifier" to "INCLUDES"))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data").getAsJsonObject("findScenes")
        val count = data.get("count").asInt
        val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
        return Pair(count, scenes)
    }

    suspend fun searchScenes(searchTerm: String, page: Int = 1, perPage: Int = 40): Pair<Int, List<Scene>> {
        val query = """
            query(${"$"}filter: FindFilterType) {
                findScenes(filter: ${"$"}filter) {
                    count
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("q" to searchTerm, "page" to page, "per_page" to perPage, "sort" to "path", "direction" to "ASC")
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data").getAsJsonObject("findScenes")
        val count = data.get("count").asInt
        val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
        return Pair(count, scenes)
    }

    suspend fun setSceneRating(sceneId: String, rating: Int?): Boolean {
        val query = """
            mutation(${"$"}input: SceneUpdateInput!) {
                sceneUpdate(input: ${"$"}input) { id rating100 }
            }
        """.trimIndent()
        val input = mutableMapOf<String, Any?>("id" to sceneId)
        if (rating != null) input["rating100"] = rating else input["rating100"] = null
        val result = graphql(query, mapOf("input" to input))
        return result.has("data")
    }

    suspend fun getSceneRating(sceneId: String): Int? {
        val query = """
            query(${"$"}id: ID!) {
                findScene(id: ${"$"}id) { id rating100 }
            }
        """.trimIndent()
        val result = graphql(query, mapOf("id" to sceneId))
        val scene = result.getAsJsonObject("data")?.getAsJsonObject("findScene") ?: return null
        return if (scene.get("rating100")?.isJsonNull != false) null else scene.get("rating100").asInt
    }

    suspend fun findFavoriteScenes(): List<Scene> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    count
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 100, "sort" to "updated_at", "direction" to "DESC"),
            "scene_filter" to mapOf("rating100" to mapOf("value" to 1, "modifier" to "GREATER_THAN"))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes") ?: return emptyList()
        return data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
    }

    suspend fun findRecentScenes(perPage: Int = 25): List<Scene> {
        val query = """
            query(${"$"}filter: FindFilterType) {
                findScenes(filter: ${"$"}filter) {
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to perPage, "sort" to "created_at", "direction" to "DESC")
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes") ?: return emptyList()
        return data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
    }

    suspend fun findTopLevelFolders(): List<Pair<String, String>> {
        val idQuery = """
            query {
                findFolders(folder_filter: { path: { value: "/data", modifier: EQUALS } }, filter: { per_page: 1 }) {
                    folders { id }
                }
            }
        """.trimIndent()
        val idResult = graphql(idQuery)
        val dataFolderId = idResult.getAsJsonObject("data")
            ?.getAsJsonObject("findFolders")
            ?.getAsJsonArray("folders")
            ?.firstOrNull()?.asJsonObject?.get("id")?.asString ?: return emptyList()

        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}folder_filter: FolderFilterType) {
                findFolders(filter: ${"$"}filter, folder_filter: ${"$"}folder_filter) {
                    count
                    folders { id path }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 500, "sort" to "path", "direction" to "ASC"),
            "folder_filter" to mapOf("parent_folder" to mapOf("value" to listOf(dataFolderId), "modifier" to "INCLUDES", "depth" to 0))
        )
        val result = graphql(query, variables)
        val folders = result.getAsJsonObject("data")
            ?.getAsJsonObject("findFolders")
            ?.getAsJsonArray("folders") ?: return emptyList()

        return folders.map { f ->
            val obj = f.asJsonObject
            val path = obj.get("path").asString
            val name = path.removePrefix("/data/")
            Pair(name, path)
        }
    }

    suspend fun findTopLevelFoldersPaged(page: Int = 1, perPage: Int = 500): Pair<Int, List<Pair<String, String>>> {
        val idQuery = """
            query {
                findFolders(folder_filter: { path: { value: "/data", modifier: EQUALS } }, filter: { per_page: 1 }) {
                    folders { id }
                }
            }
        """.trimIndent()
        val idResult = graphql(idQuery)
        val dataFolderId = idResult.getAsJsonObject("data")
            ?.getAsJsonObject("findFolders")
            ?.getAsJsonArray("folders")
            ?.firstOrNull()?.asJsonObject?.get("id")?.asString ?: return Pair(0, emptyList())

        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}folder_filter: FolderFilterType) {
                findFolders(filter: ${"$"}filter, folder_filter: ${"$"}folder_filter) {
                    count
                    folders { id path }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to perPage, "page" to page, "sort" to "path", "direction" to "ASC"),
            "folder_filter" to mapOf("parent_folder" to mapOf("value" to listOf(dataFolderId), "modifier" to "INCLUDES", "depth" to 0))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findFolders") ?: return Pair(0, emptyList())
        val count = data.get("count").asInt
        val folders = data.getAsJsonArray("folders").map { f ->
            val obj = f.asJsonObject
            val path = obj.get("path").asString
            val name = path.removePrefix("/data/")
            Pair(name, path)
        }
        return Pair(count, folders)
    }

    suspend fun findScenesByIds(ids: List<String>): List<Scene> {
        if (ids.isEmpty()) return emptyList()
        val intIds = ids.mapNotNull { it.toIntOrNull() }
        if (intIds.isEmpty()) return emptyList()
        val query = """
            query(${"$"}ids: [Int!]) {
                findScenes(scene_ids: ${"$"}ids, filter: { per_page: ${intIds.size} }) {
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val result = graphql(query, mapOf("ids" to intIds))
        val scenes = result.getAsJsonObject("data")
            ?.getAsJsonObject("findScenes")
            ?.getAsJsonArray("scenes")
            ?.map { parseScene(it.asJsonObject) } ?: return emptyList()
        val sceneMap = scenes.associateBy { it.id }
        return ids.mapNotNull { sceneMap[it] }
    }

    suspend fun saveSceneActivity(sceneId: String, resumeTimeSec: Double, playDurationSec: Double) {
        val query = """
            mutation(${"$"}id: ID!, ${"$"}resume_time: Float, ${"$"}playDuration: Float) {
                sceneSaveActivity(id: ${"$"}id, resume_time: ${"$"}resume_time, playDuration: ${"$"}playDuration)
            }
        """.trimIndent()
        graphql(query, mapOf("id" to sceneId, "resume_time" to resumeTimeSec, "playDuration" to playDurationSec))
    }

    suspend fun clearResumeTime(sceneId: String) {
        val query = """
            mutation(${"$"}id: ID!, ${"$"}resume_time: Float, ${"$"}playDuration: Float) {
                sceneSaveActivity(id: ${"$"}id, resume_time: ${"$"}resume_time, playDuration: ${"$"}playDuration)
            }
        """.trimIndent()
        val result = graphql(query, mapOf("id" to sceneId, "resume_time" to 0, "playDuration" to 0))
        Log.d("Sinema", "clearResumeTime (saveActivity) result: $result")
    }

    suspend fun incrementPlayCount(sceneId: String) {
        val query = """
            mutation(${"$"}id: ID!) {
                sceneIncrementPlayCount(id: ${"$"}id)
            }
        """.trimIndent()
        graphql(query, mapOf("id" to sceneId))
    }

    suspend fun findRecentlyPlayed(perPage: Int = 25): List<Scene> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    scenes {
                        id title play_count last_played_at resume_time rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to perPage, "sort" to "last_played_at", "direction" to "DESC"),
            "scene_filter" to mapOf(
                "play_count" to mapOf("value" to 0, "modifier" to "GREATER_THAN"),
                "resume_time" to mapOf("value" to 0, "modifier" to "EQUALS")
            )
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes") ?: return emptyList()
        return data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
    }

    suspend fun findContinuePlaying(): List<Pair<Scene, Double>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    scenes {
                        id title resume_time play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 25, "sort" to "last_played_at", "direction" to "DESC"),
            "scene_filter" to mapOf("resume_time" to mapOf("value" to 0, "modifier" to "GREATER_THAN"))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes") ?: return emptyList()
        return data.getAsJsonArray("scenes").map { obj ->
            val scene = parseScene(obj.asJsonObject)
            val resumeTime = obj.asJsonObject.get("resume_time")?.asDouble ?: 0.0
            Pair(scene, resumeTime)
        }
    }

    suspend fun hasFavoritesInPath(pathPrefix: String): Boolean {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    count
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 0),
            "scene_filter" to mapOf(
                "path" to mapOf("value" to "$pathPrefix/", "modifier" to "INCLUDES"),
                "rating100" to mapOf("value" to 1, "modifier" to "GREATER_THAN")
            )
        )
        val result = graphql(query, variables)
        val count = result.getAsJsonObject("data")?.getAsJsonObject("findScenes")?.get("count")?.asInt ?: 0
        return count > 0
    }

    suspend fun getFirstSceneIdForPath(pathPrefix: String): String? {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    scenes { id }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 1, "sort" to "random"),
            "scene_filter" to mapOf("path" to mapOf("value" to "$pathPrefix/", "modifier" to "INCLUDES"))
        )
        val result = graphql(query, variables)
        return result.getAsJsonObject("data")
            ?.getAsJsonObject("findScenes")
            ?.getAsJsonArray("scenes")
            ?.firstOrNull()?.asJsonObject?.get("id")?.asString
    }

    suspend fun getSceneCountForPath(pathPrefix: String): Int {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    count
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 0),
            "scene_filter" to mapOf("path" to mapOf("value" to "$pathPrefix/", "modifier" to "INCLUDES"))
        )
        val result = graphql(query, variables)
        return result.getAsJsonObject("data")?.getAsJsonObject("findScenes")?.get("count")?.asInt ?: 0
    }

    suspend fun findScenesInFolderDirect(folderPath: String, page: Int = 1, perPage: Int = 500): Pair<Int, List<Scene>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    count
                    scenes {
                        id title play_count rating100
                        files { path size duration width height }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("page" to page, "per_page" to perPage, "sort" to "path", "direction" to "ASC"),
            "scene_filter" to mapOf("path" to mapOf("value" to "^${Regex.escape(folderPath)}/[^/]+$", "modifier" to "MATCHES_REGEX"))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes") ?: return Pair(0, emptyList())
        val count = data.get("count").asInt
        val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
        return Pair(count, scenes)
    }

    private fun parseScene(obj: JsonObject): Scene {
        val id = obj.get("id").asString
        val title = obj.get("title")?.asString ?: ""
        val files = obj.getAsJsonArray("files")
        val file = if (files != null && files.size() > 0) files[0].asJsonObject else null
        return Scene(
            id = id,
            title = title,
            path = file?.get("path")?.asString ?: "",
            size = file?.get("size")?.asLong ?: 0L,
            duration = file?.get("duration")?.asDouble ?: 0.0,
            width = file?.get("width")?.asInt ?: 0,
            height = file?.get("height")?.asInt ?: 0,
            playCount = obj.get("play_count")?.asInt ?: 0,
            rating100 = if (obj.has("rating100") && !obj.get("rating100").isJsonNull) obj.get("rating100").asInt else null
        )
    }

    suspend fun resetPlayCount(sceneId: String) {
        val query = """
            mutation(${"$"}id: ID!) {
                sceneResetPlayCount(id: ${"$"}id)
            }
        """.trimIndent()
        val result = graphql(query, mapOf("id" to sceneId))
        Log.d("Sinema", "resetPlayCount result: $result")
    }
}
