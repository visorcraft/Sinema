package com.sinema.ui

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryTest {
    @Test
    fun `network failures explain reachability`() {
        val expected = "Can't reach Stash. Check the server and network, then retry."
        assertEquals(expected, playbackRecoveryMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(expected, playbackRecoveryMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test
    fun `http failures explain server response`() {
        val expected = "Stash couldn't provide the stream. Check your sign-in and server, then retry."
        assertEquals(expected, playbackRecoveryMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(expected, playbackRecoveryMessage(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE))
    }

    @Test
    fun `decoder failures recommend transcoding`() {
        val expected = "This TV can't decode the video. Try a transcoded stream."
        val errorCodes = listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )
        errorCodes.forEach { assertEquals(expected, playbackRecoveryMessage(it)) }
    }

    @Test
    fun `unsupported or malformed streams recommend another mode`() {
        val expected = "This TV can't read the stream. Try another stream mode."
        val errorCodes = listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
        )
        errorCodes.forEach { assertEquals(expected, playbackRecoveryMessage(it)) }
    }

    @Test
    fun `unknown failure offers generic recovery`() {
        assertEquals(
            "Playback was interrupted. Retry or try another stream mode.",
            playbackRecoveryMessage(PlaybackException.ERROR_CODE_UNSPECIFIED)
        )
    }

    @Test
    fun `recovery actions stay predictable`() {
        assertEquals(
            listOf("Retry", "Try transcoded 720p", "Try direct stream"),
            PlaybackRecoveryAction.entries.map { it.label }
        )
    }
}
