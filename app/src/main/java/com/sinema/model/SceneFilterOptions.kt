package com.sinema.model

enum class WatchFilter(val label: String) {
    ALL("All scenes"),
    WATCHED("Watched"),
    UNWATCHED("Unwatched")
}

/**
 * Resolution filter mapped to Stash's ResolutionCriterionInput. Stash
 * compares enum ranks (VERY_LOW < … < HUGE) and GREATER_THAN/LESS_THAN are
 * STRICT, so "X or better" is expressed as strictly-greater-than the next
 * lower tier — e.g. "720p or better" = GREATER_THAN WEB_HD (540p).
 */
enum class ResolutionFilter(val label: String) {
    ANY("Any resolution"),
    UNDER_HD("Under 720p"),
    HD_PLUS("720p or better"),
    FULL_HD_PLUS("1080p or better"),
    FOUR_K_PLUS("4K or better")
}

/** Any / with / without for boolean-ish presence criteria. */
enum class PresenceFilter(val label: String) {
    ANY("Any"),
    WITH("With"),
    WITHOUT("Without")
}

data class SceneFilterOptions(
    val watch: WatchFilter = WatchFilter.ALL,
    val favoritesOnly: Boolean = false,
    val resolution: ResolutionFilter = ResolutionFilter.ANY,
    val markers: PresenceFilter = PresenceFilter.ANY,
    val captions: PresenceFilter = PresenceFilter.ANY,
    val minStars: Int = 0
) {
    val summary: String
        get() = buildList {
            if (watch != WatchFilter.ALL) add(watch.label)
            if (favoritesOnly) add("Favorites")
            if (minStars > 0) add("★${minStars}+")
            if (resolution != ResolutionFilter.ANY) add(resolution.label)
            if (markers != PresenceFilter.ANY) add("${markers.label} markers")
            if (captions != PresenceFilter.ANY) add("${captions.label} captions")
        }.joinToString(" + ").ifEmpty { WatchFilter.ALL.label }

    fun toPreferenceValue(): String =
        "${watch.name}:$favoritesOnly:$minStars:${resolution.name}:${markers.name}:${captions.name}"

    fun toStashFilter(): Map<String, Any?> = buildMap {
        when (watch) {
            WatchFilter.ALL -> Unit
            WatchFilter.WATCHED -> put(
                "play_count",
                mapOf("value" to 0, "modifier" to "GREATER_THAN")
            )
            WatchFilter.UNWATCHED -> put(
                "play_count",
                mapOf("value" to 0, "modifier" to "EQUALS")
            )
        }
        // favoritesOnly (rating > 0) and minStars share the rating100 criterion;
        // minStars wins when both are set since it is the stronger bound.
        if (minStars > 0) {
            put("rating100", mapOf("value" to minStars * 20 - 1, "modifier" to "GREATER_THAN"))
        } else if (favoritesOnly) {
            put("rating100", mapOf("value" to 0, "modifier" to "GREATER_THAN"))
        }
        when (resolution) {
            ResolutionFilter.ANY -> Unit
            // LESS_THAN STANDARD_HD = strictly below 720p.
            ResolutionFilter.UNDER_HD -> put(
                "resolution",
                mapOf("value" to "STANDARD_HD", "modifier" to "LESS_THAN")
            )
            // Strict comparator: "720p or better" = rank > WEB_HD (540p),
            // which is exactly STANDARD_HD and up.
            ResolutionFilter.HD_PLUS -> put(
                "resolution",
                mapOf("value" to "WEB_HD", "modifier" to "GREATER_THAN")
            )
            // "1080p or better" = rank > STANDARD_HD (720p) = FULL_HD and up.
            ResolutionFilter.FULL_HD_PLUS -> put(
                "resolution",
                mapOf("value" to "STANDARD_HD", "modifier" to "GREATER_THAN")
            )
            // "4K or better" = rank > QUAD_HD (1440p) = FOUR_K and up.
            ResolutionFilter.FOUR_K_PLUS -> put(
                "resolution",
                mapOf("value" to "QUAD_HD", "modifier" to "GREATER_THAN")
            )
        }
        // has_markers is a plain String criterion in Stash ("true"/"false").
        when (markers) {
            PresenceFilter.ANY -> Unit
            PresenceFilter.WITH -> put("has_markers", "true")
            PresenceFilter.WITHOUT -> put("has_markers", "false")
        }
        // captions is a StringCriterionInput; presence checks ignore the value.
        when (captions) {
            PresenceFilter.ANY -> Unit
            PresenceFilter.WITH -> put("captions", mapOf("value" to "", "modifier" to "NOT_NULL"))
            PresenceFilter.WITHOUT -> put("captions", mapOf("value" to "", "modifier" to "IS_NULL"))
        }
    }

    companion object {
        /**
         * Parses the persisted filter string. Format evolved over releases:
         * 1.20.0 wrote "watch:favoritesOnly"; this version appends
         * ":minStars:resolution:markers:captions". Older or malformed values
         * fall back to defaults for the missing tail.
         */
        fun fromPreferenceValue(value: String?): SceneFilterOptions {
            val parts = value?.split(':')
            if (parts.isNullOrEmpty()) return SceneFilterOptions()
            val watch = parts.getOrNull(0)
                ?.let { name -> WatchFilter.entries.firstOrNull { it.name == name } }
                ?: WatchFilter.ALL
            val favorites = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
            // Strict legacy validation: a 2-part value must carry a valid boolean.
            if (parts.size == 2 && parts[1].toBooleanStrictOrNull() == null) {
                return SceneFilterOptions()
            }
            val minStars = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, StarRating.MAX_STARS) ?: 0
            val resolution = parts.getOrNull(3)
                ?.let { name -> ResolutionFilter.entries.firstOrNull { it.name == name } }
                ?: ResolutionFilter.ANY
            val markers = parts.getOrNull(4)
                ?.let { name -> PresenceFilter.entries.firstOrNull { it.name == name } }
                ?: PresenceFilter.ANY
            val captions = parts.getOrNull(5)
                ?.let { name -> PresenceFilter.entries.firstOrNull { it.name == name } }
                ?: PresenceFilter.ANY
            return SceneFilterOptions(watch, favorites, resolution, markers, captions, minStars)
        }
    }
}
