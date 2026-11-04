package com.sinema.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sinema.SinemaApp
import com.sinema.ui.GlobalSearchActivity

internal fun componentWriteState(currentState: Int, enabled: Boolean): Int? {
    val desired = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val effectiveCurrent = if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } else {
        currentState
    }
    return desired.takeIf { it != effectiveCurrent }
}

object SystemIntegrations {
    fun applyGlobalSearchState(context: Context) {
        val appContext = context.applicationContext
        val enabled = SinemaApp.instance.prefs.globalSearchEnabled
        val pm = appContext.packageManager
        listOf(
            ComponentName(appContext, GlobalSearchProvider::class.java),
            ComponentName(appContext, GlobalSearchActivity::class.java)
        ).forEach { component ->
            componentWriteState(pm.getComponentEnabledSetting(component), enabled)?.let {
                pm.setComponentEnabledSetting(component, it, PackageManager.DONT_KILL_APP)
            }
        }
    }

    fun reconcileAfterImport(context: Context, previousProfileId: String, previousServerUrl: String) {
        val prefs = SinemaApp.instance.prefs
        applyGlobalSearchState(context)
        if (!prefs.channelsEnabled) TvChannels.clearAll(context.applicationContext)
        if (prefs.activeProfileId != previousProfileId ||
            prefs.serverUrl.trimEnd('/') != previousServerUrl.trimEnd('/')
        ) {
            onActiveServerChanged(context) {}
        } else {
            SinemaApp.instance.refreshApi()
        }
    }

    fun onActiveServerChanged(context: Context, mutate: () -> Unit) {
        TvChannels.withSyncLock {
            mutate()
            SinemaApp.instance.refreshApi()
            TvChannels.clearAll(context.applicationContext)
        }
    }
}
