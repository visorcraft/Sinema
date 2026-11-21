package com.sinema.ui

import android.content.Intent
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
    internal var player: ExoPlayer? = null
    internal lateinit var playerView: AspectRatioPlayerView
    internal var sceneId: String = ""
    internal var videoWidth: Int = 0
    internal var videoHeight: Int = 0
    internal var hasFallenBackToDirect = false
    internal var forceAlignedHls = false
    internal var hasVideoOutput = false
    internal var resumePositionMs: Long = 0L
    internal var resumeConfirmed = false
    @Volatile internal var watchClock = PlaybackWatchClock()
    internal val saveQueue = PlaybackSaveQueue(SinemaApp.instance.appScope)
    internal val playCountSentSceneIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    internal var captions: List<CaptionRef> = emptyList()
    internal var markers: List<Pair<String, Double>> = emptyList()
    internal val chaptersDialog = DialogSlot()
    internal val aspectRatioDialog = DialogSlot()
    internal val settingsMenuDialog = DialogSlot()
    internal val personalizationMenuDialog = DialogSlot()
    internal val rateDialog = DialogSlot()
    internal var rateFetchJob: kotlinx.coroutines.Job? = null
    internal val streamQualityDialog = DialogSlot()
    internal val resumePromptDialog = DialogSlot()
    internal val upNextDialog = DialogSlot()
    /** Scenes already played in the current Endless Mix session. */
    internal val endlessPlayedIds = mutableSetOf<String>()
    internal var endlessJob: kotlinx.coroutines.Job? = null
    internal val playbackErrorDialog = DialogSlot()
    /** True once the user picked Resume/Start-over or started playback manually. */
    internal var resumePromptResolved = false
    /** Paused with the resume prompt outstanding (Back keeps it outstanding). */
    internal var resumeAwaitingChoice = false
    /** First start: resolve metadata (and resume position when needed) before building the player. */
    internal var autoResumePending = false
    /** Scene viewed (partly) under incognito — stays unrecorded. */
    internal var incognitoLatchSceneId: String? = null
    internal var suppressingPromptDismiss = false
    internal var sceneSwitchJob: kotlinx.coroutines.Job? = null
    internal var aspectRatio = PlaybackAspectRatio.AUTO
    internal var streamQuality = PlaybackStreamQuality.AUTO
    internal var recoveryQualityRestore: PlaybackStreamQuality? = null
    internal var seekPreset = SeekPreset.TEN_THIRTY
    internal var sceneTitle = ""
    /**
     * Parent directory once lookup has settled. Null while the current scene's
     * path is still unknown, so the folder button cannot claim it is missing.
     */
    internal var sceneFolder: String? = null
    /** Folder screen is up. Not an end-of-video: keep the position and play state. */
    internal var folderDetour = false
    internal var resumePausedAfterFolder = false
    /** playWhenReady captured before the player is paused for a covered activity. */
    internal var heldPlayWhenStopped = false
    internal var nextThumbSceneId = ""
    internal var isControllerVisible = false
    internal val prefs by lazy { SinemaApp.instance.prefs }
    internal val renderersFactory by lazy { PlaybackRenderersFactory(this) }
    internal var isPaused = false
    internal var scrubbing = false
    internal var scrubOriginMs: Long? = null
    internal var lastMarkerPositionMs: Long? = null
    internal val handler = Handler(Looper.getMainLooper())
    internal val seekDwell by lazy {
        PausedSeekDwell(
            handler,
            { player?.playWhenReady == true },
            { player?.seekTo(it) },
            onComplete = { hideSeekPreview() }
        )
    }
    internal val hideRunnable = Runnable { playerView.hideController() }
    internal val hideMarkerSkipRunnable = Runnable { hideMarkerSkip() }
    internal val hideSeekFeedbackRunnable = Runnable { hideSeekFeedback() }
    internal val progressRunnable = object : Runnable {
        override fun run() {
            updatePlaybackText()
            updateMarkerSkip()
            handler.postDelayed(this, 500L)
        }
    }
    internal val videoOutputTimeout = Runnable {
        val exo = player
        if (exo != null && shouldRetryWithoutVideoOutput(exo.playWhenReady, hasVideoOutput)) {
            fallbackToAlignedHls()
        }
    }
    internal val hideDelayMs = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        claimPlayback(this)
        setContentView(R.layout.activity_playback)
        aspectRatio = PlaybackAspectRatio.fromName(prefs.playbackAspectRatio)
        streamQuality = PlaybackStreamQuality.fromName(prefs.playbackStreamQuality)
        seekPreset = SeekPreset.fromName(prefs.playbackSeekPreset)
        sceneId = SceneIntents.sceneIdFrom(intent)
        videoWidth = SceneIntents.videoWidthFrom(intent)
        videoHeight = SceneIntents.videoHeightFrom(intent)
        resumePositionMs = SceneIntents.resumeMsFrom(intent)
        resumeConfirmed = SceneIntents.resumeConfirmedFrom(intent)
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
        if (resumeHeldPlayer()) return
        beginPlaybackSession()
    }

    /**
     * The decoder is still ours when another Sinema screen covered this one.
     * Reattach the surface and continue. A second playback releases us first.
     */
    internal fun resumeHeldPlayer(): Boolean {
        val exo = player ?: return false
        if (::playerView.isInitialized) {
            playerView.onResume()
            playerView.player = exo
        }
        val play = if (folderDetour) !resumePausedAfterFolder else heldPlayWhenStopped
        folderDetour = false
        resumePausedAfterFolder = false
        exo.playWhenReady = play
        if (!hasVideoOutput) scheduleVideoOutputTimeout()
        handler.post(progressRunnable)
        watchClock.onIsPlayingChanged(play, android.os.SystemClock.elapsedRealtime())
        return true
    }

    internal fun beginPlaybackSession(promptAllowed: Boolean = true, startPaused: Boolean = false) {
        startPlayback(promptAllowed, startPaused)
        handler.post(progressRunnable)
        watchClock.onIsPlayingChanged(
            player?.isPlaying == true,
            android.os.SystemClock.elapsedRealtime()
        )
    }

    internal fun startPlayback(promptAllowed: Boolean = true, startPaused: Boolean = false) {
        // Prompt whenever a meaningful saved position is still unresolved —
        // including after a lifecycle dismissal (backgrounding the dialog),
        // which must never silently approve resuming. Once resolved (choice
        // made or manual play), background restarts resume silently at the
        // live position onStop persisted.
        val promptResume = shouldShowResumePrompt(
            promptAllowed = promptAllowed,
            resumeResolved = resumePromptResolved || resumeConfirmed,
            resumeMs = resumePositionMs
        )
        resumeAwaitingChoice = promptResume
        initPlayer(startPaused = promptResume || startPaused)
        if (promptResume) showResumePrompt()
    }

    /** Maps full-scene metadata (captions, markers) shared by every switch path. */
    internal fun applySceneMetadata(details: com.sinema.model.SceneDetails) {
        captions = details.scene.captions
        markers = details.markers.map { it.title.ifBlank { it.primaryTag } to it.seconds }
        sceneFolder = folderContaining(details.scene.path)
        bindFolderButton()
    }

    internal fun clearSceneMetadata() {
        captions = emptyList()
        markers = emptyList()
        // Unknown again until the next path arrives. Keeps the previous
        // folder from opening against the new video, and stays quiet.
        sceneFolder = null
        bindFolderButton()
        hideMarkerSkip()
        hideSeekFeedback()
        if (::playerView.isInitialized) {
            playerView.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress)
                .setSceneMarkers(emptyList())
            updatePlaybackText()
        }
    }

    /** Fetch scene metadata before silent Watch Next or stored-session playback. */
    internal fun resolveAutoResume(preservePosition: Boolean = false) {
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

    internal fun persistPlaybackSession() {
        if (sceneId.isBlank()) return
        if (prefs.incognitoMode || incognitoLatchSceneId == sceneId) {
            prefs.playbackSessionJson = ""
            return
        }
        val exo = player
        val positionMs = (exo?.currentPosition ?: resumePositionMs).coerceAtLeast(0L)
        val durationMs = exo?.duration ?: 0L
        val finished = !folderDetour && durationMs > 0L && durationMs - positionMs < 30_000L
        if (!com.sinema.util.PlaybackSessionRestore.shouldPersist(positionMs) || finished) {
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
        hideSeekFeedback()
        dismissPlaybackDialogs()
    }


    /** Shared teardown for onPause/onStop: identical dialog/job cleanup. */
    internal fun dismissPlaybackDialogs() {
        listOf(
            aspectRatioDialog, chaptersDialog, settingsMenuDialog, personalizationMenuDialog,
            rateDialog, streamQualityDialog, upNextDialog, playbackErrorDialog
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
        handler.removeCallbacks(hideSeekFeedbackRunnable)
        super.onStop()
        dismissPlaybackDialogs()
        // Keep the in-session position so a restart resumes where the user
        // left off instead of the intent's original resume point. Always
        // update — leaving a stale value would jump backwards after seeks to
        // near the start, or forwards after seeking near the end.
        player?.let { exo ->
            resumePositionMs = keptResumePositionMs(exo.currentPosition, exo.duration, folderDetour)
            heldPlayWhenStopped = exo.playWhenReady
        }
        handler.removeCallbacks(videoOutputTimeout)
        watchClock.onIsPlayingChanged(false, android.os.SystemClock.elapsedRealtime())
        // Home leaves the app and drops the decoder. A Sinema screen on top
        // keeps it so Back does not reopen the stream. A new player claims
        // the decoder in onCreate and this release becomes a no-op.
        val keep = player != null && !isFinishing && !isChangingConfigurations &&
            SinemaApp.instance.isInternalActivityReturn(this)
        if (keep) {
            player?.playWhenReady = false
            if (::playerView.isInitialized) playerView.onPause()
        } else {
            releasePlayer()
        }
        if (isFinishing) PlaybackQueue.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(hideSeekFeedbackRunnable)
        releasePlayer()
        releasePlaybackClaim(this)
        saveQueue.close()
        super.onDestroy()
    }

    /** Called when another playback screen needs the decoder. */
    fun releaseHeldPlayer() {
        releasePlayer()
    }

    internal fun savePlayback() {
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

        val save = resumeSave(pos, duration, folderDetour) ?: return
        val resumeTimeSec = save.resumeTimeSec

        // The app-scope FIFO drains after activity destruction without dropping
        // progress or allowing an older position to overwrite a newer one.
        val appCtx = applicationContext
        val (profileId, serverUrl, api) = com.sinema.util.TvChannels.withSyncLock {
            Triple(app.prefs.activeProfileId, app.prefs.serverUrl, app.api)
        }
        saveQueue.enqueue {
            try {
                val playDurationMs = savedWatchClock.pendingMs(nowMs)
                api.saveSceneActivity(savedSceneId, resumeTimeSec, playDurationMs / 1000.0)
                savedWatchClock.markReported(playDurationMs)
                val shouldIncrement = save.countAsFinished && totalPlayedMs > 5_000L &&
                    savedSceneId !in playCountSentSceneIds
                if (shouldIncrement) {
                    api.incrementPlayCount(savedSceneId)
                    playCountSentSceneIds.add(savedSceneId)
                }
                // Refresh Watch Next only if the originating server is still active.
                if (resumeTimeSec > 0 && app.prefs.activeProfileId == profileId &&
                    app.prefs.serverUrl.trimEnd('/') == serverUrl.trimEnd('/')
                ) {
                    val continuePairs = api.findContinuePlaying()
                    com.sinema.util.TvChannels.syncWatchNext(appCtx, continuePairs, profileId, serverUrl)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Sinema", "Failed to save playback state", e)
            }
        }
    }

    internal fun buildMediaItem(): MediaItem {
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

    internal fun mountPlayerView(width: Int = videoWidth, height: Int = videoHeight) {
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

    internal fun loadInitialMetadata() {
        val requestedScene = sceneId
        // Raw launch on purpose: a metadata failure must still fall through
        // to clear autoResumePending and begin the playback session —
        // launchSafely's catch would skip that tail and stall startup.
        lifecycleScope.launch {
            try {
                val details = SinemaApp.instance.api.findSceneFull(requestedScene)
                if (sceneId == requestedScene) {
                    details?.let { d ->
                        sceneTitle = d.scene.displayTitle
                        videoWidth = d.scene.width
                        videoHeight = d.scene.height
                        if (!resumeConfirmed) resumePositionMs = (d.scene.resumeTime * 1000).toLong()
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

    internal fun bindPlaybackControls() {
        if (!::playerView.isInitialized) return
        SubtitleAppearance.apply(playerView, prefs)
        playerView.findViewById<android.widget.TextView>(R.id.playback_scene_title).text = sceneTitle
        playerView.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress).apply {
            setSceneMarkers(markers)
            setKeyTimeIncrements(seekPreset.backwardMs, seekPreset.forwardMs)
            scrubPreviewListener = object : MarkerTimeBar.ScrubPreviewListener {
                override fun onPreviewStart(positionMs: Long) {
                    scrubbing = true
                    scrubOriginMs = player?.currentPosition ?: positionMs
                    hideMarkerSkip()
                    hideSeekFeedback()
                    showSeekPreview(positionMs)
                }
                override fun onPreviewMove(positionMs: Long) = showSeekPreview(positionMs)
                override fun onPreviewStop(positionMs: Long, canceled: Boolean, fromKeyScrub: Boolean) {
                    if (!canceled && fromKeyScrub) player?.seekTo(positionMs)
                    lastMarkerPositionMs = player?.currentPosition
                    hideSeekPreview()
                }
                override fun onPreviewPlayPause() {
                    player?.let { it.playWhenReady = !it.playWhenReady }
                }
            }
        }
        playerView.findViewById<View>(R.id.playback_folder_button).setOnClickListener {
            openSceneFolder()
        }
        bindFolderButton()
        playerView.findViewById<View>(R.id.playback_personalization_button).setOnClickListener {
            showPersonalizationMenu()
        }
        playerView.findViewById<View>(R.id.playback_settings_button).setOnClickListener { showSettingsMenu() }
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

    internal fun bindFolderButton() {
        if (!::playerView.isInitialized) return
        playerView.findViewById<View>(R.id.playback_folder_button)?.isEnabled =
            !sceneFolder.isNullOrEmpty()
    }

    internal fun openSceneFolder() {
        val folder = sceneFolder
        if (folder.isNullOrEmpty()) {
            // Still loading (null) stays quiet. A settled miss can say so.
            if (folder != null) {
                Toast.makeText(this, R.string.playback_folder_unavailable, Toast.LENGTH_SHORT).show()
            }
            return
        }
        folderDetour = true
        resumePausedAfterFolder = player?.playWhenReady != true
        startActivity(Intent(this, FolderBrowseActivity::class.java).putExtra("path", folder))
    }

    internal fun updatePlaybackText() {
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
        val hasMarkers = markers.isNotEmpty()
        playerView.findViewById<View>(R.id.playback_marker_button).apply {
            isEnabled = hasMarkers
            alpha = if (hasMarkers) 1.0f else 0.5f
        }
        playerView.findViewById<View>(R.id.playback_prev_marker_button)?.apply {
            isEnabled = hasMarkers
            alpha = if (hasMarkers) 1.0f else 0.4f
        }
        playerView.findViewById<View>(R.id.playback_next_marker_button)?.apply {
            isEnabled = hasMarkers
            alpha = if (hasMarkers) 1.0f else 0.4f
        }
    }

    internal fun seekRelative(deltaMs: Long) {
        val exo = player ?: return
        val duration = exo.duration
        val target = (exo.currentPosition + deltaMs).coerceIn(
            0L,
            if (duration > 0L) duration else Long.MAX_VALUE
        )
        exo.seekTo(target)
        playerView.showController()
        val sec = (kotlin.math.abs(deltaMs) / 1000L).toInt()
        if (sec > 0) {
            showSeekFeedback(
                getString(
                    if (deltaMs >= 0L) R.string.playback_polish_seek_feedback_forward
                    else R.string.playback_polish_seek_feedback_backward,
                    sec
                )
            )
        }
        updatePlaybackText()
    }

    internal fun cycleSeekPreset() {
        seekPreset = seekPreset.next()
        prefs.playbackSeekPreset = seekPreset.name
        applySeekPreset()
        bindPlaybackControls()
        Toast.makeText(this, "Seek buttons ${seekPreset.label}", Toast.LENGTH_SHORT).show()
    }

    internal fun applySeekPreset(target: ExoPlayer? = player) {
        target?.setSeekBackIncrementMs(seekPreset.backwardMs)
        target?.setSeekForwardIncrementMs(seekPreset.forwardMs)
    }

    internal fun showSeekPreview(positionMs: Long) {
        if (!::playerView.isInitialized) return
        val exo = player ?: return
        val timeBar = playerView.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress)
        val previewDuration = exo.duration.takeIf { it > 0L } ?: timeBar?.knownDurationMs ?: 0L
        val preview = buildScrubPreview(
            targetMs = positionMs,
            originMs = scrubOriginMs,
            durationMs = previewDuration,
            isPaused = !exo.playWhenReady,
            markers = markers
        )
        val playerTarget = clampSeekPositionMs(positionMs, exo.duration)
        val container = playerView.findViewById<View>(R.id.playback_preview_container)
        val wasHidden = container.visibility != View.VISIBLE
        container.visibility = View.VISIBLE
        if (wasHidden) {
            if (prefs.reduceMotion) {
                container.alpha = 1f
            } else {
                container.animate().cancel()
                container.alpha = 0f
                container.animate().alpha(1f).setDuration(120L).start()
            }
        }
        playerView.findViewById<android.widget.TextView>(R.id.playback_preview_time).text =
            preview.timestampText
        playerView.findViewById<android.widget.TextView>(R.id.playback_preview_delta)?.apply {
            text = preview.deltaText
            visibility = if (preview.deltaText.isNotEmpty()) View.VISIBLE else View.GONE
        }
        playerView.findViewById<android.widget.TextView>(R.id.playback_preview_chapter)?.apply {
            text = preview.chapterTitle
            visibility = if (preview.chapterTitle.isNotEmpty()) View.VISIBLE else View.GONE
        }
        playerView.findViewById<View>(R.id.playback_preview_hint)?.visibility =
            if (preview.showPausedDwellHint) View.VISIBLE else View.GONE
        seekDwell.onPositionChanged(playerTarget)
    }

    internal fun hideSeekPreview() {
        scrubbing = false
        scrubOriginMs = null
        seekDwell.cancel()
        if (!::playerView.isInitialized) return
        playerView.findViewById<MarkerTimeBar>(androidx.media3.ui.R.id.exo_progress).cancelKeyScrub()
        val container = playerView.findViewById<View>(R.id.playback_preview_container)
        container.animate().cancel()
        container.alpha = 1f
        container.visibility = View.GONE
    }

    internal fun showSeekFeedback(label: String) {
        if (!::playerView.isInitialized || label.isBlank()) return
        val badge = playerView.findViewById<android.widget.TextView>(R.id.playback_seek_feedback) ?: return
        badge.text = label
        val wasHidden = badge.visibility != View.VISIBLE
        badge.visibility = View.VISIBLE
        if (wasHidden) {
            if (prefs.reduceMotion) {
                badge.alpha = 1f
            } else {
                badge.animate().cancel()
                badge.alpha = 0f
                badge.animate().alpha(1f).setDuration(120L).start()
            }
        }
        handler.removeCallbacks(hideSeekFeedbackRunnable)
        handler.postDelayed(hideSeekFeedbackRunnable, 1_500L)
    }

    internal fun hideSeekFeedback() {
        handler.removeCallbacks(hideSeekFeedbackRunnable)
        if (!::playerView.isInitialized) return
        playerView.findViewById<View>(R.id.playback_seek_feedback)?.apply {
            animate().cancel()
            alpha = 1f
            visibility = View.GONE
        }
    }

    internal fun scheduleVideoOutputTimeout() {
        handler.removeCallbacks(videoOutputTimeout)
        if (hasVideoOutput) return
        // Only AUTO retries a silent direct stream; manual modes surface
        // errors via onPlayerError instead of looping retries.
        if (!allowsNoVideoRetry(streamQuality)) return
        val originalKind = playbackStreamKindForFrame(videoWidth, videoHeight)
        if (!shouldFallbackToAlignedHls(originalKind, forceAlignedHls, hasFallenBackToDirect)) return
        handler.postDelayed(videoOutputTimeout, VIDEO_OUTPUT_TIMEOUT_MS)
    }

    internal fun fallbackToAlignedHls() {
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
    internal fun rebuildCurrentStream() {
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

    internal fun initPlayer(startPaused: Boolean = false) {
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

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady) hideSeekPreview()
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

    internal fun hideMarkerSkip() {
        handler.removeCallbacks(hideMarkerSkipRunnable)
        if (::playerView.isInitialized) {
            playerView.findViewById<View>(R.id.playback_skip_marker)?.visibility = View.GONE
            playerView.controllerShowTimeoutMs = hideDelayMs.toInt()
        }
    }

    internal fun updateMarkerSkip() {
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

    internal fun releasePlayer() {
        handler.removeCallbacks(videoOutputTimeout)
        hideSeekPreview()
        hideMarkerSkip()
        hideSeekFeedback()
        lastMarkerPositionMs = null
        player?.release()
        player = null
    }

    internal fun recoverPlayback(temporaryQuality: PlaybackStreamQuality? = null) {
        playbackErrorDialog.dismiss()
        val selection = recoveryQualitySelection(streamQuality, recoveryQualityRestore, temporaryQuality)
        streamQuality = selection.quality
        recoveryQualityRestore = selection.restoreAfterScene
        restartCurrentStream()
    }

    /** Switch stream quality mid-playback, preserving the current position. */
    internal fun applyStreamQuality(chosen: PlaybackStreamQuality) {
        streamQuality = chosen
        recoveryQualityRestore = null
        prefs.playbackStreamQuality = chosen.name
        restartCurrentStream()
        android.util.Log.i("Sinema", "Stream quality changed to ${chosen.name}")
    }

    internal fun restartCurrentStream() {
        if (player == null) return
        forceAlignedHls = false
        hasFallenBackToDirect = false
        hasVideoOutput = false
        rebuildCurrentStream()
    }

    internal fun incrementOCount() {
        val current = sceneId
        if (current.isEmpty()) return
        // appScope: the mutation must survive an immediate back-press exit.
        // appScope runs on Dispatchers.IO — Toast needs the main thread.
        val app = SinemaApp.instance
        val appCtx = applicationContext
        val api = app.api
        app.appScope.launch {
            try {
                api.sceneIncrementO(current)
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

    internal fun seekToAdjacentMarker(forward: Boolean) {
        val exo = player ?: return
        val target = adjacentMarker(exo.currentPosition, markers, forward) ?: return
        exo.seekTo((target.second * 1000).toLong())
        playerView.showController()
        val label = target.first.ifBlank { getString(R.string.playback_polish_marker_fallback) }
        showSeekFeedback(
            getString(
                if (forward) R.string.playback_polish_marker_jump_next
                else R.string.playback_polish_marker_jump_prev,
                label
            )
        )
        updatePlaybackText()
    }

    /** Swap the player to [nextId] (queue advance or Up Next jump). */
    internal fun playSceneNow(
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
                loadSceneMetadataForSwitch(
                    clear = ::clearSceneMetadata,
                    fetch = { app.api.findSceneFull(nextId) },
                    apply = { details ->
                        nextWidth = details.scene.width
                        nextHeight = details.scene.height
                        nextTitle = details.scene.displayTitle
                        applySceneMetadata(details)
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sinema", "Failed to fetch next scene metadata, playing without captions/markers", e)
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
    internal fun extendEndlessMix() {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val seekWithDpad = PlaybackKeyRouting.shouldSeekToAdjacentMarker(
            controllerVisible = isControllerVisible,
            hasMarkers = markers.isNotEmpty(),
            playerSurfaceFocused = playerView.isFocused
        )
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> { showSettingsMenu(); return true }
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

/** Parent directory of a media path. `/clip.mp4` lives in `/`. A bare filename has none. */
internal fun folderContaining(path: String): String {
    val trimmed = path.trim()
    if (!trimmed.contains('/')) return ""
    val parent = trimmed.substringBeforeLast('/')
    return if (parent.isEmpty()) "/" else parent
}

internal data class ResumeSave(val resumeTimeSec: Double, val countAsFinished: Boolean)

/**
 * Position to write when leaving playback. A folder detour near the end is
 * still that position. Leaving for good inside the last 30 seconds clears it.
 * Null when playback is too short to record.
 */
internal fun resumeSave(positionMs: Long, durationMs: Long, folderDetour: Boolean): ResumeSave? {
    val nearEnd = durationMs > 0L && durationMs - positionMs < 30_000L
    if (!folderDetour && nearEnd) return ResumeSave(0.0, countAsFinished = true)
    if (positionMs <= 5_000L) return null
    return ResumeSave(positionMs / 1000.0, countAsFinished = false)
}

private val playbackHolder = java.util.concurrent.atomic.AtomicReference<java.lang.ref.WeakReference<PlaybackActivity>?>()

private fun claimPlayback(activity: PlaybackActivity) {
    val previous = playbackHolder.get()?.get()
    if (previous != null && previous !== activity) previous.releaseHeldPlayer()
    playbackHolder.set(java.lang.ref.WeakReference(activity))
}

private fun releasePlaybackClaim(activity: PlaybackActivity) {
    val current = playbackHolder.get()
    if (current?.get() === activity) playbackHolder.compareAndSet(current, null)
}

/** In-memory resume point after onStop. Same near-end rule as [resumeSave]. */
internal fun keptResumePositionMs(positionMs: Long, durationMs: Long, folderDetour: Boolean): Long {
    val nearEnd = !folderDetour && durationMs > 0L && durationMs - positionMs < 30_000L
    return if (nearEnd) 0L else positionMs.coerceAtLeast(0L)
}
