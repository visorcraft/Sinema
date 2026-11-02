package com.sinema.ui

internal data class GridLoadState(
    val visible: Boolean,
    val showProgress: Boolean = false,
    val message: String = "",
    val showRetry: Boolean = false
) {
    companion object {
        fun loading(hasContent: Boolean) = GridLoadState(
            visible = !hasContent,
            showProgress = !hasContent
        )

        fun loaded(itemCount: Int, emptyMessage: String) = GridLoadState(
            visible = itemCount == 0,
            message = if (itemCount == 0) emptyMessage else ""
        )

        fun failed(hasContent: Boolean, message: String) = GridLoadState(
            visible = !hasContent,
            message = if (hasContent) "" else message,
            showRetry = !hasContent
        )
    }
}
