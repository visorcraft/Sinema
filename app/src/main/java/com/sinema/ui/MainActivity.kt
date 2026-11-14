package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.util.SceneIntents

class MainActivity : SinemaActivity() {
    private val app get() = SinemaApp.instance
    private var waitingForPin = false
    private var resumeFromBackground = true
    private var sessionRestoreOffered = false
    private var sessionRestoreDialog: android.app.AlertDialog? = null
    private var exitDialog: android.app.AlertDialog? = null
    private val ambientHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ambientCheck = object : Runnable {
        override fun run() {
            val idle = android.os.SystemClock.elapsedRealtime() - app.lastInteractionMs
            maybeAutoLock(idle)
            if (app.prefs.ambientGallery &&
                !app.prefs.discreetMode &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                // Dialogs (About, sort menus…) steal window focus; never
                // launch the gallery over an open dialog.
                hasWindowFocus() &&
                (!app.prefs.hasPinSet() || app.pinVerifiedThisSession) &&
                idle >= AMBIENT_IDLE_MS
            ) {
                startActivity(Intent(this@MainActivity, AmbientActivity::class.java))
                app.noteUserInteraction()
            }
            ambientHandler.postDelayed(this, AMBIENT_CHECK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Wipe saved fragment state to prevent zombie fragment restoration
        savedInstanceState?.remove("android:support_fragments")
        sessionRestoreOffered = savedInstanceState?.getBoolean(STATE_RESTORE_OFFERED) ?: false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check if API key is configured - if not, go to setup
        if (!app.prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Check if PIN is required and not verified
        if (app.prefs.hasPinSet() && !app.pinVerifiedThisSession) {
            waitingForPin = true
            startActivity(Intent(this, PinActivity::class.java))
            return
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showExitPrompt()
        })
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame, MainFragment())
            .commitNowAllowingStateLoss()
        startAmbientWatch()
        maybeOfferPlaybackSessionRestore()
    }

    override fun onStart() {
        resumeFromBackground = !app.isInternalActivityReturn(this)
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        val returningFromPinSuccess = waitingForPin && app.pinVerifiedThisSession
        if (returningFromPinSuccess || !resumeFromBackground) {
            app.noteUserInteraction()
        } else {
            maybeAutoLock(
                android.os.SystemClock.elapsedRealtime() - app.lastInteractionMs,
                isResumed = true
            )
        }
        resumeFromBackground = false
        // A decoy session must never fall through to the real library.
        if (app.decoySession && !isFinishing) {
            startActivity(Intent(this, DecoyActivity::class.java))
            return
        }
        if (waitingForPin && app.pinVerifiedThisSession) {
            waitingForPin = false
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, MainFragment())
                .commitNowAllowingStateLoss()
            maybeOfferPlaybackSessionRestore()
        }
        if (!waitingForPin && (!app.prefs.hasPinSet() || app.pinVerifiedThisSession)) {
            startAmbientWatch()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_RESTORE_OFFERED, sessionRestoreOffered)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        ambientHandler.removeCallbacks(ambientCheck)
        super.onStop()
    }

    override fun onDestroy() {
        exitDialog?.dismiss()
        sessionRestoreDialog?.dismiss()
        ambientHandler.removeCallbacks(ambientCheck)
        super.onDestroy()
    }

    private fun showExitPrompt() {
        if (exitDialog?.isShowing == true || isFinishing || isDestroyed) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.exit_sinema_title)
            .setItems(arrayOf(getString(R.string.exit_minimize), getString(R.string.exit_logout))) { _, which ->
                when (which) {
                    0 -> moveTaskToBack(true)
                    1 -> {
                        app.pinVerifiedThisSession = false
                        finishAffinity()
                    }
                }
            }
            .setOnDismissListener { exitDialog = null }
            .create()
        exitDialog = dialog
        dialog.show()
    }

    private fun maybeOfferPlaybackSessionRestore() {
        if (sessionRestoreOffered || intent?.data != null || app.decoySession || isFinishing) return
        sessionRestoreOffered = true
        val session = com.sinema.util.PlaybackSessionCodec.decode(app.prefs.playbackSessionJson) ?: return
        if (!com.sinema.util.PlaybackSessionRestore.isEligible(
                session,
                activeProfileId = app.prefs.activeProfileId,
                hasPin = app.prefs.hasPinSet(),
                pinVerified = app.pinVerifiedThisSession,
                incognito = app.prefs.incognitoMode
            )
        ) return
        val message = if (app.prefs.discreetMode || session.title.isBlank()) {
            getString(R.string.playback_restore_message_neutral)
        } else {
            getString(R.string.playback_restore_message, session.title)
        }
        sessionRestoreDialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.playback_restore_title)
            .setMessage(message)
            .setPositiveButton(R.string.playback_restore_action) { _, _ ->
                if (session.queueItems.isNotEmpty()) {
                    com.sinema.util.PlaybackQueue.restore(
                        com.sinema.util.PlaybackQueue.Snapshot(session.queueItems, session.queueIndex)
                    )
                } else {
                    com.sinema.util.PlaybackQueue.clear()
                }
                startActivity(SceneIntents.playbackRestored(this, session))
            }
            .setNegativeButton(R.string.playback_restore_not_now, null)
            .setOnDismissListener { sessionRestoreDialog = null }
            .show()
    }

    private fun startAmbientWatch() {
        ambientHandler.removeCallbacks(ambientCheck)
        ambientHandler.postDelayed(ambientCheck, AMBIENT_CHECK_MS)
    }

    private fun maybeAutoLock(
        idleMs: Long,
        isResumed: Boolean = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    ) {
        if (!shouldAutoLock(
                isResumed = isResumed,
                hasPin = app.prefs.hasPinSet(),
                pinVerified = app.pinVerifiedThisSession,
                decoySession = app.decoySession,
                lockMinutes = app.prefs.autoLockMinutes,
                idleMs = idleMs
            )
        ) return
        app.pinVerifiedThisSession = false
        waitingForPin = true
        sessionRestoreDialog?.dismiss()
        startActivity(Intent(this, PinActivity::class.java))
        app.noteUserInteraction()
    }

    companion object {
        private const val AMBIENT_IDLE_MS = 5 * 60_000L
        private const val AMBIENT_CHECK_MS = 30_000L
        private const val STATE_RESTORE_OFFERED = "restore_offered"
    }
}
