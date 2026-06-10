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
        api = SinemaApi(prefs.serverUrl.trimEnd('/'), prefs.apiKey, prefs.sessionCookie, prefs.authMode)
        configureApi()
    }

    fun refreshApi() {
        api.updateConfig(prefs.serverUrl, prefs.apiKey, prefs.sessionCookie, prefs.authMode)
        configureApi()
    }

    private fun configureApi() {
        api.stashUsername = prefs.stashUsername
        api.stashPassword = prefs.stashPassword
        api.onSessionRefreshed = { cookie -> prefs.sessionCookie = cookie }
    }

    companion object {
        lateinit var instance: SinemaApp
    }
}
