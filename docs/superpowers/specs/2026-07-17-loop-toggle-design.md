# Loop-One Toggle for the Player

**Status:** Draft for review
**Date:** 2026-07-17
**Scope:** A small, always-visible ImageButton overlay on the playback screen that toggles `REPEAT_MODE_ONE` for the current video, with the toggle state persisted across launches via `Prefs`. When loop is on, the playback screen never advances to the queue's next item at end of video — the player loops seamlessly.

## Problem

The user has no way to keep a single video looping in Sinema. Media3's default controller's `repeat` button was deliberately not enabled (the layout only sets `show_subtitle_button="true"`), and even when enabled it cycles through three states (`OFF` / `loop-all` / `loop-one`). The user only wants `OFF` ↔ loop-the-current-video, persisted across app restarts.

## Approach

Add a small, always-visible `ImageButton` overlay on top of the `PlayerView`, positioned in the bottom-right corner outside the player controller area. The button is a single Material loop-arrow vector drawable; tint toggles between grey (off) and accent (on). Click toggles a `Prefs` boolean and sets `exo.repeatMode` to `REPEAT_MODE_ONE` or `REPEAT_MODE_OFF`. Reads the persisted flag at `initPlayer()` time and applies it to the freshly built player.

Because `REPEAT_MODE_ONE` makes the player auto-seek to 0 after `STATE_ENDED`, the existing end-of-video logic in `onPlaybackStateChanged(STATE_ENDED)` must be guarded to skip the queue advance when loop is on — otherwise it would race with Media3's auto-seek and break the loop.

## Behavior details

| State                                | Action                                                |
| ------------------------------------ | ----------------------------------------------------- |
| App start, `Prefs.loopEnabled=true`  | `initPlayer` sets `exo.repeatMode = REPEAT_MODE_ONE`, tint on. |
| App start, default (first run)       | `initPlayer` sets `exo.repeatMode = REPEAT_MODE_OFF`, tint off. |
| User taps button (off → on)          | `Prefs.loopEnabled = true`, `exo.repeatMode = REPEAT_MODE_ONE`, tint on. |
| User taps button (on → off)          | `Prefs.loopEnabled = false`, `exo.repeatMode = REPEAT_MODE_OFF`, tint off. |
| Video ends while loop off            | Existing path: `savePlayback()`, queue-advance or finish. |
| Video ends while loop on             | `savePlayback()`, then **return early without queue advance**. Media3 auto-seeks to 0; video restarts. |
| Mid-loop, user backs out of activity | Prefs persists; next open resumes in same loop state. |
| Activity recreated (rotation etc.)    | Button tint restored from `Prefs.loopEnabled` in `onCreate`. |

The button is always visible regardless of the controller's auto-hide (it lives outside `PlayerView`'s controller overlay — it is not part of the controls that fade after 3 s of pause).

## Components

Five files touched. No new dependencies.

1. `app/src/main/res/drawable/ic_loop.xml` (NEW) — Material loop-arrow vector drawable, drawn at 24dp fill, tint applied at runtime.

2. `app/src/main/res/values/strings.xml` — one new string:
   ```xml
   <string name="loop_video">Loop this video</string>
   ```

3. `app/src/main/res/layout/activity_playback.xml` — add an `ImageButton` as a sibling of `PlayerView` inside the existing `FrameLayout`. Placed bottom-end, with margin to clear any system overlay. Background `selectableItemBackgroundBorderless`. `contentDescription` references the new string.

4. `app/src/main/java/com/sinema/util/Prefs.kt` — new property following the existing `channelsEnabled` pattern:
   ```kotlin
   var loopEnabled: Boolean
       get() = prefs.getBoolean("loop_enabled", false)
       set(value) = prefs.edit().putBoolean("loop_enabled", value).apply()
   ```

5. `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`:
   - New field `private lateinit var loopButton: ImageButton`.
   - Helper `applyLoopTint(enabled: Boolean)` that calls `loopButton.setColorFilter(ContextCompat.getColor(this, if (enabled) android.R.color.holo_blue_bright else android.R.color.darker_gray))`.
   - In `onCreate`, after `markers = SceneIntents.markersFrom(intent)`, `loopButton = findViewById(R.id.loop_button)` then `applyLoopTint(SinemaApp.instance.prefs.loopEnabled)`.
   - Wire click: toggle the Prefs value, set `exo.repeatMode` to the matching value, call `applyLoopTint(newValue)`. Click handler no-ops on `player == null`.
   - In `initPlayer`, after `exo.playWhenReady = true`, set `exo.repeatMode = if (prefs.loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF`.
   - In `onPlaybackStateChanged(STATE_ENDED)`, gate the queue advance on `exo.repeatMode == Player.REPEAT_MODE_OFF`. Skips the auto-advance block when loop is on so Media3's auto-seek-to-0 wins and produces an uninterrupted loop.

Tint values: `ContextCompat.getColor(this, android.R.color.darker_gray)` for off; `ContextCompat.getColor(this, android.R.color.holo_blue_bright)` for on. Both are framework colors, so no new color resources are introduced.

## Data flow

```
onCreate                              → findViewById<ImageButton>(R.id.loop_button)
                                     → applyTint(currentState = prefs.loopEnabled)

initPlayer (per activity start)      → exo.repeatMode = if (prefs.loopEnabled) ONE else OFF

User taps loop button                 → prefs.loopEnabled = !prefs.loopEnabled
                                     → exo.repeatMode = matching value
                                     → applyTint(newState)

Video ends, exo.repeatMode == ONE    → savePlayback(); return. (No queue advance.)
Video ends, exo.repeatMode == OFF    → existing path: savePlayback + queue advance / finish.
```

## Edge handling

- **`Prefs.loopEnabled` race with initPlayer.** Read is synchronous (plain SharedPreferences), no race.
- **`exo.repeatMode` set in `initPlayer` but the player hasn't reached `STATE_READY` yet.** Setting repeat mode before `prepare()` is legal in ExoPlayer; the value applies at first buffer/play.
- **`onPlaybackStateChanged(STATE_ENDED)` queue-advance race with Media3's auto-seek.** With `REPEAT_MODE_ONE`, Media3 immediately seeks back to 0 after firing STATE_ENDED. The guard `if (exo.repeatMode == Player.REPEAT_MODE_OFF)` runs BEFORE the queue advance block, so when loop is on, we never enter the `lifecycleScope.launch { ... }` path that reassigns sceneId and switches scenes.
- **`savePlayback()` still fires per loop iteration.** With `REPEAT_MODE_ONE`, after one full play of the video it sets `resumeTimeSec = 0.0` (because we're within 30 s of end) and increments play count exactly once. On subsequent iterations the `playCountSent` guard prevents re-incrementing. Behavior is consistent with a user finishing the video normally.
- **Always-visible button during dim/scrim.** The loop button sits outside the `PlayerView` controller overlay, so it is not part of the controller that fades after 3 s of pause. The autohide feature for the rest of the controls is unaffected.
- **Activity destroyed mid-loop (rotation, process death).** `Prefs.loopEnabled` survives; next `onCreate` reapplies.
- **Layout on small TV screens.** `wrap_content` + `bottom|end` margin; the `ImageButton` is 40 dp wide. No risk of overlapping the controller bottom row (controller buttons are centered bottom).

## Testing

No automated tests (matches the v1.15.0 project's testing policy for UI-coupled code — Robolectric/instrumentation would be needed; out of scope). Manual device verification deferred to the user per the project's "no device installs without explicit permission" rule. The 8-item checklist from the v1.15.0 spec's Testing section does not apply here; the equivalent here is:

1. Play a video → loop button visible bottom-right, tinted grey.
2. Tap loop button → tint changes to accent. `Prefs.loopEnabled = true`.
3. Let the video play to end → it restarts seamlessly instead of advancing to the next queue item.
4. Tap loop button again → tint back to grey. Video continues normally and ends.
5. App force-stop, relaunch → loop button tint reflects the last persisted state.
6. Repeat with a queue of 5 items → with loop off, plays 5 then finishes (existing behavior); with loop on, plays item 1 forever.
7. With loop on, press BACK twice → exits; loop state persisted for next launch.

If any fail, fix in the implementation task before release.

## Rollback

Single logical change across 5 files. Revert one commit.

## Alternatives considered

- **A. Enable Media3's built-in repeat button (`app:show_repeat_button="true"`) and intercept the three-state cycle via `Player.Listener.onRepeatModeChanged` to drop `REPEAT_MODE_ALL`, plus persist on every change.** Conceptually small but API-fragile across Media3 versions, the icon flickers through Media3's three icon variants on click, and depends on internal view-id stability. Rejected.
- **C. Drop `PlaybackQueue` auto-advance entirely.** Doesn't address the user's request — they want the queue when loop is off. Rejected.
- **B. (chosen) Custom overlay ImageButton with always-visible tint toggling.** Predictable, every line dead-simple, no Media3 controller-API gymnastics, state visible at a glance. Rejected alternatives have hidden costs.
