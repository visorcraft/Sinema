# Playback and Home UX Fixes

**Date:** 2026-11-03
**Status:** Approved design
**Branch:** `privacy-profile-reliability`

## Context

Four UX issues make common actions inconsistent or misleading: Resume and Play both show the resume prompt, the Home logo can overlap Quick Actions, seek previews can show unrelated static artwork, and playback Settings opens a far-right Media3 submenu unlike Sinema's other player menus.

The approved approach uses existing playback intents, Home layout, controller callbacks, and centered player dialog. It does not add a thumbnail service, a new UI framework, or unrelated refactoring.

## 1. Distinguish confirmed Resume from ordinary Play

Add an explicit playback intent flag meaning that the user has already confirmed the saved resume position.

- Scene Resume buttons, Home hero Resume, and timestamp-marker playback set the flag and continue from their intended position without showing the **Resume playback** dialog.
- Ordinary Play does not set the flag. When a meaningful saved position exists, it retains the existing dialog offering resume or start-over behavior.
- Playback without a meaningful saved position starts normally regardless of entry point.
- The flag affects only prompt suppression. It must not change watch-time accounting, saved-position persistence, stream selection, PIN behavior, or other playback session setup.
- Keep the behavior in the shared `SceneIntents`/`PlaybackActivity` path so equivalent entry points do not diverge.

Regression tests must inspect real intents from each Resume entry-point family and exercise playback startup for confirmed Resume, ordinary Play with a saved position, and playback with no saved position.

## 2. Reserve Home logo space

Size the Home content's top inset from the logo's rendered bottom plus enough clearance for its focus/selection scale. Remove reliance on the fixed `80dp` inset that can place Quick Actions beneath the logo.

- Preserve the logo's current width, horizontal placement, asset, and visual styling.
- Keep Quick Actions and all rows D-pad reachable, with existing Home focus ownership intact.
- Apply the inset after layout so it uses actual rendered geometry rather than an assumed asset height.
- Do not alter or replace screenshots or other image assets.

Add a layout regression test proving that the first actionable Home content begins below the scaled logo bounds, including the Quick Actions region.

## 3. Make stationary scrubbing seek without resuming

Replace the misleading seek-preview artwork with a time-only indicator for the currently scrubbed position. Sinema will not claim frame-accurate artwork while using the existing server screenshot URL and cache behavior.

When playback is paused and the user moves along the seek bar:

- Each movement updates the displayed target time and cancels any pending dwell seek.
- After two seconds at a stationary target, seek the player to that target but leave playback paused.
- Further movement starts a new two-second dwell period.
- Center/Play uses the normal transport action to resume playback.
- Leaving the controller, starting playback, or tearing down the player cancels pending work.
- Do not auto-resume after the dwell seek, and do not trigger repeated seeks while the target remains unchanged.

The implementation should use the existing controller/main-thread lifecycle and Media3 player APIs. Unit or Robolectric tests must control time and verify cancellation on movement and teardown, the paused dwell seek, and explicit resume. Tests must not depend on real network thumbnails.

## 4. Use Sinema's centered Settings dialog

Replace the Media3 `exo_settings` wiring with an app-owned Settings button that invokes the existing `showPlayerMenu()` dialog.

- Settings opens in the same centered dialog style as Rate, Quality, and Aspect Ratio.
- Preserve the existing settings choices and their behavior.
- Keep the button's position, focusability, and D-pad navigation.
- Remove obsolete Media3 settings-submenu wiring rather than leaving two competing paths.

Add a controller test proving that activating Settings opens Sinema's dialog and does not open Media3's far-right settings submenu.

## Verification and scope

Follow test-driven development for each fix. Run focused tests as changes land, then the full debug unit suite, Android lint, and debug assembly on the combined deliverable. Use the documented emulator smoke tests if available; do not install on a physical TV.

Before completion, obtain the requested peer review and address findings within this scope. Preserve the branch's existing profile-isolation work and keep its uncommitted files out of UX-only commits. No dependency changes, screenshots, version bump, push, or GitHub release are part of this design.
