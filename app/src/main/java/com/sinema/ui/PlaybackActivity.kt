package com.sinema.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.PlaybackAspectRatio
import com.sinema.model.PlaybackStreamQuality
import com.sinema.model.CaptionRef
import com.sinema.model.StarRating
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
    private var playerMenuDialog: android.app.AlertDialog? = null
    private var rateDialog: android.app.AlertDialog? = null
    private var streamQualityDialog: android.app.AlertDialog? = null
    private var aspectRatio = PlaybackAspectRatio.AUTO
    private var streamQuality = PlaybackStreamQuality.AUTO
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
        streamQuality = PlaybackStreamQuality.fromName(prefs.playbackStreamQuality)
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
        playerMenuDialog?.dismiss()
        playerMenuDialog = null
        rateFetchJob?.cancel()
        rateFetchJob = null
        rateDialog?.dismiss()
        rateDialog = null
        streamQualityDialog?.dismiss()
        streamQualityDialog = null
    }

    override fun onStop() {
        handler.removeCallbacks(hideRunnable)
        super.onStop()
        aspectRatioDialog?.dismiss()
        aspectRatioDialog = null
        chaptersDialog?.dismiss()
        chaptersDialog = null
        playerMenuDialog?.dismiss()
        playerMenuDialog = null
        rateFetchJob?.cancel()
        rateFetchJob = null
        rateDialog?.dismiss()
        rateDialog = null
        streamQualityDialog?.dismiss()
        streamQualityDialog = null
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
        val streamUrl = when {
            // Manual direct-only: never transcode.
            streamQuality == PlaybackStreamQuality.DIRECT -> app.api.getStreamUrl(sceneId)
            // Manual HLS tier: transcode at that resolution; if the player
            // already fell back to direct after an error, stay on direct.
            streamQuality.isForcedHls && !hasFallenBackToDirect ->
                app.api.getHlsStreamUrl(sceneId, streamQuality.resolutionParam!!)
            else -> {
                val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
                val useAligned = shouldUseAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)
                if (useAligned) app.api.getAlignedHlsStreamUrl(sceneId)
                else app.api.getStreamUrl(sceneId)
            }
        }
        android.util.Log.d(
            "Sinema",
            "Stream selection ${streamQuality.name} -> $streamUrl for ${videoWidth}x$videoHeight"
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
        // Only AUTO retries a silent direct stream; manual modes surface
        // errors via onPlayerError instead of looping retries.
        if (!allowsNoVideoRetry(streamQuality)) return
        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
        if (!shouldFallbackToAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)) return
        handler.postDelayed(videoOutputTimeout, VIDEO_OUTPUT_TIMEOUT_MS)
    }

    private fun fallbackToAlignedHls() {
        val exo = player ?: return
        if (!allowsNoVideoRetry(streamQuality)) return
        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
        if (!shouldFallbackToAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)) return
        handler.removeCallbacks(videoOutputTimeout)
        forceAlignedHls = true
        hasVideoOutput = false
        android.util.Log.w("Sinema", "Direct playback produced no video; retrying aligned HLS")
        rebuildCurrentStream()
    }

    /** Stops, rebuilds the media item at the current/resume position, and plays. */
    private fun rebuildCurrentStream() {
        val exo = player ?: return
        val positionMs = exo.currentPosition.takeIf { it > 0L } ?: resumePositionMs
        resumePositionMs = positionMs
        exo.stop()
        exo.clearMediaItems()
        mountPlayerView(videoWidth, videoHeight)
        exo.setMediaItem(buildMediaItem())
        exo.prepare()
        if (positionMs > 0L) exo.seekTo(positionMs)
        exo.playWhenReady = true
        scheduleVideoOutputTimeout()
    }

    private fun initPlayer() {
        if (sceneId.isEmpty()) return
        hasVideoOutput = false
        mountPlayerView()
        playerView.playbackAspectRatio = aspectRatio
        val app = SinemaApp.instance

        // OkHttp data source shares the API client's TLS config (self-signed
        // opt-in) and auth headers for stream + subtitle requests.
        val dataSourceFactory = OkHttpDataSource.Factory(app.api.okHttpClient())
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
                        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
                        when (errorFallbackAction(
                            streamQuality, originalKind, forceAlignedHls, hasFallenBackToDirect
                        )) {
                            FallbackAction.RETRY_DIRECT -> {
                                hasFallenBackToDirect = true
                                hasVideoOutput = false
                                android.util.Log.w("Sinema", "Stream failed; retrying direct", error)
                                rebuildCurrentStream()
                            }
                            FallbackAction.RETRY_ALIGNED_HLS -> {
                                forceAlignedHls = true
                                hasVideoOutput = false
                                android.util.Log.w("Sinema", "Stream failed; retrying aligned HLS", error)
                                rebuildCurrentStream()
                            }
                            FallbackAction.TERMINAL ->
                                android.util.Log.e("Sinema", "Playback failed for scene $sceneId", error)
                        }
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

    private fun showStreamQualityDialog() {
        if (streamQualityDialog?.isShowing == true) return
        val dialog = StreamQualityDialog.show(this, streamQuality) { chosen ->
            if (chosen != streamQuality) applyStreamQuality(chosen)
        }
        streamQualityDialog = dialog
        dialog.setOnDismissListener {
            if (streamQualityDialog === dialog) streamQualityDialog = null
            playerView.requestFocus()
        }
    }

    /** Switch stream quality mid-playback, preserving the current position. */
    private fun applyStreamQuality(chosen: PlaybackStreamQuality) {
        streamQuality = chosen
        prefs.playbackStreamQuality = chosen.name
        if (player == null) return
        // Reset fallback state so the new selection takes effect cleanly.
        forceAlignedHls = false
        hasFallenBackToDirect = false
        hasVideoOutput = false
        rebuildCurrentStream()
        android.util.Log.i("Sinema", "Stream quality changed to ${chosen.name}")
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

    /** Player menu on the MENU key: chapters (when present), rating, aspect ratio. */
    private fun showPlayerMenu() {
        if (playerMenuDialog?.isShowing == true) return
        val items = buildList {
            if (markers.isNotEmpty()) add("Chapters")
            add("★ Rate scene")
            add("Aspect ratio")
            add("Stream quality")
        }
        playerMenuDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Playback menu")
            .setItems(items.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                when (items[which]) {
                    "Chapters" -> showChapters()
                    "★ Rate scene" -> showRateDialog()
                    "Aspect ratio" -> showAspectRatioDialog()
                    "Stream quality" -> showStreamQualityDialog()
                }
            }
            .setOnDismissListener {
                if (playerMenuDialog === it) playerMenuDialog = null
                playerView.requestFocus()
            }
            .show()
    }

    private var rateFetchJob: kotlinx.coroutines.Job? = null

    private fun showRateDialog() {
        val current = sceneId
        if (current.isEmpty()) return
        if (rateDialog?.isShowing == true || rateFetchJob?.isActive == true) return
        rateFetchJob = lifecycleScope.launch {
            var stars = 0
            try {
                stars = StarRating.fromRating100(SinemaApp.instance.api.getSceneRating(current))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sinema", "Could not fetch rating before rate dialog", e)
            }
            // The fetch can outlive onPause/onStop dismissal; never show a
            // dialog on a stopped or finishing activity.
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ||
                isFinishing || isDestroyed
            ) return@launch
            val options = (0..StarRating.MAX_STARS).map { count ->
                if (count == 0) "No rating" else StarRating.glyphs(count)
            }
            rateDialog = android.app.AlertDialog.Builder(this@PlaybackActivity)
                .setTitle("Rate scene")
                .setSingleChoiceItems(options.toTypedArray(), stars) { dialog, which ->
                    dialog.dismiss()
                    val chosen = which
                    lifecycleScope.launch {
                        try {
                            SinemaApp.instance.api.setSceneRating(current, StarRating.toRating100(chosen))
                            Toast.makeText(
                                this@PlaybackActivity,
                                if (chosen > 0) "Rated ${StarRating.glyphs(chosen)}" else "Rating cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("Sinema", "Failed to set rating for $current", e)
                            Toast.makeText(this@PlaybackActivity,
                                "Failed to set rating: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setOnDismissListener {
                    if (rateDialog === it) rateDialog = null
                    playerView.requestFocus()
                }
                .show()
        }
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
                KeyEvent.KEYCODE_MENU -> { showPlayerMenu(); return true }
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
