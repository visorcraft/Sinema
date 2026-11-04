package com.sinema.util

import android.content.Context

object SystemIntegrations {
    fun onActiveServerChanged(context: Context, mutate: () -> Unit) {
        TvChannels.withSyncLock {
            mutate()
            TvChannels.clearAll(context.applicationContext)
        }
    }
}
