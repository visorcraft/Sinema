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
import com.bumptech.glide.Glide
import com.sinema.util.GlideAuth
import com.sinema.util.PlaybackQueue
import com.sinema.util.QueuePlanning
import com.sinema.util.SceneIntents
import com.sinema.util.TimeFormat
import com.sinema.util.TonightMix
import kotlinx.coroutines.launch

private const val VIDEO_OUTPUT_TIMEOUT_MS = 5_000L

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity : SinemaActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: AspectRatioPlayerView
    private var sceneId: String = ""
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var hasFallenBackToDirect = false
    private var forceAlignedHls = false
    private var hasVideoOutput = false
    private var resumePositionMs: Long = 0L
    @Volatile private var watchClock = PlaybackWatchClock()
    private val saveQueue = PlaybackSaveQueue(SinemaApp.instance.appScope)
    private val playCountSentSceneIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var captions: List<CaptionRef> = emptyList()
    private var markers: List<Pair<String, Double>> = emptyList()
    private val chaptersDialog = DialogSlot()
    private val aspectRatioDialog = DialogSlot()
    private val playerMenuDialog = DialogSlot()
    private val rateDialog = DialogSlot()
    private val streamQualityDialog = DialogSlot()
    private val resumePromptDialog = DialogSlot()
    private val upNextDialog = DialogSlot()
    /** Scenes already played in the current Endless Mix session. */
    private val endlessPlayedIds = mutableSetOf<String>()
    private var endlessJob: kotlinx.coroutines.Job? = null
    private val playbackErrorDialog = DialogSlot()
    /** True once the user picked Resume/Start-over or started playback manually. */
    private var resumePromptResolved = false
    /** Paused with the resume prompt outstanding (Back keeps it outstanding). */
    private var resumeAwaitingChoice = false
    /** First start: resolve metadata (and resume position when needed) before building the player. */
    private var autoResumePending = false
    /** Scene viewed (partly) under incognito — stays unrecorded. */
    private var incognitoLatchSceneId: String? = null
    private var suppressingPromptDismiss = false
    private var sceneSwitchJob: kotlinx.coroutines.Job? = null
    private var aspectRatio = PlaybackAspectRatio.AUTO
    private var streamQuality = PlaybackStreamQuality.AUTO
    private var recoveryQualityRestore: PlaybackStreamQuality? = null
    private var seekPreset = SeekPreset.TEN_THIRTY
    private var sceneTitle = ""
    private var previewBucket = -1L
    private var previewSceneId = ""
    private var nextThumbSceneId = ""
    private var isControllerVisible = false
    private val prefs by lazy { SinemaApp.instance.prefs }
    private val renderersFactory by lazy { PlaybackRenderersFactory(this) }
    private var isPaused = false
    private var scrubbing = false
    private var lastMarkerPositionMs: Long? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { playerView.hideController() }
    private val hideMarkerSkipRunnable = Runnable { hideMarkerSkip() }
    private val progressRunnable = object : Runnable {
        override fun run() {
            updatePlaybackText()
            updateMarkerSkip()
            handler.postDelayed(this, 500L)
        }
    }
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
        seekPreset = SeekPreset.fromName(prefs.playbackSeekPreset)
        sceneId = SceneIntents.sceneIdFrom(intent)
        videoWidth = SceneIntents.videoWidthFrom(intent)
        videoHeight = SceneIntents.videoHeightFrom(intent)
        resumePositionMs = SceneIntents.resumeMsFrom(intent)
        captions = SceneIntents.captionsFrom(intent)
        markers = SceneIntents.markersFrom(intent)
        if (SceneIntents.restoreSessionFrom(intent) && sceneId.isNotEmpty()) {
            resumePromptResolved = true
            autoResumePending = true
            resolveAutoResume(preservePosition = true)
        } else if (SceneIntents.autoResumeFrom(intent) && sceneId.isNotEmpty()) {
            autoResumePending = true
            resolveAutoResume()
        } else {
            autoResumePending = true
            loadInitialMetadata()
        }
        // A scene begun under incognito stays unrecorded even if the user
        // turns incognito off before leaving it.
        if (SinemaApp.instance.prefs.incognitoMode) incognitoLatchSceneId = sceneId

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
        // Every first start resolves full scene metadata before the player is
        // built; the coroutine owns that initial start.
        if (autoResumePending) return
        beginPlaybackSession()
    }

    private fun beginPlaybackSession(promptAllowed: Boolean = true) {
        startPlayback(promptAllowed)
        handler.post(progressRunnable)
        watchClock.onIsPlayingChanged(
            player?.isPlaying == true,
            android.os.SystemClock.elapsedRealtime()
        )
    }

    private fun startPlayback(promptAllowed: Boolean = true) {
        // Prompt whenever a meaningful saved position is still unresolved —
        // including after a lifecycle dismissal (backgrounding the dialog),
        // which must never silently approve resuming. Once resolved (choice
        // made or manual play), background restarts resume silently at the
        // live position onStop persisted.
        val promptResume = promptAllowed && !resumePromptResolved && shouldPromptResume(resumePositionMs)
        resumeAwaitingChoice = promptResume
        initPlayer(startPaused = promptResume)
        if (promptResume) showResumePrompt()
    }

    /** Maps full-scene metadata (captions, markers) shared by every switch path. */
    private fun applySceneMetadata(details: com.sinema.model.SceneDetails) {
        captions = details.scene.captions
        markers = details.markers.map { it.title.ifBlank { it.primaryTag } to it.seconds }
    }

    /** Fetch scene metadata before silent Watch Next or stored-session playback. */
    private fun resolveAutoResume(preservePosition: Boolean = false) {
        val targetId = sceneId
        lifecycleScope.launch {
            try {
                val details = SinemaApp.instance.api.findSceneFull(targetId)
                details?.let { d ->
                    sceneTitle = d.scene.displayTitle
                    videoWidth = d.scene.width
                    videoHeight = d.scene.height
                    if (!preservePosition) resumePositionMs = (d.scene.resumeTime * 1000).toLong()
                    applySceneMetadata(d)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline/unavailable: fall through and let the player surface
                // the error from position zero.
                android.util.Log.w("Sinema", "Auto-resume fetch failed for $targetId", e)
            }
            autoResumePending = false
            // Watch Next and explicit session restoration never re-prompt.
            resumePromptResolved = true
            if (isFinishing || isDestroyed) return@launch
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@launch
            beginPlaybackSession(promptAllowed = false)
        }
    }

    private fun persistPlaybackSession() {
        if (sceneId.isBlank()) return
        if (prefs.incognitoMode || incognitoLatchSceneId == sceneId) {
            prefs.playbackSessionJson = ""
            return
        }
        val exo = player
        val positionMs = (exo?.currentPosition ?: resumePositionMs).coerceAtLeast(0L)
        val durationMs = exo?.duration ?: 0L
        if (!com.sinema.util.PlaybackSessionRestore.shouldPersist(positionMs) ||
            (durationMs > 0L && durationMs - positionMs < 30_000L)
        ) {
            prefs.playbackSessionJson = ""
            return
        }
        val snapshot = PlaybackQueue.snapshot()
        val restoreQueue = PlaybackQueue.isActive && snapshot.items.getOrNull(snapshot.index)?.id == sceneId
        prefs.playbackSessionJson = com.sinema.util.PlaybackSessionCodec.encode(
            com.sinema.util.PlaybackSession(
                profileId = prefs.activeProfileId,
                sceneId = sceneId,
                title = sceneTitle,
                positionMs = positionMs,
                queueItems = if (restoreQueue) snapshot.items else emptyList(),
                queueIndex = if (restoreQueue) snapshot.index else -1
            )
        )
    }

    override fun onPause() {
        super.onPause()
        persistPlaybackSession()
        savePlayback()
        hideSeekPreview()
        dismissPlaybackDialogs()
    }


    /** Shared teardown for onPause/onStop: identical dialog/job cleanup. */
    private fun dismissPlaybackDialogs() {
        listOf(
            aspectRatioDialog, chaptersDialog, playerMenuDialog, rateDialog,
            streamQualityDialog, upNextDialog, playbackErrorDialog
        ).forEach { it.dismiss() }
        rateFetchJob?.cancel()
        rateFetchJob = null
        resumePromptDialog.dialog?.let { dialog ->
            suppressingPromptDismiss = true
            resumePromptDialog.dismiss()
            suppressingPromptDismiss = false
        }
    }

    override fun onStop() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(progressRunnable)
        super.onStop()
        dismissPlaybackDialogs()
        // Keep the in-session position so a restart resumes where the user
        // left off instead of the intent's original resume point. Always
        // update — leaving a stale value would jump backwards after seeks to
        // near the start, or forwards after seeking near the end.
        player?.let { exo ->
            val pos = exo.currentPosition
            val duration = exo.duration
            val nearEnd = duration > 0 && duration - pos < 30_000
            resumePositionMs = if (nearEnd) 0L else pos.coerceAtLeast(0L)
        }
        watchClock.onIsPlayingChanged(false, android.os.SystemClock.elapsedRealtime())
        releasePlayer()
        if (isFinishing) PlaybackQueue.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(progressRunnable)
        saveQueue.close()
        super.onDestroy()
    }

    private fun savePlayback() {
        val exo = player ?: return
        // Incognito: never write watch history (resume, play count, duration)
        // or feed Watch Next from this session. The latch keeps viewing that
        // happened *while* incognito was on unrecorded even if the user turns
        // it off again before leaving this scene.
        if (SinemaApp.instance.prefs.incognitoMode || incognitoLatchSceneId == sceneId) return
        // Snapshot scene state now: a queue switch must not move the save to a
        // different scene, API client, or watch clock while the write is queued.
        val savedSceneId = sceneId
        if (savedSceneId.isEmpty()) return
        val savedWatchClock = watchClock
        val nowMs = android.os.SystemClock.elapsedRealtime()
        savedWatchClock.onIsPlayingChanged(exo.isPlaying, nowMs)
        val totalPlayedMs = savedWatchClock.totalMs(nowMs)
        val pos = exo.currentPosition
        val duration = exo.duration
        val app = SinemaApp.instance

        val resumeTimeSec = if (duration > 0 && (duration - pos) < 30_000) {
            0.0
        } else if (pos > 5_000) {
            pos / 1000.0
        } else {
            return
        }

        // The app-scope FIFO drains after activity destruction without dropping
        // progress or allowing an older position to overwrite a newer one.
        val appCtx = applicationContext
        val api = app.api
        val profileId = app.prefs.activeProfileId
        saveQueue.enqueue {
            try {
                val playDurationMs = savedWatchClock.pendingMs(nowMs)
                api.saveSceneActivity(savedSceneId, resumeTimeSec, playDurationMs / 1000.0)
                savedWatchClock.markReported(playDurationMs)
                val shouldIncrement = resumeTimeSec == 0.0 && totalPlayedMs > 5_000L &&
                    savedSceneId !in playCountSentSceneIds
                if (shouldIncrement) {
                    api.incrementPlayCount(savedSceneId)
                    playCountSentSceneIds.add(savedSceneId)
                }
                // Refresh Watch Next only if the originating server is still active.
                if (resumeTimeSec > 0 && app.prefs.activeProfileId == profileId) {
                    val continuePairs = api.findContinuePlaying()
                    com.sinema.util.TvChannels.syncWatchNext(appCtx, continuePairs)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
            bindPlaybackControls()
            return
        }
        existing?.let {
            it.player = null
            container.removeView(it)
            // Fresh controller views: force the next-up thumbnail to reload.
            nextThumbSceneId = ""
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
            if (!isControllerVisible) hideSeekPreview()
        })
        bindPlaybackControls()
        android.util.Log.d(
            "Sinema",
            "${if (wantTexture) "TextureView" else "SurfaceView"} output for ${width}x$height"
        )
    }

    private fun loadInitialMetadata() {
        val requestedScene = sceneId
        lifecycleScope.launch {
            try {
                val details = SinemaApp.instance.api.findSceneFull(requestedScene)
                if (sceneId == requestedScene) {
                    details?.let { d ->
                        sceneTitle = d.scene.displayTitle
                        videoWidth = d.scene.width
                        videoHeight = d.scene.height
                        applySceneMetadata(d)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sinema", "Could not load playback metadata for $requestedScene", e)
            }
            autoResumePending = false
            if (sceneId != requestedScene || isFinishing || isDestroyed) return@launch
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@launch
            beginPlaybackSession()
        }
    }

    private fun bindPlaybackControls() {
        if (!::playerView.isInitialized) return
        SubtitleAppearance.apply(playerView, prefs)
        playerView.findViewById<android.widget.TextView>(R.id.playback_scene_title).text = sceneTitle
        playerView.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress).apply {
            setSceneMarkers(markers)
            setKeyTimeIncrements(seekPreset.backwardMs, seekPreset.forwardMs)
            scrubPreviewListener = object : MarkerTimeBar.ScrubPreviewListener {
                override fun onPreviewStart(positionMs: Long) {
                    scrubbing = true
                    hideMarkerSkip()
                    showSeekPreview(positionMs)
                }
                override fun onPreviewMove(positionMs: Long) = showSeekPreview(positionMs)
                override fun onPreviewStop() {
                    scrubbing = false
                    lastMarkerPositionMs = player?.currentPosition
                    hideSeekPreview()
                }
            }
        }
        playerView.findViewById<View>(R.id.aspect_ratio_button).setOnClickListener {
            showAspectRatioDialog()
        }
        playerView.findViewById<View>(R.id.playback_quality_button).setOnClickListener {
            showStreamQualityDialog()
        }
        playerView.findViewById<View>(R.id.playback_rate_button).setOnClickListener {
            showRateDialog()
        }
        playerView.findViewById<View>(R.id.playback_marker_button).setOnClickListener {
            seekToAdjacentMarker(true)
        }
        playerView.findViewById<View>(R.id.playback_prev_marker_button).setOnClickListener {
            seekToAdjacentMarker(false)
        }
        playerView.findViewById<View>(R.id.playback_next_marker_button).setOnClickListener {
            seekToAdjacentMarker(true)
        }
        playerView.findViewById<View>(R.id.playback_queue_chip).setOnClickListener {
            showUpNextDialog()
        }
        playerView.findViewById<View>(R.id.playback_next_up).setOnClickListener {
            showUpNextDialog()
        }
        playerView.findViewById<android.widget.Button>(R.id.playback_seek_preset_button).apply {
            text = "Seek: ${seekPreset.label}"
            setOnClickListener { cycleSeekPreset() }
        }
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_rew_with_amount).apply {
            contentDescription = "Rewind ${seekPreset.backwardMs / 1000} seconds"
            setOnClickListener { seekRelative(-seekPreset.backwardMs) }
        }
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_ffwd_with_amount).apply {
            contentDescription = "Forward ${seekPreset.forwardMs / 1000} seconds"
            setOnClickListener { seekRelative(seekPreset.forwardMs) }
        }
        updatePlaybackText()
    }

    private fun updatePlaybackText() {
        if (!::playerView.isInitialized) return
        playerView.findViewById<android.widget.TextView>(R.id.playback_scene_title).text = sceneTitle
        val exo = player
        playerView.findViewById<android.widget.TextView>(R.id.playback_remaining).text =
            if (exo == null) "" else formatRemainingMs(exo.currentPosition, exo.duration)
        playerView.findViewById<android.widget.TextView>(R.id.playback_queue_chip).apply {
            val remaining = PlaybackQueue.remainingCount()
            if (remaining > 0) {
                text = upNextChipLabel(remaining)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        playerView.findViewById<View>(R.id.playback_next_up).apply {
            val next = PlaybackQueue.remaining().firstOrNull()
            if (next == null) {
                visibility = View.GONE
                nextThumbSceneId = ""
            } else {
                visibility = View.VISIBLE
                playerView.findViewById<android.widget.TextView>(R.id.playback_next_title).text =
                    "▸ ${next.title}"
                if (nextThumbSceneId != next.id) {
                    nextThumbSceneId = next.id
                    val image = playerView.findViewById<android.widget.ImageView>(R.id.playback_next_thumb)
                    // Frame at ~25% beats a black 0-second screenshot.
                    val frameSec = next.durationSec * 0.25
                    Glide.with(image)
                        .load(GlideAuth.url(SinemaApp.instance.api,
                            SinemaApp.instance.api.getMarkerScreenshotUrl(next.id, frameSec)))
                        .centerCrop()
                        .into(image)
                }
            }
        }
        playerView.findViewById<View>(R.id.playback_marker_button).apply {
            isEnabled = markers.isNotEmpty()
            alpha = if (markers.isEmpty()) 0.5f else 1.0f
        }
    }

    private fun seekRelative(deltaMs: Long) {
        val exo = player ?: return
        val duration = exo.duration
        val target = (exo.currentPosition + deltaMs).coerceIn(
            0L,
            if (duration > 0L) duration else Long.MAX_VALUE
        )
        exo.seekTo(target)
        playerView.showController()
        updatePlaybackText()
    }

    private fun cycleSeekPreset() {
        seekPreset = seekPreset.next()
        prefs.playbackSeekPreset = seekPreset.name
        applySeekPreset()
        bindPlaybackControls()
        Toast.makeText(this, "Seek buttons ${seekPreset.label}", Toast.LENGTH_SHORT).show()
    }

    private fun applySeekPreset(target: ExoPlayer? = player) {
        target?.setSeekBackIncrementMs(seekPreset.backwardMs)
        target?.setSeekForwardIncrementMs(seekPreset.forwardMs)
    }

    private fun showSeekPreview(positionMs: Long) {
        if (!::playerView.isInitialized) return
        val exo = player ?: return
        val bucket = previewBucketMs(positionMs, exo.duration)
        val container = playerView.findViewById<View>(R.id.playback_preview_container)
        container.visibility = View.VISIBLE
        playerView.findViewById<android.widget.TextView>(R.id.playback_preview_time).text =
            TimeFormat.formatMs(bucket)
        if (bucket == previewBucket && previewSceneId == sceneId) return
        previewBucket = bucket
        previewSceneId = sceneId
        val image = playerView.findViewById<android.widget.ImageView>(R.id.playback_preview_image)
        Glide.with(image).clear(image)
        Glide.with(image)
            .load(GlideAuth.url(SinemaApp.instance.api,
                SinemaApp.instance.api.getMarkerScreenshotUrl(sceneId, bucket / 1000.0)))
            .centerCrop()
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (previewSceneId == sceneId && previewBucket == bucket) image.visibility = View.INVISIBLE
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (previewSceneId == sceneId && previewBucket == bucket) image.visibility = View.VISIBLE
                    return false
                }
            })
            .into(image)
    }

    private fun hideSeekPreview() {
        previewBucket = -1L
        previewSceneId = ""
        if (!::playerView.isInitialized) return
        playerView.findViewById<View>(R.id.playback_preview_container).visibility = View.GONE
        val image = playerView.findViewById<android.widget.ImageView>(R.id.playback_preview_image)
        Glide.with(image).clear(image)
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
        // A stream rebuild (quality switch/fallback) must not unpause playback
        // while the resume prompt is still outstanding.
        exo.playWhenReady = !resumeAwaitingChoice
        scheduleVideoOutputTimeout()
    }

    private fun initPlayer(startPaused: Boolean = false) {
        if (sceneId.isEmpty()) return
        hasVideoOutput = false
        mountPlayerView()
        playerView.playbackAspectRatio = aspectRatio
        val app = SinemaApp.instance

        // OkHttp data source shares the API client's TLS config (self-signed
        // opt-in) and auth headers for stream + subtitle requests.
        val dataSourceFactory = OkHttpDataSource.Factory(app.api.okHttpClient())
            .setDefaultRequestProperties(app.api.mediaAuthHeaders())

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)
        val preferredLanguages = com.sinema.model.SubtitleLanguage.fromCode(prefs.subtitleLanguage).code
            .takeIf { it.isNotBlank() }?.let { arrayOf(it) } ?: emptyArray()
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setPreferredTextLanguages(*preferredLanguages)
            .build()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exo ->
                applySeekPreset(exo)
                playerView.player = exo
                exo.setMediaItem(buildMediaItem())
                exo.prepare()
                if (resumePositionMs > 0) {
                    exo.seekTo(resumePositionMs)
                }
                exo.playWhenReady = !startPaused
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
                        watchClock.onIsPlayingChanged(isPlaying, android.os.SystemClock.elapsedRealtime())
                        if (isPlaying) {
                            // Any playback start (prompt choice or manual
                            // controller press) resolves the resume prompt.
                            resumePromptResolved = true
                            resumeAwaitingChoice = false
                        }
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
                            FallbackAction.TERMINAL -> {
                                android.util.Log.e("Sinema", "Playback failed for scene $sceneId", error)
                                showPlaybackRecovery(error)
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_ENDED) return
                        handler.removeCallbacks(videoOutputTimeout)
                        handler.removeCallbacks(hideRunnable)
                        // A scene switch (manual Up Next jump or an earlier
                        // auto-advance still fetching metadata) owns the
                        // queue now; the outgoing player's EOF must not
                        // consume another item or finish the activity.
                        if (sceneSwitchJob?.isActive == true) return
                        // Subsequent loop iterations re-fire STATE_ENDED; skip savePlayback
                        // because its finished-clear branch would overwrite the user's actual
                        // resume position with "completed".
                        if (exo.repeatMode == Player.REPEAT_MODE_ONE && sceneId in playCountSentSceneIds) return
                        savePlayback()
                        // Skip the queue advance when loop-one is on; Media3's
                        // auto-seek-to-0 will replay this video and the
                        // queue advance would race with it and break the loop.
                        if (exo.repeatMode != Player.REPEAT_MODE_OFF) return
                        val wasActive = PlaybackQueue.isActive
                        val next = PlaybackQueue.next()
                        if (next == null) {
                            if (wasActive && SinemaApp.instance.prefs.endlessMix) {
                                extendEndlessMix()
                                return
                            }
                            prefs.playbackSessionJson = ""
                            if (wasActive) finish()
                            return
                        }
                        playSceneNow(next.id, next.title)
                    }
                })
            }
    }

    private fun hideMarkerSkip() {
        handler.removeCallbacks(hideMarkerSkipRunnable)
        if (::playerView.isInitialized) {
            playerView.findViewById<View>(R.id.playback_skip_marker)?.visibility = View.GONE
            playerView.controllerShowTimeoutMs = hideDelayMs.toInt()
        }
    }

    private fun updateMarkerSkip() {
        val exo = player ?: return
        val currentMs = exo.currentPosition
        val previousMs = lastMarkerPositionMs
        lastMarkerPositionMs = currentMs
        if (scrubbing || !exo.isPlaying) {
            hideMarkerSkip()
            return
        }
        if (previousMs == null) return
        val marker = MarkerSkipState.entered(previousMs, currentMs, markers) ?: return
        if (MarkerSkipState.nextAfter(currentMs, markers) == null) return
        val button = playerView.findViewById<android.widget.Button>(R.id.playback_skip_marker) ?: return
        button.text = getString(
            R.string.playback_skip_marker,
            marker.first.ifBlank { getString(R.string.playback_marker_generic) }
        )
        button.setOnClickListener {
            val nowMs = player?.currentPosition ?: currentMs
            MarkerSkipState.nextAfter(nowMs, markers)?.let { targetMs -> player?.seekTo(targetMs) }
            lastMarkerPositionMs = player?.currentPosition
            hideMarkerSkip()
        }
        button.visibility = View.VISIBLE
        playerView.controllerShowTimeoutMs = 6_000
        playerView.showController()
        handler.removeCallbacks(hideMarkerSkipRunnable)
        handler.postDelayed(hideMarkerSkipRunnable, 6_000L)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(videoOutputTimeout)
        hideMarkerSkip()
        lastMarkerPositionMs = null
        player?.release()
        player = null
    }

    private fun showAspectRatioDialog() {
        if (aspectRatioDialog.isShowing) return
        val dialog = AspectRatioDialog.show(this, aspectRatio) { chosen ->
            aspectRatio = chosen
            prefs.playbackAspectRatio = chosen.name
            playerView.playbackAspectRatio = chosen
        }
        aspectRatioDialog.dialog = dialog
        dialog.setOnDismissListener {
            aspectRatioDialog.dismissIf(dialog)
            playerView.requestFocus()
        }
    }

    private fun showStreamQualityDialog() {
        if (streamQualityDialog.isShowing) return
        val dialog = StreamQualityDialog.show(this, streamQuality) { chosen ->
            if (chosen != streamQuality) applyStreamQuality(chosen)
        }
        streamQualityDialog.dialog = dialog
        dialog.setOnDismissListener {
            streamQualityDialog.dismissIf(dialog)
            playerView.requestFocus()
        }
    }

    private fun showPlaybackRecovery(error: androidx.media3.common.PlaybackException) {
        if (playbackErrorDialog.isShowing) return
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ||
            isFinishing || isDestroyed
        ) return
        resumePositionMs = player?.currentPosition?.takeIf { it > 0L } ?: resumePositionMs
        player?.playWhenReady = false
        val message = playbackRecoveryMessage(error.errorCode) +
            sceneTitle.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Playback problem")
            .setMessage(message)
            .setItems(PlaybackRecoveryAction.entries.map { it.label }.toTypedArray()) { _, which ->
                when (PlaybackRecoveryAction.entries[which]) {
                    PlaybackRecoveryAction.RETRY -> recoverPlayback()
                    PlaybackRecoveryAction.TRANSCODED_720P ->
                        recoverPlayback(PlaybackStreamQuality.HLS_720P)
                    PlaybackRecoveryAction.DIRECT -> recoverPlayback(PlaybackStreamQuality.DIRECT)
                }
            }
            .setNegativeButton("Back") { _, _ -> finish() }
            .setCancelable(false)
            .create()
        playbackErrorDialog.dialog = dialog
        dialog.setOnDismissListener {
            playbackErrorDialog.dismissIf(dialog)
        }
        dialog.show()
    }

    private fun recoverPlayback(temporaryQuality: PlaybackStreamQuality? = null) {
        playbackErrorDialog.dismiss()
        val selection = recoveryQualitySelection(streamQuality, recoveryQualityRestore, temporaryQuality)
        streamQuality = selection.quality
        recoveryQualityRestore = selection.restoreAfterScene
        restartCurrentStream()
    }

    /** Switch stream quality mid-playback, preserving the current position. */
    private fun applyStreamQuality(chosen: PlaybackStreamQuality) {
        streamQuality = chosen
        recoveryQualityRestore = null
        prefs.playbackStreamQuality = chosen.name
        restartCurrentStream()
        android.util.Log.i("Sinema", "Stream quality changed to ${chosen.name}")
    }

    private fun restartCurrentStream() {
        if (player == null) return
        forceAlignedHls = false
        hasFallenBackToDirect = false
        hasVideoOutput = false
        rebuildCurrentStream()
    }

    private fun showChapters() {
        if (markers.isEmpty()) return
        if (chaptersDialog.isShowing) return
        val scene = sceneId
        chaptersDialog.show(android.app.AlertDialog.Builder(this)
            .setTitle("Chapters")
            .setAdapter(ChapterListAdapter(this, SinemaApp.instance.api, scene, markers)) { _, which ->
                markers.getOrNull(which)?.let { player?.seekTo((it.second * 1000).toLong()) }
            }
            .setOnDismissListener { playerView.requestFocus() }
        )
    }

    private fun showPlayerMenu() {
        if (playerMenuDialog.isShowing) return
        val incognito = SinemaApp.instance.prefs.incognitoMode
        // Label→action pairs: localized labels are never identity keys.
        val entries = buildList {
            if (markers.isNotEmpty()) {
                add(getString(R.string.playback_menu_chapters) to Runnable { showChapters() })
            }
            add(getString(R.string.playback_menu_rate) to Runnable { showRateDialog() })
            add(getString(R.string.playback_menu_o) to Runnable { incrementOCount() })
            add(
                (if (incognito) "🕶 Incognito: ON" else "🕶 Incognito: OFF") to Runnable {
                    SinemaApp.instance.prefs.incognitoMode = !incognito
                    if (incognito) {
                        Toast.makeText(this@PlaybackActivity, "Incognito off", Toast.LENGTH_SHORT).show()
                    } else {
                        // Viewing from here on stays unrecorded for this scene
                        // even if incognito is disabled before exit.
                        incognitoLatchSceneId = sceneId
                        Toast.makeText(this@PlaybackActivity, "Incognito on — watch history paused", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            add(
                (if (SinemaApp.instance.prefs.endlessMix) "♾ Endless Mix: ON" else "♾ Endless Mix: OFF") to Runnable {
                    val enable = !SinemaApp.instance.prefs.endlessMix
                    SinemaApp.instance.prefs.endlessMix = enable
                    Toast.makeText(
                        this@PlaybackActivity,
                        getString(if (enable) R.string.endless_on_toast else R.string.endless_off_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            add(getString(R.string.playback_menu_aspect) to Runnable { showAspectRatioDialog() })
            add(getString(R.string.playback_menu_quality) to Runnable { showStreamQualityDialog() })
            add(getString(R.string.playback_menu_seek) to Runnable { cycleSeekPreset() })
        }
        playerMenuDialog.show(android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.playback_menu_title))
            .setItems(entries.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                entries[which].second.run()
            }
            .setOnDismissListener {
                playerMenuDialog.dialog?.let { d -> playerMenuDialog.dismissIf(d) }
                playerView.requestFocus()
            }
        )
    }

    private var rateFetchJob: kotlinx.coroutines.Job? = null

    /** Count one O for the scene currently loaded in the player. */
    private fun incrementOCount() {
        val current = sceneId
        if (current.isEmpty()) return
        // appScope: the mutation must survive an immediate back-press exit.
        // appScope runs on Dispatchers.IO — Toast needs the main thread.
        val app = SinemaApp.instance
        val appCtx = applicationContext
        app.appScope.launch {
            try {
                app.api.sceneIncrementO(current)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    Toast.makeText(appCtx, "O counted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "O count failed for $current", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    Toast.makeText(appCtx, "Failed to count O: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showRateDialog() {
        val current = sceneId
        if (current.isEmpty()) return
        if (rateDialog.isShowing || rateFetchJob?.isActive == true) return
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
            rateDialog.show(android.app.AlertDialog.Builder(this@PlaybackActivity)
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
                    rateDialog.dialog?.let { d -> rateDialog.dismissIf(d) }
                    playerView.requestFocus()
                }
        )
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

    /** Swap the player to [nextId] (queue advance or Up Next jump). */
    private fun playSceneNow(
        nextId: String,
        fallbackTitle: String = "",
        saveCurrent: Boolean = false
    ) {
        // Manual Up Next jumps leave without onPause, so progress for the
        // current scene must be saved here (auto-advance saves at STATE_ENDED).
        if (saveCurrent) savePlayback()
        // Any switch invalidates an open Up Next dialog's snapshot and the
        // chapters list (it belongs to the outgoing scene).
        upNextDialog.dismiss()
        playbackErrorDialog.dismiss()
        chaptersDialog.dismiss()
        // Serialize switches: a superseded jump must not interleave metadata
        // or sceneId updates with a newer one.
        sceneSwitchJob?.cancel()
        val app = SinemaApp.instance
        sceneSwitchJob = lifecycleScope.launch {
            var nextWidth = 0
            var nextHeight = 0
            var nextTitle = fallbackTitle
            try {
                val details = app.api.findSceneFull(nextId)
                nextWidth = details?.scene?.width ?: 0
                nextHeight = details?.scene?.height ?: 0
                nextTitle = details?.scene?.displayTitle ?: fallbackTitle
                details?.let { applySceneMetadata(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sinema", "Failed to fetch next scene metadata, playing without captions/markers", e)
                captions = emptyList()
                markers = emptyList()
            }
            if (isFinishing || isDestroyed) return@launch
            hideSeekPreview()
            endlessPlayedIds.add(nextId)
            incognitoLatchSceneId = if (SinemaApp.instance.prefs.incognitoMode) nextId else null
            sceneTitle = nextTitle
            sceneId = nextId
            videoWidth = nextWidth
            videoHeight = nextHeight
            val quality = qualityAfterRecoveredScene(streamQuality, recoveryQualityRestore)
            streamQuality = quality.quality
            recoveryQualityRestore = quality.restoreAfterScene
            hasFallenBackToDirect = false
            forceAlignedHls = false
            hasVideoOutput = false
            resumePositionMs = 0L
            watchClock = PlaybackWatchClock()
            releasePlayer()
            // The next lifecycle pause stores the new scene once it has a
            // meaningful position; never leave the outgoing scene restorable.
            prefs.playbackSessionJson = ""
            // Never prepare a player while stopped — the fetch can straddle
            // onStop (which releases the player); onStart then re-inits with
            // the already-swapped sceneId.
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                initPlayer()
            }
        }
    }

    /**
     * Endless Mix: an exhausted queue refills itself with fresh unwatched
     * favorites (falling back to unwatched recent scenes), skipping anything
     * already played this session. Stops cleanly when nothing is left.
     */
    private fun extendEndlessMix() {
        if (endlessJob?.isActive == true) return
        val app = SinemaApp.instance
        // lifecycleScope: extending requires this activity alive to continue
        // playback — closing it cancels the fetch instead of mutating the
        // shared queue from a dead session.
        endlessJob = lifecycleScope.launch {
            try {
                endlessPlayedIds.add(sceneId)
                val candidates = app.api.findMixCandidates(endlessPlayedIds)
                if (candidates.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        if (!isFinishing) {
                            Toast.makeText(applicationContext, getString(R.string.endless_nothing), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    return@launch
                }
                val batch = TonightMix.select(candidates)
                if (batch.isEmpty()) return@launch
                val next = batch.first()
                PlaybackQueue.start(QueuePlanning.toQueueItems(batch), startAt = 0)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (isFinishing || isDestroyed) return@withContext
                    playSceneNow(next.id, next.title)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sinema", "Endless Mix extension failed", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (!isFinishing) finish()
                }
            }
        }
    }

    private fun showResumePrompt() {
        if (resumePromptDialog.isShowing) return
        val exo = player ?: return
        val at = com.sinema.util.TimeFormat.formatMs(resumePositionMs)
        resumePromptDialog.show(android.app.AlertDialog.Builder(this)
            .setTitle("Resume playback")
            .setMessage("Resume at $at or start over?")
            .setPositiveButton("Resume") { _, _ ->
                resumePromptResolved = true
                resumeAwaitingChoice = false
                exo.seekTo(resumePositionMs)
                exo.playWhenReady = true
            }
            .setNeutralButton("Start over") { _, _ ->
                resumePromptResolved = true
                resumeAwaitingChoice = false
                exo.seekTo(0L)
                resumePositionMs = 0L
                exo.playWhenReady = true
            }
            .setOnDismissListener {
                // Back cancels: stay paused with controls shown; the choice
                // stays unresolved so a later restart re-prompts instead of
                // silently resuming. Lifecycle teardown dismissals are silent.
                if (!suppressingPromptDismiss) playerView.showController()
                playerView.requestFocus()
            }
        )
    }

    private fun showUpNextDialog() {
        if (upNextDialog.isShowing) return
        val remaining = PlaybackQueue.remaining()
        if (remaining.isEmpty()) {
            Toast.makeText(this, getString(R.string.up_next_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = UpNextAdapter(this) { target ->
            // Resolve by stable id through the live queue.
            if (PlaybackQueue.current()?.id == target.id) return@UpNextAdapter
            PlaybackQueue.jumpToItem(target.id)?.let {
                upNextDialog.dismiss()
                playSceneNow(it.id, it.title, saveCurrent = true)
            } ?: Toast.makeText(this, getString(R.string.up_next_not_in_queue), Toast.LENGTH_SHORT).show()
        }
        adapter.refresh()
        upNextDialog.show(android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.up_next_title, remaining.size))
            .setAdapter(adapter, null)
            .setNegativeButton("Unwatched only") { _, _ ->
                PlaybackQueue.filterRemainingUnwatched()
                val after = PlaybackQueue.remainingCount()
                if (after == 0) {
                    Toast.makeText(this, getString(R.string.up_next_unwatched_emptied), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.up_next_unwatched_count, after), Toast.LENGTH_SHORT).show()
                }
                updatePlaybackText()
            }
            .setNeutralButton("Shuffle") { _, _ ->
                if (PlaybackQueue.shuffleRemaining()) {
                    Toast.makeText(this, getString(R.string.up_next_shuffled, PlaybackQueue.remainingCount()), Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("Exit queue") { _, _ ->
                PlaybackQueue.clear()
                Toast.makeText(this, getString(R.string.up_next_cleared), Toast.LENGTH_SHORT).show()
                updatePlaybackText()
            }
            .setOnDismissListener { playerView.requestFocus() }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val seekWithDpad = PlaybackKeyRouting.shouldSeekToAdjacentMarker(
            controllerVisible = isControllerVisible,
            hasMarkers = markers.isNotEmpty(),
            playerSurfaceFocused = playerView.isFocused
        )
        when (keyCode) {
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
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        val exo = player ?: return
        if (exo.playWhenReady || exo.playbackState != Player.STATE_READY) return
        handler.removeCallbacks(hideRunnable)
        if (!isControllerVisible) playerView.showController()
    }
}
