package com.sinema.ui

import com.sinema.model.PlaybackStreamQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDecodersTest {
    private data class Decoder(val name: String, val software: Boolean)

    private fun order(vararg decoders: Decoder, width: Int, height: Int) =
        orderDecodersForVideoFrame(decoders.toList(), width, height) { it.software }

    @Test
    fun `unaligned frame puts software decoders first`() {
        val ordered = order(
            Decoder("c2.allwinner.avc.decoder", software = false),
            Decoder("c2.android.avc.decoder", software = true),
            width = 404,
            height = 720
        )
        assertEquals(listOf("c2.android.avc.decoder", "c2.allwinner.avc.decoder"), ordered.map { it.name })
    }

    @Test
    fun `either dimension not divisible by 8 is unaligned`() {
        val ordered = order(
            Decoder("hw", software = false),
            Decoder("sw", software = true),
            width = 708,
            height = 960
        )
        assertEquals("sw", ordered.first().name)
    }

    @Test
    fun `ordinary hd frames keep the platform decoder order`() {
        val input = listOf(
            Decoder("c2.allwinner.avc.decoder", software = false),
            Decoder("c2.android.avc.decoder", software = true)
        )
        assertEquals(input, orderDecodersForVideoFrame(input, 1920, 1080) { it.software })
        assertEquals(input, orderDecodersForVideoFrame(input, 1280, 720) { it.software })
        assertEquals(
            listOf("c2.android.avc.decoder", "c2.allwinner.avc.decoder"),
            orderDecodersForVideoFrame(input, 1080, 1920) { it.software }.map { it.name }
        )
    }

    @Test
    fun `only an unaligned known frame needs software output`() {
        assertEquals(true, videoFrameNeedsSoftwareOutput(404, 720))
        assertEquals(true, videoFrameNeedsSoftwareOutput(708, 940))
        assertEquals(true, videoFrameNeedsSoftwareOutput(1080, 1920))
        assertEquals(false, videoFrameNeedsSoftwareOutput(1920, 1080))
        assertEquals(false, videoFrameNeedsSoftwareOutput(1280, 720))
        assertEquals(false, videoFrameNeedsSoftwareOutput(0, 0))
    }

    @Test
    fun `unknown frame size keeps the platform decoder order`() {
        val input = listOf(Decoder("hw", false), Decoder("sw", true))
        assertEquals(input, orderDecodersForVideoFrame(input, 0, 0) { it.software })
        assertEquals(input, orderDecodersForVideoFrame(input, -1, -1) { it.software })
    }

    @Test
    fun `unaligned frame without a software decoder stays on hardware`() {
        val input = listOf(Decoder("hw", false))
        assertEquals(input, orderDecodersForVideoFrame(input, 404, 720) { it.software })
    }

    @Test
    fun `relative order inside each group is preserved`() {
        val ordered = order(
            Decoder("hw-a", software = false),
            Decoder("sw-a", software = true),
            Decoder("hw-b", software = false),
            Decoder("sw-b", software = true),
            width = 404,
            height = 720
        )
        assertEquals(listOf("sw-a", "sw-b", "hw-a", "hw-b"), ordered.map { it.name })
    }

    @Test
    fun `only incompatible originals select aligned hls`() {
        assertEquals(PlaybackStreamKind.ALIGNED_HLS, playbackStreamKindForFrame(1080, 1920))
        assertEquals(PlaybackStreamKind.ALIGNED_HLS, playbackStreamKindForFrame(404, 720))
        assertEquals(PlaybackStreamKind.DIRECT, playbackStreamKindForFrame(1280, 720))
        assertEquals(PlaybackStreamKind.DIRECT, playbackStreamKindForFrame(1920, 1080))
        assertEquals(PlaybackStreamKind.DIRECT, playbackStreamKindForFrame(0, 0))
    }

    @Test
    fun `only the first aligned hls failure falls back to direct`() {
        assertEquals(true, shouldFallbackToDirect(PlaybackStreamKind.ALIGNED_HLS, false))
        assertEquals(false, shouldFallbackToDirect(PlaybackStreamKind.ALIGNED_HLS, true))
        assertEquals(false, shouldFallbackToDirect(PlaybackStreamKind.DIRECT, false))
    }

    @Test
    fun `direct original can fall back to aligned hls once`() {
        assertEquals(true, shouldFallbackToAlignedHls(PlaybackStreamKind.DIRECT, false, false))
        assertEquals(false, shouldFallbackToAlignedHls(PlaybackStreamKind.DIRECT, true, false))
        assertEquals(false, shouldFallbackToAlignedHls(PlaybackStreamKind.DIRECT, false, true))
        assertEquals(false, shouldFallbackToAlignedHls(PlaybackStreamKind.ALIGNED_HLS, false, false))
    }

    @Test
    fun `fallback state selects a stream without looping`() {
        assertEquals(false, shouldUseAlignedHls(PlaybackStreamKind.DIRECT, false, false))
        assertEquals(true, shouldUseAlignedHls(PlaybackStreamKind.DIRECT, true, false))
        assertEquals(false, shouldUseAlignedHls(PlaybackStreamKind.DIRECT, true, true))
        assertEquals(true, shouldUseAlignedHls(PlaybackStreamKind.ALIGNED_HLS, false, false))
        assertEquals(false, shouldUseAlignedHls(PlaybackStreamKind.ALIGNED_HLS, false, true))
    }

    @Test
    fun `requested playback without video retries before decoder becomes ready`() {
        assertEquals(true, shouldRetryWithoutVideoOutput(playWhenReady = true, hasVideoOutput = false))
        assertEquals(false, shouldRetryWithoutVideoOutput(playWhenReady = false, hasVideoOutput = false))
        assertEquals(false, shouldRetryWithoutVideoOutput(playWhenReady = true, hasVideoOutput = true))
    }

    @Test
    fun `only nonzero video dimensions count as output`() {
        assertEquals(true, hasVideoOutputDimensions(1920, 1080))
        assertEquals(false, hasVideoOutputDimensions(0, 0))
        assertEquals(false, hasVideoOutputDimensions(1920, 0))
        assertEquals(false, hasVideoOutputDimensions(0, 1080))
    }

    @Test
    fun `direct-only never falls back on error`() {
        // Any shape, any state: DIRECT is terminal.
        PlaybackStreamKind.entries.forEach { kind ->
            assertEquals(
                FallbackAction.TERMINAL,
                errorFallbackAction(PlaybackStreamQuality.DIRECT, kind, forceAlignedHls = false, hasFallenBackToDirect = false)
            )
            assertEquals(
                FallbackAction.TERMINAL,
                errorFallbackAction(PlaybackStreamQuality.DIRECT, kind, forceAlignedHls = true, hasFallenBackToDirect = true)
            )
        }
    }

    @Test
    fun `forced hls retries direct exactly once regardless of source shape`() {
        // Direct-compatible shape (kind=DIRECT): the shape heuristic alone
        // would suggest an HLS retry; the manual quality overrides it.
        assertEquals(
            FallbackAction.RETRY_DIRECT,
            errorFallbackAction(PlaybackStreamQuality.HLS_720P, PlaybackStreamKind.DIRECT, forceAlignedHls = false, hasFallenBackToDirect = false)
        )
        // Software-output shape (kind=ALIGNED_HLS): also retries direct once.
        assertEquals(
            FallbackAction.RETRY_DIRECT,
            errorFallbackAction(PlaybackStreamQuality.HLS_1080P, PlaybackStreamKind.ALIGNED_HLS, forceAlignedHls = false, hasFallenBackToDirect = false)
        )
        // Second failure is terminal.
        assertEquals(
            FallbackAction.TERMINAL,
            errorFallbackAction(PlaybackStreamQuality.HLS_720P, PlaybackStreamKind.ALIGNED_HLS, forceAlignedHls = false, hasFallenBackToDirect = true)
        )
    }

    @Test
    fun `auto keeps the shape-based state machine`() {
        assertEquals(
            FallbackAction.RETRY_DIRECT,
            errorFallbackAction(PlaybackStreamQuality.AUTO, PlaybackStreamKind.ALIGNED_HLS, forceAlignedHls = false, hasFallenBackToDirect = false)
        )
        assertEquals(
            FallbackAction.RETRY_ALIGNED_HLS,
            errorFallbackAction(PlaybackStreamQuality.AUTO, PlaybackStreamKind.DIRECT, forceAlignedHls = false, hasFallenBackToDirect = false)
        )
        assertEquals(
            FallbackAction.TERMINAL,
            errorFallbackAction(PlaybackStreamQuality.AUTO, PlaybackStreamKind.ALIGNED_HLS, forceAlignedHls = false, hasFallenBackToDirect = true)
        )
    }

    @Test
    fun `only auto retries on missing video output`() {
        assertEquals(true, allowsNoVideoRetry(PlaybackStreamQuality.AUTO))
        assertEquals(false, allowsNoVideoRetry(PlaybackStreamQuality.DIRECT))
        assertEquals(false, allowsNoVideoRetry(PlaybackStreamQuality.HLS_480P))
        assertEquals(false, allowsNoVideoRetry(PlaybackStreamQuality.HLS_1080P))
    }
}
