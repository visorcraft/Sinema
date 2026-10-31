package com.sinema.model

enum class HomeShortcut(val label: String, val primary: Boolean = false) {
    SEARCH("Search", primary = true),
    SURPRISE_ME("Surprise Me", primary = true),
    FAVORITES("Favorites", primary = true),
    BROWSE_FOLDERS("Browse Folders"),
    TAGS("Tags"),
    PERFORMERS("Performers"),
    STUDIOS("Studios"),
    MARKERS("Markers"),
    STATS("Stats"),
    SAVED_FILTERS("Saved Filters"),
    LIBRARY("Library", primary = true);

    companion object {
        val customizable: List<HomeShortcut> = entries.filter { it != LIBRARY }
    }
}

enum class HomeContentRow(val label: String, val optional: Boolean = false) {
    CONTINUE_PLAYING("Continue Playing"),
    RECENTLY_PLAYED("Recently Played"),
    RECENTLY_ADDED("Recently Added"),
    FAVORITES("Favorites"),
    UNWATCHED_FAVORITES("Unwatched Favorites", optional = true),
    RECENTLY_RELEASED("Recently Released", optional = true),
    SAVED_FILTER("Saved Filter", optional = true)
}

enum class HomeCardSize(val widthDp: Int, val heightDp: Int, val imageHeightDp: Int) {
    COMPACT(266, 220, 158),
    LARGE(320, 270, 200)
}

data class HomeLayoutSettings(
    val shortcutOrder: List<HomeShortcut>? = null,
    val hiddenShortcuts: Set<HomeShortcut>? = null,
    val rowOrder: List<HomeContentRow>? = null,
    val hiddenRows: Set<HomeContentRow>? = null,
    val cardSize: HomeCardSize = HomeCardSize.COMPACT,
    val groupLibrary: Boolean = false,
    val showHero: Boolean = true,
    val savedFilterId: String = "",
    val savedFilterName: String = ""
) {
    fun normalized(): HomeLayoutSettings {
        val shortcuts = (shortcutOrder.orEmpty().filter { it != HomeShortcut.LIBRARY } +
            HomeShortcut.customizable).distinct()
        val rows = (rowOrder.orEmpty() + HomeContentRow.entries).distinct()
        return copy(
            shortcutOrder = shortcuts,
            hiddenShortcuts = hiddenShortcuts.orEmpty().intersect(HomeShortcut.customizable.toSet()),
            rowOrder = rows,
            hiddenRows = hiddenRows ?: HomeContentRow.entries.filter { it.optional }.toSet()
        )
    }

    fun visibleShortcuts(): List<HomeShortcut> {
        val settings = normalized()
        val visible = settings.shortcutOrder.orEmpty() - settings.hiddenShortcuts.orEmpty()
        if (!settings.groupLibrary) return visible
        val primary = visible.filter { it.primary }
        val libraryIndex = primary.indexOf(HomeShortcut.FAVORITES).takeIf { it >= 0 }?.plus(1)
            ?: primary.size
        return primary.toMutableList().apply { add(libraryIndex, HomeShortcut.LIBRARY) }
    }

    fun visibleLibraryShortcuts(): List<HomeShortcut> {
        val settings = normalized()
        return settings.shortcutOrder.orEmpty().filter {
            !it.primary && it !in settings.hiddenShortcuts.orEmpty()
        }
    }

    fun orderedRows(): List<HomeContentRow> {
        val settings = normalized()
        return settings.rowOrder.orEmpty().filter { it !in settings.hiddenRows.orEmpty() }
    }

    fun isRowVisible(row: HomeContentRow): Boolean = row in orderedRows()

    fun moveShortcut(shortcut: HomeShortcut, delta: Int): HomeLayoutSettings {
        val settings = normalized()
        val moved = settings.shortcutOrder.orEmpty().move(shortcut, delta)
        return settings.copy(shortcutOrder = moved)
    }

    fun moveRow(row: HomeContentRow, delta: Int): HomeLayoutSettings {
        val settings = normalized()
        val moved = settings.rowOrder.orEmpty().move(row, delta)
        return settings.copy(rowOrder = moved)
    }

    fun toggleShortcut(shortcut: HomeShortcut): HomeLayoutSettings {
        val settings = normalized()
        val hidden = settings.hiddenShortcuts.orEmpty()
        return settings.copy(
            hiddenShortcuts = if (shortcut in hidden) hidden - shortcut else hidden + shortcut
        )
    }

    fun toggleRow(row: HomeContentRow): HomeLayoutSettings {
        val settings = normalized()
        val hidden = settings.hiddenRows.orEmpty()
        return settings.copy(hiddenRows = if (row in hidden) hidden - row else hidden + row)
    }
}

private fun <T> List<T>.move(item: T, delta: Int): List<T> {
    val index = indexOf(item)
    if (index < 0 || delta == 0) return this
    val target = (index + delta).coerceIn(0, lastIndex)
    if (target == index) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
