package com.sinema.api

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sinema.model.CaptionRef
import com.sinema.model.EntityHeader
import com.sinema.model.EntityItem
import com.sinema.model.SavedSceneFilter
import com.sinema.model.ImageItem
import com.sinema.model.MarkerRef
import com.sinema.model.PerformerRef
import com.sinema.model.Scene
import com.sinema.model.SceneDetails
import com.sinema.model.StudioRef
import com.sinema.model.TagRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SinemaApi(
    private var serverUrl: String,
    private var apiKey: String,
    private var sessionCookie: String = "",
    private var authMode: String = "apikey",
    allowSelfSigned: Boolean = false
) {

    companion object {
        // Single source of truth for scene list payloads.
        // resume_time ships on Scene since Stash 0.24 and powers the
        // watch-progress bar on cards (see Scene.watchProgressPercent).
        internal const val SCENE_FIELDS =
            "id title play_count rating100 resume_time files { path size duration width height }"

        // Full scene payload — used by findSceneFull for detail/player screens.
        internal const val SCENE_FIELDS_FULL = SCENE_FIELDS +
            " date" +
            " details" +
            " o_counter" +
            " studio { id name }" +
            " tags { id name }" +
            " performers { id name }" +
            " captions { language_code caption_type }"

        /**
         * Scene filter for Recently Played.
         *
         * Intentionally does **not** filter on `last_played_at`:
         * - That SceneFilterType field only exists on Stash ≥ 0.26; Sinema
         *   targets 0.24+ (see README). Sending it 422s older servers.
         * - Even on ≥ 0.26, Stash implements the filter as
         *   `IFNULL(last_played_at, datetime(0)) …`, so `NOT_NULL` is a no-op.
         * - 1.17.0 shipped `modifier: IS_NOT_NULL` (invalid CriterionModifier
         *   enum; real value is `NOT_NULL`) which 422'd every home load.
         *
         * Recency is enforced client-side from the `last_played_at` field
         * (available on Scene since 0.24) after play_count/resume_time filter.
         */
        @VisibleForTesting
        internal val RECENTLY_PLAYED_SCENE_FILTER: Map<String, Any?> = mapOf(
            "play_count" to mapOf("value" to 0, "modifier" to "GREATER_THAN"),
            "resume_time" to mapOf("value" to 0, "modifier" to "EQUALS")
        )
    }

    /**
     * Opt-in trust-all TLS for servers behind self-signed certificates
     * (per profile, off by default). Disables certificate and hostname
     * verification for this server's connections only.
     */
    private var trustAllCerts: Boolean = allowSelfSigned

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Folder browsing fans out many small queries; the default
        // per-host cap of 5 serializes them into a long stall.
        .dispatcher(Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 12
        })

    private val strictClient: OkHttpClient by lazy { baseClientBuilder().build() }

    private val trustAllClient: OkHttpClient by lazy {
        with(com.sinema.util.TlsTrust) {
            baseClientBuilder().trustAllCertificates().build()
        }
    }

    /** Active HTTP client: strict TLS unless the profile opted into self-signed trust. */
    val client: OkHttpClient get() = if (trustAllCerts) trustAllClient else strictClient

    /** Client for ExoPlayer's OkHttp data source; shares the active TLS config. */
    fun okHttpClient(): OkHttpClient = client

    /**
     * Client for system-search suggestions. Runs on a binder thread inside
     * GlobalSearchProvider where coroutine cancellation cannot interrupt a
     * blocking execute(), so OkHttp's total callTimeout enforces the
     * deadline instead (6s covers connect + read for a healthy server).
     */
    private val strictSuggestionsClient: OkHttpClient by lazy {
        strictClient.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    private val trustAllSuggestionsClient: OkHttpClient by lazy {
        trustAllClient.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    private val suggestionsClient: OkHttpClient
        get() = if (trustAllCerts) trustAllSuggestionsClient else strictSuggestionsClient
    private val gson = Gson()
    private val gsonWithNulls = com.google.gson.GsonBuilder().serializeNulls().create()

    var stashUsername: String = ""
    var stashPassword: String = ""
    var onSessionRefreshed: ((String) -> Unit)? = null

    fun updateConfig(
        url: String,
        key: String,
        cookie: String = "",
        mode: String = "apikey",
        allowSelfSigned: Boolean = false
    ) {
        serverUrl = url.trimEnd('/')
        apiKey = key
        sessionCookie = cookie
        authMode = mode
        trustAllCerts = allowSelfSigned
    }

    fun getScreenshotUrl(sceneId: String): String = "$serverUrl/scene/$sceneId/screenshot"
    fun getStreamUrl(sceneId: String): String = "$serverUrl/scene/$sceneId/stream"
    fun getAlignedHlsStreamUrl(sceneId: String): String =
        getHlsStreamUrl(sceneId, "STANDARD")

    /** Aligned HLS transcode URL at a Stash ResolutionEnum tier. */
    fun getHlsStreamUrl(sceneId: String, resolution: String): String =
        "$serverUrl/scene/$sceneId/stream.m3u8?resolution=$resolution&align=16x8"
    fun getImageThumbnailUrl(imageId: String): String = "$serverUrl/image/$imageId/thumbnail"
    fun getImageUrl(imageId: String): String = "$serverUrl/image/$imageId/image"

    /**
     * Auth headers for non-GraphQL media requests (screenshots, streams,
     * images). In session mode the API key is blank, so media must be
     * authenticated with the session cookie instead.
     */
    fun mediaAuthHeaders(): Map<String, String> =
        if (authMode == "session" && sessionCookie.isNotBlank()) mapOf("Cookie" to sessionCookie)
        else mapOf("ApiKey" to apiKey)

    // Stash's INCLUDES modifier tokenizes the value on spaces and treats
    // "-"-prefixed tokens as exclusions, so folder names like "Foo - Bar"
    // match nothing. Anchored MATCHES_REGEX matches the path literally.
    private fun pathPrefixRegex(path: String): String = "^${Regex.escape(path.trimEnd('/'))}/"

    private suspend fun findScenesInternal(
        page: Int = 1,
        perPage: Int = 100,
        sort: String = "path",
        direction: String = "ASC",
        searchTerm: String? = null,
        sceneFilter: Map<String, Any?>? = null,
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        fields: String = SCENE_FIELDS
    ): Pair<Int, List<Scene>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    count
                    scenes { $fields }
                }
            }
        """.trimIndent()
        val filter = mutableMapOf<String, Any?>(
            "page" to page, "per_page" to perPage, "sort" to sort, "direction" to direction
        )
        if (searchTerm != null) filter["q"] = searchTerm
        val combinedSceneFilter = (sceneFilter.orEmpty() + filters.toStashFilter())
            .takeIf { it.isNotEmpty() }
        val result = graphql(query, mapOf("filter" to filter, "scene_filter" to combinedSceneFilter))
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes")
            ?: return Pair(0, emptyList())
        val count = data.get("count").asInt
        val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
        return Pair(count, scenes)
    }

    private suspend fun graphql(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        serializeNulls: Boolean = false
    ): JsonObject {
        return withContext(Dispatchers.IO) {
            val requestGson = if (serializeNulls) gsonWithNulls else gson
            val body = requestGson.toJson(mapOf("query" to query, "variables" to variables))

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
            // Derive from the active client so self-signed trust applies.
            val loginClient = client.newBuilder()
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

    suspend fun findAllScenes(page: Int = 1, perPage: Int = 100): Pair<Int, List<Scene>> =
        findScenesInternal(page = page, perPage = perPage)

    suspend fun findScenesByPath(
        pathPrefix: String, page: Int = 1, perPage: Int = 100,
        sort: String = "path", direction: String = "ASC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>> =
        findScenesInternal(
            page = page, perPage = perPage, sort = sort, direction = direction,
            sceneFilter = mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX")),
            filters = filters
        )

    suspend fun findImagesByPath(pathPrefix: String, page: Int = 1, perPage: Int = 100): Pair<Int, List<ImageItem>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}image_filter: ImageFilterType) {
                findImages(filter: ${"$"}filter, image_filter: ${"$"}image_filter) {
                    count
                    images {
                        id title rating100
                        visual_files {
                            ... on ImageFile { path size width height }
                            ... on VideoFile { path size width height }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("page" to page, "per_page" to perPage, "sort" to "path", "direction" to "ASC"),
            "image_filter" to mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data").getAsJsonObject("findImages")
        val count = data.get("count").asInt
        val images = data.getAsJsonArray("images").map { parseImage(it.asJsonObject) }
        return Pair(count, images)
    }

    suspend fun getImageCountForPath(pathPrefix: String): Int {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}image_filter: ImageFilterType) {
                findImages(filter: ${"$"}filter, image_filter: ${"$"}image_filter) {
                    count
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 0),
            "image_filter" to mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))
        )
        val result = graphql(query, variables)
        return result.getAsJsonObject("data")?.getAsJsonObject("findImages")?.get("count")?.asInt ?: 0
    }

    suspend fun getFirstImageIdForPath(pathPrefix: String): String? {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}image_filter: ImageFilterType) {
                findImages(filter: ${"$"}filter, image_filter: ${"$"}image_filter) {
                    images { id }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("per_page" to 1, "sort" to "random"),
            "image_filter" to mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))
        )
        val result = graphql(query, variables)
        return result.getAsJsonObject("data")
            ?.getAsJsonObject("findImages")
            ?.getAsJsonArray("images")
            ?.firstOrNull()?.asJsonObject?.get("id")?.asString
    }

    suspend fun findImagesInFolderDirect(folderPath: String, page: Int = 1, perPage: Int = 500): Pair<Int, List<ImageItem>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}image_filter: ImageFilterType) {
                findImages(filter: ${"$"}filter, image_filter: ${"$"}image_filter) {
                    count
                    images {
                        id title rating100
                        visual_files {
                            ... on ImageFile { path size width height }
                            ... on VideoFile { path size width height }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = mapOf(
            "filter" to mapOf("page" to page, "per_page" to perPage, "sort" to "path", "direction" to "ASC"),
            "image_filter" to mapOf("path" to mapOf("value" to "^${Regex.escape(folderPath.trimEnd('/'))}/[^/]+$", "modifier" to "MATCHES_REGEX"))
        )
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findImages") ?: return Pair(0, emptyList())
        val count = data.get("count").asInt
        val images = data.getAsJsonArray("images").map { parseImage(it.asJsonObject) }
        return Pair(count, images)
    }

    private fun parseImage(obj: JsonObject): ImageItem {
        val files = obj.getAsJsonArray("visual_files")
        val file = if (files != null && files.size() > 0) files[0].asJsonObject else null
        return ImageItem(
            id = obj.get("id").asString,
            title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
            path = file?.get("path")?.asString ?: "",
            size = file?.get("size")?.asLong ?: 0L,
            width = file?.get("width")?.asInt ?: 0,
            height = file?.get("height")?.asInt ?: 0,
            rating100 = if (obj.has("rating100") && !obj.get("rating100").isJsonNull) obj.get("rating100").asInt else null
        )
    }

    suspend fun searchScenes(
        searchTerm: String, page: Int = 1, perPage: Int = 40,
        sort: String = "path", direction: String = "ASC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>> = findScenesInternal(
        page = page,
        perPage = perPage,
        sort = sort,
        direction = direction,
        searchTerm = searchTerm,
        filters = filters
    )

    /**
     * Best-effort scene search for system-search suggestions. Bounded by the
     * 6s suggestionsClient callTimeout (binder-thread caller cannot rely on
     * coroutine cancellation), no filters, no session re-login — a miss just
     * means no suggestions.
     */
    suspend fun searchScenesForSuggestions(searchTerm: String, perPage: Int = 8): List<Scene> {
        val query = """
            query(${"$"}filter: FindFilterType) {
                findScenes(filter: ${"$"}filter) {
                    count
                    scenes { $SCENE_FIELDS }
                }
            }
        """.trimIndent()
        return withContext(Dispatchers.IO) {
            val body = gson.toJson(
                mapOf(
                    "query" to query,
                    "variables" to mapOf(
                        "filter" to mapOf(
                            "page" to 1, "per_page" to perPage,
                            "sort" to "path", "direction" to "ASC", "q" to searchTerm
                        )
                    )
                )
            )
            val request = Request.Builder()
                .url("$serverUrl/graphql")
                .addHeader("Content-Type", "application/json")
                .apply {
                    if (authMode == "session" && sessionCookie.isNotBlank()) addHeader("Cookie", sessionCookie)
                    else addHeader("ApiKey", apiKey)
                }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            suggestionsClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val parsed = runCatching {
                    parseGraphqlResponse(response.code, response.body?.string() ?: "{}")
                }.getOrNull() ?: return@withContext emptyList()
                parsed.getAsJsonObject("data")?.getAsJsonObject("findScenes")
                    ?.getAsJsonArray("scenes")?.map { parseScene(it.asJsonObject) }
                    ?: emptyList()
            }
        }
    }

    suspend fun setSceneRating(sceneId: String, rating: Int?): Boolean {
        val query = """
            mutation(${"$"}input: SceneUpdateInput!) {
                sceneUpdate(input: ${"$"}input) { id rating100 }
            }
        """.trimIndent()
        val input = mutableMapOf<String, Any?>("id" to sceneId)
        input["rating100"] = rating
        val result = graphql(query, mapOf("input" to input), serializeNulls = true)
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

    suspend fun findFavoriteScenes(sort: String = "updated_at", direction: String = "DESC"): List<Scene> =
        findScenesInternal(
            perPage = 100, sort = sort, direction = direction,
            sceneFilter = mapOf("rating100" to mapOf("value" to 1, "modifier" to "GREATER_THAN"))
        ).second

    suspend fun findRecentScenes(perPage: Int = 25): List<Scene> =
        findScenesInternal(perPage = perPage, sort = "created_at", direction = "DESC").second

    /**
     * One random scene from the whole library, for the "Surprise Me" card.
     * @return a scene, or null when the library is empty.
     */
    suspend fun findRandomScene(): Scene? =
        findScenesInternal(perPage = 1, sort = "random").second.firstOrNull()

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
                    scenes { $SCENE_FIELDS }
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
        // Server sorts by last_played_at DESC, but Stash's row order is
        // unpredictable for scenes whose last_played_at is NULL even when
        // play_count > 0. Request the timestamp and re-sort client-side with
        // nulls last so Recently Played is always true recency order.
        // Do not send a last_played_at scene_filter — see
        // RECENTLY_PLAYED_SCENE_FILTER for why (compat + Stash no-op).
        // last_played_at is appended on top of SCENE_FIELDS for the client-side
        // recency re-sort below; resume_time already comes via SCENE_FIELDS.
        val scenes = findScenesInternal(
            perPage = perPage,
            sort = "last_played_at",
            direction = "DESC",
            sceneFilter = RECENTLY_PLAYED_SCENE_FILTER,
            fields = SCENE_FIELDS + " last_played_at"
        ).second
        val (withTs, withoutTs) = scenes.partition { it.lastPlayedAt != null }
        return withTs.sortedByDescending { it.lastPlayedAt } + withoutTs
    }

    suspend fun findContinuePlaying(): List<Pair<Scene, Double>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
                findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                    scenes { $SCENE_FIELDS }
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
            val resumeTime = obj.asJsonObject.get("resume_time")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
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
                "path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"),
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
            "scene_filter" to mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))
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
            "scene_filter" to mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))
        )
        val result = graphql(query, variables)
        return result.getAsJsonObject("data")?.getAsJsonObject("findScenes")?.get("count")?.asInt ?: 0
    }

    suspend fun findScenesInFolderDirect(
        folderPath: String, page: Int = 1, perPage: Int = 500,
        sort: String = "path", direction: String = "ASC"
    ): Pair<Int, List<Scene>> =
        findScenesInternal(
            page = page, perPage = perPage, sort = sort, direction = direction,
            sceneFilter = mapOf("path" to mapOf("value" to "^${Regex.escape(folderPath)}/[^/]+$", "modifier" to "MATCHES_REGEX"))
        )

    private fun parseTagRefs(arr: JsonArray): List<TagRef> = arr.map { el ->
        val t = el.asJsonObject
        TagRef(
            id = t.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
            name = t.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
        )
    }

    private fun parsePerformerRefs(arr: JsonArray): List<PerformerRef> = arr.map { el ->
        val p = el.asJsonObject
        PerformerRef(
            id = p.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
            name = p.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
        )
    }

    private fun parseCaptionRefs(arr: JsonArray): List<CaptionRef> = arr.map { el ->
        val c = el.asJsonObject
        CaptionRef(
            languageCode = c.get("language_code")?.takeIf { !it.isJsonNull }?.asString ?: "",
            captionType = c.get("caption_type")?.takeIf { !it.isJsonNull }?.asString ?: ""
        )
    }

    @VisibleForTesting
    internal fun parseScene(obj: JsonObject): Scene {
        val files = obj.getAsJsonArray("files")
        val file = if (files != null && files.size() > 0) files[0].asJsonObject else null
        // Optional metadata — only present in SCENE_FIELDS_FULL queries; guarded to keep lean list queries working.
        val studioObj = obj.get("studio")?.takeIf { !it.isJsonNull }?.asJsonObject
        return Scene(
            id = obj.get("id").asString,
            title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
            path = file?.get("path")?.asString ?: "",
            size = file?.get("size")?.asLong ?: 0L,
            duration = file?.get("duration")?.asDouble ?: 0.0,
            width = file?.get("width")?.asInt ?: 0,
            height = file?.get("height")?.asInt ?: 0,
            playCount = obj.get("play_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            rating100 = obj.get("rating100")?.takeIf { !it.isJsonNull }?.asInt,
            resumeTime = obj.get("resume_time")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
            date = obj.get("date")?.takeIf { !it.isJsonNull }?.asString,
            details = obj.get("details")?.takeIf { !it.isJsonNull }?.asString?.trim(),
            oCounter = obj.get("o_counter")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            studio = studioObj?.let { s ->
                StudioRef(
                    id = s.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    name = s.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                )
            },
            tags = (obj.get("tags") as? JsonArray)?.let(::parseTagRefs) ?: emptyList(),
            performers = (obj.get("performers") as? JsonArray)?.let(::parsePerformerRefs) ?: emptyList(),
            captions = (obj.get("captions") as? JsonArray)?.let(::parseCaptionRefs) ?: emptyList(),
            lastPlayedAt = obj.get("last_played_at")?.takeIf { !it.isJsonNull }?.asString
        )
    }

    /**
     * Fetch a paginated list of tags, performers, or studios with optional name search.
     * The default scene-count sort field is "scenes_count" (plural).
     */
    suspend fun findEntities(
        kind: EntityItem.Kind,
        page: Int = 1,
        perPage: Int = 100,
        searchTerm: String? = null,
        sort: String = "scenes_count",
        direction: String = "DESC"
    ): Pair<Int, List<EntityItem>> {
        val (queryName, listKey) = when (kind) {
            EntityItem.Kind.TAG -> "findTags" to "tags"
            EntityItem.Kind.PERFORMER -> "findPerformers" to "performers"
            EntityItem.Kind.STUDIO -> "findStudios" to "studios"
        }
        val query = """
            query(${"$"}filter: FindFilterType) {
                $queryName(filter: ${"$"}filter) {
                    count
                    $listKey { id name scene_count image_path }
                }
            }
        """.trimIndent()
        val filter = mutableMapOf<String, Any?>(
            "page" to page,
            "per_page" to perPage,
            "sort" to sort,
            "direction" to direction
        )
        if (!searchTerm.isNullOrBlank()) filter["q"] = searchTerm
        val variables = mapOf("filter" to filter)
        val result = graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject(queryName) ?: return Pair(0, emptyList())
        val count = data.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val items = (data.get(listKey) as? JsonArray)?.map { el ->
            parseEntity(el.asJsonObject, kind)
        } ?: emptyList()
        return Pair(count, items)
    }

    @VisibleForTesting
    internal fun parseEntity(obj: JsonObject, kind: EntityItem.Kind): EntityItem = EntityItem(
        id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
        name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
        sceneCount = obj.get("scene_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
        imagePath = obj.get("image_path")?.takeIf { !it.isJsonNull }?.asString,
        kind = kind
    )

    /**
     * Fetches header info for one entity (detail header on the entity scenes
     * screen). Performers include their tag list; tags and studios do not.
     * @throws IOException on transport or GraphQL errors, like every method here.
     */
    suspend fun findEntityHeader(kind: EntityItem.Kind, entityId: String): EntityHeader? {
        val queryName = when (kind) {
            EntityItem.Kind.TAG -> "findTag"
            EntityItem.Kind.PERFORMER -> "findPerformer"
            EntityItem.Kind.STUDIO -> "findStudio"
        }
        val tagsField = if (kind == EntityItem.Kind.PERFORMER) " tags { id name }" else ""
        val query = """
            query(${"$"}id: ID!) {
                $queryName(id: ${"$"}id) {
                    id name scene_count image_path$tagsField
                }
            }
        """.trimIndent()
        val result = graphql(query, mapOf("id" to entityId))
        val obj = result.getAsJsonObject("data")
            ?.get(queryName)?.takeIf { !it.isJsonNull }?.asJsonObject
            ?: return null
        return parseEntityHeader(obj, kind)
    }

    @VisibleForTesting
    internal fun parseEntityHeader(obj: JsonObject, kind: EntityItem.Kind): EntityHeader = EntityHeader(
        id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
        name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
        kind = kind,
        sceneCount = obj.get("scene_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
        imagePath = obj.get("image_path")?.takeIf { !it.isJsonNull }?.asString,
        tags = (obj.get("tags") as? JsonArray)?.let(::parseTagRefs) ?: emptyList()
    )

    /**
     * Lists scene-mode saved filters stored in Stash (created via the Stash
     * web UI). Available since Stash 0.16; safe for the 0.24+ baseline.
     */
    suspend fun findSavedSceneFilters(): List<SavedSceneFilter> {
        val query = """
            query {
                findSavedFilters(mode: SCENES) {
                    id name
                    find_filter { sort direction }
                    object_filter
                }
            }
        """.trimIndent()
        val result = graphql(query)
        val arr = result.getAsJsonObject("data")
            ?.get("findSavedFilters")?.takeIf { !it.isJsonNull }?.asJsonArray
            ?: return emptyList()
        return arr.map { parseSavedSceneFilter(it.asJsonObject) }
    }

    /** Fetches one saved scene filter by id, or null if it does not exist. */
    suspend fun findSavedSceneFilter(id: String): SavedSceneFilter? {
        val query = """
            query(${"$"}id: ID!) {
                findSavedFilter(id: ${"$"}id) {
                    id name
                    find_filter { sort direction }
                    object_filter
                }
            }
        """.trimIndent()
        val result = graphql(query, mapOf("id" to id))
        val obj = result.getAsJsonObject("data")
            ?.get("findSavedFilter")?.takeIf { !it.isJsonNull }?.asJsonObject
            ?: return null
        return parseSavedSceneFilter(obj)
    }

    /** Runs a saved filter's raw SceneFilterType through the normal scene query. */
    suspend fun findScenesForSavedFilter(
        filter: SavedSceneFilter,
        page: Int = 1,
        perPage: Int = 100,
        sort: String = filter.sort ?: "path",
        direction: String = filter.direction ?: "ASC"
    ): Pair<Int, List<Scene>> = findScenesInternal(
        page = page,
        perPage = perPage,
        sort = sort,
        direction = direction,
        sceneFilter = filter.sceneFilter
    )

    @VisibleForTesting
    internal fun parseSavedSceneFilter(obj: JsonObject): SavedSceneFilter {
        val findFilter = obj.get("find_filter")?.takeIf { !it.isJsonNull }?.asJsonObject
        val rawObjectFilter = obj.get("object_filter")?.takeIf { !it.isJsonNull }?.asJsonObject
        return SavedSceneFilter(
            id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
            name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
            sceneFilter = rawObjectFilter?.let { gson.fromJson(it, MapType.savingFilterMap) },
            sort = findFilter?.get("sort")?.takeIf { !it.isJsonNull }?.asString,
            direction = findFilter?.get("direction")?.takeIf { !it.isJsonNull }?.asString
        )
    }

    private object MapType {
        val savingFilterMap: java.lang.reflect.Type =
            object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
    }

    /**
     * Fetch scenes filtered by a tag, performer, or studio entity.
     * performers uses MultiCriterionInput (no depth); tags/studios use HierarchicalMultiCriterionInput (depth supported).
     */
    suspend fun findScenesForEntity(
        kind: EntityItem.Kind, entityId: String,
        page: Int = 1, perPage: Int = 100,
        sort: String = "path", direction: String = "ASC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>> {
        val sceneFilter = when (kind) {
            EntityItem.Kind.TAG ->
                mapOf("tags" to mapOf("value" to listOf(entityId), "modifier" to "INCLUDES", "depth" to 0))
            EntityItem.Kind.PERFORMER ->
                mapOf("performers" to mapOf("value" to listOf(entityId), "modifier" to "INCLUDES"))
            EntityItem.Kind.STUDIO ->
                mapOf("studios" to mapOf("value" to listOf(entityId), "modifier" to "INCLUDES", "depth" to 0))
        }
        return findScenesInternal(
            page = page,
            perPage = perPage,
            sort = sort,
            direction = direction,
            sceneFilter = sceneFilter,
            filters = filters
        )
    }

    /**
     * Fetches the full metadata payload for one scene.
     * @return scene details, or null if the scene does not exist.
     * @throws IOException on transport or GraphQL errors (callers must catch, as with every method here).
     */
    suspend fun findSceneFull(sceneId: String): SceneDetails? {
        val query = """
            query(${"$"}id: ID!) {
                findScene(id: ${"$"}id) {
                    $SCENE_FIELDS_FULL
                    resume_time
                    scene_markers { id title seconds primary_tag { id name } }
                }
            }
        """.trimIndent()
        val result = graphql(query, mapOf("id" to sceneId))
        // Use .get().takeIf{!isJsonNull}.asJsonObject rather than getAsJsonObject() because
        // Gson's getAsJsonObject(String) does a raw checkcast and throws ClassCastException
        // when the value is JsonNull (scene not found). The safe accessor avoids that.
        val obj = result.getAsJsonObject("data")
            ?.get("findScene")?.takeIf { !it.isJsonNull }?.asJsonObject
            ?: return null
        val markers = (obj.get("scene_markers") as? JsonArray)?.map { m ->
            parseMarker(m.asJsonObject)
        } ?: emptyList()
        return SceneDetails(
            scene = parseScene(obj),
            resumeTime = obj.get("resume_time")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
            markers = markers
        )
    }

    @VisibleForTesting
    internal fun parseMarker(mo: JsonObject): MarkerRef = MarkerRef(
        id = mo.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
        title = mo.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
        seconds = mo.get("seconds")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
        primaryTag = (mo.get("primary_tag") as? JsonObject)?.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
    )

    fun getCaptionUrl(sceneId: String, caption: CaptionRef): String =
        "$serverUrl/scene/$sceneId/caption?" +
            "lang=${URLEncoder.encode(caption.languageCode, "UTF-8")}" +
            "&type=${URLEncoder.encode(caption.captionType, "UTF-8")}"

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
