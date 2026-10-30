package com.sinema.ui

/**
 * The hardware AVC decoder on this Android TV (`c2.allwinner.avc.decoder`)
 * advertises 2×2 alignment, then times out in `fetchGraphicBlock` unless the
 * frame width is a multiple of 16 and the height is a multiple of 8.
 * 1920×1080 and 1280×720 pass. 404×720 and portrait 1080×1920 do not.
 * Telling the codec the padded coded size (1088×1920 for a 1080×1920 frame)
 * makes it publish a bogus crop and the picture stays black, so those frames
 * use the platform software decoder. The display tunnel drops that decoder's
 * buffers, so the picture is drawn through a TextureView.
 * Requiring 16 of both sides would also demote landscape 1080p, because 1080
 * is not a multiple of 16.
 */
internal const val VIDEO_DECODER_WIDTH_BLOCK = 16
internal const val VIDEO_DECODER_HEIGHT_BLOCK = 8

internal fun videoFrameNeedsSoftwareOutput(width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0) return false
    return width % VIDEO_DECODER_WIDTH_BLOCK != 0 || height % VIDEO_DECODER_HEIGHT_BLOCK != 0
}

internal fun <T> orderDecodersForVideoFrame(
    decoders: List<T>,
    width: Int,
    height: Int,
    softwareOnly: (T) -> Boolean
): List<T> {
    if (!videoFrameNeedsSoftwareOutput(width, height)) return decoders
    if (decoders.none(softwareOnly)) return decoders
    return decoders.sortedBy { if (softwareOnly(it)) 0 else 1 }
}

internal enum class PlaybackStreamKind {
    DIRECT,
    ALIGNED_HLS
}

internal fun playbackStreamKindForFrame(width: Int, height: Int): PlaybackStreamKind =
    if (videoFrameNeedsSoftwareOutput(width, height)) {
        PlaybackStreamKind.ALIGNED_HLS
    } else {
        PlaybackStreamKind.DIRECT
    }
