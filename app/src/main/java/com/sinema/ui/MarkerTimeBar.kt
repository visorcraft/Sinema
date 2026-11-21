package com.sinema.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import com.sinema.R

@androidx.annotation.OptIn(UnstableApi::class)
class MarkerTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : DefaultTimeBar(context, attrs) {
    interface ScrubPreviewListener {
        fun onPreviewStart(positionMs: Long)
        fun onPreviewMove(positionMs: Long)
        fun onPreviewStop(positionMs: Long, canceled: Boolean, fromKeyScrub: Boolean)
        fun onPreviewPlayPause()
    }

    var scrubPreviewListener: ScrubPreviewListener? = null
    private var sceneMarkers: List<Pair<String, Double>> = emptyList()
    private var markerPositions: List<Float> = emptyList()
    internal var activeMarkerPos: Float? = null
        private set
    private var timelineDurationMs = 0L
    internal val knownDurationMs: Long get() = timelineDurationMs
    private var timelinePositionMs = 0L
    private var keyBackwardMs = 0L
    private var keyForwardMs = 0L
    private var keyScrubbing = false
    private var keyScrubPositionMs = 0L
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.playback_polish_marker_tick)
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val activeMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.playback_polish_marker_active)
        strokeWidth = 5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val activeMarkerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x552AABE0
        strokeWidth = 10f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val focusTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x442AABE0
        strokeWidth = 12f * resources.displayMetrics.density
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
                scrubPreviewListener?.onPreviewStop(position, canceled, fromKeyScrub = false)
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
            timelinePositionMs = keyScrubPositionMs
            super.setPosition(keyScrubPositionMs)
            updateActiveMarker()
            scrubPreviewListener?.onPreviewMove(keyScrubPositionMs)
            return true
        }
        if (isEnabled && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            if (keyScrubbing) finishKeyScrub(canceled = false)
            scrubPreviewListener?.onPreviewPlayPause()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    fun cancelKeyScrub() {
        keyScrubbing = false
    }

    private fun finishKeyScrub(canceled: Boolean) {
        keyScrubbing = false
        scrubPreviewListener?.onPreviewStop(keyScrubPositionMs, canceled, fromKeyScrub = true)
    }

    override fun setPosition(position: Long) {
        if (keyScrubbing) return
        timelinePositionMs = position
        super.setPosition(position)
        updateActiveMarker()
    }

    override fun setDuration(duration: Long) {
        timelineDurationMs = duration
        super.setDuration(duration)
        updateMarkerPositions()
    }

    override fun onDraw(canvas: Canvas) {
        val usableWidth = width - paddingLeft - paddingRight
        val centerY = height / 2f
        if (usableWidth > 0 && isFocused) {
            canvas.drawLine(
                paddingLeft.toFloat(),
                centerY,
                (width - paddingRight).toFloat(),
                centerY,
                focusTrackPaint
            )
        }
        super.onDraw(canvas)
        if (usableWidth <= 0) return
        val density = resources.displayMetrics.density
        val halfTick = 8f * density
        val activeHalfTick = 12f * density
        val active = activeMarkerPos
        markerPositions.forEach { fraction ->
            val x = paddingLeft + usableWidth * fraction
            val isActive = active != null && kotlin.math.abs(fraction - active) < 0.0005f
            if (isActive) {
                canvas.drawLine(x, centerY - activeHalfTick, x, centerY + activeHalfTick, activeMarkerHaloPaint)
                canvas.drawLine(x, centerY - activeHalfTick, x, centerY + activeHalfTick, activeMarkerPaint)
            } else {
                canvas.drawLine(x, centerY - halfTick, x, centerY + halfTick, markerPaint)
            }
        }
    }

    private fun updateMarkerPositions() {
        markerPositions = markerFractions(sceneMarkers, timelineDurationMs)
        updateActiveMarker()
    }

    private fun updateActiveMarker() {
        val nextActive = activeMarkerFraction(timelinePositionMs, sceneMarkers, timelineDurationMs)
        if (nextActive != activeMarkerPos) {
            activeMarkerPos = nextActive
        }
        invalidate()
    }
}
