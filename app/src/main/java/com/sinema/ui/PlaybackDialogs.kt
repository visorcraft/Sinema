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

internal fun PlaybackActivity.showPlaybackRecovery(error: androidx.media3.common.PlaybackException) {
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

internal fun PlaybackActivity.showChapters() {
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
                }
            )
        }
        personalizationMenuDialog.show(android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.personalization_menu_title))
            .setItems(entries.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                entries[which].second.run()
            }
            .setOnDismissListener {
                personalizationMenuDialog.dialog?.let { d -> personalizationMenuDialog.dismissIf(d) }
                playerView.requestFocus()
            }
        )
    }

internal fun PlaybackActivity.showSettingsMenu() {
        if (settingsMenuDialog.isShowing) return
        val entries = listOf(
            getString(R.string.playback_menu_aspect) to Runnable { showAspectRatioDialog() },
            getString(R.string.playback_menu_quality) to Runnable { showStreamQualityDialog() },
            getString(R.string.playback_menu_seek) to Runnable { cycleSeekPreset() }
        )
        settingsMenuDialog.show(android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.playback_menu_title))
            .setItems(entries.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                entries[which].second.run()
            }
            .setOnDismissListener {
                settingsMenuDialog.dialog?.let { d -> settingsMenuDialog.dismissIf(d) }
                playerView.requestFocus()
            }
        )
    }

/** Count one O for the scene currently loaded in the player. */
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
            rateDialog.show(android.app.AlertDialog.Builder(this@showRateDialog)
                .setTitle("Rate scene")
                .setSingleChoiceItems(options.toTypedArray(), stars) { dialog, which ->
                    dialog.dismiss()
                    val chosen = which
                    lifecycleScope.launch {
                        try {
                            SinemaApp.instance.api.setSceneRating(current, StarRating.toRating100(chosen))
                            Toast.makeText(
                                this@showRateDialog,
                                if (chosen > 0) "Rated ${StarRating.glyphs(chosen)}" else "Rating cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("Sinema", "Failed to set rating for $current", e)
                            Toast.makeText(this@showRateDialog,
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

internal fun PlaybackActivity.showResumePrompt() {
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

internal fun PlaybackActivity.showUpNextDialog() {
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


