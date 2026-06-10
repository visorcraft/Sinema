package com.sinema.model

/** Sort choices exposed in grid screens, mapped to Stash FindFilterType sort fields. */
enum class SortOption(val label: String, val field: String, val direction: String) {
    PATH_ASC("Name (A–Z)", "path", "ASC"),
    PATH_DESC("Name (Z–A)", "path", "DESC"),
    ADDED_DESC("Recently added", "created_at", "DESC"),
    DATE_DESC("Release date", "date", "DESC"),
    DURATION_DESC("Longest first", "duration", "DESC"),
    RATING_DESC("Highest rated", "rating", "DESC"),
    PLAYS_DESC("Most played", "play_count", "DESC"),
    RANDOM("Random", "random", "ASC");

    /** Stash paginates random sorts stably when the seed is part of the field name. */
    fun apiSort(seed: Int): String = if (this == RANDOM) "random_$seed" else field

    companion object {
        fun fromName(name: String?): SortOption =
            entries.firstOrNull { it.name == name } ?: PATH_ASC
    }
}
