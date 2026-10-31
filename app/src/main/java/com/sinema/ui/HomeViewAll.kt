package com.sinema.ui

import com.sinema.model.HomeContentRow
import com.sinema.model.SortOption

data class HomeViewAll(val row: HomeContentRow, val title: String) {
    val key: String get() = "view_all:${row.name}"
    val actionLabel: String get() = "View All"
}

val HomeContentRow.defaultSort: SortOption
    get() = when (this) {
        HomeContentRow.CONTINUE_PLAYING,
        HomeContentRow.RECENTLY_PLAYED -> SortOption.LAST_PLAYED_DESC
        HomeContentRow.RECENTLY_ADDED -> SortOption.ADDED_DESC
        HomeContentRow.RECENTLY_RELEASED -> SortOption.DATE_DESC
        HomeContentRow.FAVORITES,
        HomeContentRow.UNWATCHED_FAVORITES,
        HomeContentRow.SAVED_FILTER -> SortOption.UPDATED_DESC
    }
