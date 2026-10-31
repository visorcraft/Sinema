package com.sinema.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
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
