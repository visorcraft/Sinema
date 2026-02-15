package com.sinema.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionCode: Int,
    val apkUrl: String?,
    val body: String
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val REPO = "visorcraft/Sinema"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    private fun normalizeVersionString(raw: String): String {
        // Extract the first semver-ish token: e.g. "v1.9.1" -> "1.9.1", "1.9-beta1" -> "1.9"
        val s = raw.trim().removePrefix("v")
        val match = Regex("\\d+(?:\\.\\d+)*").find(s)
        return match?.value ?: "0"
    }

    private fun compareVersions(aRaw: String, bRaw: String): Int {
        val a = normalizeVersionString(aRaw)
        val b = normalizeVersionString(bRaw)

        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(aParts.size, bParts.size)
        for (i in 0 until len) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${conn.responseCode}")
                return@withContext null
            }

            val json = conn.inputStream.bufferedReader().readText()
            val release = JSONObject(json)
            val tagName = release.getString("tag_name")
            val body = release.optString("body", "")

            // Parse version from tag: "v1.2" -> "1.2" (also supports patch: "v1.9.1")
            val remoteVersionName = tagName.removePrefix("v")

            // Find APK asset
            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            val info = ReleaseInfo(tagName, 0, apkUrl, body)

            // Compare version names (e.g., "1.1" vs "1.2")
            val currentVersionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            } catch (e: Exception) { "0" }

            Log.d(TAG, "Current: $currentVersionName, Remote: $remoteVersionName ($tagName)")

            if (compareVersions(remoteVersionName, currentVersionName) > 0) info else null
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    fun promptUpdate(context: Context, release: ReleaseInfo) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("Sinema ${release.tagName} is available. Update now?")
            .setPositiveButton("Update") { _, _ ->
                if (release.apkUrl != null) {
                    downloadAndInstall(context, release.apkUrl)
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, apkUrl: String) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true

                val apkFile = File(context.getExternalFilesDir(null), "sinema-update.apk")
                conn.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Update download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }
}
