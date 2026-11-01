package com.sinema.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/**
 * Shared programmatic-UI factories for TV screens: focus-highlighted
 * buttons and pill chips with consistent styling. Top-level so any
 * activity/fragment in the package can call them directly.
 */
private const val INPUT_COLOR = 0xFF333333.toInt()
private const val FOCUS_COLOR = 0xFF2AABE0.toInt()

fun View.bindFocusBackground(normal: Int = INPUT_COLOR, focused: Int = FOCUS_COLOR) {
    setBackgroundColor(normal)
    setOnFocusChangeListener { v, hasFocus ->
        v.setBackgroundColor(if (hasFocus) focused else normal)
    }
}

fun Context.tvButton(
    label: String,
    onClick: (View) -> Unit
): Button = Button(this).apply {
    text = label
    isFocusable = true
    setTextColor(Color.WHITE)
    textSize = 14f
    minimumHeight = 48
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
        setBackgroundResource(com.sinema.R.drawable.bg_pill)
        setTextColor(Color.WHITE)
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        val hPad = (16 * density).toInt()
        val vPad = (8 * density).toInt()
        setPadding(hPad, vPad, hPad, vPad)
        setOnClickListener { onClick() }
    }
}
