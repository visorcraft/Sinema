package com.sinema.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class SetupActivity : SinemaActivity() {
    private val app get() = SinemaApp.instance
    private var webSetupServer: WebSetupServer? = null
    private val isAddProfile get() = intent.getBooleanExtra("add_profile", false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopHttpServer()
                showMenuScreen()
            }
        })
        showMenuScreen()
    }

    private fun finishWithSuccess() {
        if (isAddProfile) {
            setResult(RESULT_OK)
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopHttpServer()
    }
    
    private fun showMenuScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 60, 80, 60)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        
        val title = TextView(this).apply {
            text = "Welcome to Sinema"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })
        
        val subtitle = TextView(this).apply {
            text = "Choose a setup method:"
            textSize = 20f
            setTextColor(0xFFCCCCCC.toInt())
        }
        layout.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 })
        
        val stashBtn = createButton("Sign in with Stash") {
            showStashLoginScreen()
        }
        layout.addView(stashBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 })
        
        val webBtn = createButton("Web Setup") {
            showWebSetupScreen()
        }
        layout.addView(webBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 })
        
        val manualBtn = createButton("Enter API Key Manually") {
            showManualEntryScreen()
        }
        layout.addView(manualBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        if (isAddProfile) {
            val cancelBtn = createButton("Cancel") {
                setResult(RESULT_CANCELED)
                finish()
            }
            layout.addView(cancelBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 40 })
        }

        val scrollView = ScrollView(this).apply {
            isFocusable = false
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        }
        
        setContentView(scrollView)
        stashBtn.requestFocus()
    }
    
    private fun showStashLoginScreen() {
        val layout = setupColumn()
        layout.addTitle("Sign in with Stash")

        layout.addLabel("Server URL:")
        val urlEdit = createEditText(app.prefs.serverUrl.takeIf { it.isNotBlank() } ?: "http://")
        layout.addSetupField(urlEdit)

        layout.addLabel("Username:")
        val usernameEdit = createEditText("")
        layout.addSetupField(usernameEdit)

        layout.addLabel("Password:")
        val passwordEdit = createEditText("").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addSetupField(passwordEdit)

        val selfSignedCheck = android.widget.CheckBox(this).apply {
            text = "Trust self-signed certificates"
            textSize = 15f
            setTextColor(0xFFCCCCCC.toInt())
            isChecked = app.prefs.setupAllowSelfSigned
        }
        layout.addSetupField(selfSignedCheck, bottomMargin = 32)

        val errorText = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFFF4444.toInt())
            visibility = android.view.View.GONE
        }
        layout.addSetupField(errorText, bottomMargin = 16)

        val signInBtn = createButton("Sign In") {}

        fun attemptSignIn(serverUrl: String, username: String, password: String, trustSelfSigned: Boolean) {
            errorText.visibility = android.view.View.GONE
            signInBtn.isEnabled = false
            signInBtn.text = "Signing in..."
            lifecycleScope.launch {
                try {
                    val sessionCookie = performStashLogin(serverUrl, username, password, trustSelfSigned)
                    showAuthModeChoice(serverUrl, username, password, sessionCookie, trustSelfSigned)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorText.text = "Login failed: ${e.message}"
                    errorText.visibility = android.view.View.VISIBLE
                    signInBtn.isEnabled = true
                    signInBtn.text = "Sign In"
                }
            }
        }

        signInBtn.setOnClickListener {
            val serverUrl = normalizeServerUrl(urlEdit.text.toString())
            val username = usernameEdit.text.toString().trim()
            val password = passwordEdit.text.toString()

            if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
                errorText.text = "Please fill in all fields"
                errorText.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            if (selfSignedCheck.isChecked) {
                confirmSelfSignedTrust(serverUrl) { attemptSignIn(serverUrl, username, password, true) }
            } else {
                attemptSignIn(serverUrl, username, password, false)
            }
        }
        layout.addSetupButton(signInBtn)

        layout.addSetupButton(createButton("Back") { showMenuScreen() }, bottomMargin = 0)

        finishSetupScreen(layout, urlEdit)
    }
    
    private suspend fun performStashLogin(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean = false
    ): String {
        return withContext(Dispatchers.IO) {
            val client = with(com.sinema.util.TlsTrust) {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .let { if (trustSelfSigned) it.trustAllCertificates() else it }
                    .build()
            }
            when (val result = com.sinema.api.StashAuth.login(client, serverUrl, username, password)) {
                is com.sinema.api.LoginResult.Success -> result.cookie
                com.sinema.api.LoginResult.InvalidCredentials -> throw Exception("Invalid username or password")
                com.sinema.api.LoginResult.MissingCookie -> throw Exception("No session cookie received")
                is com.sinema.api.LoginResult.HttpFailure ->
                    throw Exception("Unexpected login response: ${result.code}")
                is com.sinema.api.LoginResult.TransportFailure ->
                    throw Exception("Login failed: ${result.cause.message ?: "network error"}")
            }
        }
    }
    
    private suspend fun generateApiKey(
        serverUrl: String,
        sessionCookie: String,
        trustSelfSigned: Boolean = false
    ): String {
        return withContext(Dispatchers.IO) {
            val client = with(com.sinema.util.TlsTrust) {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .let { if (trustSelfSigned) it.trustAllCertificates() else it }
                    .build()
            }
            com.sinema.api.StashAuth.generateApiKey(client, serverUrl, sessionCookie)
        }
    }
    
    private fun showAuthModeChoice(serverUrl: String, username: String, password: String, sessionCookie: String, trustSelfSigned: Boolean) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 60, 80, 60)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }

        val title = TextView(this).apply {
            text = "Login Successful!"
            textSize = 28f
            setTextColor(0xFF2AABE0.toInt())
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })

        val subtitle = TextView(this).apply {
            text = "How would you like Sinema to stay connected?"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 })

        // Option 1: Generate API Key
        val apiKeyTitle = TextView(this).apply {
            text = "Permanent Sign-In"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(apiKeyTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 })

        val apiKeyDesc = TextView(this).apply {
            text = "Generates a dedicated API key for Sinema. You'll stay signed in permanently and never need to log in again.\n\n⚠️  This will invalidate any existing Stash API key. If other apps or scripts use your current API key, they will stop working."
            textSize = 15f
            setTextColor(0xFFAAAAAA.toInt())
        }
        layout.addView(apiKeyDesc, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 })

        val apiKeyBtn = createButton("Generate New API Key") {}
        apiKeyBtn.setOnClickListener {
            apiKeyBtn.isEnabled = false
            apiKeyBtn.text = "Generating..."
            
            lifecycleScope.launch {
                try {
                    val apiKey = generateApiKey(serverUrl, sessionCookie, trustSelfSigned)

                    com.sinema.util.SystemIntegrations.onActiveServerChanged(this@SetupActivity) {
                        app.prefs.serverUrl = serverUrl
                        app.prefs.apiKey = apiKey
                        app.prefs.authMode = "apikey"
                        app.prefs.sessionCookie = ""
                        app.prefs.stashUsername = ""
                        app.prefs.stashPassword = ""
                        app.prefs.setupAllowSelfSigned = trustSelfSigned
                        app.prefs.migrateToProfilesIfNeeded()
                    }

                    Toast.makeText(this@SetupActivity, "API key generated! You're all set.", Toast.LENGTH_SHORT).show()
                    finishWithSuccess()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(this@SetupActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    apiKeyBtn.isEnabled = true
                    apiKeyBtn.text = "Generate New API Key"
                }
            }
        }
        layout.addView(apiKeyBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 })

        // Divider
        layout.addView(android.view.View(this).apply {
            setBackgroundColor(0xFF444444.toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { bottomMargin = 40 })

        // Option 2: Session Cookie
        val sessionTitle = TextView(this).apply {
            text = "Session Sign-In"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(sessionTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 })

        val sessionDesc = TextView(this).apply {
            text = "Uses your login session to connect. Your existing API key stays untouched.\n\nSinema will save your credentials and automatically re-login when the session expires. You may need to sign in again if your Stash password changes."
            textSize = 15f
            setTextColor(0xFFAAAAAA.toInt())
        }
        layout.addView(sessionDesc, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 })

        val sessionBtn = createButton("Use Session Sign-In") {
            com.sinema.util.SystemIntegrations.onActiveServerChanged(this@SetupActivity) {
                app.prefs.serverUrl = serverUrl
                app.prefs.sessionCookie = sessionCookie
                app.prefs.authMode = "session"
                app.prefs.apiKey = ""
                app.prefs.stashUsername = username
                app.prefs.stashPassword = password
                app.prefs.setupAllowSelfSigned = trustSelfSigned
                app.prefs.migrateToProfilesIfNeeded()
            }

            Toast.makeText(this, "Connected! You're all set.", Toast.LENGTH_SHORT).show()
            finishWithSuccess()
        }
        layout.addView(sessionBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })

        val backBtn = createButton("Back") {
            showStashLoginScreen()
        }
        layout.addView(backBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val scrollView = ScrollView(this).apply {
            isFocusable = false
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        }

        setContentView(scrollView)
        sessionBtn.requestFocus()
    }

    private fun showWebSetupScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 60, 80, 60)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        
        val title = TextView(this).apply {
            text = "Web Setup"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
        
        layout.addView(TextView(this).apply {
            text = "Server URL (required for web form):"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        })
        
        val urlEdit = createEditText(app.prefs.serverUrl.takeIf { it.isNotBlank() } ?: "http://")
        layout.addView(urlEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })
        
        val instructionText = TextView(this).apply {
            text = ""
            textSize = 20f
            setTextColor(0xFF2AABE0.toInt())
            visibility = android.view.View.GONE
        }
        layout.addView(instructionText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
        
        val startBtn = createButton("Start Web Setup") {}
        startBtn.setOnClickListener {
            val serverUrl = normalizeServerUrl(urlEdit.text.toString())
            
            if (serverUrl.isEmpty()) {
                Toast.makeText(this, "Please enter a server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            app.prefs.serverUrl = serverUrl
            
            val ip = getLocalIpAddress()
            if (ip == null) {
                Toast.makeText(this, "Could not determine IP address", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            val token = generateSetupToken()
            if (!startHttpServer(serverUrl, token)) return@setOnClickListener
            
            instructionText.text = "Open http://$ip:8888 on your phone or computer\nSetup code: $token"
            instructionText.visibility = android.view.View.VISIBLE
            startBtn.visibility = android.view.View.GONE
            urlEdit.isEnabled = false
        }
        layout.addView(startBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })
        
        val backBtn = createButton("Back") {
            stopHttpServer()
            showMenuScreen()
        }
        layout.addView(backBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        val scrollView = ScrollView(this).apply {
            isFocusable = false
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        }
        
        setContentView(scrollView)
        urlEdit.requestFocus()
    }
    
    private fun getLocalIpAddress(): String? {
        try {
            // Prefer the currently active network's LinkProperties (covers Wi‑Fi and Ethernet)
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val linkProps = if (network != null) cm.getLinkProperties(network) else null

            linkProps?.linkAddresses
                ?.map(LinkAddress::getAddress)
                ?.firstOrNull { addr ->
                    !addr.isLoopbackAddress && addr is java.net.Inet4Address
                }
                ?.let { return it.hostAddress }

            // Fallback: scan all interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private fun startHttpServer(serverUrl: String, setupToken: String): Boolean {
        stopHttpServer()
        return try {
            webSetupServer = WebSetupServer.start(serverUrl, setupToken, onComplete = { newServerUrl, apiKey ->
                runOnUiThread {
                    if (webSetupServer == null || isFinishing || isDestroyed) return@runOnUiThread
                    com.sinema.util.SystemIntegrations.onActiveServerChanged(this@SetupActivity) {
                        app.prefs.serverUrl = newServerUrl
                        app.prefs.apiKey = apiKey
                        app.prefs.authMode = "apikey"
                        app.prefs.sessionCookie = ""
                        app.prefs.stashUsername = ""
                        app.prefs.stashPassword = ""
                        app.prefs.migrateToProfilesIfNeeded()
                    }

                    Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show()
                    stopHttpServer()
                    finishWithSuccess()
                }
            })
            true
        } catch (e: java.io.IOException) {
            webSetupServer = null
            android.util.Log.w("Sinema", "Could not start Web Setup server", e)
            Toast.makeText(this, "Could not start Web Setup: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
    

    private fun normalizeServerUrl(raw: String): String {
        val cleaned = raw.trim().trimEnd('/')
        if (cleaned.isBlank()) return ""
        val ok = cleaned.startsWith("http://") || cleaned.startsWith("https://")
        return if (ok) cleaned else ""
    }

    /**
     * MITM warning shown before any setup action proceeds with the
     * self-signed trust checkbox ticked. Mirrors the Settings toggle.
     */
    private fun confirmSelfSignedTrust(serverUrl: String, onEnable: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Trust self-signed certificates")
            .setMessage(
                "This disables TLS certificate verification for $serverUrl. " +
                    "Only continue on private networks you control — a " +
                    "man-in-the-middle could otherwise impersonate your server."
            )
            .setPositiveButton("Enable & Continue") { _, _ -> onEnable() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun stopHttpServer() {
        try {
            webSetupServer?.close()
            webSetupServer = null
        } catch (e: Exception) {
            android.util.Log.w("Sinema", "Could not stop Web Setup server", e)
        }
    }
    
    private fun showManualEntryScreen() {
        val layout = setupColumn()
        layout.addTitle("Manual Setup")

        layout.addLabel("Server URL:")
        val urlEdit = createEditText(app.prefs.serverUrl.takeIf { it.isNotBlank() } ?: "http://")
        layout.addSetupField(urlEdit)

        layout.addLabel("API Key:")
        val keyEdit = createEditText("")
        layout.addSetupField(keyEdit)

        val selfSignedCheck = android.widget.CheckBox(this).apply {
            text = "Trust self-signed certificates"
            textSize = 15f
            setTextColor(0xFFCCCCCC.toInt())
            isChecked = app.prefs.setupAllowSelfSigned
        }
        layout.addSetupField(selfSignedCheck, bottomMargin = 32)

        fun performManualSave() {
            val serverUrl = normalizeServerUrl(urlEdit.text.toString())
            val apiKey = keyEdit.text.toString().trim()

            if (serverUrl.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this@SetupActivity, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return
            }

            com.sinema.util.SystemIntegrations.onActiveServerChanged(this@SetupActivity) {
                app.prefs.serverUrl = serverUrl
                app.prefs.apiKey = apiKey
                app.prefs.authMode = "apikey"
                app.prefs.sessionCookie = ""
                app.prefs.stashUsername = ""
                app.prefs.stashPassword = ""
                app.prefs.setupAllowSelfSigned = selfSignedCheck.isChecked
                app.prefs.migrateToProfilesIfNeeded()
            }

            Toast.makeText(this@SetupActivity, "Settings saved!", Toast.LENGTH_SHORT).show()
            finishWithSuccess()
        }

        val saveBtn = createButton("Save & Continue") {
            if (selfSignedCheck.isChecked) {
                val serverUrl = normalizeServerUrl(urlEdit.text.toString())
                if (serverUrl.isEmpty()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@createButton
                }
                confirmSelfSignedTrust(serverUrl) { performManualSave() }
                return@createButton
            }
            performManualSave()
        }
        layout.addSetupButton(saveBtn)

        layout.addSetupButton(createButton("Back") { showMenuScreen() }, bottomMargin = 0)

        finishSetupScreen(layout, urlEdit)
    }
    
    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 18f
            isFocusable = true
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 24, 32, 24)
            bindFocusBackground(normal = 0xFF333333.toInt())
            setOnClickListener { onClick() }
        }
    }

    private fun createEditText(initialText: String): EditText {
        return EditText(this).apply {
            setText(initialText)
            textSize = 18f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 20, 24, 20)
            bindFocusBackground(normal = 0xFF333333.toInt(), focused = 0xFF555555.toInt())
        }
    }

    /**
     * Shared scaffolding for the setup screens: padded dark column
     * ([setupColumn]), title ([addTitle]), and scroll wrapper
     * ([finishSetupScreen]). Screen bodies add fields with
     * [LinearLayout.addLabel] / [addSetupField] / [addSetupButton].
     */
    private fun setupColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(80, 60, 80, 60)
        setBackgroundColor(0xFF1B1B1B.toInt())
    }

    private fun LinearLayout.addTitle(text: String) {
        addView(TextView(this@SetupActivity).apply {
            this.text = text
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
    }

    private fun finishSetupScreen(layout: LinearLayout, focus: View? = null) {
        setContentView(ScrollView(this).apply {
            isFocusable = false
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        })
        focus?.requestFocus()
    }

    private fun LinearLayout.addLabel(text: String, bottomMargin: Int = 0) {
        addView(TextView(this@SetupActivity).apply {
            this.text = text
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.bottomMargin = bottomMargin })
    }

    private fun LinearLayout.addSetupField(view: View, bottomMargin: Int = 24) {
        addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.bottomMargin = bottomMargin })
    }

    private fun LinearLayout.addSetupButton(button: Button, bottomMargin: Int = 16) {
        addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.bottomMargin = bottomMargin })
    }
}
