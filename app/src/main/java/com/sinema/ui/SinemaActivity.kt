package com.sinema.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.InterfaceTextScale

abstract class SinemaActivity : FragmentActivity() {
    private var systemFontScale = 1.0f
    private var appliedTextScale = 1.0f
    private var appliedReduceMotion = false
    private var focusRoot: ViewGroup? = null
    private var focusListener: ViewTreeObserver.OnGlobalFocusChangeListener? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = SinemaApp.instance.prefs
        systemFontScale = newBase.resources.configuration.fontScale
        appliedTextScale = InterfaceTextScale.fromName(prefs.interfaceTextScale)
            .effectiveFontScale(systemFontScale)
        appliedReduceMotion = prefs.reduceMotion
        val configuration = Configuration(newBase.resources.configuration).apply {
            fontScale = appliedTextScale
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onResume() {
        super.onResume()
        val prefs = SinemaApp.instance.prefs
        val preferredScale = InterfaceTextScale.fromName(prefs.interfaceTextScale)
            .effectiveFontScale(systemFontScale)
        if (appliedTextScale != preferredScale || appliedReduceMotion != prefs.reduceMotion) {
            recreate()
            return
        }
        installFocusRing()
    }

    override fun onStop() {
        focusRoot?.let { root ->
            focusListener?.let(root.viewTreeObserver::removeOnGlobalFocusChangeListener)
        }
        focusRoot = null
        focusListener = null
        super.onStop()
    }

    fun refreshAccessibilityFocus() {
        val view = currentFocus ?: return
        if (SinemaApp.instance.prefs.highContrastFocus) {
            view.foreground = ContextCompat.getDrawable(this, R.drawable.accessibility_focus_ring)
            view.setTag(R.id.accessibility_focus_ring, true)
        } else if (view.getTag(R.id.accessibility_focus_ring) == true) {
            view.foreground = null
            view.setTag(R.id.accessibility_focus_ring, false)
        }
    }

    private fun installFocusRing() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (focusRoot != null) return
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
            oldFocus?.let { view ->
                if (view.getTag(R.id.accessibility_focus_ring) == true) {
                    view.foreground = null
                    view.setTag(R.id.accessibility_focus_ring, false)
                }
            }
            if (SinemaApp.instance.prefs.highContrastFocus && newFocus != null) {
                newFocus.foreground = ContextCompat.getDrawable(this, R.drawable.accessibility_focus_ring)
                newFocus.setTag(R.id.accessibility_focus_ring, true)
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        focusRoot = root
        focusListener = listener
        refreshAccessibilityFocus()
    }
}
