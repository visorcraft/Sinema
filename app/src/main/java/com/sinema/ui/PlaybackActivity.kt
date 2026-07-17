package com.sinema.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.CaptionRef
import com.sinema.util.PlaybackQueue
import com.sinema.util.SceneIntents
import com.sinema.util.TimeFormat
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity : FragmentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var sceneId: String = ""
    private var resumePositionMs: Long = 0L
    private var startTimeMs: Long = 0L
    private var playCountSent = false
    private var captions: List<CaptionRef> = emptyList()
    private var markers: List<Pair<String, Double>> = emptyList()
    private var chaptersDialog: android.app.AlertDialog? = null
    private var isControllerVisible = false
    private var isPaused = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { playerView.hideController() }
    private val hideDelayMs = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
        playerView = findViewById(R.id.player_view)
        sceneId = SceneIntents.sceneIdFrom(intent)
        resumePositionMs = SceneIntents.resumeMsFrom(intent)
        captions = SceneIntents.captionsFrom(intent)
        markers = SceneIntents.markersFrom(intent)
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
        startTimeMs = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        savePlayback()
        chaptersDialog?.dismiss()
        chaptersDialog = null
    }

    override fun onStop() {
        handler.removeCallbacks(hideRunnable)
        super.onStop()
        chaptersDialog?.dismiss()
        chaptersDialog = null
        releasePlayer()
        if (isFinishing) PlaybackQueue.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        super.onDestroy()
    }

    private fun savePlayback() {
        val exo = player ?: return
        // Snapshot the id now: auto-advance reassigns sceneId synchronously
        // after this call, but the save below runs asynchronously, so reading
        // the field there could attribute the save to the next scene.
        val savedSceneId = sceneId
        if (savedSceneId.isEmpty()) return
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
        val appCtx = applicationContext
        app.appScope.launch {
            try {
                app.api.saveSceneActivity(savedSceneId, resumeTimeSec, playDurationSec)
                if (shouldIncrement) {
                    app.api.incrementPlayCount(savedSceneId)
                }
                // Refresh Watch Next after saving playback state
                if (resumeTimeSec > 0) {
                    val continuePairs = app.api.findContinuePlaying()
                    com.sinema.util.TvChannels.syncWatchNext(appCtx, continuePairs)
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
        val subs = captions.map { c ->
            val mime = when (c.captionType.lowercase()) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(app.api.getCaptionUrl(sceneId, c)))
                .setMimeType(mime)
                .setLanguage(c.languageCode.takeIf { it.isNotBlank() })
                .setLabel("${c.languageCode.uppercase().ifBlank { "UNKNOWN" }} (${c.captionType})")
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
                playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    isControllerVisible = visibility == View.VISIBLE
                })
                exo.setMediaItem(buildMediaItem())
                exo.prepare()
                if (resumePositionMs > 0) {
                    exo.seekTo(resumePositionMs)
                }
                exo.playWhenReady = true
                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isPaused = !isPlaying
                        if (isPaused) {
                            handler.removeCallbacks(hideRunnable)
                            handler.postDelayed(hideRunnable, hideDelayMs)
                        } else {
                            handler.removeCallbacks(hideRunnable)
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_ENDED) return
                        savePlayback()
                        val wasActive = PlaybackQueue.isActive
                        val nextId = PlaybackQueue.next()
                        if (nextId == null) {
                            if (wasActive) finish()
                            return
                        }
                        lifecycleScope.launch {
                            try {
                                val details = app.api.findSceneFull(nextId)
                                captions = details?.scene?.captions ?: emptyList()
                                markers = details?.markers?.map { it.title.ifBlank { it.primaryTag } to it.seconds } ?: emptyList()
                            } catch (e: Exception) {
                                android.util.Log.w("Sinema", "Failed to fetch next scene metadata, playing without captions/markers", e)
                                captions = emptyList()
                                markers = emptyList()
                            }
                            if (isFinishing) return@launch
                            sceneId = nextId
                            resumePositionMs = 0L
                            playCountSent = false
                            startTimeMs = System.currentTimeMillis()
                            releasePlayer()
                            initPlayer()
                        }
                    }
                })
            }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun showChapters() {
        if (markers.isEmpty()) return
        if (chaptersDialog?.isShowing == true) return
        val labels = markers.map { "${TimeFormat.formatSeconds(it.second)}  ${it.first}" }
        chaptersDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Chapters")
            .setItems(labels.toTypedArray()) { _, which ->
                player?.seekTo((markers[which].second * 1000).toLong())
            }
            .setOnDismissListener { playerView.requestFocus() }
            .show()
    }

    private fun seekToAdjacentMarker(forward: Boolean) {
        val exo = player ?: return
        val posSec = exo.currentPosition / 1000.0
        val target = if (forward)
            markers.firstOrNull { it.second > posSec + 3 }
        else
            markers.lastOrNull { it.second < posSec - 3 }
        target?.let { exo.seekTo((it.second * 1000).toLong()) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> { showChapters(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { seekToAdjacentMarker(true); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { seekToAdjacentMarker(false); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isControllerVisible && markers.isNotEmpty()) {
                        seekToAdjacentMarker(true); return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isControllerVisible && markers.isNotEmpty()) {
                        seekToAdjacentMarker(false); return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!isPaused) return
        handler.removeCallbacks(hideRunnable)
        if (!isControllerVisible) playerView.showController()
    }
}
