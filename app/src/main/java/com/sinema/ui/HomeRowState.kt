package com.sinema.ui

internal data class HomeRowState<T>(
    val id: Long,
    val title: String,
    val items: List<T>? = null,
    val loading: Boolean = false,
    val error: Exception? = null
) {
    val visible: Boolean
        get() = loading || !items.isNullOrEmpty() || error != null

    val displayTitle: String
        get() = when {
            loading && items == null -> "$title · Loading…"
            loading -> "$title · Refreshing…"
            error != null && items.isNullOrEmpty() -> "$title · Unavailable"
            error != null -> "$title · Refresh failed"
            else -> title
        }

    fun refreshing(): HomeRowState<T> = copy(loading = true, error = null)

    fun loaded(items: List<T>): HomeRowState<T> =
        copy(items = items, loading = false, error = null)

    fun failed(cause: Exception): HomeRowState<T> =
        copy(loading = false, error = cause)
}
