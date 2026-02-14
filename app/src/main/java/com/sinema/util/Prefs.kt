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
    private val prefs: SharedPreferences = context.getSharedPreferences("sinema", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences? = createSecurePrefs(context)
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
    }

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
}
