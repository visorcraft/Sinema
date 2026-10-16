# Playback Aspect-Ratio Override

**Status:** Approved design; written spec pending review
**Date:** 2026-10-16
**Scope:** Playback sizing, a dedicated playback-controller button, and one persisted player-wide preference. No new dependencies and no changes to Stash stream requests.

## Problem

Some media, including portrait video reported by Stash as 1080x1920, can appear extremely distorted or compressed during playback. The user wants a remote-accessible way to correct the display ratio during playback. The correct default behavior is to preserve the media shape and fit the entire image on a landscape TV without cropping.

The selection must remain active for later videos until the user changes it.

## What the code shows

`PlaybackActivity` builds a plain Media3 `ExoPlayer` and attaches it to a full-screen `PlayerView`. Sinema does not supply dimensions, rotation, scaling metadata, or a custom resize mode. Media3 1.10.1 defaults to `RESIZE_MODE_FIT`.

Stash's `Scene.width` and `Scene.height` values are used on the detail screen but are not passed to playback, and no active app code swaps them. Consequently, there is no evidence of an app-side width/height inversion. Incorrect geometry reported by the stream/decoder or device-specific surface rendering remains possible; proving that requires the affected stream and runtime geometry.

For reference, correct FIT behavior for 1080x1920 video on a 1920x1080 display is a centered image approximately 608x1080 with pillarboxing. Narrow portrait output alone is not distortion.

## Chosen approach

Keep Media3's stock Settings gear and add a separate aspect-ratio button immediately before it. The button opens a Sinema-owned, D-pad-friendly single-choice dialog.

This avoids replacing or accessing Media3's private settings adapters, so the existing playback-speed and audio-track menus remain intact. It also avoids forking the stock controller internals merely to add one row to their popup.

## User-facing behavior

The aspect-ratio dialog contains:

- **Auto (source):** use the geometry reported by ExoPlayer, preserving current behavior.
- **16:9:** fit the video as 16:9.
- **4:3:** fit the video as 4:3.
- **9:16:** fit portrait video as 9:16.

Every option uses FIT sizing. Manual ratios therefore correct the target shape without cropping; unused screen space remains black. The options do not rotate decoded pixels or alter the stream.

Selecting an option applies it immediately, dismisses the dialog, and saves it. The same selection applies to subsequent playback sessions and queue auto-advance until changed. There is no per-scene storage.

The aspect-ratio button is focusable, has a content description, and participates in normal D-pad navigation with the subtitle and Settings buttons. Opening the dialog does not pause playback. Dialog dismissal restores focus to the player controls, and activity lifecycle cleanup dismisses the dialog to avoid leaking a window.

## Components

### `PlaybackAspectRatio` model enum

Add `app/src/main/java/com/sinema/model/PlaybackAspectRatio.kt` following the existing `SortOption` pattern:

- User-facing label.
- Nullable fixed width/height ratio, where `null` represents Auto.
- `effectiveRatio(sourceRatio)` returning the fixed ratio or the supplied source ratio.
- `fromName` parsing with Auto as the safe fallback for null, empty, or unknown persisted values.

This pure Kotlin type owns ratio selection and persistence parsing without depending on Android UI classes.

### `AspectRatioPlayerView`

Add a small `PlayerView` subclass under `com.sinema.ui`. Override Media3's protected `onContentAspectRatioChanged` hook:

1. Remember the latest source ratio supplied by Media3.
2. Pass `PlaybackAspectRatio.effectiveRatio(sourceRatio)` to the content frame.
3. When the user changes the option, reapply it using the most recent source ratio.

Keep `RESIZE_MODE_FIT` for all options. Using Media3's aspect-ratio hook ensures later decoder size events cannot silently discard a manual override.

### Controller layout and button

Add a playback controller layout based on the Media3 1.10.1 default controller, retaining its required view IDs and license header. Insert one `ImageButton` before `exo_settings`, using the existing bottom-button style and a Sinema vector icon.

Point the existing player view at this controller layout in `activity_playback.xml`. Do not override `exo_settings` or replace any stock click handlers.

### Dialog and activity wiring

Add an `AspectRatioDialog` modeled on `SortDialog`. In `PlaybackActivity`:

- Restore the persisted option when creating the view.
- Open the dialog from the new button.
- On selection, update the custom player view and `Prefs`.
- Reapply the restored option when initializing a replacement player during queue advancement.
- Dismiss the dialog during the same lifecycle transitions used for the chapters dialog.

### Preference storage

Add a string preference in `Prefs` using a stable key such as `playback_aspect_ratio`. Store the enum `name`, matching the existing sort-option persistence pattern. Convert through `PlaybackAspectRatio.fromName`; invalid values fall back to Auto rather than throwing or leaving playback without a ratio.

### Diagnostic geometry logging

In the existing player listener, log `VideoSize.width`, `height`, `pixelWidthHeightRatio`, and `unappliedRotationDegrees` whenever ExoPlayer reports a size change. Include the active aspect-ratio option. Use the existing `Sinema` log tag. This distinguishes source geometry problems from downstream view/device scaling when the affected file is tested.

## Data flow

```
Playback starts
  → Prefs reads persisted enum name
  → PlaybackAspectRatio.fromName (unknown becomes AUTO)
  → AspectRatioPlayerView stores selected option

ExoPlayer reports VideoSize
  → Media3 computes source display ratio, including pixel ratio and rotation
  → AspectRatioPlayerView selects source ratio (AUTO) or fixed ratio
  → content frame applies ratio with RESIZE_MODE_FIT

User activates aspect-ratio button
  → single-choice dialog shows current option
  → selection updates AspectRatioPlayerView immediately
  → Prefs saves enum name
  → later playback and queue items restore/reapply the same option
```

## Error and edge handling

- Missing, empty, or unknown preference values select Auto.
- Before ExoPlayer supplies a source ratio, Auto retains Media3's existing empty/unknown behavior; fixed options apply as soon as the content frame receives an update.
- Reinitialization during queue advancement must not reset the selected option.
- Dialog and player cleanup must not leave callbacks or windows attached to a stopped activity.
- No changes to authentication, media URLs, caption handling, playback speed, audio selection, resume saving, or queue behavior.

## Testing

Add JUnit tests for `PlaybackAspectRatio` covering:

- Auto returns the source ratio unchanged.
- Each fixed option returns its documented ratio.
- `fromName` round-trips valid values.
- Null, empty, and unknown values fall back to Auto.

Run the project-required checks:

```bash
rtk ./gradlew :app:testDebugUnitTest --no-daemon
rtk ./gradlew :app:lintDebug --no-daemon
rtk ./gradlew :app:assembleDebug --no-daemon
```

Update `docs/playback.md` with the button location, available options, FIT behavior, and persistence.

## On-device acceptance

Rendering cannot be proven by JVM tests. On the affected TV/file, verify:

1. Auto preserves existing playback behavior.
2. Selecting 9:16 fits the affected 1080x1920 content without cropping or distortion when its intended display shape is 9:16.
3. Fixed 16:9 and 4:3 produce the expected fitted shapes for suitable test media.
4. The selection survives leaving playback, starting another video, and queue auto-advance.
5. Every option is reachable and selectable with the D-pad.
6. Existing gear speed/audio, subtitle, repeat, chapter, pause, and Back behavior still works.
7. Logcat geometry output shows whether ExoPlayer's reported dimensions, pixel ratio, or rotation explain the original distortion.

## Alternatives considered

- **Replace the gear with a Sinema Settings dialog:** matches the originally suggested location but requires reimplementing speed and audio controls, increasing regression risk. Not chosen.
- **Copy or hook Media3's private settings implementation:** provides a native settings row but creates a brittle dependency on private controller internals. Not chosen.
- **Expose only Media3 resize modes such as Fit, Fill, and Zoom:** changes sizing but cannot correct a source ratio that is simply wrong. Not sufficient.
