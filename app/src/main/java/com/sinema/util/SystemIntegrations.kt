package com.sinema.util

import android.content.Context
import com.sinema.SinemaApp

object SystemIntegrations {
    fun onActiveServerChanged(context: Context, mutate: () -> Unit) {
        TvChannels.withSyncLock {
            mutate()
            SinemaApp.instance.refreshApi()
            TvChannels.clearAll(context.applicationContext)
        }
    }
}
