package com.sinema.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.SinemaApp

class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, SettingsFragment())
                .commit()
        }
    }
}

class SettingsFragment : Fragment() {
    private lateinit var apiKeyEdit: EditText

    private val pinActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val app = SinemaApp.instance
        val data = result.data

        if (data?.getBooleanExtra("verify_for_removal", false) == true) {
            // Remove PIN after successful verification
            app.prefs.removePin()
            Toast.makeText(requireContext(), "PIN removed!", Toast.LENGTH_SHORT).show()
        } else {
            // PIN was set
            Toast.makeText(requireContext(), "PIN set successfully!", Toast.LENGTH_SHORT).show()
        }

        // Refresh the settings view
        requireActivity().recreate()
    }

    private val setupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val app = SinemaApp.instance
        val prefs = app.prefs

        // SetupActivity wrote credentials into legacy prefs; wrap them into a new profile
        val name = try { java.net.URI(prefs.serverUrl).host ?: prefs.serverUrl } catch (_: Exception) { prefs.serverUrl }
        val p = com.sinema.model.ServerProfile(
            name = name,
            serverUrl = prefs.serverUrl,
            apiKey = prefs.apiKey,
            sessionCookie = prefs.sessionCookie,
            authMode = prefs.authMode,
            stashUsername = prefs.stashUsername,
            stashPassword = prefs.stashPassword
        )
        prefs.profiles = prefs.profiles + p
        prefs.applyProfile(p)
        app.refreshApi()

        Toast.makeText(requireContext(), "Server added: ${p.name}", Toast.LENGTH_SHORT).show()
        requireActivity().recreate()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val app = SinemaApp.instance
        val ctx = requireContext()
        val padding = 48

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setBackgroundColor(0xFF1B1B1B.toInt())
        }

        val title = TextView(ctx).apply {
            text = "Sinema Settings"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(title)

        val urlLabel = TextView(ctx).apply {
            text = "\nServer URL:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        }
        layout.addView(urlLabel)

        val urlEdit = EditText(ctx).apply {
            setText(app.prefs.serverUrl)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF555555.toInt() else 0xFF333333.toInt())
            }
        }
        layout.addView(urlEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })

        // API Key Section
        val apiKeyLabel = TextView(ctx).apply {
            text = "\nStash API Key:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        }
        layout.addView(apiKeyLabel)

        apiKeyEdit = EditText(ctx).apply {
            setText(app.prefs.apiKey)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF555555.toInt() else 0xFF333333.toInt())
            }
            visibility = View.GONE
        }

        val apiKeyToggleBtn = Button(ctx).apply {
            text = if (app.prefs.apiKey.isNotEmpty()) "Change API Key" else "Set API Key"
            isFocusable = true
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener {
                if (apiKeyEdit.visibility == View.VISIBLE) {
                    apiKeyEdit.visibility = View.GONE
                    this.text = if (app.prefs.apiKey.isNotEmpty()) "Change API Key" else "Set API Key"
                } else {
                    apiKeyEdit.visibility = View.VISIBLE
                    this.text = "Hide API Key"
                    apiKeyEdit.requestFocus()
                }
            }
        }
        layout.addView(apiKeyToggleBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8; bottomMargin = 8 })

        layout.addView(apiKeyEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })

        // PIN Management Section
        val pinSectionLabel = TextView(ctx).apply {
            text = "\nPIN Lock:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        }
        layout.addView(pinSectionLabel)

        val pinStatusText = TextView(ctx).apply {
            text = if (app.prefs.hasPinSet()) "PIN is currently set" else "No PIN set"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(pinStatusText)

        if (app.prefs.hasPinSet()) {
            val removePinBtn = Button(ctx).apply {
                text = "Remove PIN"
                isFocusable = true
                setBackgroundColor(0xFF333333.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnFocusChangeListener { _, hasFocus ->
                    setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
                }
                setOnClickListener {
                    promptForCurrentPinAndRemove()
                }
            }
            layout.addView(removePinBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16; bottomMargin = 16 })
        } else {
            val setPinBtn = Button(ctx).apply {
                text = "Set PIN"
                isFocusable = true
                setBackgroundColor(0xFF333333.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnFocusChangeListener { _, hasFocus ->
                    setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
                }
                setOnClickListener {
                    val intent = Intent(requireContext(), PinActivity::class.java)
                    intent.putExtra("setup_mode", true)
                    pinActivityLauncher.launch(intent)
                }
            }
            layout.addView(setPinBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16; bottomMargin = 16 })
        }

        ServerProfileSection.build(ctx, layout) {
            val intent = Intent(ctx, SetupActivity::class.java)
            intent.putExtra("add_profile", true)
            setupLauncher.launch(intent)
        }

        val pm = ctx.packageManager
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
            val channelsToggle = Button(ctx).apply {
                text = if (app.prefs.channelsEnabled) "Android TV Channels: ON" else "Android TV Channels: OFF"
                isFocusable = true
                setBackgroundColor(0xFF333333.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnFocusChangeListener { _, hasFocus ->
                    setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
                }
                setOnClickListener {
                    val newState = !app.prefs.channelsEnabled
                    app.prefs.channelsEnabled = newState
                    this.text = if (newState) "Android TV Channels: ON" else "Android TV Channels: OFF"
                    if (!newState) {
                        com.sinema.util.TvChannels.clearAll(ctx)
                    }
                    Toast.makeText(ctx, "Channels ${if (newState) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(channelsToggle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 })
        }

        val saveBtn = Button(ctx).apply {
            text = "Save Settings"
            isFocusable = true
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener {
                app.prefs.serverUrl = urlEdit.text.toString().trim()
                if (apiKeyEdit.visibility == View.VISIBLE) {
                    app.prefs.apiKey = apiKeyEdit.text.toString().trim()
                }
                // Also update the active profile so the change survives switches.
                // If activeProfileId is stale, update the profile with matching ID directly.
                val activeId = app.prefs.activeProfileId
                if (activeId.isNotBlank()) {
                    app.prefs.profiles = app.prefs.profiles.map {
                        if (it.id == activeId) it.copy(
                            serverUrl = app.prefs.serverUrl,
                            apiKey = app.prefs.apiKey,
                            sessionCookie = app.prefs.sessionCookie,
                            authMode = app.prefs.authMode,
                            stashUsername = app.prefs.stashUsername,
                            stashPassword = app.prefs.stashPassword
                        ) else it
                    }
                }
                app.refreshApi()
                Toast.makeText(ctx, "Settings saved!", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
        layout.addView(saveBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })

        // Wrap in ScrollView for D-pad scrolling
        val scrollView = ScrollView(ctx).apply {
            isFocusable = false
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(layout)
        }

        // Request focus on first interactive element
        urlEdit.requestFocus()

        return scrollView
    }
    
    private fun promptForCurrentPinAndRemove() {
        val intent = Intent(requireContext(), PinActivity::class.java)
        intent.putExtra("verify_for_removal", true)
        pinActivityLauncher.launch(intent)
    }
}
