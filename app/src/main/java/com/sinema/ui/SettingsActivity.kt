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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.PlaybackAspectRatio
import com.sinema.model.PlaybackStreamQuality
import com.sinema.model.ServerProfile

internal enum class SettingsCategory(val title: String) {
    SERVER("Server"),
    HOME("Home"),
    PLAYBACK("Playback"),
    PRIVACY("Privacy"),
    ACCESSIBILITY("Accessibility"),
    SYSTEM("System")
}

internal data class ServerSettingsDraft(
    private val originalUrl: String,
    private val originalApiKey: String,
    var url: String = originalUrl,
    var apiKey: String = originalApiKey
) {
    val hasChanges: Boolean
        get() = normalizedUrl() != originalUrl.trim() || normalizedApiKey() != originalApiKey.trim()

    fun normalizedUrl(): String = url.trim()
    fun normalizedApiKey(): String = apiKey.trim()
}

internal fun shouldResetServerSettingsDraft(
    draftProfileId: String?,
    activeProfileId: String
): Boolean = draftProfileId != activeProfileId

internal class SettingsDraftViewModel : ViewModel() {
    var serverDraft: ServerSettingsDraft? = null
    var draftProfileId: String? = null
    var apiKeyVisible = false
}

class SettingsActivity : SinemaActivity() {
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
    private val app get() = SinemaApp.instance
    private lateinit var container: FrameLayout
    private lateinit var draftState: SettingsDraftViewModel
    private var serverDraft: ServerSettingsDraft
        get() = checkNotNull(draftState.serverDraft)
        set(value) { draftState.serverDraft = value }
    private var apiKeyVisible: Boolean
        get() = draftState.apiKeyVisible
        set(value) { draftState.apiKeyVisible = value }
    private var currentCategory: SettingsCategory? = null
    private var returnFocusCategory: SettingsCategory? = null
    private var discardDialog: AlertDialog? = null
    private var qualityDialog: AlertDialog? = null
    private var aspectDialog: AlertDialog? = null

    private val pinActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val data = result.data
        if (data?.getBooleanExtra("verify_for_removal", false) == true) {
            app.prefs.removePin()
            Toast.makeText(requireContext(), "PIN removed!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "PIN set successfully!", Toast.LENGTH_SHORT).show()
        }
        requireActivity().recreate()
    }

    private val setupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val prefs = app.prefs
        val name = try {
            java.net.URI(prefs.serverUrl).host ?: prefs.serverUrl
        } catch (_: Exception) {
            prefs.serverUrl
        }
        val profile = ServerProfile(
            name = name,
            serverUrl = prefs.serverUrl,
            apiKey = prefs.apiKey,
            sessionCookie = prefs.sessionCookie,
            authMode = prefs.authMode,
            stashUsername = prefs.stashUsername,
            stashPassword = prefs.stashPassword,
            allowSelfSigned = prefs.setupAllowSelfSigned
        )
        prefs.profiles = prefs.profiles + profile
        prefs.applyProfile(profile)
        prefs.setupAllowSelfSigned = false
        app.refreshApi()
        serverDraft = ServerSettingsDraft(prefs.serverUrl, prefs.apiKey)
        draftState.draftProfileId = prefs.activeProfileId

        Toast.makeText(requireContext(), "Server added: ${profile.name}", Toast.LENGTH_SHORT).show()
        requireActivity().recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draftState = ViewModelProvider(this)[SettingsDraftViewModel::class.java]
        ensureServerDraftForActiveProfile(savedInstanceState?.getString(KEY_SERVER_URL))
        currentCategory = categoryNamed(savedInstanceState?.getString(KEY_CATEGORY))
        returnFocusCategory = categoryNamed(savedInstanceState?.getString(KEY_RETURN_FOCUS))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext()).apply {
        container = this
        setBackgroundColor(BACKGROUND_COLOR)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        currentCategory != null -> showCategories()
                        serverDraft.hasChanges -> confirmDiscardServerChanges()
                        else -> {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        )
        currentCategory?.let(::renderCategory) ?: showCategories()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SERVER_URL, serverDraft.url)
        outState.putString(KEY_CATEGORY, currentCategory?.name)
        outState.putString(KEY_RETURN_FOCUS, returnFocusCategory?.name)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        dismissSettingsDialogs()
        super.onPause()
    }

    override fun onStop() {
        dismissSettingsDialogs()
        super.onStop()
    }

    private fun dismissSettingsDialogs() {
        discardDialog?.dismiss()
        discardDialog = null
        qualityDialog?.dismiss()
        qualityDialog = null
        aspectDialog?.dismiss()
        aspectDialog = null
    }

    private fun categoryNamed(name: String?): SettingsCategory? =
        SettingsCategory.entries.firstOrNull { it.name == name }

    private fun ensureServerDraftForActiveProfile(savedUrl: String? = null) {
        val hasDraft = draftState.serverDraft != null
        if (hasDraft && !shouldResetServerSettingsDraft(
                draftState.draftProfileId,
                app.prefs.activeProfileId
            )
        ) return
        serverDraft = ServerSettingsDraft(
            originalUrl = app.prefs.serverUrl,
            originalApiKey = app.prefs.apiKey,
            url = if (hasDraft) app.prefs.serverUrl else savedUrl ?: app.prefs.serverUrl
        )
        draftState.draftProfileId = app.prefs.activeProfileId
    }

    private fun showCategories() {
        currentCategory = null
        var firstButton: Button? = null
        var focusButton: Button? = null
        showContent(
            "Settings",
            "Choose a category. Server edits are kept while you move between categories."
        ) { layout ->
            SettingsCategory.entries.forEach { category ->
                val button = addButton(
                    layout,
                    "${category.title}\n${categorySummary(category)}"
                ) {
                    returnFocusCategory = category
                    currentCategory = category
                    renderCategory(category)
                }
                if (firstButton == null) firstButton = button
                if (category == returnFocusCategory) focusButton = button
            }
            focusButton ?: firstButton
        }
    }

    private fun categorySummary(category: SettingsCategory): String = when (category) {
        SettingsCategory.SERVER -> {
            val profile = app.prefs.profiles.firstOrNull { it.id == app.prefs.activeProfileId }?.name
                ?: app.prefs.serverUrl
            profile + if (serverDraft.hasChanges) " · Unsaved changes" else ""
        }
        SettingsCategory.HOME ->
            "Rows, shortcuts, artwork · Ambient ${onOff(app.prefs.ambientGallery)}"
        SettingsCategory.PLAYBACK ->
            PlaybackStreamQuality.fromName(app.prefs.playbackStreamQuality).label
        SettingsCategory.PRIVACY -> {
            val enabled = listOfNotNull(
                "Discreet".takeIf { app.prefs.discreetMode },
                "Incognito".takeIf { app.prefs.incognitoMode },
                "Screenshots blocked".takeIf { app.prefs.blockScreenshots }
            )
            if (enabled.isEmpty()) "PIN and local privacy controls" else enabled.joinToString(" · ")
        }
        SettingsCategory.ACCESSIBILITY -> {
            val text = com.sinema.model.InterfaceTextScale.fromName(app.prefs.interfaceTextScale).label
            "$text · High-visibility focus ${onOff(app.prefs.highContrastFocus)} · Reduce motion ${onOff(app.prefs.reduceMotion)}"
        }
        SettingsCategory.SYSTEM ->
            "Channels ${onOff(app.prefs.channelsEnabled)} · Global Search ${onOff(app.prefs.globalSearchEnabled)}"
    }

    private fun renderCategory(category: SettingsCategory) = when (category) {
        SettingsCategory.SERVER -> showServerSettings()
        SettingsCategory.HOME -> showHomeSettings()
        SettingsCategory.PLAYBACK -> showPlaybackSettings()
        SettingsCategory.PRIVACY -> showPrivacySettings()
        SettingsCategory.ACCESSIBILITY -> showAccessibilitySettings()
        SettingsCategory.SYSTEM -> showSystemSettings()
    }

    private fun showServerSettings() {
        ensureServerDraftForActiveProfile()
        showContent(
            "Server",
            "Edit the active connection or manage server profiles. Save applies URL and API-key changes."
        ) { layout ->
            addLabel(layout, "Server URL")
            val urlEdit = addEditText(layout, serverDraft.url) { serverDraft.url = it }
            addLabel(layout, "Stash API Key")
            lateinit var apiKeyEdit: EditText
            val apiKeyToggle = addButton(layout, apiKeyToggleLabel()) {
                apiKeyVisible = !apiKeyVisible
                apiKeyEdit.visibility = if (apiKeyVisible) View.VISIBLE else View.GONE
                (it as Button).text = apiKeyToggleLabel()
                if (apiKeyVisible) apiKeyEdit.requestFocus()
            }
            apiKeyEdit = addEditText(layout, serverDraft.apiKey) { serverDraft.apiKey = it }
            apiKeyEdit.visibility = if (apiKeyVisible) View.VISIBLE else View.GONE

            ServerProfileSection.build(requireContext(), layout) {
                val intent = Intent(requireContext(), SetupActivity::class.java)
                intent.putExtra("add_profile", true)
                setupLauncher.launch(intent)
            }
            addButton(layout, "Save Server Settings") { saveServerSettings() }
            urlEdit
        }
    }

    private fun apiKeyToggleLabel(): String = when {
        apiKeyVisible -> "Hide API Key"
        serverDraft.apiKey.isNotEmpty() -> "Change API Key"
        else -> "Set API Key"
    }

    private fun saveServerSettings() {
        app.prefs.serverUrl = serverDraft.normalizedUrl()
        app.prefs.apiKey = serverDraft.normalizedApiKey()
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
        serverDraft = ServerSettingsDraft(app.prefs.serverUrl, app.prefs.apiKey)
        Toast.makeText(requireContext(), "Server settings saved", Toast.LENGTH_SHORT).show()
        showCategories()
    }

    private fun confirmDiscardServerChanges() {
        if (discardDialog?.isShowing == true) return
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Unsaved server changes")
            .setMessage("Save your URL or API-key edits before leaving Settings?")
            .setPositiveButton("Save") { _, _ -> saveServerSettings() }
            .setNeutralButton("Discard") { _, _ -> requireActivity().finish() }
            .setNegativeButton("Keep editing") { _, _ ->
                returnFocusCategory = SettingsCategory.SERVER
                currentCategory = SettingsCategory.SERVER
                renderCategory(SettingsCategory.SERVER)
            }
            .create()
        discardDialog = dialog
        dialog.setOnDismissListener {
            if (discardDialog === dialog) discardDialog = null
        }
        dialog.show()
    }

    private fun showHomeSettings() {
        showContent("Home", "Personalize the dashboard and its idle behavior.") { layout ->
            val homeButton = addButton(layout, "Home Settings") {
                startActivity(Intent(requireContext(), HomeSettingsActivity::class.java))
            }
            addDescription(layout, "Reorder, show, or hide shortcuts and content rows; choose artwork and the hero banner.")
            addToggle(
                layout,
                "Ambient Gallery",
                "Show a top-rated slideshow after five minutes idle on Home. Discreet Mode and an unverified PIN prevent it.",
                { app.prefs.ambientGallery },
                { app.prefs.ambientGallery = it }
            )
            homeButton
        }
    }

    private fun showPlaybackSettings() {
        showContent("Playback", "Defaults for new playback sessions. Player controls can still override them.") { layout ->
            lateinit var qualityButton: Button
            qualityButton = addButton(layout, qualityLabel()) {
                val dialog = StreamQualityDialog.show(
                    requireContext(),
                    PlaybackStreamQuality.fromName(app.prefs.playbackStreamQuality)
                ) { chosen ->
                    app.prefs.playbackStreamQuality = chosen.name
                    qualityButton.text = qualityLabel()
                }
                qualityDialog = dialog
                dialog.setOnDismissListener {
                    if (qualityDialog === dialog) qualityDialog = null
                }
            }
            addDescription(layout, "Auto tries direct playback with compatible transcoding fallbacks.")

            lateinit var aspectButton: Button
            aspectButton = addButton(layout, aspectRatioLabel()) {
                val dialog = AspectRatioDialog.show(
                    requireContext(),
                    PlaybackAspectRatio.fromName(app.prefs.playbackAspectRatio)
                ) { chosen ->
                    app.prefs.playbackAspectRatio = chosen.name
                    aspectButton.text = aspectRatioLabel()
                }
                aspectDialog = dialog
                dialog.setOnDismissListener {
                    if (aspectDialog === dialog) aspectDialog = null
                }
            }

            lateinit var seekButton: Button
            seekButton = addButton(layout, seekPresetLabel()) {
                val next = SeekPreset.fromName(app.prefs.playbackSeekPreset).next()
                app.prefs.playbackSeekPreset = next.name
                seekButton.text = seekPresetLabel()
            }
            addDescription(layout, "Sets the rewind and forward increments used by the player and timeline.")

            lateinit var subtitleSizeButton: Button
            subtitleSizeButton = addButton(layout, subtitleSizeLabel()) {
                val next = com.sinema.model.SubtitleSize.fromName(app.prefs.subtitleSize).next()
                app.prefs.subtitleSize = next.name
                subtitleSizeButton.text = subtitleSizeLabel()
            }

            lateinit var subtitleBackgroundButton: Button
            subtitleBackgroundButton = addButton(layout, subtitleBackgroundLabel()) {
                val next = com.sinema.model.SubtitleBackground.fromName(app.prefs.subtitleBackground).next()
                app.prefs.subtitleBackground = next.name
                subtitleBackgroundButton.text = subtitleBackgroundLabel()
            }

            lateinit var subtitlePositionButton: Button
            subtitlePositionButton = addButton(layout, subtitlePositionLabel()) {
                val next = com.sinema.model.SubtitlePosition.fromName(app.prefs.subtitlePosition).next()
                app.prefs.subtitlePosition = next.name
                subtitlePositionButton.text = subtitlePositionLabel()
            }

            lateinit var subtitleLanguageButton: Button
            subtitleLanguageButton = addButton(layout, subtitleLanguageLabel()) {
                val next = com.sinema.model.SubtitleLanguage.fromCode(app.prefs.subtitleLanguage).next()
                app.prefs.subtitleLanguage = next.code
                subtitleLanguageButton.text = subtitleLanguageLabel()
            }
            addDescription(layout, "Subtitle appearance applies to player views. Preferred language selects a matching track when available; it does not force captions on.")

            addToggle(
                layout,
                "Loop current scene",
                "Repeat one scene instead of advancing through a queue.",
                { app.prefs.loopEnabled },
                { app.prefs.loopEnabled = it }
            )
            qualityButton
        }
    }

    private fun qualityLabel(): String =
        "Stream quality: ${PlaybackStreamQuality.fromName(app.prefs.playbackStreamQuality).label}"

    private fun aspectRatioLabel(): String =
        "Aspect ratio: ${PlaybackAspectRatio.fromName(app.prefs.playbackAspectRatio).label}"

    private fun seekPresetLabel(): String =
        "Seek buttons: ${SeekPreset.fromName(app.prefs.playbackSeekPreset).label}"

    private fun subtitleSizeLabel(): String =
        "Subtitle size: ${com.sinema.model.SubtitleSize.fromName(app.prefs.subtitleSize).label}"

    private fun subtitleBackgroundLabel(): String =
        "Subtitle background: ${com.sinema.model.SubtitleBackground.fromName(app.prefs.subtitleBackground).label}"

    private fun subtitlePositionLabel(): String =
        "Subtitle position: ${com.sinema.model.SubtitlePosition.fromName(app.prefs.subtitlePosition).label}"

    private fun subtitleLanguageLabel(): String =
        "Preferred subtitles: ${com.sinema.model.SubtitleLanguage.fromCode(app.prefs.subtitleLanguage).label}"

    private fun showAccessibilitySettings() {
        showContent("Accessibility", "Text, focus, motion, and remote-control help.") { layout ->
            lateinit var textScaleButton: Button
            textScaleButton = addButton(layout, interfaceTextScaleLabel()) {
                val next = com.sinema.model.InterfaceTextScale.fromName(app.prefs.interfaceTextScale).next()
                app.prefs.interfaceTextScale = next.name
                textScaleButton.text = interfaceTextScaleLabel()
                requireActivity().recreate()
            }
            addDescription(layout, "Large and Extra Large increase interface text while keeping your Android TV layout.")

            addToggle(
                layout,
                "High-visibility focus",
                "Draw a bright yellow outline around the currently focused control.",
                { app.prefs.highContrastFocus },
                {
                    app.prefs.highContrastFocus = it
                    (requireActivity() as? SinemaActivity)?.refreshAccessibilityFocus()
                }
            )
            addToggle(
                layout,
                "Reduce motion",
                "Disable card zoom and Ambient Gallery pan, zoom, and crossfade animations.",
                { app.prefs.reduceMotion },
                { app.prefs.reduceMotion = it }
            )
            addButton(layout, "Remote Control Guide") {
                startActivity(Intent(requireContext(), RemoteGuideActivity::class.java))
            }
            textScaleButton
        }
    }

    private fun interfaceTextScaleLabel(): String =
        "Interface text: ${com.sinema.model.InterfaceTextScale.fromName(app.prefs.interfaceTextScale).label}"

    private fun autoLockLabel(): String = when (app.prefs.autoLockMinutes) {
        0 -> "Auto-Lock: never"
        else -> "Auto-Lock: ${app.prefs.autoLockMinutes} min idle"
    }

    private fun decoyPinLabel(): String =
        if (app.prefs.hasDecoyPinSet()) "Remove Decoy PIN" else "Set Decoy PIN"

    private fun showPrivacySettings() {
        showContent("Privacy", "These controls affect Sinema on this device unless stated otherwise.") { layout ->
            addLabel(layout, if (app.prefs.hasPinSet()) "PIN Lock: set" else "PIN Lock: not set")
            val pinButton = addButton(layout, if (app.prefs.hasPinSet()) "Remove PIN" else "Set PIN") {
                val intent = Intent(requireContext(), PinActivity::class.java).apply {
                    putExtra(if (app.prefs.hasPinSet()) "verify_for_removal" else "setup_mode", true)
                }
                pinActivityLauncher.launch(intent)
            }
            addDescription(layout, "A local four-digit lock for launching Sinema; it does not lock the Stash web interface.")

            addButton(layout, autoLockLabel()) {
                val next = when (app.prefs.autoLockMinutes) {
                    0 -> 5; 5 -> 15; 15 -> 30; else -> 0
                }
                app.prefs.autoLockMinutes = next
                (it as Button).text = autoLockLabel()
            }
            addDescription(layout, "Auto-Lock re-asks for the PIN after the selected idle time.")

            addButton(layout, decoyPinLabel()) {
                if (app.prefs.hasDecoyPinSet()) {
                    app.prefs.removeDecoyPin()
                    (it as Button).text = decoyPinLabel()
                } else {
                    startActivity(
                        Intent(requireContext(), PinActivity::class.java).putExtra("decoy_setup", true)
                    )
                }
            }
            addDescription(layout, "Entering the Decoy PIN unlocks a blank shell instead of your library.")

            addToggle(
                layout,
                "Discreet Mode",
                "Use neutral labels and hide artwork until cards are focused. Stash metadata is unchanged.",
                { app.prefs.discreetMode },
                { value ->
                    app.prefs.discreetMode = value
                    CardPresenter.discreetGeneration++
                }
            )
            addToggle(
                layout,
                "Incognito (pause watch history)",
                "Pause resume, play-count, Watch Next, and search-history writes from this device.",
                { app.prefs.incognitoMode },
                { app.prefs.incognitoMode = it }
            )
            addToggle(
                layout,
                "Block Screenshots",
                "Block screenshots and recents thumbnails throughout Sinema on this device.",
                { app.prefs.blockScreenshots },
                { app.prefs.blockScreenshots = it }
            )
            pinButton
        }
    }

    private fun showSystemSettings() {
        showContent("System", "Optional Android TV integrations. Both are off by default.") { layout ->
            var firstButton: Button? = null
            val pm = requireContext().packageManager
            if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
                val channelsButton = addToggle(
                    layout,
                    "Android TV Channels",
                    "Publish Continue Playing and Recently Added to the TV launcher. Disabling removes Sinema's entries.",
                    { app.prefs.channelsEnabled },
                    { value ->
                        app.prefs.channelsEnabled = value
                        if (!value) com.sinema.util.TvChannels.clearAll(requireContext())
                        Toast.makeText(
                            requireContext(),
                            "Channels ${if (value) "enabled" else "disabled"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                firstButton = channelsButton
            }

            val globalSearchButton = addToggle(
                layout,
                "Global Search",
                "Allow the Android TV system search to query scene titles and metadata on your Stash server.",
                { app.prefs.globalSearchEnabled },
                { value ->
                    app.prefs.globalSearchEnabled = value
                    val flag = android.content.pm.PackageManager.DONT_KILL_APP
                    val state = if (value) {
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }
                    pm.setComponentEnabledSetting(
                        android.content.ComponentName(requireContext(), com.sinema.util.GlobalSearchProvider::class.java),
                        state,
                        flag
                    )
                    pm.setComponentEnabledSetting(
                        android.content.ComponentName(requireContext(), GlobalSearchActivity::class.java),
                        state,
                        flag
                    )
                    Toast.makeText(
                        requireContext(),
                        if (value) "Global search enabled — Sinema scenes appear in Android TV search"
                        else "Global search disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            firstButton ?: globalSearchButton

            addButton(layout, "Export Settings Backup") {
                backupExportLauncher.launch("sinema-backup.json")
            }.also { firstButton = firstButton ?: it }
            addDescription(layout, "Writes a JSON file with all settings and server profiles — it contains credentials, store it safely.")

            addButton(layout, "Import Settings Backup") {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Import backup?")
                    .setMessage("This overwrites all current settings and server profiles on this device.")
                    .setPositiveButton("Import") { _, _ ->
                        backupImportLauncher.launch(
                            arrayOf("application/json")
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            addDescription(layout, "Restores settings from a sinema-backup.json file. Restart Sinema afterwards.")
            firstButton
        }
    }

    private val backupExportLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(app.prefs.exportSnapshot().toByteArray())
            }
            Toast.makeText(requireContext(), "Backup exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("Sinema", "Backup export failed", e)
            Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val backupImportLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw java.io.IOException("Could not read file")
            val written = app.prefs.importSnapshot(json)
            app.refreshApi()
            Toast.makeText(
                requireContext(),
                "Imported $written entries — restart Sinema to apply fully", Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("Sinema", "Backup import failed", e)
            Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showContent(
        title: String,
        subtitle: String,
        build: (LinearLayout) -> View?
    ) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setBackgroundColor(BACKGROUND_COLOR)
        }
        layout.addView(TextView(context).apply {
            text = title
            textSize = 28f
            setTextColor(TEXT_COLOR)
        })
        layout.addView(TextView(context).apply {
            text = subtitle
            textSize = 16f
            setTextColor(SECONDARY_TEXT_COLOR)
            setPadding(0, 12, 0, 12)
        })
        val initialFocus = build(layout)
        val scrollView = ScrollView(context).apply {
            isFocusable = false
            setBackgroundColor(BACKGROUND_COLOR)
            addView(layout)
        }
        container.removeAllViews()
        container.addView(scrollView)
        initialFocus?.requestFocus()
    }

    private fun addLabel(parent: LinearLayout, value: String) {
        parent.addView(TextView(requireContext()).apply {
            text = value
            textSize = 18f
            setTextColor(SECONDARY_TEXT_COLOR)
            setPadding(0, 12, 0, 4)
        })
    }

    private fun addDescription(parent: LinearLayout, value: String) {
        parent.addView(TextView(requireContext()).apply {
            text = value
            textSize = 14f
            setTextColor(SECONDARY_TEXT_COLOR)
            setPadding(8, 4, 8, 12)
        })
    }

    private fun addEditText(
        parent: LinearLayout,
        initialValue: String,
        onChanged: (String) -> Unit
    ): EditText {
        val edit = EditText(requireContext()).apply {
            setText(initialValue)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(TEXT_COLOR)
            setBackgroundColor(INPUT_COLOR)
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) FOCUSED_INPUT_COLOR else INPUT_COLOR)
            }
            doAfterTextChanged { onChanged(it?.toString().orEmpty()) }
        }
        parent.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })
        return edit
    }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        action: (View) -> Unit
    ): Button {
        val button = Button(requireContext()).apply {
            text = label
            isFocusable = true
            setBackgroundColor(INPUT_COLOR)
            setTextColor(TEXT_COLOR)
            setOnFocusChangeListener { _, focused ->
                setBackgroundColor(if (focused) FOCUS_COLOR else INPUT_COLOR)
            }
            setOnClickListener { action(it) }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 })
        return button
    }

    private fun addToggle(
        parent: LinearLayout,
        label: String,
        description: String,
        read: () -> Boolean,
        write: (Boolean) -> Unit
    ): Button {
        lateinit var button: Button
        button = addButton(parent, "$label: ${onOff(read())}") {
            write(!read())
            button.text = "$label: ${onOff(read())}"
        }
        addDescription(parent, description)
        return button
    }

    private fun onOff(value: Boolean): String = if (value) "ON" else "OFF"

    companion object {
        private const val KEY_SERVER_URL = "settings_server_url"
        private const val KEY_CATEGORY = "settings_category"
        private const val KEY_RETURN_FOCUS = "settings_return_focus"
        private const val BACKGROUND_COLOR = 0xFF1B1B1B.toInt()
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val SECONDARY_TEXT_COLOR = 0xFFCCCCCC.toInt()
        private const val INPUT_COLOR = 0xFF333333.toInt()
        private const val FOCUSED_INPUT_COLOR = 0xFF555555.toInt()
        private const val FOCUS_COLOR = 0xFF2AABE0.toInt()
    }
}
