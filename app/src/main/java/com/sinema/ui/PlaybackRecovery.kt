package com.sinema.ui

import androidx.media3.common.PlaybackException
import com.sinema.model.PlaybackStreamQuality

internal enum class PlaybackRecoveryAction(val label: String) {
    RETRY("Retry"),
    TRANSCODED_720P("Try transcoded 720p"),
    DIRECT("Try direct stream")
}

internal fun playbackRecoveryMessage(errorCode: Int): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Can't reach Stash. Check the server and network, then retry."

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
        "Stash couldn't provide the stream. Check your sign-in and server, then retry."

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        "This TV can't decode the video. Try a transcoded stream."

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "This TV can't read the stream. Try another stream mode."

    else -> "Playback was interrupted. Retry or try another stream mode."
}

internal data class RecoveryQualitySelection(
    val quality: PlaybackStreamQuality,
    val restoreAfterScene: PlaybackStreamQuality?
)

internal fun recoveryQualitySelection(
    current: PlaybackStreamQuality,
    restoreAfterScene: PlaybackStreamQuality?,
    temporary: PlaybackStreamQuality?
): RecoveryQualitySelection = if (temporary == null) {
    RecoveryQualitySelection(current, restoreAfterScene)
} else {
    RecoveryQualitySelection(temporary, restoreAfterScene ?: current)
}

internal fun qualityAfterRecoveredScene(
    current: PlaybackStreamQuality,
    restoreAfterScene: PlaybackStreamQuality?
): RecoveryQualitySelection = RecoveryQualitySelection(restoreAfterScene ?: current, null)
