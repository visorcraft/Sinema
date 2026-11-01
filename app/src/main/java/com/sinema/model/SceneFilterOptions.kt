package com.sinema.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class WatchFilter(val label: String) {
    ALL("All scenes"),
    WATCHED("Watched"),
    UNWATCHED("Unwatched")
}

enum class ResolutionFilter(val label: String) {
    ANY("Any resolution"),
    UNDER_HD("Under 720p"),
    HD_PLUS("720p or better"),
    FULL_HD_PLUS("1080p or better"),
    FOUR_K_PLUS("4K or better")
}

enum class PresenceFilter(val label: String) {
    ANY("Any"),
    WITH("With"),
    WITHOUT("Without")
}

enum class DurationFilter(val label: String) {
    ANY("Any duration"),
    UNDER_10("Under 10 min"),
    TEN_TO_THIRTY("10–30 min"),
    THIRTY_TO_SIXTY("30–60 min"),
    OVER_60("Over 60 min")
}

enum class ReleaseDateFilter(val label: String) {
    ANY("Any release date"),
    PAST_30_DAYS("Last 30 days"),
    PAST_YEAR("Last year"),
    OLDER_THAN_YEAR("Over a year old")
}

enum class VideoCodecFilter(val label: String, val stashValue: String) {
    ANY("Any codec", ""),
    H264("H.264", "h264"),
    HEVC("HEVC", "hevc"),
    VP9("VP9", "vp9"),
    AV1("AV1", "av1")
}

enum class EntityFilterMode { ANY, INCLUDE, EXCLUDE }

data class EntitySelection(val id: String, val name: String)

data class EntityFilter(
    val mode: EntityFilterMode = EntityFilterMode.ANY,
    val entities: List<EntitySelection> = emptyList()
) {
    val isActive: Boolean get() = mode != EntityFilterMode.ANY && entities.isNotEmpty()

    fun summary(label: String): String {
        val names = entities.map { it.name }.filter { it.isNotBlank() }
        val title = if (mode == EntityFilterMode.EXCLUDE) "Exclude ${label.lowercase()}" else label
        val first = names.firstOrNull().orEmpty()
        return "$title: $first" + if (names.size > 1) " +${names.size - 1}" else ""
    }
}

fun WatchFilter.cycled(): WatchFilter = when (this) {
    WatchFilter.ALL -> WatchFilter.UNWATCHED
    WatchFilter.UNWATCHED -> WatchFilter.WATCHED
    WatchFilter.WATCHED -> WatchFilter.ALL
}

fun SceneFilterOptions.effectiveFavorites(): Boolean = favoritesOnly || minStars > 0

fun SceneFilterOptions.toggledQuickFavorites(): SceneFilterOptions =
    if (effectiveFavorites()) copy(favoritesOnly = false, minStars = 0)
    else copy(favoritesOnly = true)

data class SceneFilterOptions(
    val watch: WatchFilter = WatchFilter.ALL,
    val favoritesOnly: Boolean = false,
    val resolution: ResolutionFilter = ResolutionFilter.ANY,
    val markers: PresenceFilter = PresenceFilter.ANY,
    val captions: PresenceFilter = PresenceFilter.ANY,
    val minStars: Int = 0,
    val duration: DurationFilter = DurationFilter.ANY,
    val releaseDate: ReleaseDateFilter = ReleaseDateFilter.ANY,
    val videoCodec: VideoCodecFilter = VideoCodecFilter.ANY,
    val performers: EntityFilter = EntityFilter(),
    val studios: EntityFilter = EntityFilter(),
    val tags: EntityFilter = EntityFilter()
) {
    val summary: String
        get() = buildList {
            if (watch != WatchFilter.ALL) add(watch.label)
            if (favoritesOnly) add("Favorites")
            if (minStars > 0) add("★${minStars}+")
            if (resolution != ResolutionFilter.ANY) add(resolution.label)
            if (markers != PresenceFilter.ANY) add("${markers.label} markers")
            if (captions != PresenceFilter.ANY) add("${captions.label} captions")
            if (duration != DurationFilter.ANY) add(duration.label)
            if (releaseDate != ReleaseDateFilter.ANY) add(releaseDate.label)
            if (videoCodec != VideoCodecFilter.ANY) add(videoCodec.label)
            if (performers.isActive) add(performers.summary("Performers"))
            if (studios.isActive) add(studios.summary("Studios"))
            if (tags.isActive) add(tags.summary("Tags"))
        }.joinToString(" + ").ifEmpty { WatchFilter.ALL.label }

    fun toPreferenceValue(): String {
        val root = JsonObject().apply {
            addProperty("watch", watch.name)
            addProperty("favoritesOnly", favoritesOnly)
            addProperty("resolution", resolution.name)
            addProperty("markers", markers.name)
            addProperty("captions", captions.name)
            addProperty("minStars", minStars)
            addProperty("duration", duration.name)
            addProperty("releaseDate", releaseDate.name)
            addProperty("videoCodec", videoCodec.name)
            add("performers", performers.toJson())
            add("studios", studios.toJson())
            add("tags", tags.toJson())
        }
        return root.toString()
    }

    fun toStashFilter(referenceDate: String = currentDate()): Map<String, Any?> = buildMap {
        when (watch) {
            WatchFilter.ALL -> Unit
            WatchFilter.WATCHED -> put("play_count", mapOf("value" to 0, "modifier" to "GREATER_THAN"))
            WatchFilter.UNWATCHED -> put("play_count", mapOf("value" to 0, "modifier" to "EQUALS"))
        }
        if (minStars > 0) {
            put("rating100", mapOf("value" to minStars * 20 - 1, "modifier" to "GREATER_THAN"))
        } else if (favoritesOnly) {
            put("rating100", mapOf("value" to 0, "modifier" to "GREATER_THAN"))
        }
        when (resolution) {
            ResolutionFilter.ANY -> Unit
            ResolutionFilter.UNDER_HD -> put(
                "resolution", mapOf("value" to "STANDARD_HD", "modifier" to "LESS_THAN")
            )
            ResolutionFilter.HD_PLUS -> put(
                "resolution", mapOf("value" to "WEB_HD", "modifier" to "GREATER_THAN")
            )
            ResolutionFilter.FULL_HD_PLUS -> put(
                "resolution", mapOf("value" to "STANDARD_HD", "modifier" to "GREATER_THAN")
            )
            ResolutionFilter.FOUR_K_PLUS -> put(
                "resolution", mapOf("value" to "QUAD_HD", "modifier" to "GREATER_THAN")
            )
        }
        when (markers) {
            PresenceFilter.ANY -> Unit
            PresenceFilter.WITH -> put("has_markers", "true")
            PresenceFilter.WITHOUT -> put("has_markers", "false")
        }
        when (captions) {
            PresenceFilter.ANY -> Unit
            PresenceFilter.WITH -> put("captions", mapOf("value" to "", "modifier" to "NOT_NULL"))
            PresenceFilter.WITHOUT -> put("captions", mapOf("value" to "", "modifier" to "IS_NULL"))
        }
        when (duration) {
            DurationFilter.ANY -> Unit
            DurationFilter.UNDER_10 -> put("duration", mapOf("value" to 600, "modifier" to "LESS_THAN"))
            DurationFilter.TEN_TO_THIRTY -> put(
                "duration", mapOf("value" to 600, "value2" to 1800, "modifier" to "BETWEEN")
            )
            DurationFilter.THIRTY_TO_SIXTY -> put(
                "duration", mapOf("value" to 1800, "value2" to 3600, "modifier" to "BETWEEN")
            )
            DurationFilter.OVER_60 -> put("duration", mapOf("value" to 3600, "modifier" to "GREATER_THAN"))
        }
        when (releaseDate) {
            ReleaseDateFilter.ANY -> Unit
            ReleaseDateFilter.PAST_30_DAYS -> put(
                "date", mapOf("value" to shiftDate(referenceDate, Calendar.DAY_OF_YEAR, -30), "modifier" to "GREATER_THAN")
            )
            ReleaseDateFilter.PAST_YEAR -> put(
                "date", mapOf("value" to shiftDate(referenceDate, Calendar.YEAR, -1), "modifier" to "GREATER_THAN")
            )
            ReleaseDateFilter.OLDER_THAN_YEAR -> put(
                "date", mapOf("value" to shiftDate(referenceDate, Calendar.YEAR, -1), "modifier" to "LESS_THAN")
            )
        }
        if (videoCodec != VideoCodecFilter.ANY) {
            put("video_codec", mapOf("value" to videoCodec.stashValue, "modifier" to "EQUALS"))
        }
        putEntityFilter("performers", performers, hierarchical = false)
        putEntityFilter("studios", studios, hierarchical = true)
        putEntityFilter("tags", tags, hierarchical = true)
    }

    companion object {
        fun fromPreferenceValue(value: String?): SceneFilterOptions {
            if (value.isNullOrBlank()) return SceneFilterOptions()
            if (value.trimStart().startsWith("{")) {
                return try {
                    fromJson(JsonParser.parseString(value).asJsonObject)
                } catch (_: Exception) {
                    SceneFilterOptions()
                }
            }
            val parts = value.split(':')
            val watch = parts.getOrNull(0)
                ?.let { name -> WatchFilter.entries.firstOrNull { it.name == name } }
                ?: WatchFilter.ALL
            val favorites = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
            if (parts.size == 2 && parts[1].toBooleanStrictOrNull() == null) return SceneFilterOptions()
            val minStars = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, StarRating.MAX_STARS) ?: 0
            val resolution = parts.getOrNull(3).enumOrDefault(ResolutionFilter.ANY)
            val markers = parts.getOrNull(4).enumOrDefault(PresenceFilter.ANY)
            val captions = parts.getOrNull(5).enumOrDefault(PresenceFilter.ANY)
            return SceneFilterOptions(watch, favorites, resolution, markers, captions, minStars)
        }

        private fun fromJson(obj: JsonObject): SceneFilterOptions {
            val watch = obj.string("watch").enumOrDefault(WatchFilter.ALL)
            val favorites = obj.get("favoritesOnly")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            val minStars = obj.get("minStars")?.takeIf { it.isJsonPrimitive }?.asInt
                ?.coerceIn(0, StarRating.MAX_STARS) ?: 0
            return SceneFilterOptions(
                watch = watch,
                favoritesOnly = favorites,
                resolution = obj.string("resolution").enumOrDefault(ResolutionFilter.ANY),
                markers = obj.string("markers").enumOrDefault(PresenceFilter.ANY),
                captions = obj.string("captions").enumOrDefault(PresenceFilter.ANY),
                minStars = minStars,
                duration = obj.string("duration").enumOrDefault(DurationFilter.ANY),
                releaseDate = obj.string("releaseDate").enumOrDefault(ReleaseDateFilter.ANY),
                videoCodec = obj.string("videoCodec").enumOrDefault(VideoCodecFilter.ANY),
                performers = entityFilterFromJson(obj, "performers"),
                studios = entityFilterFromJson(obj, "studios"),
                tags = entityFilterFromJson(obj, "tags")
            )
        }
    }
}

private fun EntityFilter.toJson(): JsonObject = JsonObject().apply {
    addProperty("mode", mode.name)
    add("entities", com.google.gson.JsonArray().apply {
        entities.forEach { selection ->
            add(JsonObject().apply {
                addProperty("id", selection.id)
                addProperty("name", selection.name)
            })
        }
    })
}

private fun entityFilterFromJson(root: JsonObject, key: String): EntityFilter {
    val element = root.get(key) ?: return EntityFilter()
    if (element.isJsonNull) return EntityFilter()
    if (!element.isJsonObject) throw IllegalArgumentException("$key must be an object")
    val obj = element.asJsonObject
    val mode = obj.string("mode").enumOrDefault(EntityFilterMode.ANY)
    val entityArray = obj.get("entities") ?: return EntityFilter(mode)
    if (!entityArray.isJsonArray) throw IllegalArgumentException("$key entities must be an array")
    val entities = entityArray.asJsonArray.mapNotNull { item ->
        if (!item.isJsonObject) return@mapNotNull null
        val id = item.asJsonObject.string("id").orEmpty()
        if (id.isBlank()) null else EntitySelection(id, item.asJsonObject.string("name").orEmpty())
    }
    return EntityFilter(mode, entities)
}

private fun JsonObject.string(key: String): String? {
    val value = get(key) ?: return null
    if (value.isJsonNull) return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
        throw IllegalArgumentException("$key must be a string")
    }
    return value.asString
}

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

private fun <K> MutableMap<K, Any?>.putEntityFilter(
    key: K,
    filter: EntityFilter,
    hierarchical: Boolean
) {
    if (!filter.isActive) return
    val criterion = mutableMapOf<String, Any?>(
        "value" to filter.entities.map { it.id },
        "modifier" to if (filter.mode == EntityFilterMode.EXCLUDE) "EXCLUDES" else "INCLUDES"
    )
    if (hierarchical) criterion["depth"] = 0
    put(key, criterion)
}

private fun currentDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun shiftDate(value: String, field: Int, amount: Int): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val date = format.parse(value) ?: return currentDate()
    val calendar = Calendar.getInstance().apply {
        time = date
        add(field, amount)
    }
    return format.format(calendar.time)
}
