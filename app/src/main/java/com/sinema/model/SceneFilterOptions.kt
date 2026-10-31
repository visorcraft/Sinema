package com.sinema.model

enum class WatchFilter(val label: String) {
    ALL("All scenes"),
    WATCHED("Watched"),
    UNWATCHED("Unwatched")
}

data class SceneFilterOptions(
    val watch: WatchFilter = WatchFilter.ALL,
    val favoritesOnly: Boolean = false
) {
    val summary: String
        get() = buildList {
            if (watch != WatchFilter.ALL) add(watch.label)
            if (favoritesOnly) add("Favorites")
        }.joinToString(" + ").ifEmpty { WatchFilter.ALL.label }

    fun toPreferenceValue(): String = "${watch.name}:$favoritesOnly"

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
        if (favoritesOnly) {
            put("rating100", mapOf("value" to 0, "modifier" to "GREATER_THAN"))
        }
    }

    companion object {
        fun fromPreferenceValue(value: String?): SceneFilterOptions {
            val parts = value?.split(':') ?: return SceneFilterOptions()
            val watch = parts.firstOrNull()
                ?.let { name -> WatchFilter.entries.firstOrNull { it.name == name } }
                ?: WatchFilter.ALL
            val favoritesOnly = parts.getOrNull(1)?.toBooleanStrictOrNull() == true
            if (parts.size != 2 || parts[1].toBooleanStrictOrNull() == null) {
                return SceneFilterOptions()
            }
            return SceneFilterOptions(watch, favoritesOnly)
        }
    }
}
