package com.sinema.ui

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import com.sinema.SinemaApp

internal object TvMotion {
    enum class FocusRole {
        /** Leanback row cards (Leanback already applies focus zoom when enabled). */
        CARD,
        /** Home hero banner card. */
        HERO,
        /** Quick action command chips on home. */
        COMMAND,
        /** Standalone programmatic TV buttons and chips outside Leanback zoom rows. */
        CONTROL
    }

    data class FocusChoreography(
        val scale: Float,
        val translationZDp: Float,
        val durationMs: Long,
        val contentAlpha: Float
    )

    private val decelerate = DecelerateInterpolator(1.6f)
    private const val IMAGE_CROSSFADE_MS = 180

    fun focusZoom(reduceMotion: Boolean): Int = if (reduceMotion) 0 else 1

    fun focusZoom(): Int = focusZoom(SinemaApp.instance.prefs.reduceMotion)

    fun imageCrossfadeMs(reduceMotion: Boolean = SinemaApp.instance.prefs.reduceMotion): Int =
        if (reduceMotion) 0 else IMAGE_CROSSFADE_MS

    /**
     * Pure focus-motion specification per TV surface role. Honors [reduceMotion]
     * by disabling scale deltas and animation durations while preserving crisp
     * depth separation via Z elevation.
     */
    fun focusChoreography(
        role: FocusRole,
        focused: Boolean,
        reduceMotion: Boolean
    ): FocusChoreography = when (role) {
        FocusRole.CARD -> FocusChoreography(
            scale = 1f,
            translationZDp = if (focused) 12f else 0f,
            durationMs = if (reduceMotion) 0L else if (focused) 150L else 120L,
            contentAlpha = if (focused) 1f else 0.92f
        )
        FocusRole.HERO -> FocusChoreography(
            scale = 1f,
            translationZDp = if (focused) 14f else 2f,
            durationMs = if (reduceMotion) 0L else if (focused) 180L else 140L,
            contentAlpha = 1f
        )
        FocusRole.COMMAND -> FocusChoreography(
            scale = if (focused && !reduceMotion) 1.04f else 1f,
            translationZDp = if (focused) 8f else 0f,
            durationMs = if (reduceMotion) 0L else if (focused) 140L else 110L,
            contentAlpha = if (focused) 1f else 0.90f
        )
        FocusRole.CONTROL -> FocusChoreography(
            scale = if (focused && !reduceMotion) 1.02f else 1f,
            translationZDp = if (focused) 6f else 0f,
            durationMs = if (reduceMotion) 0L else if (focused) 130L else 100L,
            contentAlpha = 1f
        )
    }

    /**
     * Applies smooth elevation and (where appropriate) subtle scale motion on focus.
     * Cancels prior in-flight property animations so rapid D-pad flicks settle cleanly.
     */
    fun applyFocusMotion(
        view: View,
        role: FocusRole,
        focused: Boolean,
        reduceMotion: Boolean = SinemaApp.instance.prefs.reduceMotion,
        animate: Boolean = true
    ) {
        val spec = focusChoreography(role, focused, reduceMotion)
        val targetZ = spec.translationZDp * view.resources.displayMetrics.density
        view.animate().cancel()
        if (!animate || spec.durationMs <= 0L) {
            view.scaleX = spec.scale
            view.scaleY = spec.scale
            view.translationZ = targetZ
        } else {
            view.animate()
                .scaleX(spec.scale)
                .scaleY(spec.scale)
                .translationZ(targetZ)
                .setDuration(spec.durationMs)
                .setInterpolator(decelerate)
                .start()
        }
    }

    /** Immediately resets focus choreography on recycled views so stale lift never leaks. */
    fun resetFocusMotion(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationZ = 0f
    }

    /** Clips a view's outline to [radiusDp] rounded corners so artwork matches focus borders. */
    fun clipRoundedCorners(view: View, radiusDp: Float) {
        val radiusPx = radiusDp * view.resources.displayMetrics.density
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) {
                outline.setRoundRect(0, 0, target.width, target.height, radiusPx)
            }
        }
        view.clipToOutline = true
    }
}
