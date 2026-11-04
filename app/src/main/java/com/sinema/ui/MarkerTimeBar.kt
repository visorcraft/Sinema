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
        fun onPreviewStop(positionMs: Long, canceled: Boolean)
    }

    var scrubPreviewListener: ScrubPreviewListener? = null
    private var sceneMarkers: List<Pair<String, Double>> = emptyList()
    private var markerPositions: List<Float> = emptyList()
    private var timelineDurationMs = 0L
    private var timelinePositionMs = 0L
    private var keyBackwardMs = 0L
    private var keyForwardMs = 0L
    private var keyScrubbing = false
    private var keyScrubPositionMs = 0L
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
                scrubPreviewListener?.onPreviewStop(position, canceled)
            }
        })
    }

    fun setSceneMarkers(markers: List<Pair<String, Double>>) {
        sceneMarkers = markers
        updateMarkerPositions()
    }

    fun setKeyTimeIncrements(backwardMs: Long, forwardMs: Long) {
        keyBackwardMs = backwardMs
        keyForwardMs = forwardMs
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && isEnabled) {
            if (event.action != KeyEvent.ACTION_DOWN) return keyScrubbing
            if (!keyScrubbing) {
                keyScrubbing = true
                keyScrubPositionMs = timelinePositionMs
                scrubPreviewListener?.onPreviewStart(keyScrubPositionMs)
            }
            val increment = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -keyBackwardMs else keyForwardMs
            keyScrubPositionMs = (keyScrubPositionMs + increment)
                .coerceIn(0L, timelineDurationMs.coerceAtLeast(0L))
            setPosition(keyScrubPositionMs)
            scrubPreviewListener?.onPreviewMove(keyScrubPositionMs)
            return true
        }
        if (keyScrubbing && event.action == KeyEvent.ACTION_DOWN &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            finishKeyScrub(canceled = false)
        }
        return super.onKeyDown(keyCode, event)
    }

    fun cancelKeyScrub() {
        keyScrubbing = false
    }

    private fun finishKeyScrub(canceled: Boolean) {
        keyScrubbing = false
        scrubPreviewListener?.onPreviewStop(keyScrubPositionMs, canceled)
    }

    override fun setPosition(position: Long) {
        timelinePositionMs = position
        super.setPosition(position)
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
