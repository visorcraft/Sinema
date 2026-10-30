package com.sinema.ui

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.util.ArrayList

/**
 * Prefers a software video decoder when the frame width is not a multiple of
 * [VIDEO_DECODER_WIDTH_BLOCK], or the height is not a multiple of
 * [VIDEO_DECODER_HEIGHT_BLOCK]. The decision uses [Format.width]
 * and [Format.height] from the stream, so Play, Resume, and queue advance all
 * see the real frame. Audio decoder order is unchanged.
 *
 * Hardware remains the fallback when no software decoder exists, and
 * [setEnableDecoderFallback] still retries the next decoder if the first one
 * fails to initialize.
 *
 * Extension video renderers are not built here. Sinema leaves the extension
 * renderer mode at its default, off.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    init {
        setEnableDecoderFallback(true)
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        out.add(
            newVideoRenderer(
                context,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                allowedVideoJoiningTimeMs
            )
        )
    }

    override fun buildSecondaryVideoRenderer(
        renderer: Renderer,
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long
    ): Renderer? {
        if (renderer !is FrameAlignedVideoRenderer) return null
        return newVideoRenderer(
            context,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs
        )
    }

    private fun newVideoRenderer(
        context: Context,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long
    ): FrameAlignedVideoRenderer {
        val builder = MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(getCodecAdapterFactory())
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
            .experimentalSetParseAv1SampleDependencies(true)
            .experimentalSetLateThresholdToDropDecoderInputUs(
                MediaCodecVideoRenderer.DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US
            )
        return FrameAlignedVideoRenderer(builder)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class FrameAlignedVideoRenderer(
    builder: MediaCodecVideoRenderer.Builder
) : MediaCodecVideoRenderer(builder) {
    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean
    ): List<MediaCodecInfo> {
        val found = super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
        val ordered = orderDecodersForVideoFrame(found, format.width, format.height) { it.softwareOnly }
        Log.d(
            "Sinema",
            "Video decoder for ${format.width}x${format.height}: ${ordered.firstOrNull()?.name}"
        )
        return ordered
    }
}
