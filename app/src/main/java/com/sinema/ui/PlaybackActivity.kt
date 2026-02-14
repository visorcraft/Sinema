package com.sinema.ui

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sinema.R
import com.sinema.SinemaApp
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity : FragmentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var sceneId: String = ""
    private var resumePositionMs: Long = 0L
    private var startTimeMs: Long = 0L

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

        lifecycleScope.launch {
            try {
                app.api.saveSceneActivity(sceneId, resumeTimeSec, playDurationSec)
                // Only increment play count when finishing (resume cleared), not on pause/resume
                if (resumeTimeSec == 0.0 && playDurationSec > 5) {
                    app.api.incrementPlayCount(sceneId)
                }
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "Failed to save playback state", e)
            }
        }
    }

    private fun initPlayer() {
        if (sceneId.isEmpty()) return
        val app = SinemaApp.instance
        val streamUrl = app.api.getStreamUrl(sceneId)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("ApiKey" to app.prefs.apiKey))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exo ->
                playerView.player = exo
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
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
