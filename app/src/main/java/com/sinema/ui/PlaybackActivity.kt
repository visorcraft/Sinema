package com.sinema.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.PlaybackAspectRatio
import com.sinema.model.CaptionRef
import com.sinema.util.PlaybackQueue
import com.sinema.util.SceneIntents
import com.sinema.util.TimeFormat
import kotlinx.coroutines.launch

private const val VIDEO_OUTPUT_TIMEOUT_MS = 5_000L

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity : FragmentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: AspectRatioPlayerView
    private var sceneId: String = ""
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var hasFallenBackToDirect = false
    private var forceAlignedHls = false
    private var hasVideoOutput = false
    private var resumePositionMs: Long = 0L
    private var startTimeMs: Long = 0L
    private var playCountSent = false
    private var captions: List<CaptionRef> = emptyList()
    private var markers: List<Pair<String, Double>> = emptyList()
    private var chaptersDialog: android.app.AlertDialog? = null
    private var aspectRatioDialog: android.app.AlertDialog? = null
    private var aspectRatio = PlaybackAspectRatio.AUTO
    private var isControllerVisible = false
    private val prefs by lazy { SinemaApp.instance.prefs }
    private val renderersFactory by lazy { PlaybackRenderersFactory(this) }
    private var isPaused = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { playerView.hideController() }
    private val videoOutputTimeout = Runnable {
        val exo = player
        if (exo != null && shouldRetryWithoutVideoOutput(exo.playWhenReady, hasVideoOutput)) {
            fallbackToAlignedHls()
        }
    }
    private val hideDelayMs = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
        aspectRatio = PlaybackAspectRatio.fromName(prefs.playbackAspectRatio)
        sceneId = SceneIntents.sceneIdFrom(intent)
        videoWidth = SceneIntents.videoWidthFrom(intent)
        videoHeight = SceneIntents.videoHeightFrom(intent)
        resumePositionMs = SceneIntents.resumeMsFrom(intent)
        captions = SceneIntents.captionsFrom(intent)
        markers = SceneIntents.markersFrom(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val exo = player
                if (exo != null && !exo.playWhenReady && !isControllerVisible) {
                    playerView.showController()
                    handler.removeCallbacks(hideRunnable)
                    handler.postDelayed(hideRunnable, hideDelayMs)
                } else {
                    finish()
                }
            }
        })
        mountPlayerView()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
        startTimeMs = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        savePlayback()
        aspectRatioDialog?.dismiss()
        aspectRatioDialog = null
        chaptersDialog?.dismiss()
        chaptersDialog = null
    }

    override fun onStop() {
        handler.removeCallbacks(hideRunnable)
        super.onStop()
        aspectRatioDialog?.dismiss()
        aspectRatioDialog = null
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
        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
        val useAligned = shouldUseAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)
        val streamUrl = if (useAligned) {
            app.api.getAlignedHlsStreamUrl(sceneId)
        } else {
            app.api.getStreamUrl(sceneId)
        }
        android.util.Log.d(
            "Sinema",
            "Starting ${if (useAligned) PlaybackStreamKind.ALIGNED_HLS else PlaybackStreamKind.DIRECT} " +
                "for ${videoWidth}x$videoHeight"
        )
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

    private fun mountPlayerView(width: Int = videoWidth, height: Int = videoHeight) {
        val container = findViewById<FrameLayout>(R.id.player_container)
        val wantTexture = videoFrameNeedsSoftwareOutput(width, height)
        val existing = container.findViewById<AspectRatioPlayerView>(R.id.player_view)
        if (existing != null && (existing.videoSurfaceView is TextureView) == wantTexture) {
            playerView = existing
            return
        }
        existing?.let {
            it.player = null
            container.removeView(it)
        }
        val layout = if (wantTexture) R.layout.playback_player_texture else R.layout.playback_player_surface
        playerView = layoutInflater.inflate(layout, container, false) as AspectRatioPlayerView
        container.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        playerView.playbackAspectRatio = aspectRatio
        playerView.player = player
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            isControllerVisible = visibility == View.VISIBLE
        })
        playerView.findViewById<View>(R.id.aspect_ratio_button).setOnClickListener {
            showAspectRatioDialog()
        }
        android.util.Log.d(
            "Sinema",
            "${if (wantTexture) "TextureView" else "SurfaceView"} output for ${width}x$height"
        )
    }

    private fun scheduleVideoOutputTimeout() {
        handler.removeCallbacks(videoOutputTimeout)
        if (hasVideoOutput) return
        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
        if (!shouldFallbackToAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)) return
        handler.postDelayed(videoOutputTimeout, VIDEO_OUTPUT_TIMEOUT_MS)
    }

    private fun fallbackToAlignedHls() {
        val exo = player ?: return
        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
        if (!shouldFallbackToAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)) return
        handler.removeCallbacks(videoOutputTimeout)
        forceAlignedHls = true
        hasVideoOutput = false
        val retryPositionMs = exo.currentPosition.takeIf { it > 0L } ?: resumePositionMs
        android.util.Log.w("Sinema", "Direct playback produced no video; retrying aligned HLS")
        resumePositionMs = retryPositionMs
        exo.stop()
        exo.clearMediaItems()
        mountPlayerView(videoWidth, videoHeight)
        exo.setMediaItem(buildMediaItem())
        exo.prepare()
        if (retryPositionMs > 0L) exo.seekTo(retryPositionMs)
        exo.playWhenReady = true
    }

    private fun initPlayer() {
        if (sceneId.isEmpty()) return
        hasVideoOutput = false
        mountPlayerView()
        playerView.playbackAspectRatio = aspectRatio
        val app = SinemaApp.instance

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(app.api.mediaAuthHeaders())

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
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
                exo.repeatMode = if (prefs.loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                scheduleVideoOutputTimeout()
                exo.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (hasVideoOutputDimensions(videoSize.width, videoSize.height)) {
                            handler.removeCallbacks(videoOutputTimeout)
                            hasVideoOutput = true
                        }
                        android.util.Log.d(
                            "Sinema",
                            "Video size ${videoSize.width}x${videoSize.height}, " +
                                "pixelRatio=${videoSize.pixelWidthHeightRatio}, " +
                                "rotation=${exo.videoFormat?.rotationDegrees}, " +
                                "aspectRatio=$aspectRatio"
                        )
                        exo.videoFormat?.let { format ->
                            mountPlayerView(format.width, format.height)
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        prefs.loopEnabled = repeatMode == Player.REPEAT_MODE_ONE
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isPaused = !isPlaying
                        val exo = player
                        if (isPlaying) {
                            scheduleVideoOutputTimeout()
                        } else if (exo?.playWhenReady != true) {
                            handler.removeCallbacks(videoOutputTimeout)
                        }
                        val isEnded = exo != null && exo.playbackState == Player.STATE_ENDED
                        if (isPaused && !isEnded) {
                            handler.removeCallbacks(hideRunnable)
                            handler.postDelayed(hideRunnable, hideDelayMs)
                        } else {
                            handler.removeCallbacks(hideRunnable)
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        handler.removeCallbacks(videoOutputTimeout)
                        val streamKind = playbackStreamKindForFrame(videoWidth, videoHeight)
                        if (shouldFallbackToDirect(streamKind, hasFallenBackToDirect)) {
                            hasFallenBackToDirect = true
                            hasVideoOutput = false
                            val retryPositionMs = exo.currentPosition.takeIf { it > 0L } ?: resumePositionMs
                            android.util.Log.w("Sinema", "Aligned HLS failed; retrying direct", error)
                            resumePositionMs = retryPositionMs
                            exo.stop()
                            exo.clearMediaItems()
                            mountPlayerView(videoWidth, videoHeight)
                            exo.setMediaItem(buildMediaItem())
                            exo.prepare()
                            if (retryPositionMs > 0L) exo.seekTo(retryPositionMs)
                            exo.playWhenReady = true
                            return
                        }
                        if (shouldFallbackToAlignedHls(streamKind, forceAlignedHls, hasFallenBackToDirect)) {
                            fallbackToAlignedHls()
                            return
                        }
                        android.util.Log.e("Sinema", "Playback failed for scene $sceneId", error)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_ENDED) return
                        handler.removeCallbacks(videoOutputTimeout)
                        handler.removeCallbacks(hideRunnable)
                        // Subsequent loop iterations re-fire STATE_ENDED; skip savePlayback
                        // because its finished-clear branch would overwrite the user's actual
                        // resume position with "completed".
                        if (exo.repeatMode == Player.REPEAT_MODE_ONE && playCountSent) return
                        savePlayback()
                        // Skip the queue advance when loop-one is on; Media3's
                        // auto-seek-to-0 will replay this video and the
                        // queue advance would race with it and break the loop.
                        if (exo.repeatMode != Player.REPEAT_MODE_OFF) return
                        val wasActive = PlaybackQueue.isActive
                        val nextId = PlaybackQueue.next()
                        if (nextId == null) {
                            if (wasActive) finish()
                            return
                        }
                        lifecycleScope.launch {
                            var nextWidth = 0
                            var nextHeight = 0
                            try {
                                val details = app.api.findSceneFull(nextId)
                                captions = details?.scene?.captions ?: emptyList()
                                markers = details?.markers?.map { it.title.ifBlank { it.primaryTag } to it.seconds } ?: emptyList()
                                nextWidth = details?.scene?.width ?: 0
                                nextHeight = details?.scene?.height ?: 0
                            } catch (e: Exception) {
                                android.util.Log.w("Sinema", "Failed to fetch next scene metadata, playing without captions/markers", e)
                                captions = emptyList()
                                markers = emptyList()
                            }
                            if (isFinishing) return@launch
                            sceneId = nextId
                            videoWidth = nextWidth
                            videoHeight = nextHeight
                            hasFallenBackToDirect = false
                            forceAlignedHls = false
                            hasVideoOutput = false
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
        handler.removeCallbacks(videoOutputTimeout)
        player?.release()
        player = null
    }

    private fun showAspectRatioDialog() {
        if (aspectRatioDialog?.isShowing == true) return
        val dialog = AspectRatioDialog.show(this, aspectRatio) { chosen ->
            aspectRatio = chosen
            prefs.playbackAspectRatio = chosen.name
            playerView.playbackAspectRatio = chosen
        }
        aspectRatioDialog = dialog
        dialog.setOnDismissListener {
            if (aspectRatioDialog === dialog) aspectRatioDialog = null
            playerView.requestFocus()
        }
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
            val seekWithDpad = PlaybackKeyRouting.shouldSeekToAdjacentMarker(
                controllerVisible = isControllerVisible,
                hasMarkers = markers.isNotEmpty(),
                playerSurfaceFocused = playerView.isFocused
            )
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> { showChapters(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { seekToAdjacentMarker(true); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { seekToAdjacentMarker(false); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (seekWithDpad) {
                        seekToAdjacentMarker(true); return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (seekWithDpad) {
                        seekToAdjacentMarker(false); return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        val exo = player ?: return
        if (exo.playWhenReady || exo.playbackState != Player.STATE_READY) return
        handler.removeCallbacks(hideRunnable)
        if (!isControllerVisible) playerView.showController()
    }
}
