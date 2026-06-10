package com.sinema.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinema.SinemaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TvChannelInitReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != androidx.tvprovider.media.tv.TvContractCompat.ACTION_INITIALIZE_PROGRAMS) return

        // Only accept broadcasts from the system / launcher
        val callingUid = android.os.Binder.getCallingUid()
        if (callingUid != android.os.Process.SYSTEM_UID && callingUid != context.applicationInfo.uid) {
            return
        }

        val app = SinemaApp.instance
        if (!app.prefs.channelsEnabled) return

        val pending = goAsync()
        scope.launch {
            try {
                val continuePairs = app.api.findContinuePlaying()
                TvChannels.syncWatchNext(context, continuePairs)
                val recentScenes = app.api.findRecentScenes(20)
                TvChannels.syncRecentlyAdded(context, recentScenes)
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "TV channel init sync failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
