package com.sinema.ui

internal object HomeCommandRow {
    const val LOG_OUT = "Log Out"
    const val REFRESH = "Refresh"
    const val SETTINGS = "Settings"
    const val ABOUT = "About"

    const val LOG_OUT_KEY = "string:Log Out"
    const val REFRESH_KEY = "string:Refresh"
    const val SETTINGS_KEY = "string:Settings"
    const val ABOUT_KEY = "string:About"

    fun items(hasPin: Boolean): List<String> = buildList {
        if (hasPin) add(LOG_OUT)
        add(REFRESH)
        add(SETTINGS)
        add(ABOUT)
    }
}

internal enum class HomeInitialTarget { RETRY, HERO, BROWSE, REFRESH }

internal object HomeFocusPolicy {
    fun initialTarget(retryVisible: Boolean, heroVisible: Boolean, browseItemCount: Int): HomeInitialTarget = when {
        retryVisible -> HomeInitialTarget.RETRY
        heroVisible -> HomeInitialTarget.HERO
        browseItemCount > 0 -> HomeInitialTarget.BROWSE
        else -> HomeInitialTarget.REFRESH
    }

    fun restoredItemIndex(oldKey: String?, oldIndex: Int, newKeys: List<String>): Int {
        val restored = FocusRestore.positionAfterReload(oldKey, oldIndex, newKeys)
        if (restored < 0 || newKeys[restored] != HomeCommandRow.LOG_OUT_KEY) return restored
        if (oldKey == HomeCommandRow.LOG_OUT_KEY) return restored
        return newKeys.indexOf(HomeCommandRow.REFRESH_KEY)
    }
}
