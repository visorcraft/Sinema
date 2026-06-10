package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.sinema.SinemaApp
import com.sinema.model.ServerProfile

object ServerProfileSection {

    fun build(ctx: Context, layout: LinearLayout, onAddServer: () -> Unit) {
        val app = SinemaApp.instance

        val serversLabel = android.widget.TextView(ctx).apply {
            text = "\nServers:"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
        }
        layout.addView(serversLabel)

        val profiles = app.prefs.profiles
        val activeId = app.prefs.activeProfileId
        var prevMainBtn: Button? = null

        profiles.forEach { profile ->
            val mainBtn = addProfileRow(ctx, layout, profile, profile.id == activeId, profiles.size <= 1)
            prevMainBtn?.let { it.nextFocusDownId = mainBtn.id }
            prevMainBtn = mainBtn
        }

        val addServerBtn = Button(ctx).apply {
            id = View.generateViewId()
            text = "+ Add Server"
            isFocusable = true
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener { onAddServer() }
        }
        prevMainBtn?.let { it.nextFocusDownId = addServerBtn.id }
        layout.addView(addServerBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8; bottomMargin = 16 })
    }

    private fun addProfileRow(ctx: Context, layout: LinearLayout, profile: ServerProfile, isActive: Boolean, isLast: Boolean): Button {
        val app = SinemaApp.instance
        val rowLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF333333.toInt())
            isFocusable = false
        }

        val btn = Button(ctx).apply {
            id = View.generateViewId()
            text = "${profile.name}${if (isActive) " ✓" else ""}\n${profile.serverUrl}"
            textSize = 14f
            isFocusable = true
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
                rowLayout.setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener {
                if (isActive) {
                    Toast.makeText(ctx, "${profile.name} is already active", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(ctx)
                        .setTitle("Switch Server")
                        .setMessage("Switch to ${profile.name}?")
                        .setPositiveButton("Switch") { _, _ ->
                            app.prefs.applyProfile(profile)
                            app.refreshApi()
                            val intent = Intent(ctx, MainActivity::class.java)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            ctx.startActivity(intent)
                            (ctx as? android.app.Activity)?.finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
        rowLayout.addView(btn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))

        val optionsBtn = Button(ctx).apply {
            text = "..."
            textSize = 14f
            isFocusable = true
            nextFocusLeftId = btn.id
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) 0xFF2AABE0.toInt() else 0xFF333333.toInt())
            }
            setOnClickListener {
                showProfileOptions(ctx, profile, isLast)
            }
        }
        rowLayout.addView(optionsBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(rowLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8 })
        return btn
    }

    private fun showProfileOptions(ctx: Context, profile: ServerProfile, isLast: Boolean) {
        val items = arrayOf("Rename", "Remove")
        AlertDialog.Builder(ctx)
            .setTitle(profile.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> renameProfile(ctx, profile)
                    1 -> removeProfile(ctx, profile, isLast)
                }
            }
            .show()
    }

    private fun renameProfile(ctx: Context, profile: ServerProfile) {
        val app = SinemaApp.instance
        val edit = EditText(ctx).apply {
            setText(profile.name)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
        }
        AlertDialog.Builder(ctx)
            .setTitle("Rename Server")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val newName = edit.text.toString().trim()
                if (newName.isNotBlank()) {
                    val updated = profile.copy(name = newName)
                    app.prefs.profiles = app.prefs.profiles.map { if (it.id == profile.id) updated else it }
                    if (app.prefs.activeProfileId == profile.id) {
                        app.prefs.applyProfile(updated)
                    }
                    (ctx as? android.app.Activity)?.recreate()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeProfile(ctx: Context, profile: ServerProfile, isLast: Boolean) {
        val app = SinemaApp.instance
        if (isLast) {
            Toast.makeText(ctx, "Cannot remove the last server", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(ctx)
            .setTitle("Remove Server")
            .setMessage("Remove ${profile.name}?")
            .setPositiveButton("Remove") { _, _ ->
                val remaining = app.prefs.profiles.filter { it.id != profile.id }
                app.prefs.profiles = remaining
                if (app.prefs.activeProfileId == profile.id) {
                    remaining.firstOrNull()?.let {
                        app.prefs.applyProfile(it)
                        app.refreshApi()
                    }
                }
                (ctx as? android.app.Activity)?.recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
