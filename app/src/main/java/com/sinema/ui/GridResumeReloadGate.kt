package com.sinema.ui

class GridResumeReloadGate {
    private var hasResumed = false

    fun onResume(): Boolean {
        val shouldReload = hasResumed
        hasResumed = true
        return shouldReload
    }
}
