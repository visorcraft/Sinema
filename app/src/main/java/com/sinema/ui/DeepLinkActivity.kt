package com.sinema.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.sinema.SinemaApp
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class DeepLinkActivity : ComponentActivity() {
    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            resolveDeepLink()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = SinemaApp.instance
        val prefs = app.prefs

        if (prefs.hasPinSet() && !app.pinVerifiedThisSession) {
            pinLauncher.launch(Intent(this, PinActivity::class.java))
            return
        }

        resolveDeepLink()
    }

    private fun resolveDeepLink() {
        if (isFinishing) return

        val path = intent?.data?.path
        val sceneId = path?.removePrefix("/scene/")?.takeIf { it.isNotBlank() && "/" !in it }
        if (sceneId.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            if (isFinishing || isDestroyed) return@launch
            try {
                val details = SinemaApp.instance.api.findSceneFull(sceneId)
                if (isFinishing || isDestroyed) return@launch
                val scene = details?.scene
                if (scene != null) {
                    startActivity(SceneIntents.detail(this@DeepLinkActivity, scene))
                } else {
                    Log.w("Sinema", "Deep link scene not found: $sceneId")
                }
            } catch (e: java.io.IOException) {
                Log.e("Sinema", "Deep link network error", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Deep link failed", e)
            } finally {
                finish()
            }
        }
    }
}
