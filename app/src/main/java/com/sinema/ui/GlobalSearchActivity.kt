package com.sinema.ui

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sinema.SinemaApp

/**
 * Receives ACTION_SEARCH from the Android TV system search dialog ("search
 * in Sinema") and forwards the query into the in-app search screen. Like
 * DeepLinkActivity it enforces the setup and PIN gates before showing any
 * library UI. Ships disabled in the manifest; enabled together with
 * GlobalSearchProvider from Settings → Global Search.
 */
class GlobalSearchActivity : ComponentActivity() {
    private var pendingQuery: String? = null

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            forwardQuery()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent?.getStringExtra(SearchManager.QUERY)
        if (query.isNullOrBlank()) {
            finish()
            return
        }
        pendingQuery = query

        val app = SinemaApp.instance
        if (!app.prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        if (app.prefs.hasPinSet() && !app.pinVerifiedThisSession) {
            pinLauncher.launch(Intent(this, PinActivity::class.java))
            return
        }
        forwardQuery()
    }

    private fun forwardQuery() {
        val query = pendingQuery
        if (!query.isNullOrBlank()) {
            startActivity(
                Intent(this, SearchActivity::class.java)
                    .putExtra(EXTRA_PREFILL_QUERY, query)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_PREFILL_QUERY = "prefill_query"
    }
}
