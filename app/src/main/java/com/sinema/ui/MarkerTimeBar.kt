package com.sinema.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar

@androidx.annotation.OptIn(UnstableApi::class)
class MarkerTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : DefaultTimeBar(context, attrs) {
    interface ScrubPreviewListener {
        fun onPreviewStart(positionMs: Long)
        fun onPreviewMove(positionMs: Long)
        fun onPreviewStop()
    }

    var scrubPreviewListener: ScrubPreviewListener? = null
    private var sceneMarkers: List<Pair<String, Double>> = emptyList()
    private var markerPositions: List<Float> = emptyList()
    private var timelineDurationMs = 0L
    private var keyBackwardMs = 0L
    private var keyForwardMs = 0L
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF54C7EC.toInt()
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    init {
        addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                scrubPreviewListener?.onPreviewStart(position)
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                scrubPreviewListener?.onPreviewMove(position)
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                scrubPreviewListener?.onPreviewStop()
            }
        })
    }

    fun setSceneMarkers(markers: List<Pair<String, Double>>) {
        sceneMarkers = markers
        updateMarkerPositions()
    }

    /**
     * Asymmetric quick-seek amounts for D-pad scrubbing. DefaultTimeBar keeps
     * a single keyTimeIncrement and negates it for DPAD_LEFT, so switch it per
     * key direction before delegating; super.onKeyDown reads the increment
     * synchronously, making this safe for repeat events.
     */
    fun setKeyTimeIncrements(backwardMs: Long, forwardMs: Long) {
        keyBackwardMs = backwardMs
        keyForwardMs = forwardMs
        setKeyTimeIncrement(forwardMs)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (keyBackwardMs > 0) setKeyTimeIncrement(keyBackwardMs)
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (keyForwardMs > 0) setKeyTimeIncrement(keyForwardMs)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun setDuration(duration: Long) {
        timelineDurationMs = duration
        super.setDuration(duration)
        updateMarkerPositions()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val usableWidth = width - paddingLeft - paddingRight
        if (usableWidth <= 0) return
        val centerY = height / 2f
        val halfTick = 8f * resources.displayMetrics.density
        markerPositions.forEach { fraction ->
            val x = paddingLeft + usableWidth * fraction
            canvas.drawLine(x, centerY - halfTick, x, centerY + halfTick, markerPaint)
        }
    }

    private fun updateMarkerPositions() {
        markerPositions = markerFractions(sceneMarkers, timelineDurationMs)
        invalidate()
    }
}
