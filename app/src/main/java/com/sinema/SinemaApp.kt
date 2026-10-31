package com.sinema

import android.app.Application
import com.sinema.api.SinemaApi
import com.sinema.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SinemaApp : Application() {
    lateinit var prefs: Prefs
    lateinit var api: SinemaApi
    var pinVerifiedThisSession = false
    var updateCheckedThisSession = false

    // For work that must outlive an activity, e.g. saving playback state
    // while the playback activity is being destroyed.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        prefs.migrateToProfilesIfNeeded()
        api = SinemaApi(
            prefs.serverUrl.trimEnd('/'), prefs.apiKey, prefs.sessionCookie, prefs.authMode,
            allowSelfSigned = prefs.activeProfile?.allowSelfSigned == true
        )
        configureApi()
        configureGlideTransport()
    }

    fun refreshApi() {
        api.updateConfig(
            prefs.serverUrl, prefs.apiKey, prefs.sessionCookie, prefs.authMode,
            allowSelfSigned = prefs.activeProfile?.allowSelfSigned == true
        )
        configureApi()
        configureGlideTransport()
    }

    /**
     * Route Glide image loads through the API's OkHttp client so images
     * from a self-signed server load once the profile opts in. Replaced on
     * every refresh because OkHttpClient instances are immutable.
     */
    private fun configureGlideTransport() {
        try {
            com.bumptech.glide.Glide.get(this).registry.replace(
                com.bumptech.glide.load.model.GlideUrl::class.java,
                java.io.InputStream::class.java,
                com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader.Factory(api.okHttpClient())
            )
        } catch (e: Exception) {
            android.util.Log.w("Sinema", "Could not install Glide OkHttp transport", e)
        }
    }

    private fun configureApi() {
        api.stashUsername = prefs.stashUsername
        api.stashPassword = prefs.stashPassword
        api.onSessionRefreshed = { cookie ->
            prefs.sessionCookie = cookie
            // Also update the active profile's cookie so it survives profile switches.
            // Synchronize against UI-driven mutations via the prefs object monitor.
            synchronized(prefs) {
                val activeId = prefs.activeProfileId
                if (activeId.isNotBlank()) {
                    prefs.profiles = prefs.profiles.map {
                        if (it.id == activeId) it.copy(sessionCookie = cookie) else it
                    }
                }
            }
        }
    }

    companion object {
        lateinit var instance: SinemaApp
    }
}
