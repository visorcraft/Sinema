@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sinema.ui

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.PlaybackStreamQuality
import com.sinema.model.StarRating
import com.sinema.util.PlaybackQueue
import kotlinx.coroutines.launch

/** Playback menus. Members stay on PlaybackActivity; these functions only build the dialogs. */

internal fun PlaybackActivity.showAspectRatioDialog() {
    if (aspectRatioDialog.isShowing) return
    val dialog = AspectRatioDialog.show(this, aspectRatio) { chosen ->
        aspectRatio = chosen
        prefs.playbackAspectRatio = chosen.name
        playerView.playbackAspectRatio = chosen
        updatePlaybackText()
    }
    aspectRatioDialog.dialog = dialog
    dialog.setOnDismissListener {
        aspectRatioDialog.dismissIf(dialog)
        playerView.requestFocus()
    }
}

internal fun PlaybackActivity.showStreamQualityDialog() {
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

internal fun PlaybackActivity.showSpeedDialog() {
    if (speedDialog.isShowing) return
    val presets = PlaybackSpeedPreset.entries
    val selected = presets.indexOf(playbackSpeed).coerceAtLeast(0)
    speedDialog.show(
        builder = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.playback_polish_speed_title))
            .setSingleChoiceItems(presets.map { it.label }.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                applyPlaybackSpeed(presets[which])
            }
            .setNegativeButton(android.R.string.cancel, null),
        onDismiss = { playerView.requestFocus() },
        onShow = { dialog -> dialog.listView?.setSelection(selected) }
    )
}

internal fun PlaybackActivity.showPlaybackRecovery(error: androidx.media3.common.PlaybackException) {
    if (playbackErrorDialog.isShowing) return
    if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ||
        isFinishing || isDestroyed
    ) return
    resumePositionMs = player?.currentPosition?.takeIf { it > 0L } ?: resumePositionMs
    player?.playWhenReady = false
    val safeTitle = playbackDisplayTitle(sceneTitle, sceneId, prefs.discreetMode)
    val message = playbackRecoveryMessage(error.errorCode) +
        safeTitle.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
    playbackErrorDialog.show(
        builder = android.app.AlertDialog.Builder(this)
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
            .setCancelable(false),
        onDismiss = {}
    )
}

internal fun PlaybackActivity.showChapters() {
    if (markers.isEmpty()) return
    if (chaptersDialog.isShowing) return
    val scene = sceneId
    val activeIndex = activeChapterIndex(player?.currentPosition ?: 0L, markers)
    chaptersDialog.show(
        builder = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.playback_polish_chapters_title, markers.size))
            .setAdapter(ChapterListAdapter(this, SinemaApp.instance.api, scene, markers)) { _, which ->
                markers.getOrNull(which)?.let { marker ->
                    player?.seekTo((marker.second * 1000).toLong())
                    val label = marker.first.ifBlank { getString(R.string.playback_polish_marker_fallback) }
                    showSeekFeedback(getString(R.string.playback_polish_marker_jump_next, label))
                    updatePlaybackText()
                }
            },
        onDismiss = { playerView.requestFocus() },
        onShow = { dialog ->
            if (activeIndex >= 0) {
                dialog.listView?.setSelection(activeIndex)
            }
        }
    )
}

internal fun PlaybackActivity.showPersonalizationMenu() {
    if (personalizationMenuDialog.isShowing) return
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
                    Toast.makeText(this@showPersonalizationMenu, "Incognito off", Toast.LENGTH_SHORT).show()
                } else {
                    // Viewing from here on stays unrecorded for this scene
                    // even if incognito is disabled before exit.
                    incognitoLatchSceneId = sceneId
                    Toast.makeText(this@showPersonalizationMenu, "Incognito on — watch history paused", Toast.LENGTH_SHORT).show()
                }
                updatePlaybackText()
            }
        )
        add(
            (if (SinemaApp.instance.prefs.endlessMix) "♾ Endless Mix: ON" else "♾ Endless Mix: OFF") to Runnable {
                val enable = !SinemaApp.instance.prefs.endlessMix
                SinemaApp.instance.prefs.endlessMix = enable
                Toast.makeText(
                    this@showPersonalizationMenu,
                    getString(if (enable) R.string.endless_on_toast else R.string.endless_off_toast),
                    Toast.LENGTH_SHORT
                ).show()
                updatePlaybackText()
            }
        )
    }
    personalizationMenuDialog.show(
        builder = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.personalization_menu_title))
            .setItems(entries.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                entries[which].second.run()
            },
        onDismiss = { playerView.requestFocus() }
    )
}

internal fun PlaybackActivity.showSettingsMenu() {
    if (settingsMenuDialog.isShowing) return
    val entries = listOf(
        getString(R.string.playback_menu_aspect) to Runnable { showAspectRatioDialog() },
        getString(R.string.playback_menu_quality) to Runnable { showStreamQualityDialog() },
        getString(R.string.playback_menu_seek) to Runnable { cycleSeekPreset() }
    )
    settingsMenuDialog.show(
        builder = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.playback_menu_title))
            .setItems(entries.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                entries[which].second.run()
            },
        onDismiss = { playerView.requestFocus() }
    )
}

/** Rate the scene currently loaded in the player. */
internal fun PlaybackActivity.showRateDialog() {
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
        rateDialog.show(
            builder = android.app.AlertDialog.Builder(this@showRateDialog)
                .setTitle("Rate scene")
                .setSingleChoiceItems(options.toTypedArray(), stars) { dialog, which ->
                    dialog.dismiss()
                    val chosen = which
                    launchSafely(
                        label = "Set rating for $current",
                        onError = { e ->
                            Toast.makeText(
                                this@showRateDialog,
                                "Failed to set rating: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    ) {
                        SinemaApp.instance.api.setSceneRating(current, StarRating.toRating100(chosen))
                        Toast.makeText(
                            this@showRateDialog,
                            if (chosen > 0) "Rated ${StarRating.glyphs(chosen)}" else "Rating cleared",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            onDismiss = { playerView.requestFocus() },
            onShow = { dialog -> dialog.listView?.setSelection(stars) }
        )
    }
}

internal fun PlaybackActivity.showResumePrompt() {
    if (resumePromptDialog.isShowing) return
    val exo = player ?: return
    val at = com.sinema.util.TimeFormat.formatMs(resumePositionMs)
    resumePromptDialog.show(
        builder = android.app.AlertDialog.Builder(this)
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
            },
        onDismiss = {
            // Back cancels: stay paused with controls shown; the choice
            // stays unresolved so a later restart re-prompts instead of
            // silently resuming. Lifecycle teardown dismissals are silent.
            if (!suppressingPromptDismiss) playerView.showController()
            playerView.requestFocus()
        }
    )
}

internal fun PlaybackActivity.showUpNextDialog() {
    if (upNextDialog.isShowing) return
    val remaining = PlaybackQueue.remaining()
    if (remaining.isEmpty()) {
        Toast.makeText(this, getString(R.string.up_next_empty), Toast.LENGTH_SHORT).show()
        return
    }
    val adapter = UpNextAdapter(
        context = this,
        onQueueChanged = {
            val count = PlaybackQueue.remainingCount()
            if (count == 0) {
                upNextDialog.dismiss()
            } else {
                upNextDialog.dialog?.setTitle(getString(R.string.up_next_title, count))
            }
            updatePlaybackText()
        }
    ) { target ->
        // Resolve by stable id through the live queue.
        if (PlaybackQueue.current()?.id == target.id) return@UpNextAdapter
        PlaybackQueue.jumpToItem(target.id)?.let {
            upNextDialog.dismiss()
            playSceneNow(it.id, it.title, saveCurrent = true)
        } ?: Toast.makeText(this, getString(R.string.up_next_not_in_queue), Toast.LENGTH_SHORT).show()
    }
    adapter.refresh()
    upNextDialog.show(
        builder = android.app.AlertDialog.Builder(this)
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
                    updatePlaybackText()
                }
            }
            .setPositiveButton("Exit queue") { _, _ ->
                PlaybackQueue.clear()
                Toast.makeText(this, getString(R.string.up_next_cleared), Toast.LENGTH_SHORT).show()
                updatePlaybackText()
            },
        onDismiss = { playerView.requestFocus() }
    )
}
