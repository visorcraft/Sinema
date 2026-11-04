package com.sinema

import android.app.Application
import com.sinema.api.SinemaApi
import com.sinema.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SinemaApp : Application() {
    lateinit var prefs: Prefs
    @Volatile lateinit var api: SinemaApi
    var pinVerifiedThisSession = false
    /** Decoy PIN unlocked the blank shell for this process. */
    var decoySession = false
    var updateCheckedThisSession = false
    @Volatile var lastInteractionMs: Long = android.os.SystemClock.elapsedRealtime()
        private set

    fun noteUserInteraction() {
        lastInteractionMs = android.os.SystemClock.elapsedRealtime()
    }

    @Volatile private var internalActivityResume = false
    private var lastResumedActivityName: String? = null

    fun consumeInternalActivityResume(): Boolean {
        val result = internalActivityResume
        internalActivityResume = false
        return result
    }

    /** Small ring buffer of recent user-facing errors for the server dashboard. */
    private val recentErrorsBuffer = ArrayDeque<String>()
    val recentErrors: List<String> get() = synchronized(recentErrorsBuffer) { recentErrorsBuffer.toList() }

    fun noteError(message: String) {
        synchronized(recentErrorsBuffer) {
            if (recentErrorsBuffer.lastOrNull() == message) return
            recentErrorsBuffer.addLast(message.take(200))
            while (recentErrorsBuffer.size > 20) recentErrorsBuffer.removeFirst()
        }
    }

    // For work that must outlive an activity, e.g. saving playback state
    // while the playback activity is being destroyed.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        prefs.migrateToProfilesIfNeeded()
        api = buildConfiguredApi()
        configureGlideTransport(api)
        com.sinema.util.SystemIntegrations.applyGlobalSearchState(this)
        // FLAG_SECURE must cover every activity, including ones opened before
        // the setting flips — re-apply on each start.
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                applySecureFlag(activity)
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {
                internalActivityResume = lastResumedActivityName?.let { it != activity.javaClass.name } == true
                lastResumedActivityName = activity.javaClass.name
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                noteUserInteraction()
            }
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
        api = buildConfiguredApi()
        configureGlideTransport(api)
    }

    private fun buildConfiguredApi(): SinemaApi {
        val originatingProfileId = prefs.activeProfileId
        return SinemaApi(
            prefs.serverUrl.trimEnd('/'), prefs.apiKey, prefs.sessionCookie, prefs.authMode,
            allowSelfSigned = prefs.activeProfile?.allowSelfSigned == true
        ).also { newApi ->
            newApi.stashUsername = prefs.stashUsername
            newApi.stashPassword = prefs.stashPassword
            newApi.onSessionRefreshed = { cookie ->
                synchronized(prefs) {
                    val result = com.sinema.util.routeSessionRefresh(
                        profiles = prefs.profiles,
                        activeProfileId = prefs.activeProfileId,
                        originatingProfileId = originatingProfileId,
                        cookie = cookie
                    )
                    prefs.profiles = result.profiles
                    if (result.updateActiveCookie) prefs.sessionCookie = cookie
                }
            }
        }
    }

    /**
     * Route Glide image loads through the API's OkHttp client so images
     * from a self-signed server load once the profile opts in. Replaced on
     * every refresh because OkHttpClient instances are immutable.
     */
    private fun configureGlideTransport(api: SinemaApi) {
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

    companion object {
        lateinit var instance: SinemaApp
    }
}
