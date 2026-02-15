package com.sinema

import android.app.Application
import com.sinema.api.SinemaApi
import com.sinema.util.Prefs

class SinemaApp : Application() {
    lateinit var prefs: Prefs
    lateinit var api: SinemaApi
    var pinVerifiedThisSession = false
    var updateCheckedThisSession = false

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
