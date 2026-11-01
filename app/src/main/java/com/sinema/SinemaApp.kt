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
        // FLAG_SECURE must cover every activity, including ones opened before
        // the setting flips — re-apply on each start.
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                applySecureFlag(activity)
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun applySecureFlag(activity: android.app.Activity) {
        try {
            val secure = prefs.blockScreenshots
            val flags = activity.window.attributes.flags
            val hasFlag = (flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
            if (secure && !hasFlag) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else if (!secure && hasFlag) {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (e: Exception) {
            android.util.Log.w("Sinema", "Could not apply FLAG_SECURE", e)
        }
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
