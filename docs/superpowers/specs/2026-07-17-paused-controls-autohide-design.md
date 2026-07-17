# Paused-State Auto-Hide of Player Controls

**Status:** Draft for review
**Date:** 2026-07-17
**Scope:** `app/src/main/java/com/sinema/ui/PlaybackActivity.kt` + the existing `PlayerView` controller inside `activity_playback.xml`. No new files, no new dependencies.

## Problem

When a video is paused in Sinema, the Media3 default controller (pause / progress / play row) stays on screen indefinitely. The user wants:

1. After the user pauses, the controller hides after **3 seconds**, no dim/scrim left behind.
2. While paused with the controller hidden, any D-pad or media key (and BACK) brings the controller back.
3. While paused with the controller visible, BACK exits the video (current behavior).
4. PLAY → existing default controller behavior is unchanged.

## Approach

Wrap the existing Media3 default controller with three small additions inside `PlaybackActivity.kt`:

1. A `Player.Listener.onIsPlayingChanged` hook drives a single "hide after 3 s when paused" timer.
2. `onUserInteraction` (the system-level activity callback) cancels the timer — any user key while paused either brings the controller back via Media3's built-in tap/key path, or restarts the countdown if the controller is currently visible.
3. `OnBackPressedDispatcher` intercepts BACK: if paused **and** controller hidden, show the controller; otherwise super (finish).

The dim/scrim lives inside Media3's controller overlay, so it fades out with the controller — no custom dim work needed.

## Behavior details

| State                                | Action                                                |
| ------------------------------------ | ----------------------------------------------------- |
| Playing → user pauses                | Controller visible. Schedule `hideController` in 3 s. |
| Paused, 3 s elapsed                  | `playerView.hideController()`. Dim fades too.         |
| Paused, hidden, BACK                 | `playerView.showController()`. Re-arm 3 s timer.      |
| Paused, hidden, any D-pad / media    | Controller shows via Media3 default. Re-arm timer.    |
| Paused, visible, BACK                | `super.onBackPressed()` → finish.                     |
| Paused → playing                     | Cancel pending hide. Controller follows normal rules. |
| Player `STATE_ENDED`                 | Cancel pending hide. Auto-advance handles exit.       |
| Activity `onStop` / player released  | Cancel pending hide, remove callbacks.                |
| Activity destroyed mid-timer         | Same — `removeCallbacks` in `onStop` and `onDestroy`. |

## Components

Single file change: `PlaybackActivity.kt`.

New fields:
- `private var isPaused = false`
- `private val handler = Handler(Looper.getMainLooper())`
- `private val hideRunnable = Runnable { playerView.hideController() }`
- `private val hideDelayMs = 3000L`

Hooks:
- `Player.Listener.onIsPlayingChanged(isPlaying)` — set `isPaused`, schedule/cancel `hideRunnable`.
- `onUserInteraction()` override — `handler.removeCallbacks(hideRunnable)`; if controller hidden `playerView.showController()`. (System delivers this for any input dispatch including D-pad.)
- `OnBackPressedDispatcher.addCallback(this, true) { ... }` registered in `onCreate`. Logic:
  ```kotlin
  val exo = player
  if (exo != null && !exo.playWhenReady && !playerView.isControllerVisible) {
      playerView.showController()
      handler.removeCallbacks(hideRunnable)
      handler.postDelayed(hideRunnable, hideDelayMs)
  } else {
      finish()
  }
  ```
- `onStop()` calls `handler.removeCallbacks(hideRunnable)`.

No layout changes. No new test scripts beyond a manual smoke pass on a TV / emulator.

## Data flow

```
User presses pause → ExoPlayer.pause() → onIsPlayingChanged(false)
  → handler.postDelayed(hideRunnable, 3000)            // schedule hide

3000 ms elapse, still paused                       → hideRunnable.run()
  → playerView.hideController()                      // controller + dim gone

User presses BACK while paused-and-hidden
  → OnBackPressedCallback runs
  → showController(); postDelayed(hideRunnable, 3000)

User presses any key during paused-and-hidden
  → onUserInteraction()
  → removeCallbacks(hideRunnable); showController()

User presses BACK while paused-and-visible
  → OnBackPressedCallback runs
  → finish()

User resumes playback
  → onIsPlayingChanged(true)
  → removeCallbacks(hideRunnable)                    // defer to Media3 default
```

## Edge handling

- **`!player.isPlaying` vs `!playWhenReady`.** Use the listener's `isPlaying` boolean (false while paused or while buffering-but-not-ready). The 3 s timer only arms when we actually transition from playing → not playing. While still buffering after a seek, controller is already visible from the seek interaction, so the timer arming here matches user expectation.
- **Race on rapid play/pause.** Each `onIsPlayingChanged` cancels prior callback before scheduling. No stale runs after a fast toggle.
- **Mid-timer user interaction.** Handled by `onUserInteraction` (cancels) + Media3 default (which surfaces the controller in response to the same key). The result: visible controller + fresh 3 s countdown.
- **Activity destroyed mid-timer.** `onStop` + `onDestroy` both `removeCallbacks`.
- **`isControllerVisible`** is already a field on this activity, populated by the existing `ControllerVisibilityListener` wired in `onCreate`. Use that field directly — it stays in sync via Media3's own callbacks and avoids depending on a public getter that may not exist in all `media3-ui` versions.
- **Chapters dialog open** — `chaptersDialog?.isShowing == true` does not change the timer; dialog dismiss focuses the player via `setOnDismissListener { playerView.requestFocus() }` which will trigger `onUserInteraction` and re-arm the timer. Acceptable.

## Testing

Sinema has unit tests under `app/src/test/`. This feature is a UI-state machine tightly coupled to `PlayerView` and the activity lifecycle; a meaningful test requires Robolectric or instrumentation. **Out of scope** for this change — verified manually on a TV / emulator:

1. Play a video → pause → controls hide after ~3 s, no dim remains.
2. While paused-hidden, press any d-pad / media key → controls reappear, dim back, fresh countdown.
3. While paused-hidden, press BACK → controls reappear without exiting.
4. While paused-visible, press BACK → activity finishes.
5. While playing, default controller behavior unchanged.
6. Play → pause → resume within 3 s → no stuck hidden state.
7. Open chapters dialog → press BACK to dismiss → playback state unaffected, timer state correct.
8. Pause → background the app → return → controller still in expected state, no leaked runnables.

## Rollback

Single-file change. Revert the commit.

## Alternatives considered

- **A. Custom controller view.** Replaces Media3 default; ~150-300 LoC; loses CC / gear / track UI for free. Rejected.
- **B. `setControllerShowTimeoutMs(3000)` only.** Verified against Media3 source: `PlayerControlView` stops its hide runnable when `playWhenReady == false`, so this does nothing while paused. Rejected.
- **C. (chosen) Default controller + thin wrapper.** Matches the symptom exactly. ~30 lines.
