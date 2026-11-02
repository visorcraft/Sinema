package com.sinema.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.TextView
import com.sinema.SinemaApp

/**
 * Blank shell shown when the decoy PIN is entered. Reveals nothing; Back
 * backgrounds the app (finishing would fall through to the real library).
 * MENU offers a discreet path back to the real PIN prompt.
 */
class DecoyActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "No content available"
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(0xFFAAAAAA.toInt())
            setBackgroundColor(0xFF1B1B1B.toInt())
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showSwitchUserPrompt()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showSwitchUserPrompt() {
        AlertDialog.Builder(this)
            .setMessage("Switch user?")
            .setPositiveButton("Enter PIN") { _, _ ->
                val app = SinemaApp.instance
                app.decoySession = false
                app.pinVerifiedThisSession = false
                // MainActivity's waitingForPin flow re-runs its real PIN gate.
                startActivity(android.content.Intent(this, PinActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Never finish back into the real library.
        moveTaskToBack(true)
    }
}
