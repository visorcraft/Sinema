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
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("sinema", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences? = createSecurePrefs(appContext)
    private val gson = Gson()

    var serverUrl: String
        get() = prefs.getString("server_url", "http://localhost:6969") ?: "http://localhost:6969"
        set(value) = prefs.edit().putString("server_url", value).apply()

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
    var authMode: String
        get() = prefs.getString("auth_mode", "apikey") ?: "apikey"
        set(value) = prefs.edit().putString("auth_mode", value).apply()

    var channelsEnabled: Boolean
        get() = prefs.getBoolean("channels_enabled", false)
        set(value) = prefs.edit().putBoolean("channels_enabled", value).apply()

    var setupAllowSelfSigned: Boolean
        get() = prefs.getBoolean("setup_allow_self_signed", false)
        set(value) = prefs.edit().putBoolean("setup_allow_self_signed", value).apply()

    /**
     * Opt-in participation in Android TV system search. The manifest ships
     * the search components disabled; Settings flips them (and this flag)
     * together. Default false — privacy-first.
     */
    var globalSearchEnabled: Boolean
        get() = prefs.getBoolean("global_search_enabled", false)
        set(value) = prefs.edit().putBoolean("global_search_enabled", value).apply()

    var loopEnabled: Boolean
        get() = prefs.getBoolean("loop_enabled", false)
        set(value) = prefs.edit().putBoolean("loop_enabled", value).apply()

    var playbackAspectRatio: String
        get() = prefs.getString("playback_aspect_ratio", "") ?: ""
        set(value) = prefs.edit().putString("playback_aspect_ratio", value).apply()

    var playbackStreamQuality: String
        get() = prefs.getString("playback_stream_quality", "") ?: ""
        set(value) = prefs.edit().putString("playback_stream_quality", value).apply()

    var playbackSeekPreset: String
        get() = prefs.getString("playback_seek_preset", "") ?: ""
        set(value) = prefs.edit().putString("playback_seek_preset", value).apply()

    // Privacy: neutral card labels until focused, no watch-history writes,
    // and FLAG_SECURE (blocks screenshots + recents thumbnails).
    var discreetMode: Boolean
        get() = prefs.getBoolean("discreet_mode", false)
        set(value) = prefs.edit().putBoolean("discreet_mode", value).apply()

    var incognitoMode: Boolean
        get() = prefs.getBoolean("incognito_mode", false)
        set(value) = prefs.edit().putBoolean("incognito_mode", value).apply()

    var blockScreenshots: Boolean
        get() = prefs.getBoolean("block_screenshots", false)
        set(value) = prefs.edit().putBoolean("block_screenshots", value).apply()

    var ambientGallery: Boolean
        get() = prefs.getBoolean("ambient_gallery", false)
        set(value) = prefs.edit().putBoolean("ambient_gallery", value).apply()

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

    fun getFavorites(): MutableSet<String> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        val type = object : TypeToken<MutableSet<String>>() {}.type
        return gson.fromJson(json, type) ?: mutableSetOf()
    }

    fun setFavorite(sceneId: String, fav: Boolean) {
        val favs = getFavorites()
        if (fav) favs.add(sceneId) else favs.remove(sceneId)
        prefs.edit().putString("favorites", gson.toJson(favs)).apply()
    }

    fun isFavorite(sceneId: String): Boolean = getFavorites().contains(sceneId)

    fun getRecentlyWatched(): List<String> {
        val json = prefs.getString("recently_watched", "[]") ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addRecentlyWatched(sceneId: String) {
        val list = getRecentlyWatched().toMutableList()
        list.remove(sceneId)
        list.add(0, sceneId)
        val trimmed = list.take(50)
        prefs.edit().putString("recently_watched", gson.toJson(trimmed)).apply()
    }

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

    fun getAllResumePositions(): Map<String, Long> {
        return prefs.all.filter { it.key.startsWith("resume_") && it.value is Long && (it.value as Long) > 0 }
            .map { it.key.removePrefix("resume_") to (it.value as Long) }
            .toMap()
    }

    fun hasPinSet(): Boolean {
        return prefs.getString("pin_hash", null) != null
    }

    fun setPinHash(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString("pin_salt", saltHex)
            .putString("pin_hash", hash)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        val saltHex = prefs.getString("pin_salt", null)
        val salt = if (saltHex != null) {
            saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            ByteArray(0)
        }
        return storedHash == hashPin(pin, salt)
    }

    fun removePin() {
        prefs.edit().remove("pin_hash").remove("pin_salt").apply()
        removeDecoyPin()
    }

    // Decoy PIN: entering it unlocks a blank shell instead of the library.
    fun hasDecoyPinSet(): Boolean = prefs.getString("decoy_pin_hash", null) != null

    fun setDecoyPinHash(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString("decoy_pin_salt", saltHex)
            .putString("decoy_pin_hash", hash)
            .apply()
    }

    fun verifyDecoyPin(pin: String): Boolean {
        val storedHash = prefs.getString("decoy_pin_hash", null) ?: return false
        val saltHex = prefs.getString("decoy_pin_salt", null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return storedHash == hashPin(pin, salt)
    }

    fun removeDecoyPin() {
        prefs.edit().remove("decoy_pin_hash").remove("decoy_pin_salt").apply()
    }

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
