package com.sinema.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import java.security.SecureRandom

class Prefs(context: Context) {

    /** Standard string pref as a property delegate. */
    private fun string(key: String, default: String) =
        object : kotlin.properties.ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) =
                prefs.getString(key, default) ?: default
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) {
                prefs.edit().putString(key, value).apply()
            }
        }

    /** Standard int pref as a property delegate. */
    private fun int(key: String, default: Int) =
        object : kotlin.properties.ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) =
                prefs.getInt(key, default)
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) {
                prefs.edit().putInt(key, value).apply()
            }
        }

    /** Standard boolean pref as a property delegate. */
    private fun bool(key: String, default: Boolean) =
        object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) =
                prefs.getBoolean(key, default)
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
                prefs.edit().putBoolean(key, value).apply()
            }
        }
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("sinema", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences? = createSecurePrefs(appContext)
    private val gson = Gson()

    var serverUrl: String by string("server_url", "http://localhost:6969")

    var apiKey: String
        get() = getSecureString("api_key")
        set(value) = putSecureString("api_key", value)

    var sessionCookie: String
        get() = getSecureString("session_cookie")
        set(value) = putSecureString("session_cookie", value)


    var stashUsername: String
        get() = getSecureString("stash_username")
        set(value) = putSecureString("stash_username", value)

    var stashPassword: String
        get() = getSecureString("stash_password")
        set(value) = putSecureString("stash_password", value)

    // "apikey" or "session"
    var authMode: String by string("auth_mode", "apikey")

    var channelsEnabled: Boolean by bool("channels_enabled", false)

    var setupAllowSelfSigned: Boolean by bool("setup_allow_self_signed", false)

    /**
     * Opt-in participation in Android TV system search. The manifest ships
     * the search components disabled; Settings flips them (and this flag)
     * together. Default false — privacy-first.
     */
    var globalSearchEnabled: Boolean by bool("global_search_enabled", false)

    var loopEnabled: Boolean by bool("loop_enabled", false)

    var playbackAspectRatio: String by string("playback_aspect_ratio", "")

    var playbackStreamQuality: String by string("playback_stream_quality", "")

    var playbackSeekPreset: String by string("playback_seek_preset", "")

    var subtitleSize: String
        get() = prefs.getString("subtitle_size", "") ?: ""
        set(value) = prefs.edit().putString("subtitle_size", value).apply()

    var subtitleBackground: String
        get() = prefs.getString("subtitle_background", "") ?: ""
        set(value) = prefs.edit().putString("subtitle_background", value).apply()

    var subtitlePosition: String
        get() = prefs.getString("subtitle_position", "") ?: ""
        set(value) = prefs.edit().putString("subtitle_position", value).apply()

    var subtitleLanguage: String
        get() = prefs.getString("subtitle_language", "") ?: ""
        set(value) = prefs.edit().putString("subtitle_language", value).apply()

    // Privacy: neutral card labels until focused, no watch-history writes,
    // and FLAG_SECURE (blocks screenshots + recents thumbnails).
    var discreetMode: Boolean by bool("discreet_mode", false)

    var incognitoMode: Boolean
        get() = prefs.getBoolean("incognito_mode", false)
        set(value) {
            prefs.edit().putBoolean("incognito_mode", value).apply()
            if (value) playbackSessionJson = ""
        }

    var blockScreenshots: Boolean by bool("block_screenshots", false)

    var ambientGallery: Boolean by bool("ambient_gallery", false)

    /** Endless Mix: auto-extend an exhausted queue with fresh candidates. */
    var endlessMix: Boolean by bool("endless_mix", false)

    var playbackSessionJson: String
        get() = getSecureString("playback_session")
        set(value) = putSecureString("playback_session", value)

    // Server profiles (stored in secure prefs)
    var profiles: List<com.sinema.model.ServerProfile>
        get() = ProfileCodec.fromJson(getSecureString("server_profiles"))
        set(value) = putSecureString("server_profiles", ProfileCodec.toJson(value))

    var activeProfileId: String
        get() = prefs.getString("active_profile_id", "") ?: ""
        set(value) = prefs.edit().putString("active_profile_id", value).apply()

    val activeProfile: com.sinema.model.ServerProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }

    /** Migrate legacy single-server fields into a profile on first use.
     *  Legacy accessors remain the live config; profiles act as a switcher
     *  behind them. applyProfile() writes the selected profile back into
     *  the legacy fields so all existing consumers keep working. */
    fun migrateToProfilesIfNeeded() {
        if (!isConfigured) return
        val existing = profiles
        if (existing.isNotEmpty() && activeProfileId.isNotBlank()) return
        // Safety-net: if profiles exist but activeProfileId is blank (crash during migration),
        // just set it to the first profile instead of re-migrating.
        if (existing.isNotEmpty()) {
            activeProfileId = existing.first().id
            return
        }
        val p = com.sinema.model.ServerProfile(
            name = "Default",
            serverUrl = serverUrl,
            apiKey = apiKey,
            sessionCookie = sessionCookie,
            authMode = authMode,
            stashUsername = stashUsername,
            stashPassword = stashPassword,
            allowSelfSigned = setupAllowSelfSigned
        )
        profiles = listOf(p)
        activeProfileId = p.id
        // Consume the one-shot setup opt-in so a later Web Setup (which has
        // no such checkbox) cannot silently inherit trust-all TLS.
        setupAllowSelfSigned = false
    }

    /** Persist the active profile's fields back into the legacy accessors. */
    fun applyProfile(p: com.sinema.model.ServerProfile) {
        serverUrl = p.serverUrl
        apiKey = p.apiKey
        sessionCookie = p.sessionCookie
        authMode = p.authMode
        stashUsername = p.stashUsername
        stashPassword = p.stashPassword
        activeProfileId = p.id
    }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() || sessionCookie.isNotBlank()






    fun getResumePosition(sceneId: String): Long {
        return prefs.getLong("resume_$sceneId", 0L)
    }

    fun setResumePosition(sceneId: String, positionMs: Long) {
        prefs.edit().putLong("resume_$sceneId", positionMs).apply()
    }

    fun clearResumePosition(sceneId: String) {
        prefs.edit().remove("resume_$sceneId").apply()
    }

    fun sortFor(screen: String): String =
        prefs.getString("sort_$screen", "") ?: ""

    fun setSortFor(screen: String, optionName: String) =
        prefs.edit().putString("sort_$screen", optionName).apply()

    fun sceneFilterFor(screen: String): com.sinema.model.SceneFilterOptions =
        com.sinema.model.SceneFilterOptions.fromPreferenceValue(
            prefs.getString("scene_filter_$screen", null)
        )

    fun setSceneFilterFor(screen: String, filter: com.sinema.model.SceneFilterOptions) =
        prefs.edit().putString("scene_filter_$screen", filter.toPreferenceValue()).apply()

    var homeLayout: com.sinema.model.HomeLayoutSettings
        get() = try {
            gson.fromJson(
                prefs.getString("home_layout", null),
                com.sinema.model.HomeLayoutSettings::class.java
            ) ?: com.sinema.model.HomeLayoutSettings()
        } catch (e: Exception) {
            Log.w("Sinema", "Invalid home layout preferences; using defaults", e)
            com.sinema.model.HomeLayoutSettings()
        }.normalized()
        set(value) = prefs.edit().putString("home_layout", gson.toJson(value.normalized())).apply()

    // Stored newline-joined: queries never contain newlines (keyboard input).
    var recentSearches: List<String>
        get() = prefs.getString("recent_searches", null)
            ?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        private set(value) = prefs.edit()
            .putString("recent_searches", value.joinToString("\n")).apply()

    fun recordRecentSearch(query: String) {
        recentSearches = com.sinema.util.RecentSearches.updated(recentSearches, query)
    }

    fun clearRecentSearches() {
        prefs.edit().remove("recent_searches").apply()
    }


    // ------------------------------------------------------------------
    // PIN credentials: primary and decoy share one salted-hash store,
    // parameterized by key prefix. The primary PIN keeps its legacy
    // salt-less verification path for pre-salt installs.
    // ------------------------------------------------------------------

    private fun hasCredential(prefix: String) = prefs.getString(prefix + "_hash", null) != null

    private fun setCredential(prefix: String, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        prefs.edit()
            .putString(prefix + "_salt", saltHex)
            .putString(prefix + "_hash", hashPin(pin, salt))
            .apply()
    }

    private fun verifyCredential(prefix: String, pin: String): Boolean {
        val storedHash = prefs.getString(prefix + "_hash", null) ?: return false
        val saltHex = prefs.getString(prefix + "_salt", null)
        val salt = if (saltHex != null) {
            saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else if (prefix == "pin") {
            ByteArray(0) // legacy pre-salt primary PIN
        } else {
            return false
        }
        return storedHash == hashPin(pin, salt)
    }

    private fun removeCredential(prefix: String) {
        prefs.edit().remove(prefix + "_hash").remove(prefix + "_salt").apply()
    }

    fun hasPinSet(): Boolean = hasCredential("pin")

    fun setPinHash(pin: String) = setCredential("pin", pin)

    fun verifyPin(pin: String): Boolean = verifyCredential("pin", pin)

    fun removePin() {
        removeCredential("pin")
        removeDecoyPin()
    }

    /** Decoy PIN: entering it unlocks a blank shell instead of the library. */
    fun hasDecoyPinSet(): Boolean = hasCredential("decoy_pin")

    fun setDecoyPinHash(pin: String) = setCredential("decoy_pin", pin)

    fun verifyDecoyPin(pin: String): Boolean = verifyCredential("decoy_pin", pin)

    fun removeDecoyPin() = removeCredential("decoy_pin")

    /** Auto-lock: re-ask for the PIN after this many idle minutes (0 = never). */
    var autoLockMinutes: Int
        get() = prefs.getInt("auto_lock_minutes", 0)
        set(value) = prefs.edit().putInt("auto_lock_minutes", value).apply()

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun createSecurePrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "sinema_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("Sinema", "EncryptedSharedPreferences unavailable, using fallback", e)
            null
        }
    }

    private fun getSecureString(key: String): String {
        val secure = securePrefs
        if (secure != null) {
            val secureVal = secure.getString(key, null)
            if (secureVal != null) return secureVal

            // One-time migration from plain prefs if present.
            val legacy = prefs.getString(key, null)
            if (!legacy.isNullOrBlank()) {
                secure.edit().putString(key, legacy).apply()
                prefs.edit().remove(key).apply()
                return legacy
            }
            return ""
        }

        return prefs.getString(key, "") ?: ""
    }

    private fun putSecureString(key: String, value: String) {
        val secure = securePrefs
        if (secure != null) {
            secure.edit().putString(key, value).apply()
            // Ensure legacy cleartext copy is removed.
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    // ------------------------------------------------------------------
    // Backup / restore. The snapshot carries regular prefs (settings,
    // layout, filters, PIN hashes) plus the secure server profiles, so the
    // JSON *contains credentials* — Settings warns before export.
    // ------------------------------------------------------------------

    fun exportSnapshot(): String {
        val secureEntries = mutableMapOf<String, String>()
        securePrefs?.all?.forEach { (key, value) ->
            if (value is String) secureEntries[key] = value
        }
        val payload = mapOf(
            "format" to "sinema-backup",
            "version" to 1,
            "prefs" to prefs.all,
            "secure" to secureEntries
        )
        return com.google.gson.Gson().toJson(payload)
    }

    /** Applies a snapshot produced by [exportSnapshot]. Returns entries written. */
    fun importSnapshot(json: String): Int {
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        require(root.get("format")?.asString == "sinema-backup") { "Not a Sinema backup file" }
        // Replace, not merge: the confirmation promises an overwrite, so keys
        // absent from the snapshot (stale settings, deleted profiles) go away.
        val editor = prefs.edit()
        prefs.all.keys.forEach { editor.remove(it) }
        var written = 0
        root.getAsJsonObject("prefs")?.entrySet()?.forEach { (key, value) ->
            when {
                value.isJsonNull -> editor.remove(key)
                value.isJsonPrimitive -> {
                    val p = value.asJsonPrimitive
                    when {
                        p.isBoolean -> editor.putBoolean(key, p.asBoolean)
                        p.isNumber -> when (BackupTypes.numberKind(key, p.asString)) {
                            BackupTypes.Kind.FLOAT -> editor.putFloat(key, p.asFloat)
                            BackupTypes.Kind.LONG -> editor.putLong(key, p.asLong)
                            BackupTypes.Kind.INT -> editor.putInt(key, p.asInt)
                        }
                        else -> editor.putString(key, p.asString)
                    }
                    written++
                }
                value.isJsonArray -> {
                    val set = value.asJsonArray.mapNotNull { el -> el.asJsonPrimitive?.asString }.toSet()
                    editor.putStringSet(key, set)
                    written++
                }
                else -> Unit
            }
        }
        editor.apply()
        val secure = securePrefs
        if (secure != null) {
            val secureEditor = secure.edit()
            secure.all.keys.forEach { secureEditor.remove(it) }
            root.getAsJsonObject("secure")?.entrySet()?.forEach { (key, value) ->
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    secureEditor.putString(key, value.asString)
                    written++
                }
            }
            secureEditor.apply()
        } else {
            // No encrypted store: putSecureString falls back to the regular
            // prefs (already replaced above), so restores keep credentials
            // working on devices without EncryptedSharedPreferences.
            root.getAsJsonObject("secure")?.entrySet()?.forEach { (key, value) ->
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    putSecureString(key, value.asString)
                    written++
                }
            }
        }
        return written
    }
}
