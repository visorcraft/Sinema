package com.sinema.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/**
 * Shared programmatic-UI factories for TV screens: focus-highlighted
 * buttons and pill chips with consistent styling. Top-level so any
 * activity/fragment in the package can call them directly.
 */
private const val INPUT_COLOR = 0xFF232A30.toInt()
private const val FOCUS_COLOR = 0xFF2AABE0.toInt()
private const val UNFOCUSED_STROKE_COLOR = 0x26FFFFFF
private const val FOCUSED_STROKE_COLOR = 0xFF7AD7F7.toInt()
private const val CONTROL_CORNER_DP = 8f

private fun View.controlBackground(fillColor: Int, focused: Boolean): GradientDrawable {
    val density = resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = CONTROL_CORNER_DP * density
        setColor(fillColor)
        setStroke(
            ((if (focused) 2f else 1f) * density).toInt().coerceAtLeast(1),
            if (focused) FOCUSED_STROKE_COLOR else UNFOCUSED_STROKE_COLOR
        )
    }
}

fun View.bindFocusBackground(normal: Int = INPUT_COLOR, focused: Int = FOCUS_COLOR) {
    stateListAnimator = null
    background = controlBackground(normal, focused = hasFocus())
    TvMotion.applyFocusMotion(this, TvMotion.FocusRole.CONTROL, hasFocus(), animate = false)
    setOnFocusChangeListener { v, hasFocus ->
        v.background = v.controlBackground(if (hasFocus) focused else normal, focused = hasFocus)
        TvMotion.applyFocusMotion(v, TvMotion.FocusRole.CONTROL, hasFocus)
    }
}

fun Context.tvButton(
    label: String,
    onClick: (View) -> Unit
): Button = Button(this).apply {
    val density = resources.displayMetrics.density
    text = label
    isFocusable = true
    isFocusableInTouchMode = true
    isAllCaps = false
    stateListAnimator = null
    gravity = Gravity.START or Gravity.CENTER_VERTICAL
    setTextColor(Color.WHITE)
    textSize = 15f
    minimumHeight = (48 * density).toInt()
    val hPad = (18 * density).toInt()
    val vPad = (12 * density).toInt()
    setPadding(hPad, vPad, hPad, vPad)
    bindFocusBackground()
    setOnClickListener { onClick(it) }
}

fun LinearLayout.addTvButton(
    label: String,
    topMargin: Int = 12,
    onClick: (View) -> Unit
): Button {
    val button = context.tvButton(label, onClick)
    addView(button, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { this.topMargin = topMargin })
    return button
}

/** Rounded pill chip (tags/performers/galleries rows). */
fun Context.tvChip(label: String, onClick: () -> Unit): Button {
    val density = resources.displayMetrics.density
    return Button(this).apply {
        text = label
        isFocusable = true
        isFocusableInTouchMode = true
        isAllCaps = false
        stateListAnimator = null
        setBackgroundResource(com.sinema.R.drawable.bg_pill)
        setTextColor(Color.WHITE)
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        val hPad = (16 * density).toInt()
        val vPad = (8 * density).toInt()
        setPadding(hPad, vPad, hPad, vPad)
        setOnFocusChangeListener { v, hasFocus ->
            TvMotion.applyFocusMotion(v, TvMotion.FocusRole.CONTROL, hasFocus)
        }
        setOnClickListener { onClick() }
    }
}
