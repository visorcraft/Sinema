package com.sinema.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.SinemaApp

class PinActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private lateinit var titleText: TextView
    private lateinit var errorText: TextView
    private lateinit var dots: List<View>
    private lateinit var buttons: List<Button>
    
    private var currentPin = ""
    private var mode = Mode.VERIFY
    private var setupPin = ""
    
    enum class Mode {
        VERIFY,     // Normal verification
        SETUP,      // Setting up new PIN (enter PIN)
        CONFIRM     // Confirming new PIN (confirm PIN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        mode = when {
            intent.getBooleanExtra("setup_mode", false) -> Mode.SETUP
            !app.prefs.hasPinSet() -> Mode.SETUP
            else -> Mode.VERIFY
        }

        titleText = findViewById(R.id.pin_title)
        errorText = findViewById(R.id.pin_error)
        
        dots = listOf(
            findViewById(R.id.pin_dot1),
            findViewById(R.id.pin_dot2),
            findViewById(R.id.pin_dot3),
            findViewById(R.id.pin_dot4)
        )
        
        buttons = listOf(
            findViewById(R.id.btn_0),
            findViewById(R.id.btn_1),
            findViewById(R.id.btn_2),
            findViewById(R.id.btn_3),
            findViewById(R.id.btn_4),
            findViewById(R.id.btn_5),
            findViewById(R.id.btn_6),
            findViewById(R.id.btn_7),
            findViewById(R.id.btn_8),
            findViewById(R.id.btn_9),
            findViewById(R.id.btn_backspace)
        )
        
        setupUI()
        setupClickListeners()
        updateUI()
    }

    private fun setupUI() {
        when (mode) {
            Mode.VERIFY -> titleText.text = "Enter PIN to continue"
            Mode.SETUP -> titleText.text = "Enter new PIN"
            Mode.CONFIRM -> titleText.text = "Confirm PIN"
        }
    }

    private fun setupClickListeners() {
        buttons.forEach { button ->
            button.setOnClickListener { handleButtonClick(it) }
        }
        
        findViewById<Button>(R.id.btn_backspace).setOnClickListener {
            handleBackspace()
        }

        findViewById<Button>(R.id.btn_close_app).setOnClickListener {
            showCloseAppConfirm()
        }
    }

    private fun showCloseAppConfirm() {
        AlertDialog.Builder(this)
            .setTitle("Close Sinema?")
            .setMessage("Are you sure you want to close Sinema?")
            .setPositiveButton("Close") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleButtonClick(view: View) {
        when (view.id) {
            R.id.btn_0 -> addDigit("0")
            R.id.btn_1 -> addDigit("1")
            R.id.btn_2 -> addDigit("2")
            R.id.btn_3 -> addDigit("3")
            R.id.btn_4 -> addDigit("4")
            R.id.btn_5 -> addDigit("5")
            R.id.btn_6 -> addDigit("6")
            R.id.btn_7 -> addDigit("7")
            R.id.btn_8 -> addDigit("8")
            R.id.btn_9 -> addDigit("9")
        }
    }

    private fun addDigit(digit: String) {
        if (currentPin.length < 4) {
            currentPin += digit
            updateUI()
            
            if (currentPin.length == 4) {
                handlePinComplete()
            }
        }
    }

    private fun handleBackspace() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            updateUI()
            hideError()
        }
    }

    private fun handlePinComplete() {
        when (mode) {
            Mode.VERIFY -> {
                if (app.prefs.verifyPin(currentPin)) {
                    app.pinVerifiedThisSession = true
                    val resultIntent = Intent()
                    if (intent.getBooleanExtra("verify_for_removal", false)) {
                        resultIntent.putExtra("verify_for_removal", true)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                } else {
                    showError("Incorrect PIN")
                    currentPin = ""
                    updateUI()
                }
            }
            Mode.SETUP -> {
                setupPin = currentPin
                currentPin = ""
                mode = Mode.CONFIRM
                titleText.text = "Confirm PIN"
                hideError()
                updateUI()
            }
            Mode.CONFIRM -> {
                if (setupPin == currentPin) {
                    app.prefs.setPinHash(currentPin)
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    showError("PINs do not match")
                    mode = Mode.SETUP
                    titleText.text = "Enter new PIN"
                    setupPin = ""
                    currentPin = ""
                    updateUI()
                }
            }
        }
    }

    private fun updateUI() {
        dots.forEachIndexed { index, dot ->
            dot.isSelected = index < currentPin.length
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorText.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_0 -> { addDigit("0"); return true }
            KeyEvent.KEYCODE_1 -> { addDigit("1"); return true }
            KeyEvent.KEYCODE_2 -> { addDigit("2"); return true }
            KeyEvent.KEYCODE_3 -> { addDigit("3"); return true }
            KeyEvent.KEYCODE_4 -> { addDigit("4"); return true }
            KeyEvent.KEYCODE_5 -> { addDigit("5"); return true }
            KeyEvent.KEYCODE_6 -> { addDigit("6"); return true }
            KeyEvent.KEYCODE_7 -> { addDigit("7"); return true }
            KeyEvent.KEYCODE_8 -> { addDigit("8"); return true }
            KeyEvent.KEYCODE_9 -> { addDigit("9"); return true }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK -> {
                handleBackspace(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        // Prevent back button from bypassing PIN
        if (mode == Mode.VERIFY) {
            finishAffinity()
        } else {
            super.onBackPressed()
        }
    }
}