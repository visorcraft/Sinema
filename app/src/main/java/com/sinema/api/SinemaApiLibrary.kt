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


/** Tags, performers, studios, groups, galleries, markers, stats, and saved filters. */
internal class LibraryQueries(private val api: SinemaApi) {
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
    ): Pair<Int, List<EntityItem>> = try {
        findEntitiesInternal(kind, page, perPage, searchTerm, sort, direction, includeRatings = true)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        // Older Stash schemas reject entity rating fields entirely; retry
        // without them so browsing keeps working (favorites stay null).
        // A genuinely failing server just fails again on the retry.
        findEntitiesInternal(kind, page, perPage, searchTerm, sort, direction, includeRatings = false)
    }

    suspend fun findAllTags(perPage: Int = 100, maxPages: Int = 20): List<EntityItem> {
        val all = mutableListOf<EntityItem>()
        var page = 1
        while (page <= maxPages) {
            val (count, items) = findEntities(EntityItem.Kind.TAG, page = page, perPage = perPage)
            all += items
            if (items.isEmpty() || all.size >= count) break
            page++
        }
        return all
    }

    private suspend fun findEntitiesInternal(
        kind: EntityItem.Kind,
        page: Int,
        perPage: Int,
        searchTerm: String?,
        sort: String,
        direction: String,
        includeRatings: Boolean
    ): Pair<Int, List<EntityItem>> {
        val (queryName, listKey) = when (kind) {
            EntityItem.Kind.TAG -> "findTags" to "tags"
            EntityItem.Kind.PERFORMER -> "findPerformers" to "performers"
            EntityItem.Kind.STUDIO -> "findStudios" to "studios"
        }
        val ratingField = if (includeRatings) " rating100" else ""
        val query = """
            query(${"$"}filter: FindFilterType) {
                $queryName(filter: ${"$"}filter) {
                    count
                    $listKey { id name scene_count image_path$ratingField }
                }
            }
        """.trimIndent()
        val result = api.graphql(
            query,
            mapOf("filter" to api.findFilter(page, perPage, sort, direction, searchTerm))
        )
        return api.parsePaged(result, queryName, listKey) { parseEntity(it, kind) }
    }

    @VisibleForTesting
    internal fun parseEntity(obj: JsonObject, kind: EntityItem.Kind): EntityItem = EntityItem(
        id = obj.optString("id", ""),
        name = obj.optString("name", ""),
        sceneCount = obj.optInt("scene_count", 0),
        imagePath = obj.optNullableString("image_path"),
        kind = kind,
        rating100 = obj.optNullableInt("rating100")
    )

    /**
     * Set or clear an entity's rating (favorite = rating > 0). Requires a
     * Stash whose entity update inputs accept rating100; older servers get
     * the error surfaced by the caller.
     */
    suspend fun entitySetRating(kind: EntityItem.Kind, entityId: String, rating100: Int?) {
        val mutation = when (kind) {
            EntityItem.Kind.TAG -> "tagUpdate"
            EntityItem.Kind.PERFORMER -> "performerUpdate"
            EntityItem.Kind.STUDIO -> "studioUpdate"
        }
        val inputType = when (kind) {
            EntityItem.Kind.TAG -> "TagUpdateInput!"
            EntityItem.Kind.PERFORMER -> "PerformerUpdateInput!"
            EntityItem.Kind.STUDIO -> "StudioUpdateInput!"
        }
        val query = """
            mutation(${"$"}input: $inputType) {
                $mutation(input: ${"$"}input) { id }
            }
        """.trimIndent()
        api.graphql(query, mapOf("input" to mapOf("id" to entityId, "rating100" to rating100)), serializeNulls = true)
    }

    /**
     * Fetches header info for one entity (detail header on the entity scenes
     * screen). Performers include their tag list; tags and studios do not.
     * Ratings are best-effort: older schemas that reject entity rating fields
     * get one retry without them (favorite state stays null).
     * @throws IOException on transport or GraphQL errors, like every method here.
     */
    suspend fun findEntityHeader(kind: EntityItem.Kind, entityId: String): EntityHeader? =
        try {
            findEntityHeaderInternal(kind, entityId, includeRating = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            findEntityHeaderInternal(kind, entityId, includeRating = false)
        }

    private suspend fun findEntityHeaderInternal(
        kind: EntityItem.Kind,
        entityId: String,
        includeRating: Boolean
    ): EntityHeader? {
        val queryName = when (kind) {
            EntityItem.Kind.TAG -> "findTag"
            EntityItem.Kind.PERFORMER -> "findPerformer"
            EntityItem.Kind.STUDIO -> "findStudio"
        }
        val tagsField = if (kind == EntityItem.Kind.PERFORMER) " tags { id name }" else ""
        val ratingField = if (includeRating) " rating100" else ""
        val query = """
            query(${"$"}id: ID!) {
                $queryName(id: ${"$"}id) {
                    id name scene_count image_path$tagsField$ratingField
                }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("id" to entityId))
        val obj = result.getAsJsonObject("data")
            ?.get(queryName)?.takeIf { !it.isJsonNull }?.asJsonObject
            ?: return null
        return parseEntityHeader(obj, kind)
    }

    @VisibleForTesting
    internal fun parseEntityHeader(obj: JsonObject, kind: EntityItem.Kind): EntityHeader = EntityHeader(
        id = obj.optString("id", ""),
        name = obj.optString("name", ""),
        kind = kind,
        sceneCount = obj.optInt("scene_count", 0),
        imagePath = obj.optNullableString("image_path"),
        tags = (obj.get("tags") as? JsonArray)?.let(api::parseTagRefs) ?: emptyList(),
        rating100 = obj.optNullableInt("rating100")
    )

    suspend fun findGroups(
        page: Int = 1,
        perPage: Int = 100,
        searchTerm: String? = null,
        sort: String = "updated_at",
        direction: String = "DESC"
    ): Pair<Int, List<GroupItem>> {
        val filter = mutableMapOf<String, Any?>(
            "page" to page,
            "per_page" to perPage,
            "sort" to sort,
            "direction" to direction
        )
        if (!searchTerm.isNullOrBlank()) filter["q"] = searchTerm
        return try {
            findGroupsUsing("findGroups", "groups", filter)
        } catch (e: IOException) {
            if (!isMissingGroupsApi(e)) throw e
            findGroupsUsing("findMovies", "movies", filter)
        }
    }

    private suspend fun findGroupsUsing(
        queryName: String,
        listKey: String,
        filter: Map<String, Any?>
    ): Pair<Int, List<GroupItem>> {
        val query = """
            query(${"$"}filter: FindFilterType) {
                $queryName(filter: ${"$"}filter) {
                    count
                    $listKey { ${SinemaApi.GROUP_FIELDS} }
                }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("filter" to filter))
        val data = result.getAsJsonObject("data")?.get(queryName)
            ?.takeIf { !it.isJsonNull }?.asJsonObject ?: return Pair(0, emptyList())
        val count = data.optInt("count", 0)
        val groups = (data.get(listKey) as? JsonArray)?.map { parseGroup(it.asJsonObject) } ?: emptyList()
        return Pair(count, groups)
    }

    suspend fun findGroupDetails(groupId: String): GroupDetails? = try {
        findGroupDetailsUsing("findGroup", groupId)
    } catch (e: IOException) {
        if (!isMissingGroupsApi(e)) throw e
        findGroupDetailsUsing("findMovie", groupId)
    }

    private suspend fun findGroupDetailsUsing(queryName: String, groupId: String): GroupDetails? {
        val query = """
            query(${"$"}id: ID!) {
                $queryName(id: ${"$"}id) {
                    ${SinemaApi.GROUP_FIELDS}
                    scenes { ${SinemaApi.SCENE_FIELDS} }
                }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("id" to groupId))
        val obj = result.getAsJsonObject("data")?.get(queryName)
            ?.takeIf { !it.isJsonNull }?.asJsonObject ?: return null
        val scenes = (obj.get("scenes") as? JsonArray)?.map { api.parseScene(it.asJsonObject) } ?: emptyList()
        return GroupDetails(parseGroup(obj), scenes)
    }

    private fun isMissingGroupsApi(error: IOException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("Cannot query field", ignoreCase = true) &&
            message.contains("findGroup", ignoreCase = true)
    }

    @VisibleForTesting
    internal fun parseGroup(obj: JsonObject): GroupItem = GroupItem(
        id = obj.optString("id", ""),
        name = obj.optString("name", ""),
        sceneCount = obj.optInt("scene_count", 0),
        frontImagePath = obj.optNullableString("front_image_path"),
        backImagePath = obj.optNullableString("back_image_path"),
        date = obj.optNullableString("date"),
        director = obj.optNullableString("director"),
        synopsis = obj.optNullableString("synopsis")?.trim(),
        studioName = obj.optObject("studio")
            ?.get("name")?.takeIf { !it.isJsonNull }?.asString
    )

    suspend fun findGalleries(
        page: Int = 1,
        perPage: Int = 100,
        searchTerm: String? = null,
        sort: String = "updated_at",
        direction: String = "DESC"
    ): Pair<Int, List<GalleryItem>> {
        val query = """
            query(${"$"}filter: FindFilterType) {
                findGalleries(filter: ${"$"}filter) {
                    count
                    galleries { ${SinemaApi.GALLERY_FIELDS} }
                }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("filter" to api.findFilter(page, perPage, sort, direction, searchTerm)))
        return api.parsePaged(result, "findGalleries", "galleries") { parseGallery(it) }
    }

    suspend fun findGallery(galleryId: String): GalleryItem? {
        val query = """
            query(${"$"}id: ID!) {
                findGallery(id: ${"$"}id) { ${SinemaApi.GALLERY_FIELDS} }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("id" to galleryId))
        val obj = result.getAsJsonObject("data")?.get("findGallery")
            ?.takeIf { !it.isJsonNull }?.asJsonObject ?: return null
        return parseGallery(obj)
    }

    suspend fun findImagesForGallery(
        galleryId: String,
        page: Int = 1,
        perPage: Int = 500
    ): Pair<Int, List<ImageItem>> = api.findImagesInternal(
        page = page,
        perPage = perPage,
        imageFilter = mapOf(
            "galleries" to mapOf("value" to listOf(galleryId), "modifier" to "INCLUDES")
        )
    )

    @VisibleForTesting
    internal fun parseGallery(obj: JsonObject): GalleryItem = GalleryItem(
        id = obj.optString("id", ""),
        title = obj.optString("title", ""),
        imageCount = obj.optInt("image_count", 0),
        coverImageId = obj.optObject("cover")
            ?.get("id")?.takeIf { !it.isJsonNull }?.asString,
        date = obj.optNullableString("date"),
        details = obj.optNullableString("details")?.trim(),
        photographer = obj.optNullableString("photographer"),
        studioName = obj.optObject("studio")
            ?.get("name")?.takeIf { !it.isJsonNull }?.asString,
        rating100 = obj.optNullableInt("rating100")
    )

    /**
     * Lists scene-mode saved filters stored in Stash. Available since Stash
     * 0.16; safe for the 0.24+ baseline.
     */
    suspend fun findSavedSceneFilters(): List<SavedSceneFilter> {
        val query = """
            query {
                findSavedFilters(mode: SCENES) {
                    id name
                    find_filter { sort direction }
                    object_filter
                    ui_options
                }
            }
        """.trimIndent()
        val result = api.graphql(query)
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
                    ui_options
                }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("id" to id))
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
    ): Pair<Int, List<Scene>> = api.findScenesInternal(
        page = page,
        perPage = perPage,
        sort = sort,
        direction = direction,
        sceneFilter = filter.sceneFilter
    )

    suspend fun saveSceneFilter(
        name: String,
        options: com.sinema.model.SceneFilterOptions,
        sort: String,
        direction: String,
        id: String? = null
    ): SavedSceneFilter = saveSceneFilterInput(id, name, options.toStashFilter(), sort, direction)

    suspend fun renameSavedSceneFilter(filter: SavedSceneFilter, name: String): SavedSceneFilter =
        saveSceneFilterInput(
            filter.id,
            name,
            filter.sceneFilter.orEmpty(),
            filter.sort,
            filter.direction,
            filter.uiOptions
        )

    private suspend fun saveSceneFilterInput(
        id: String?,
        name: String,
        objectFilter: Map<String, Any?>,
        sort: String?,
        direction: String?,
        uiOptions: Map<String, Any?>? = null
    ): SavedSceneFilter {
        val query = """
            mutation(${"$"}input: SaveFilterInput!) {
                saveFilter(input: ${"$"}input) {
                    id name
                    find_filter { sort direction }
                    object_filter
                    ui_options
                }
            }
        """.trimIndent()
        val input = mutableMapOf<String, Any?>(
            "mode" to "SCENES",
            "name" to name,
            "object_filter" to objectFilter
        )
        if (sort != null || direction != null) {
            input["find_filter"] = mapOf("sort" to sort, "direction" to direction)
        }
        uiOptions?.let { input["ui_options"] = it }
        id?.takeIf { it.isNotBlank() }?.let { input["id"] = it }
        val result = api.graphql(query, mapOf("input" to input))
        val obj = result.getAsJsonObject("data")?.getAsJsonObject("saveFilter")
            ?: throw IOException("Stash did not return the saved filter")
        return parseSavedSceneFilter(obj)
    }

    suspend fun destroySavedSceneFilter(id: String) {
        val query = """
            mutation(${"$"}input: DestroyFilterInput!) {
                destroySavedFilter(input: ${"$"}input)
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("input" to mapOf("id" to id)))
        val destroyed = result.getAsJsonObject("data")?.get("destroySavedFilter")
            ?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        if (!destroyed) throw IOException("Stash could not delete the saved filter")
    }

    /**
     * Library-wide scene markers, optionally filtered to one tag (matches
     * markers whose primary or extra tags include it, via the same
     * hierarchical criterion Stash's web UI uses).
     */
    suspend fun findSceneMarkers(
        page: Int = 1,
        perPage: Int = 100,
        sort: String = "created_at",
        direction: String = "DESC",
        tagId: String? = null,
        searchQuery: String? = null
    ): Pair<Int, List<SceneMarkerItem>> {
        val query = """
            query(${"$"}filter: FindFilterType, ${"$"}marker_filter: SceneMarkerFilterType) {
                findSceneMarkers(filter: ${"$"}filter, scene_marker_filter: ${"$"}marker_filter) {
                    count
                    scene_markers {
                        id title seconds
                        primary_tag { id name }
                        scene { id title files { path width height } }
                    }
                }
            }
        """.trimIndent()
        val markerFilter = tagId?.takeIf { it.isNotBlank() }?.let {
            mapOf("tags" to mapOf("value" to listOf(it), "modifier" to "INCLUDES", "depth" to 0))
        }
        val findFilter = mutableMapOf<String, Any?>(
            "page" to page, "per_page" to perPage,
            "sort" to sort, "direction" to direction
        )
        // FindFilterType.q searches marker titles (and their scene titles).
        searchQuery?.takeIf { it.isNotBlank() }?.let { findFilter["q"] = it }
        val variables = mapOf(
            "filter" to findFilter,
            "marker_filter" to markerFilter
        )
        val result = api.graphql(query, variables)
        val data = result.getAsJsonObject("data")?.getAsJsonObject("findSceneMarkers")
            ?: return Pair(0, emptyList())
        val count = data.optInt("count", 0)
        val markers = (data.get("scene_markers") as? JsonArray)
            ?.map { parseSceneMarker(it.asJsonObject) } ?: emptyList()
        return Pair(count, markers)
    }

    @VisibleForTesting
    internal fun parseSceneMarker(obj: JsonObject): SceneMarkerItem {
        val scene = obj.optObject("scene")
        val files = scene?.get("files") as? JsonArray
        val file = files?.firstOrNull()?.asJsonObject
        val tag = obj.optObject("primary_tag")
        return SceneMarkerItem(
            id = obj.optString("id", ""),
            title = obj.optString("title", ""),
            seconds = obj.optDouble("seconds", 0.0),
            sceneId = scene?.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
            sceneTitle = scene?.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
            sceneWidth = file?.get("width")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            sceneHeight = file?.get("height")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            primaryTagId = tag?.get("id")?.takeIf { !it.isJsonNull }?.asString ?: "",
            primaryTagName = tag?.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
        )
    }

    /** Server-wide library stats (Stash 0.24+ stats query). */
    suspend fun getStats(): StatsSummary {
        val query = """
            query {
                stats {
                    scene_count scenes_size scenes_duration image_count
                    performer_count studio_count tag_count total_o_count
                    total_play_duration total_play_count scenes_played
                }
            }
        """.trimIndent()
        val result = api.graphql(query)
        val obj = result.getAsJsonObject("data")?.get("stats")?.takeIf { !it.isJsonNull }?.asJsonObject
            ?: return StatsSummary()
        return StatsSummary(
            sceneCount = obj.optInt("scene_count", 0),
            scenesSizeBytes = obj.optDouble("scenes_size", 0.0),
            scenesDurationSeconds = obj.optDouble("scenes_duration", 0.0),
            imageCount = obj.optInt("image_count", 0),
            performerCount = obj.optInt("performer_count", 0),
            studioCount = obj.optInt("studio_count", 0),
            tagCount = obj.optInt("tag_count", 0),
            totalOCount = obj.optInt("total_o_count", 0),
            totalPlayDurationSeconds = obj.optDouble("total_play_duration", 0.0),
            totalPlayCount = obj.optInt("total_play_count", 0),
            scenesPlayed = obj.optInt("scenes_played", 0)
        )
    }

    /**
     * The most-played scenes with full metadata, for the stats screen's
     * top-entity aggregation. Client-side aggregation keeps this a single
     * 0.24-safe query instead of per-entity play-time filters.
     */
    suspend fun findTopPlayedScenes(perPage: Int = 200): List<Scene> =
        api.findScenesInternal(
            perPage = perPage,
            sort = "play_count",
            direction = "DESC",
            sceneFilter = mapOf("play_count" to mapOf("value" to 0, "modifier" to "GREATER_THAN")),
            // last_played_at powers the taste-trends window on the stats screen.
            fields = SinemaApi.SCENE_FIELDS_FULL + " last_played_at"
        ).second

    /**
     * Scenes with saved progress ("Finish Watching" triage). Near-end scenes
     * (within 30s of the end) are filtered client-side by the caller.
     */
    suspend fun findPartiallyWatched(
        page: Int,
        perPage: Int,
        sort: String = "resume_time",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>> =
        api.findScenesInternal(
            page = page,
            perPage = perPage,
            sort = sort,
            direction = direction,
            sceneFilter = mapOf("resume_time" to mapOf("value" to 0, "modifier" to "GREATER_THAN")),
            filters = filters
        )

    /**
     * Candidates for Tonight's Mix / Endless Mix: random unwatched
     * favorites, falling back to recent unwatched scenes.
     */
    suspend fun findMixCandidates(excludedIds: Set<String> = emptySet()): List<Scene> {
        var candidates = api.findFavoriteScenesPaged(
            page = 1, perPage = 100, sort = "random", direction = "DESC", unwatchedOnly = true
        ).second.filter { it.id !in excludedIds }
        if (candidates.isEmpty()) {
            candidates = api.findRecentScenesPaged(page = 1, perPage = 100)
                .second.filter { !it.isWatched && it.id !in excludedIds }
        }
        return candidates
    }

    /** Stash server version string ("unknown" when the query fails). */
    suspend fun getServerVersion(): String {
        val query = """query { version { version } }"""
        return try {
            api.graphql(query).getAsJsonObject("data")?.getAsJsonObject("version")
                ?.get("version")?.takeIf { !it.isJsonNull }?.asString ?: "unknown"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Running Stash job descriptions (scan/generate/clean…). Job-queue
     * introspection needs newer servers; callers show a placeholder when
     * unsupported. Returns null on any failure.
     */
    suspend fun getJobQueue(): List<String>? {
        val query = """query { jobQueue { id status description } }"""
        return try {
            val arr = api.graphql(query).getAsJsonObject("data")?.get("jobQueue")
                ?.takeIf { !it.isJsonNull }?.asJsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val status = obj.optString("status", "")
                val description = obj.optString("description", "")
                if (description.isBlank()) null else "$status — $description"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    @VisibleForTesting
    internal fun parseSavedSceneFilter(obj: JsonObject): SavedSceneFilter {
        val findFilter = obj.optObject("find_filter")
        val rawObjectFilter = obj.optObject("object_filter")
        val rawUiOptions = obj.optObject("ui_options")
        return SavedSceneFilter(
            id = obj.optString("id", ""),
            name = obj.optString("name", ""),
            sceneFilter = rawObjectFilter?.let { api.gson.fromJson(it, MapType.savingFilterMap) },
            sort = findFilter?.get("sort")?.takeIf { !it.isJsonNull }?.asString,
            direction = findFilter?.get("direction")?.takeIf { !it.isJsonNull }?.asString,
            uiOptions = rawUiOptions?.let { api.gson.fromJson(it, MapType.savingFilterMap) }
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
        return api.findScenesInternal(
            page = page,
            perPage = perPage,
            sort = sort,
            direction = direction,
            sceneFilter = sceneFilter,
            filters = filters
        )
    }

    suspend fun findRelatedScenes(source: Scene, perPage: Int = 40): List<Scene> {
        val performerIds = source.performers.map { it.id }.filter { it.isNotBlank() }
        val tagIds = source.tags.map { it.id }.filter { it.isNotBlank() }
        val studioId = source.studio?.id?.takeIf { it.isNotBlank() }
        if (performerIds.isEmpty() && tagIds.isEmpty() && studioId == null) return emptyList()
        return supervisorScope {
            val performers = async {
                if (performerIds.isEmpty()) emptyList() else findRelatedCandidates(
                    mapOf("performers" to mapOf("value" to performerIds, "modifier" to "INCLUDES")),
                    perPage
                )
            }
            val studio = async {
                if (studioId == null) emptyList() else findRelatedCandidates(
                    mapOf("studios" to mapOf("value" to listOf(studioId), "modifier" to "INCLUDES", "depth" to 0)),
                    perPage
                )
            }
            val tags = async {
                if (tagIds.isEmpty()) emptyList() else findRelatedCandidates(
                    mapOf("tags" to mapOf("value" to tagIds, "modifier" to "INCLUDES", "depth" to 0)),
                    perPage
                )
            }
            com.sinema.model.RelatedScenes.rank(
                source,
                performers.await() + studio.await() + tags.await(),
                limit = 40
            )
        }
    }

    private suspend fun findRelatedCandidates(
        sceneFilter: Map<String, Any?>,
        perPage: Int
    ): List<Scene> = try {
        api.findScenesInternal(
            page = 1,
            perPage = perPage.coerceIn(1, 40),
            sort = "updated_at",
            direction = "DESC",
            sceneFilter = sceneFilter,
            fields = SinemaApi.SCENE_FIELDS_FULL
        ).second
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Sinema", "Related-scene candidate query failed", e)
        emptyList()
    }

    suspend fun findUnwatchedForFavoritePerformersPaged(
        page: Int,
        perPage: Int,
        sort: String = "updated_at",
        direction: String = "DESC",
        filters: com.sinema.model.SceneFilterOptions = com.sinema.model.SceneFilterOptions()
    ): Pair<Int, List<Scene>> = api.findScenesInternal(
        page = page,
        perPage = perPage,
        sort = sort,
        direction = direction,
        sceneFilter = mapOf(
            "performer_favorite" to true,
            "play_count" to mapOf("value" to 0, "modifier" to "EQUALS")
        ),
        filters = filters
    )

    /**
     * Fetches the full metadata payload for one scene.
     * @return scene details, or null if the scene does not exist.
     * @throws IOException on transport or GraphQL errors (callers must catch, as with every method here).
     */
    suspend fun findSceneFull(sceneId: String): SceneDetails? {
        val query = """
            query(${"$"}id: ID!) {
                findScene(id: ${"$"}id) {
                    ${SinemaApi.SCENE_FIELDS_FULL}
                    resume_time
                    galleries { id title }
                    scene_markers { id title seconds primary_tag { id name } }
                }
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("id" to sceneId))
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
            scene = api.parseScene(obj),
            resumeTime = obj.optDouble("resume_time", 0.0),
            markers = markers
        )
    }

    @VisibleForTesting
    internal fun parseMarker(mo: JsonObject): MarkerRef = MarkerRef(
        id = mo.optString("id", ""),
        title = mo.optString("title", ""),
        seconds = mo.optDouble("seconds", 0.0),
        primaryTag = (mo.get("primary_tag") as? JsonObject)?.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
    )

    fun getCaptionUrl(sceneId: String, caption: CaptionRef): String =
        "${api.serverUrl}/scene/$sceneId/caption?" +
            "lang=${URLEncoder.encode(caption.languageCode, "UTF-8")}" +
            "&type=${URLEncoder.encode(caption.captionType, "UTF-8")}"

    suspend fun resetPlayCount(sceneId: String) {
        val query = """
            mutation(${"$"}id: ID!) {
                sceneResetPlayCount(id: ${"$"}id)
            }
        """.trimIndent()
        val result = api.graphql(query, mapOf("id" to sceneId))
        Log.d("Sinema", "resetPlayCount result: $result")
    }
}
