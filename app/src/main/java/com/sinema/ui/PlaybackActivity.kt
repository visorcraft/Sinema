package com.sinema.ui

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.util.SceneIntents
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity : FragmentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var sceneId: String = ""
    private var resumePositionMs: Long = 0L
    private var startTimeMs: Long = 0L
    private var playCountSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
        playerView = findViewById(R.id.player_view)
        sceneId = intent.getStringExtra("scene_id") ?: ""
        resumePositionMs = intent.getLongExtra("resume_position_ms", 0L)
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
        startTimeMs = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        savePlayback()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun savePlayback() {
        val exo = player ?: return
        if (sceneId.isEmpty()) return
        val pos = exo.currentPosition
        val duration = exo.duration
        val playDurationMs = System.currentTimeMillis() - startTimeMs
        val app = SinemaApp.instance

        // Calculate resume time in seconds
        val resumeTimeSec = if (duration > 0 && (duration - pos) < 30_000) {
            // Finished — clear resume
            0.0
        } else if (pos > 5_000) {
            pos / 1000.0
        } else {
            return // Watched less than 5 seconds, don't save anything
        }

        val playDurationSec = playDurationMs / 1000.0

        // Only increment play count once per viewing, when finishing
        // (resume cleared) — repeated pauses near the end must not
        // re-increment.
        val shouldIncrement = resumeTimeSec == 0.0 && playDurationSec > 5 && !playCountSent
        if (shouldIncrement) playCountSent = true

        // Run in the app scope: lifecycleScope is cancelled when the
        // activity is destroyed (the common back-press exit), which would
        // drop the save mid-flight and lose resume/watched state.
        app.appScope.launch {
            try {
                app.api.saveSceneActivity(sceneId, resumeTimeSec, playDurationSec)
                if (shouldIncrement) {
                    app.api.incrementPlayCount(sceneId)
                }
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "Failed to save playback state", e)
            }
        }
    }

    private fun buildMediaItem(): MediaItem {
        val app = SinemaApp.instance
        val streamUrl = app.api.getStreamUrl(sceneId)
        // Subtitle HTTP requests reuse the same dataSourceFactory → same auth headers automatically (both auth modes)
        val subs = SceneIntents.captionsFrom(intent).map { c ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(app.api.getCaptionUrl(sceneId, c)))
                .setMimeType(if (c.captionType.equals("vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(c.languageCode)
                .setLabel("${c.languageCode.uppercase()} (${c.captionType})")
                .build()
        }
        return MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(sceneId)
            .setSubtitleConfigurations(subs)
            .build()
    }

    private fun initPlayer() {
        if (sceneId.isEmpty()) return
        val app = SinemaApp.instance

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(app.api.mediaAuthHeaders())

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exo ->
                playerView.player = exo
                exo.setMediaItem(buildMediaItem())
                exo.prepare()
                if (resumePositionMs > 0) {
                    exo.seekTo(resumePositionMs)
                }
                exo.playWhenReady = true
            }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}
