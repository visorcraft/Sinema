package com.sinema.ui

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
}
