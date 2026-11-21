package com.sinema.api

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sinema.model.CaptionRef
import com.sinema.model.EntityHeader
import com.sinema.model.EntityItem
import com.sinema.model.GroupDetails
import com.sinema.model.GroupItem
import com.sinema.model.GalleryItem
import com.sinema.model.SavedSceneFilter
import com.sinema.model.SceneMarkerItem
import com.sinema.model.StatsSummary
import com.sinema.model.ImageItem
import com.sinema.model.MarkerRef
import com.sinema.model.PerformerRef
import com.sinema.model.Scene
import com.sinema.model.SceneDetails
import com.sinema.model.StudioRef
import com.sinema.model.TagRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Decorations for one folder card: subtree counts, a thumbnail id, and a favorite flag. */
data class FolderCardSummary(
    val sceneCount: Int,
    val imageCount: Int,
    val firstSceneId: String?,
    val firstImageId: String?,
    val hasFavorites: Boolean
)

class SinemaApi(
    internal val serverUrl: String,
    private val apiKey: String,
    @Volatile private var sessionCookie: String = "",
    private val authMode: String = "apikey",
    allowSelfSigned: Boolean = false
) {
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private val library = LibraryQueries(this)

    companion object {
        // Single source of truth for scene list payloads.
        // resume_time ships on Scene since Stash 0.24 and powers the
        // watch-progress bar on cards (see Scene.watchProgressPercent).
        // o_counter powers the card "O×n" badge (same availability).
        internal const val SCENE_FIELDS =
            "id title play_count rating100 resume_time o_counter files { path size duration width height }"

        // Full scene payload — used by findSceneFull for detail/player screens.
        // (o_counter already ships in SCENE_FIELDS for the card badge.)
        internal const val SCENE_FIELDS_FULL = SCENE_FIELDS +
            " date" +
            " details" +
            " studio { id name }" +
            " tags { id name }" +
            " performers { id name }" +
            " captions { language_code caption_type }"

        internal const val GROUP_FIELDS =
            "id name scene_count front_image_path back_image_path date director synopsis studio { name }"

        internal const val GALLERY_FIELDS =
            "id title image_count date details photographer rating100 studio { name } cover { id }"

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
    private val trustAllCerts: Boolean = allowSelfSigned

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
    internal val gson = Gson()
    internal val gsonWithNulls = com.google.gson.GsonBuilder().serializeNulls().create()

    var stashUsername: String = ""
    var stashPassword: String = ""
    var onSessionRefreshed: ((String) -> Unit)? = null

    /**
     * Direct image endpoint per entity kind. Used as a fallback when list
     * queries no longer return image_path (newer Stash schemas).
     */
    fun entityImageUrl(kind: EntityItem.Kind, entityId: String): String = when (kind) {
        EntityItem.Kind.PERFORMER -> "$serverUrl/performer/$entityId/image"
        EntityItem.Kind.STUDIO -> "$serverUrl/studio/$entityId/image"
        EntityItem.Kind.TAG -> "$serverUrl/tag/$entityId/image"
    }

    fun getScreenshotUrl(sceneId: String): String = "$serverUrl/scene/$sceneId/screenshot"

    /** Scene frame at a timestamp — used as the marker card thumbnail. */
    fun getMarkerScreenshotUrl(sceneId: String, seconds: Double): String =
        "$serverUrl/scene/$sceneId/screenshot?t=${seconds.toInt()}"

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
    internal fun pathPrefixRegex(path: String): String = "^${Regex.escape(path.trimEnd('/'))}/"

    /** Shared FindFilterType map builder. */
    internal fun findFilter(
        page: Int? = null,
        perPage: Int? = null,
        sort: String? = null,
        direction: String? = null,
        query: String? = null
    ): Map<String, Any?> = buildMap {
        page?.let { put("page", it) }
        perPage?.let { put("per_page", it) }
        sort?.let { put("sort", it) }
        direction?.let { put("direction", it) }
        query?.takeIf { !it.isNullOrBlank() }?.let { put("q", it) }
    }

    /** Shared paged-result extraction: count + parsed list under one key. */
    internal fun <T> parsePaged(
        result: JsonObject,
        queryName: String,
        listKey: String,
        parse: (JsonObject) -> T
    ): Pair<Int, List<T>> {
        val data = result.getAsJsonObject("data")?.optObject(queryName)
            ?: return Pair(0, emptyList())
        val count = data.optInt("count")
        val items = data.optArray(listKey)?.map { parse(it.asJsonObject) } ?: emptyList()
        return Pair(count, items)
    }

    internal suspend fun findScenesInternal(
        page: Int = 1,
        perPage: Int = 100,
        sort: String = "path",
        direction: String = "ASC",
        searchTerm: String? = null,
        sceneFilter: Map<String, Any?>? = null,
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        fields: String = SCENE_FIELDS,
        // Alpha jump: anchored title/path regex ("^B"); callers pass [A-Z0-9] only.
        pathPrefixRegex: String? = null,
        alphaField: String = "path"
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
        var combinedSceneFilter = combineSceneFilters(sceneFilter.orEmpty(), filters.toStashFilter())
        pathPrefixRegex?.takeIf { it.isNotBlank() }?.let {
            combinedSceneFilter = combineSceneFilters(
                combinedSceneFilter,
                mapOf(alphaField to mapOf("value" to it, "modifier" to "MATCHES_REGEX"))
            )
        }
        val result = graphql(query, mapOf("filter" to filter, "scene_filter" to combinedSceneFilter))
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes")
            ?: return Pair(0, emptyList())
        val count = data.get("count").asInt
        val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
        return Pair(count, scenes)
    }

    internal suspend fun graphql(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        serializeNulls: Boolean = false
    ): JsonObject {
        return withContext(Dispatchers.IO) {
            val requestGson = if (serializeNulls) gsonWithNulls else gson
            val body = requestGson.toJson(mapOf("query" to query, "variables" to variables))

            val failedCookie = sessionCookie
            val first = executeGraphql(body, failedCookie)

            if (authMode == "session" && first.code == 401) {
                val refreshed = refreshMutex.withLock {
                    val current = sessionCookie
                    if (current.isNotBlank() && current != failedCookie) {
                        current
                    } else {
                        reLogin()?.also { cookie ->
                            sessionCookie = cookie
                            onSessionRefreshed?.invoke(cookie)
                        }
                    }
                }
                if (!refreshed.isNullOrBlank()) {
                    val retry = executeGraphql(body, refreshed)
                    return@withContext parseGraphqlResponse(retry.code, retry.body)
                }
            }

            parseGraphqlResponse(first.code, first.body)
        }
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun executeGraphql(body: String, cookie: String): HttpResult {
        val requestBuilder = Request.Builder()
            .url("$serverUrl/graphql")
            .addHeader("Content-Type", "application/json")

        if (authMode == "session" && cookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
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
        val obj = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            if (code !in 200..299) throw IOException("Server error $code")
            throw e
        }
        if (obj.has("errors") && obj.getAsJsonArray("errors").size() > 0) {
            val first = obj.getAsJsonArray("errors")[0].asJsonObject
            val msg = first.get("message")?.asString ?: "GraphQL error"
            throw IOException(msg)
        }
        if (code !in 200..299) throw IOException("Server error $code")
        return obj
    }

    private fun reLogin(): String? {
        if (stashUsername.isBlank() || stashPassword.isBlank()) return null
        return (StashAuth.login(client, serverUrl, stashUsername, stashPassword) as? LoginResult.Success)?.cookie
    }


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

    suspend fun findImagesByPath(pathPrefix: String, page: Int = 1, perPage: Int = 100): Pair<Int, List<ImageItem>> =
        findImagesInternal(
            page = page,
            perPage = perPage,
            imageFilter = mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))
        )

    internal suspend fun findImagesInternal(
        page: Int,
        perPage: Int,
        imageFilter: Map<String, Any?>
    ): Pair<Int, List<ImageItem>> {
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
            "filter" to findFilter(page, perPage, "path", "ASC"),
            "image_filter" to imageFilter
        )
        val result = graphql(query, variables)
        return parsePaged(result, "findImages", "images", ::parseImage)
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

    suspend fun findImagesInFolderDirect(folderPath: String, page: Int = 1, perPage: Int = 500): Pair<Int, List<ImageItem>> =
        findImagesInternal(
            page = page,
            perPage = perPage,
            imageFilter = mapOf(
                "path" to mapOf(
                    "value" to "^${Regex.escape(folderPath.trimEnd('/'))}/[^/]+$",
                    "modifier" to "MATCHES_REGEX"
                )
            )
        )

    private fun parseImage(obj: JsonObject): ImageItem {
        val files = obj.getAsJsonArray("visual_files")
        val file = if (files != null && files.size() > 0) files[0].asJsonObject else null
        return ImageItem(
            id = obj.get("id").asString,
            title = obj.optString("title", ""),
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

    suspend fun destroyScene(sceneId: String) {
        val query = """
            mutation(${"$"}input: SceneDestroyInput!) {
                sceneDestroy(input: ${"$"}input)
            }
        """.trimIndent()
        val result = graphql(
            query,
            mapOf("input" to mapOf("id" to sceneId, "delete_file" to true))
        )
        if (result.optObject("data")?.optBoolean("sceneDestroy") != true) {
            throw java.io.IOException("Stash could not delete the scene")
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

    suspend fun findFavoriteScenesPaged(
        page: Int,
        perPage: Int,
        sort: String,
        direction: String,
        unwatchedOnly: Boolean = false,
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        pathPrefixRegex: String? = null,
        alphaField: String = "path"
    ): Pair<Int, List<Scene>> = findScenesInternal(
        page = page,
        perPage = perPage,
        sort = sort,
        direction = direction,
        sceneFilter = buildMap {
            put("rating100", mapOf("value" to 1, "modifier" to "GREATER_THAN"))
            if (unwatchedOnly) put("play_count", mapOf("value" to 0, "modifier" to "EQUALS"))
        },
        filters = filters,
        pathPrefixRegex = pathPrefixRegex,
        alphaField = alphaField
    )

    suspend fun findRecentScenesPaged(
        page: Int,
        perPage: Int,
        sort: String = "created_at",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        pathPrefixRegex: String? = null,
        alphaField: String = "path"
    ): Pair<Int, List<Scene>> = findScenesInternal(
        page = page,
        perPage = perPage,
        sort = sort,
        direction = direction,
        filters = filters,
        pathPrefixRegex = pathPrefixRegex,
        alphaField = alphaField
    )

    suspend fun findRecentlyReleasedPaged(
        page: Int,
        perPage: Int,
        sort: String = "date",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        pathPrefixRegex: String? = null,
        alphaField: String = "path"
    ): Pair<Int, List<Scene>> =
        findRecentScenesPaged(page, perPage, sort, direction, filters, pathPrefixRegex, alphaField)

    /**
     * One random scene from the whole library, for the "Surprise Me" card.
     * @return a scene, or null when the library is empty.
     */
    suspend fun findRandomScene(): Scene? =
        findScenesInternal(perPage = 1, sort = "random").second.firstOrNull()

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

    suspend fun setSceneWatched(sceneId: String, watched: Boolean) {
        if (watched) {
            clearResumeTime(sceneId)
            incrementPlayCount(sceneId)
        } else {
            resetPlayCount(sceneId)
        }
    }

    /** Increment the Stash O counter for a scene (playback menu / detail). */
    suspend fun sceneIncrementO(sceneId: String) {        val query = """
            mutation(${"$"}id: ID!) {
                sceneIncrementO(id: ${"$"}id)
            }
        """.trimIndent()
        graphql(query, mapOf("id" to sceneId))
    }

    /** Replace a scene's tag set (orphan triage on the Untagged grid). */
    suspend fun sceneUpdateTags(sceneId: String, tagIds: List<String>) {
        val query = """
            mutation(${"$"}input: SceneUpdateInput!) {
                sceneUpdate(input: ${"$"}input) { id }
            }
        """.trimIndent()
        graphql(query, mapOf("input" to mapOf("id" to sceneId, "tag_ids" to tagIds)))
    }

    /**
     * Scenes with no tags AND no performers — the curation backlog. Uses
     * IS_NULL criteria on hierarchical/multi criteria (Stash ≥ 0.26); older
     * servers reject the filter and callers surface their own error.
     */
    suspend fun findUntaggedScenes(
        page: Int,
        perPage: Int,
        sort: String = "created_at",
        direction: String = "DESC"
    ): Pair<Int, List<Scene>> =
        findScenesInternal(
            page = page,
            perPage = perPage,
            sort = sort,
            direction = direction,
            sceneFilter = mapOf(
                "tags" to mapOf("value" to emptyList<String>(), "modifier" to "IS_NULL"),
                "performers" to mapOf("value" to emptyList<String>(), "modifier" to "IS_NULL")
            )
        )

    suspend fun findRecentlyPlayed(perPage: Int = 25): List<Scene> =
        findRecentlyPlayedPaged(page = 1, perPage = perPage).second

    suspend fun findContinuePlaying(): List<Pair<Scene, Double>> =
        findContinuePlayingPaged(page = 1, perPage = 25).second.map { scene -> scene to scene.resumeTime }

    suspend fun findContinuePlayingPaged(
        page: Int,
        perPage: Int,
        sort: String = "last_played_at",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        pathPrefixRegex: String? = null,
        alphaField: String = "path"
    ): Pair<Int, List<Scene>> {
        if (sort != "last_played_at") {
            return findScenesInternal(
                page = page,
                perPage = perPage,
                sort = sort,
                direction = direction,
                sceneFilter = mapOf("resume_time" to mapOf("value" to 0, "modifier" to "GREATER_THAN")),
                filters = filters,
                pathPrefixRegex = pathPrefixRegex,
                alphaField = alphaField
            )
        }
        return findRecencyPage(
            page, perPage, direction,
            sceneFilter = mapOf("resume_time" to mapOf("value" to 0, "modifier" to "GREATER_THAN")),
            filters = filters,
            pathPrefixRegex = pathPrefixRegex,
            alphaField = alphaField
        )
    }

    suspend fun findRecentlyPlayedPaged(
        page: Int,
        perPage: Int,
        sort: String = "last_played_at",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions(),
        pathPrefixRegex: String? = null,
        alphaField: String = "path"
    ): Pair<Int, List<Scene>> {
        if (sort != "last_played_at") {
            return findScenesInternal(
                page = page,
                perPage = perPage,
                sort = sort,
                direction = direction,
                sceneFilter = RECENTLY_PLAYED_SCENE_FILTER,
                filters = filters,
                pathPrefixRegex = pathPrefixRegex,
                alphaField = alphaField
            )
        }
        return findRecencyPage(
            page, perPage, direction,
            sceneFilter = RECENTLY_PLAYED_SCENE_FILTER,
            filters = filters,
            pathPrefixRegex = pathPrefixRegex,
            alphaField = alphaField
        )
    }

    /**
     * Dated scenes stay in server order relative to each other; only null
     * timestamps wander. A DESC page can stop once it has seen enough dated
     * rows. ASC still reads through to the end because nulls sort first.
     */
    private suspend fun findRecencyPage(
        page: Int,
        perPage: Int,
        direction: String,
        sceneFilter: Map<String, Any?>,
        filters: com.sinema.model.SceneFilterOptions,
        pathPrefixRegex: String?,
        alphaField: String
    ): Pair<Int, List<Scene>> {
        val target = recencyDatedTarget(page, perPage, direction)
        val collected = mutableListOf<Scene>()
        var serverCount = 0
        var nextPage = 1
        while (nextPage <= 500) {
            val (count, batch) = findScenesInternal(
                page = nextPage,
                perPage = RECENCY_PAGE_CHUNK,
                sort = "last_played_at",
                direction = direction,
                sceneFilter = sceneFilter,
                filters = filters,
                fields = SCENE_FIELDS + " last_played_at",
                pathPrefixRegex = pathPrefixRegex,
                alphaField = alphaField
            )
            serverCount = count
            collected.addAll(batch)
            if (recencyScanComplete(
                    datedCount = collected.count { it.lastPlayedAt != null },
                    datedTarget = target,
                    batchSize = batch.size,
                    chunk = RECENCY_PAGE_CHUNK,
                    collected = collected.size,
                    serverCount = count
                )
            ) break
            nextPage++
        }
        return serverCount to recentlyPlayedPage(collected, page, perPage, direction)
    }

    /**
     * Counts, thumbnail source, and favorite flag for one folder card.
     * Three queries run together: scene count plus first id, image count
     * plus first id, and whether any rated scene is under the path.
     */
    suspend fun summarizeFolder(pathPrefix: String): FolderCardSummary = kotlinx.coroutines.coroutineScope {
        val scenes = async {
            findScenesByPath(pathPrefix, page = 1, perPage = 1)
        }
        val images = async {
            findImagesByPath(pathPrefix, page = 1, perPage = 1)
        }
        val favorites = async { hasFavoritesInPath(pathPrefix) }
        val (sceneCount, sceneRows) = scenes.await()
        val (imageCount, imageRows) = images.await()
        FolderCardSummary(
            sceneCount = sceneCount,
            imageCount = imageCount,
            firstSceneId = sceneRows.firstOrNull()?.id,
            firstImageId = imageRows.firstOrNull()?.id,
            hasFavorites = favorites.await()
        )
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

    internal fun parseTagRefs(arr: JsonArray): List<TagRef> = arr.map { el ->
        val t = el.asJsonObject
        TagRef(
            id = t.optString("id", ""),
            name = t.optString("name", "")
        )
    }

    private fun parsePerformerRefs(arr: JsonArray): List<PerformerRef> = arr.map { el ->
        val p = el.asJsonObject
        PerformerRef(
            id = p.optString("id", ""),
            name = p.optString("name", "")
        )
    }

    private fun parseCaptionRefs(arr: JsonArray): List<CaptionRef> = arr.map { el ->
        val c = el.asJsonObject
        CaptionRef(
            languageCode = c.optString("language_code", ""),
            captionType = c.optString("caption_type", "")
        )
    }

    @VisibleForTesting
    internal fun parseScene(obj: JsonObject): Scene {
        val files = obj.getAsJsonArray("files")
        val file = if (files != null && files.size() > 0) files[0].asJsonObject else null
        // Optional metadata — only present in SCENE_FIELDS_FULL queries; guarded to keep lean list queries working.
        val studioObj = obj.optObject("studio")
        return Scene(
            id = obj.get("id").asString,
            title = obj.optString("title", ""),
            path = file?.get("path")?.asString ?: "",
            size = file?.get("size")?.asLong ?: 0L,
            duration = file?.get("duration")?.asDouble ?: 0.0,
            width = file?.get("width")?.asInt ?: 0,
            height = file?.get("height")?.asInt ?: 0,
            playCount = obj.optInt("play_count", 0),
            rating100 = obj.optNullableInt("rating100"),
            resumeTime = obj.optDouble("resume_time", 0.0),
            date = obj.optNullableString("date"),
            details = obj.optNullableString("details")?.trim(),
            oCounter = obj.optInt("o_counter", 0),
            studio = studioObj?.let { s ->
                StudioRef(
                    id = s.optString("id", ""),
                    name = s.optString("name", "")
                )
            },
            tags = (obj.get("tags") as? JsonArray)?.let(::parseTagRefs) ?: emptyList(),
            performers = (obj.get("performers") as? JsonArray)?.let(::parsePerformerRefs) ?: emptyList(),
            captions = (obj.get("captions") as? JsonArray)?.let(::parseCaptionRefs) ?: emptyList(),
            lastPlayedAt = obj.optNullableString("last_played_at"),
            galleries = (obj.get("galleries") as? JsonArray)?.mapNotNull { g ->
                val go = g.asJsonObject
                val gid = go.optString("id", return@mapNotNull null)
                com.sinema.model.GalleryRef(gid, go.optString("title", ""))
            } ?: emptyList()
        )
    }

    suspend fun findEntities(
        kind: EntityItem.Kind,
        page: Int = 1,
        perPage: Int = 100,
        searchTerm: String? = null,
        sort: String = "scenes_count",
        direction: String = "DESC"
    ): Pair<Int, List<EntityItem>>  = library.findEntities(kind, page, perPage, searchTerm, sort, direction)

    suspend fun findAllTags(perPage: Int = 100, maxPages: Int = 20): List<EntityItem>  = library.findAllTags(perPage, maxPages)

    @VisibleForTesting
    internal fun parseEntity(obj: JsonObject, kind: EntityItem.Kind): EntityItem  = library.parseEntity(obj, kind)

    suspend fun entitySetRating(kind: EntityItem.Kind, entityId: String, rating100: Int?)  = library.entitySetRating(kind, entityId, rating100)

    suspend fun findEntityHeader(kind: EntityItem.Kind, entityId: String): EntityHeader?  = library.findEntityHeader(kind, entityId)

    @VisibleForTesting
    internal fun parseEntityHeader(obj: JsonObject, kind: EntityItem.Kind): EntityHeader  = library.parseEntityHeader(obj, kind)

    suspend fun findGroups(
        page: Int = 1,
        perPage: Int = 100,
        searchTerm: String? = null,
        sort: String = "updated_at",
        direction: String = "DESC"
    ): Pair<Int, List<GroupItem>>  = library.findGroups(page, perPage, searchTerm, sort, direction)

    suspend fun findGroupDetails(groupId: String): GroupDetails?  = library.findGroupDetails(groupId)

    @VisibleForTesting
    internal fun parseGroup(obj: JsonObject): GroupItem  = library.parseGroup(obj)

    suspend fun findGalleries(
        page: Int = 1,
        perPage: Int = 100,
        searchTerm: String? = null,
        sort: String = "updated_at",
        direction: String = "DESC"
    ): Pair<Int, List<GalleryItem>>  = library.findGalleries(page, perPage, searchTerm, sort, direction)

    suspend fun findGallery(galleryId: String): GalleryItem?  = library.findGallery(galleryId)

    suspend fun findImagesForGallery(
        galleryId: String,
        page: Int = 1,
        perPage: Int = 500
    ): Pair<Int, List<ImageItem>>  = library.findImagesForGallery(galleryId, page, perPage)

    @VisibleForTesting
    internal fun parseGallery(obj: JsonObject): GalleryItem  = library.parseGallery(obj)

    suspend fun findSavedSceneFilters(): List<SavedSceneFilter>  = library.findSavedSceneFilters()

    suspend fun findSavedSceneFilter(id: String): SavedSceneFilter?  = library.findSavedSceneFilter(id)

    suspend fun findScenesForSavedFilter(
        filter: SavedSceneFilter,
        page: Int = 1,
        perPage: Int = 100,
        sort: String = filter.sort ?: "path",
        direction: String = filter.direction ?: "ASC"
    ): Pair<Int, List<Scene>>  = library.findScenesForSavedFilter(filter, page, perPage, sort, direction)

    suspend fun saveSceneFilter(
        name: String,
        options: com.sinema.model.SceneFilterOptions,
        sort: String,
        direction: String,
        id: String? = null
    ): SavedSceneFilter  = library.saveSceneFilter(name, options, sort, direction, id)

    suspend fun renameSavedSceneFilter(filter: SavedSceneFilter, name: String): SavedSceneFilter  = library.renameSavedSceneFilter(filter, name)

    suspend fun destroySavedSceneFilter(id: String)  = library.destroySavedSceneFilter(id)

    suspend fun findSceneMarkers(
        page: Int = 1,
        perPage: Int = 100,
        sort: String = "created_at",
        direction: String = "DESC",
        tagId: String? = null,
        searchQuery: String? = null
    ): Pair<Int, List<SceneMarkerItem>>  = library.findSceneMarkers(page, perPage, sort, direction, tagId, searchQuery)

    @VisibleForTesting
    internal fun parseSceneMarker(obj: JsonObject): SceneMarkerItem  = library.parseSceneMarker(obj)

    suspend fun getStats(): StatsSummary  = library.getStats()

    suspend fun findTopPlayedScenes(perPage: Int = 200): List<Scene>  = library.findTopPlayedScenes(perPage)

    suspend fun findPartiallyWatched(
        page: Int,
        perPage: Int,
        sort: String = "resume_time",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>>  = library.findPartiallyWatched(page, perPage, sort, direction, filters)

    suspend fun findMixCandidates(excludedIds: Set<String> = emptySet()): List<Scene>  = library.findMixCandidates(excludedIds)

    suspend fun getServerVersion(): String  = library.getServerVersion()

    suspend fun getJobQueue(): List<String>?  = library.getJobQueue()

    @VisibleForTesting
    internal fun parseSavedSceneFilter(obj: JsonObject): SavedSceneFilter  = library.parseSavedSceneFilter(obj)

    suspend fun findScenesForEntity(
        kind: EntityItem.Kind, entityId: String,
        page: Int = 1, perPage: Int = 100,
        sort: String = "path", direction: String = "ASC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>>  = library.findScenesForEntity(kind, entityId, page, perPage, sort, direction, filters)

    suspend fun findRelatedScenes(source: Scene, perPage: Int = 40): List<Scene>  = library.findRelatedScenes(source, perPage)

    suspend fun findUnwatchedForFavoritePerformersPaged(
        page: Int,
        perPage: Int,
        sort: String = "updated_at",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>>  = library.findUnwatchedForFavoritePerformersPaged(page, perPage, sort, direction, filters)

    suspend fun findSceneFull(sceneId: String): SceneDetails?  = library.findSceneFull(sceneId)

    @VisibleForTesting
    internal fun parseMarker(mo: JsonObject): MarkerRef  = library.parseMarker(mo)

    fun getCaptionUrl(sceneId: String, caption: CaptionRef): String  = library.getCaptionUrl(sceneId, caption)

    suspend fun resetPlayCount(sceneId: String)  = library.resetPlayCount(sceneId)

}
