package com.sinema.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.os.Bundle
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class SetupActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private var httpServer: ServerSocket? = null
    private var serverThread: Thread? = null
    private var webSetupToken: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMenuScreen()
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
        
        val scrollView = ScrollView(this).apply {
            isFocusable = false
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        }
        
        setContentView(scrollView)
        stashBtn.requestFocus()
    }
    
    private fun showStashLoginScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 60, 80, 60)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        
        val title = TextView(this).apply {
            text = "Sign in with Stash"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
        
        layout.addView(TextView(this).apply {
            text = "Server URL:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        })
        
        val urlEdit = createEditText(app.prefs.serverUrl.takeIf { it.isNotBlank() } ?: "http://")
        layout.addView(urlEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })
        
        layout.addView(TextView(this).apply {
            text = "Username:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        })
        
        val usernameEdit = createEditText("")
        layout.addView(usernameEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })
        
        layout.addView(TextView(this).apply {
            text = "Password:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        })
        
        val passwordEdit = createEditText("").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(passwordEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
        
        val errorText = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFFF4444.toInt())
            visibility = android.view.View.GONE
        }
        layout.addView(errorText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })
        
        val signInBtn = createButton("Sign In") {}
        signInBtn.setOnClickListener {
            val serverUrl = normalizeServerUrl(urlEdit.text.toString())
            val username = usernameEdit.text.toString().trim()
            val password = passwordEdit.text.toString()
            
            if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
                errorText.text = "Please fill in all fields"
                errorText.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }
            
            errorText.visibility = android.view.View.GONE
            signInBtn.isEnabled = false
            signInBtn.text = "Signing in..."
            
            lifecycleScope.launch {
                try {
                    val sessionCookie = performStashLogin(serverUrl, username, password)
                    
                    withContext(Dispatchers.Main) {
                        showAuthModeChoice(serverUrl, username, password, sessionCookie)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorText.text = "Login failed: ${e.message}"
                        errorText.visibility = android.view.View.VISIBLE
                        signInBtn.isEnabled = true
                        signInBtn.text = "Sign In"
                    }
                }
            }
        }
        layout.addView(signInBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })
        
        val backBtn = createButton("Back") {
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
    
    private suspend fun performStashLogin(serverUrl: String, username: String, password: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
            
            // Login to get session cookie
            val loginBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            
            val loginRequest = Request.Builder()
                .url("$serverUrl/login")
                .post(loginBody)
                .build()
            
            val loginResponse = client.newCall(loginRequest).execute()
            
            // Stash returns 302 on success, 401 on failure
            if (loginResponse.code == 401) {
                throw Exception("Invalid username or password")
            }
            if (loginResponse.code !in listOf(302, 303, 307, 308, 200)) {
                throw Exception("Unexpected login response: ${loginResponse.code}")
            }
            
            // Extract session cookie
            val cookies = loginResponse.headers("Set-Cookie")
            val sessionCookie = cookies.firstOrNull { it.contains("session") }
                ?: throw Exception("No session cookie received")
            
            // Just need the cookie name=value part, not the attributes
            val cookieValue = sessionCookie.split(";").first()
            
            loginResponse.close()
            cookieValue
        }
    }
    
    private suspend fun generateApiKey(serverUrl: String, sessionCookie: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            
            val graphqlQuery = """{"query":"mutation { generateAPIKey(input: {}) }"}"""
            val graphqlBody = graphqlQuery.toRequestBody("application/json".toMediaType())
            
            val graphqlRequest = Request.Builder()
                .url("$serverUrl/graphql")
                .post(graphqlBody)
                .addHeader("Cookie", sessionCookie)
                .build()
            
            val graphqlResponse = client.newCall(graphqlRequest).execute()
            
            if (!graphqlResponse.isSuccessful) {
                throw Exception("Failed to generate API key: ${graphqlResponse.code}")
            }
            
            val responseBody = graphqlResponse.body?.string()
                ?: throw Exception("Empty response from server")
            
            graphqlResponse.close()
            
            // Extract API key from: {"data":{"generateAPIKey":"eyJhbGc..."}}
            val keyStart = responseBody.indexOf("\"generateAPIKey\":\"")
            if (keyStart == -1) throw Exception("Failed to extract API key")
            val start = keyStart + 18
            val end = responseBody.indexOf("\"", start)
            if (end == -1) throw Exception("Failed to extract API key")
            responseBody.substring(start, end)
        }
    }
    
    private fun showAuthModeChoice(serverUrl: String, username: String, password: String, sessionCookie: String) {
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
                    val apiKey = generateApiKey(serverUrl, sessionCookie)
                    
                    withContext(Dispatchers.Main) {
                        app.prefs.serverUrl = serverUrl
                        app.prefs.apiKey = apiKey
                        app.prefs.authMode = "apikey"
                        app.prefs.sessionCookie = ""
                        app.prefs.stashUsername = ""
                        app.prefs.stashPassword = ""
                        app.refreshApi()

                        Toast.makeText(this@SetupActivity, "API key generated! You're all set.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SetupActivity, MainActivity::class.java))
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SetupActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        apiKeyBtn.isEnabled = true
                        apiKeyBtn.text = "Generate New API Key"
                    }
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
            app.prefs.serverUrl = serverUrl
            app.prefs.sessionCookie = sessionCookie
            app.prefs.authMode = "session"
            app.prefs.apiKey = ""
            app.prefs.stashUsername = username
            app.prefs.stashPassword = password
            app.refreshApi()

            Toast.makeText(this, "Connected! You're all set.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
            
            val token = generateShortSetupToken()
            webSetupToken = token
            startHttpServer(serverUrl, token)
            
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
    
    private fun startHttpServer(serverUrl: String, setupToken: String) {
        stopHttpServer()
        
        serverThread = Thread {
            try {
                httpServer = ServerSocket(8888)
                
                while (!Thread.currentThread().isInterrupted) {
                    val client = httpServer?.accept() ?: break
                    
                    Thread clientHandler@{
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val writer = PrintWriter(client.getOutputStream(), true)
                            
                            val requestLine = reader.readLine() ?: return@clientHandler
                            
                            // Read headers to find Content-Length
                            var contentLength = 0
                            var line: String?
                            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                                }
                            }
                            
                            if (requestLine.startsWith("GET")) {
                                // Serve the HTML form
                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1">
                                        <title>Sinema Setup</title>
                                        <style>
                                            body { font-family: sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; background: #1b1b1b; color: #fff; }
                                            h1 { color: #2AABE0; }
                                            label { display: block; margin-top: 20px; margin-bottom: 8px; color: #ccc; }
                                            input { width: 100%; padding: 12px; font-size: 16px; background: #333; color: #fff; border: 1px solid #555; border-radius: 4px; box-sizing: border-box; }
                                            button { margin-top: 30px; width: 100%; padding: 14px; font-size: 18px; background: #2AABE0; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
                                            button:hover { background: #1a9bd0; }
                                        </style>
                                    </head>
                                    <body>
                                        <h1>Sinema Setup</h1>
                                        <form method="POST" action="/">
                                            <label for="serverUrl">Server URL:</label>
                                            <input type="text" id="serverUrl" name="serverUrl" value="$serverUrl" required>
                                            
                                            <label for="apiKey">API Key:</label>
                                            <input type="text" id="apiKey" name="apiKey" required>
                                            <input type="hidden" name="setupToken" value="$setupToken">
                                            
                                            <button type="submit">Save</button>
                                        </form>
                                    </body>
                                    </html>
                                """.trimIndent()
                                
                                writer.println("HTTP/1.1 200 OK")
                                writer.println("Content-Type: text/html")
                                writer.println("Content-Length: ${html.toByteArray().size}")
                                writer.println()
                                writer.println(html)
                            } else if (requestLine.startsWith("POST")) {
                                // Read POST body
                                val bodyChars = CharArray(contentLength)
                                var totalRead = 0
                                while (totalRead < contentLength) {
                                    val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                                    if (read <= 0) break
                                    totalRead += read
                                }
                                val body = String(bodyChars, 0, totalRead)
                                
                                // Parse form data
                                val params = body.split("&").associate {
                                    val parts = it.split("=", limit = 2)
                                    java.net.URLDecoder.decode(parts[0], "UTF-8") to 
                                    java.net.URLDecoder.decode(parts.getOrNull(1) ?: "", "UTF-8")
                                }
                                
                                val newServerUrl = params["serverUrl"]?.trim()?.trimEnd('/')
                                val apiKey = params["apiKey"]?.trim()
                                val postedToken = params["setupToken"]?.trim()
                                
                                if (postedToken != setupToken) {
                                    writer.println("HTTP/1.1 403 Forbidden")
                                    writer.println()
                                    writer.flush()
                                    client.close()
                                    return@clientHandler
                                }
                                
                                if (newServerUrl != null && apiKey != null && 
                                    newServerUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                                    
                                    runOnUiThread {
                                        app.prefs.serverUrl = newServerUrl
                                        app.prefs.apiKey = apiKey
                                        app.prefs.authMode = "apikey"
                                        app.prefs.sessionCookie = ""
                                        app.prefs.stashUsername = ""
                                        app.prefs.stashPassword = ""
                                        app.refreshApi()
                                        
                                        Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show()
                                        stopHttpServer()
                                        startActivity(Intent(this, MainActivity::class.java))
                                        finish()
                                    }
                                    
                                    val successHtml = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head><title>Success</title>
                                        <style>body{font-family:sans-serif;text-align:center;padding:50px;background:#1b1b1b;color:#fff;}h1{color:#2AABE0;}</style>
                                        </head>
                                        <body><h1>✓ Setup Complete!</h1><p>You can close this page now.</p></body>
                                        </html>
                                    """.trimIndent()
                                    
                                    writer.println("HTTP/1.1 200 OK")
                                    writer.println("Content-Type: text/html")
                                    writer.println("Content-Length: ${successHtml.toByteArray().size}")
                                    writer.println()
                                    writer.println(successHtml)
                                } else {
                                    writer.println("HTTP/1.1 400 Bad Request")
                                    writer.println()
                                }
                            }
                            
                            writer.flush()
                            client.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    e.printStackTrace()
                }
            }
        }.apply { start() }
    }
    

    private fun normalizeServerUrl(raw: String): String {
        val cleaned = raw.trim().trimEnd('/')
        if (cleaned.isBlank()) return ""
        val ok = cleaned.startsWith("http://") || cleaned.startsWith("https://")
        return if (ok) cleaned else ""
    }

    private fun generateShortSetupToken(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    private fun stopHttpServer() {
        try {
            serverThread?.interrupt()
            httpServer?.close()
            httpServer = null
            serverThread = null
            webSetupToken = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showManualEntryScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 60, 80, 60)
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        
        val title = TextView(this).apply {
            text = "Manual Setup"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
        
        layout.addView(TextView(this).apply {
            text = "Server URL:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        })
        
        val urlEdit = createEditText(app.prefs.serverUrl.takeIf { it.isNotBlank() } ?: "http://")
        layout.addView(urlEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })
        
        layout.addView(TextView(this).apply {
            text = "API Key:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        })
        
        val keyEdit = createEditText("")
        layout.addView(keyEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })
        
        val saveBtn = createButton("Save & Continue") {
            val serverUrl = normalizeServerUrl(urlEdit.text.toString())
            val apiKey = keyEdit.text.toString().trim()
            
            if (serverUrl.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@createButton
            }
            
            app.prefs.serverUrl = serverUrl
            app.prefs.apiKey = apiKey
            app.prefs.authMode = "apikey"
            app.prefs.sessionCookie = ""
            app.prefs.stashUsername = ""
            app.prefs.stashPassword = ""
            app.refreshApi()
            
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        layout.addView(saveBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })
        
        val backBtn = createButton("Back") {
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
    
    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 18f
            isFocusable = true
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 24, 32, 24)
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
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
            setBackgroundColor(0xFF333333.toInt())
            setPadding(24, 20, 24, 20)
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF555555.toInt() else 0xFF333333.toInt())
            }
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Don't allow back button to skip setup
        showMenuScreen()
    }
}
