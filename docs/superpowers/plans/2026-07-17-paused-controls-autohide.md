# Paused-State Auto-Hide of Player Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide the Media3 default controller 3 s after the user pauses playback in `PlaybackActivity`; no dim/scrim remains; BACK while paused-and-hidden brings the controller back; BACK otherwise finishes the activity.

**Architecture:** Thin wrapper inside the existing `PlaybackActivity.kt` around the existing Media3 default controller already shown by `activity_playback.xml`. Three additions: a `Handler`-driven hide runnable scheduled from `Player.Listener.onIsPlayingChanged`, `onUserInteraction` cancels it, and an `OnBackPressedDispatcher` callback intercepts BACK to show the controller instead of finishing. The dim/scrim is part of Media3's controller overlay and rides along with it.

**Tech Stack:** Kotlin, Android TV, Media3 (`androidx.media3:media3-ui`), AndroidX Activity `OnBackPressedDispatcher`, Android `Handler` / `Looper`. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-07-17-paused-controls-autohide-design.md`

## Global Constraints

- Single file: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`. No layout, manifest, dependency, or resource changes.
- No new unit tests. Spec is explicit that this is verified manually; the behavior is a UI state machine tightly coupled to Media3's `PlayerView` and the activity lifecycle — adding Robolectric / instrumentation is out of scope.
- Conventional Commits commit messages. No AI/agent attribution anywhere — no `Co-Authored-By`, no "Generated with…" lines.
- Android TV focus and Media3 controller auto-show behaviors are preserved (don't disable Media3's own show-on-touch / key handling).
- Hide delay is `3000L` ms (matches the user's "3 seconds" call out exactly).

---

### Task 1: Add hide runnable fields and wire `onIsPlayingChanged`

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

**Interfaces produced (other tasks will reuse these names):**
- `private val handler: android.os.Handler`
- `private val hideRunnable: Runnable`
- `private const val HIDE_DELAY_MS = 3000L` (or `private val` if you prefer no const — either is fine)
- `private var isPaused: Boolean = false`

- [ ] **Step 1: Add imports and fields at the top of `PlaybackActivity.kt`**

Add these imports near the other `android.*` / `androidx.*` imports at the top of the file:

```kotlin
import android.os.Handler
import android.os.Looper
```

Add these private fields alongside the existing ones (after `isControllerVisible`):

```kotlin
private var isPaused = false
private val handler = Handler(Looper.getMainLooper())
private val hideRunnable = Runnable { playerView.hideController() }
private val hideDelayMs = 3000L
```

- [ ] **Step 2: Add an `onIsPlayingChanged` callback to the `Player.Listener`**

Inside `initPlayer()`, the existing listener only implements `onPlaybackStateChanged(state)`. Add `onIsPlayingChanged` to it (sibling override, same listener object):

```kotlin
exo.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        isPaused = !isPlaying
        if (isPaused) {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, hideDelayMs)
        } else {
            handler.removeCallbacks(hideRunnable)
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        // ...existing body unchanged...
    }
})
```

The exact position of `onIsPlayingChanged` relative to `onPlaybackStateChanged` does not matter; both must be inside the same `object : Player.Listener { ... }`.

- [ ] **Step 3: Cancel pending hide in `onStop()` and `onDestroy()`**

Extend the existing `onStop()` override so the runnable is removed before any other teardown:

```kotlin
override fun onStop() {
    handler.removeCallbacks(hideRunnable)
    super.onStop()
    chaptersDialog?.dismiss()
    chaptersDialog = null
    releasePlayer()
    if (isFinishing) PlaybackQueue.clear()
}
```

Add a matching `onDestroy()` override to handle the case where the activity is destroyed without `onStop()` running first (rare but possible during process-death-like paths):

```kotlin
override fun onDestroy() {
    handler.removeCallbacks(hideRunnable)
    super.onDestroy()
}
```

`removeCallbacks` is idempotent and harmless after the handler / player are already gone. Do not move or delete any of the existing teardown calls in `onStop()`.

- [ ] **Step 4: Manual smoke check — paused-controller hides after 3 s**

Build and install on a TV or emulator (e.g. `Android TV (1080p) API 33` AVD). Then:

1. Open a video → press pause (center / play/pause key on remote).
2. Watch the controller overlay. It should fade out roughly 3 s later, taking the dim with it. The video frame should remain visible at full brightness on the frozen paused frame.
3. Resume → controller returns to default Media3 behavior (auto-hides on its own timeout).
4. Pause again → after 3 s, hide should fire.
5. Press BACK → activity finishes (controller state is independent of this test).

If anything goes wrong, do not commit; re-open the file and check Steps 1–3 were applied verbatim. Common mistakes:
- `Handler(Looper.getMainLooper())` constructed on a non-main thread → would crash. Default to `MainLooper` as shown.
- Forgot `removeCallbacks` in `onStop` → harmless on TV but could leak on slow emulators.
- Forgot `onIsPlayingChanged` is an override (needs `override` keyword and method signature exactly as shown).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sinema/ui/PlaybackActivity.kt
git commit -m "feat(player): hide controller 3s after pause

Schedules a hide runnable from Player.Listener.onIsPlayingChanged when
playback transitions to paused; cancels on resume. Cancels again in
onStop. Media3's dim/scrim rides the controller, so it fades too."
```

---

### Task 2: Cancel hide runnable and show controller on user interaction

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

**Interfaces consumed:**
- `handler`, `hideRunnable`, `isPaused`, `playerView` from Task 1.
- `isControllerVisible` is already populated by the existing `ControllerVisibilityListener` wired in `onCreate`.

- [ ] **Step 1: Override `onUserInteraction` to cancel the timer**

Add this method to `PlaybackActivity` (anywhere outside `dispatchKeyEvent` — placing it adjacent to it is fine):

```kotlin
override fun onUserInteraction() {
    super.onUserInteraction()
    if (!isPaused) return
    handler.removeCallbacks(hideRunnable)
    if (!isControllerVisible) playerView.showController()
}
```

The system delivers `onUserInteraction` for any dispatched input event (D-pad, media keys, touch) — this covers the "any D-pad / media key wakes controls" requirement without enumerating key codes.

- [ ] **Step 2: Manual smoke check — key interactions during paused-hidden state**

Same setup as Task 1 step 4.

1. Pause the video → wait ~3 s → controller and dim fade away (Task 1 already working).
2. Without exiting, press any D-pad key (up / down / left / right). The controller should reappear with its dim, then start its own 3 s countdown toward hide.
3. While paused-visible, press a d-pad key again → Media3's default key handling runs (e.g., d-pad-right seeks to next marker if markers exist, harmless otherwise); the timer resets so the controller does not yank itself away mid-interaction.

If the controller doesn't come back on d-pad press: confirm Step 1 was added and `isControllerVisible` is being updated by the existing listener (it is — set in `onCreate`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sinema/ui/PlaybackActivity.kt
git commit -m "feat(player): cancel pause-hide timer on any user interaction

onUserInteraction clears the pending hide runnable and shows the
controller if it's currently hidden, so any d-pad / media key during
the paused-hidden state brings controls back without finishing."
```

---

### Task 3: Intercept BACK to show controller when paused-and-hidden

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

**Interfaces consumed:**
- `handler`, `hideRunnable`, `hideDelayMs`, `playerView`, `isControllerVisible`, `player` (nullable `ExoPlayer?`), and `isPaused` from Task 1 + 2.
- `OnBackPressedDispatcher` — add `import androidx.activity.OnBackPressedDispatcher` and `import androidx.activity.OnBackPressedCallback`.

- [ ] **Step 1: Add the missing imports**

Inside `PlaybackActivity.kt`, alongside the other `androidx.*` imports:

```kotlin
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedCallback
```

(`OnBackPressedDispatcher` is technically only needed if you want to fetch the dispatcher explicitly; `addCallback` on `this` already uses the activity's `OnBackPressedDispatcher` under the hood. The minimum required import is `OnBackPressedCallback`. Including both is fine; remove `OnBackPressedDispatcher` if your editor flags it as unused.)

- [ ] **Step 2: Register the back callback inside `onCreate()`**

At the end of `onCreate()` (after `markers = SceneIntents.markersFrom(intent)`), add:

```kotlin
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
```

The condition captures exactly the "paused with controller hidden" case. Anything else — including the controller visible because the user just pressed a key — falls through to `finish()`, preserving today's behavior.

- [ ] **Step 3: Manual smoke check — BACK semantics**

Same setup as before.

1. Play a video → pause → wait ~3 s → controller and dim gone (Tasks 1+2 already working).
2. Press BACK → controller reappears. Activity does **not** finish.
3. Wait ~3 s → controller hides again automatically.
4. Press BACK → controller reappears.
5. Press BACK again → activity finishes (controller is now visible, so the else branch runs).
6. While playing (not paused): press BACK → activity finishes immediately, as before.
7. While paused-visible (within the 3 s window): press BACK → activity finishes (controller was visible; the else branch).

If BACK exits the activity even when paused-and-hidden: check the call signature against Step 2 (`handleOnBackPressed()` not `onBackPressed()`), and that `onBackPressedDispatcher.addCallback(this, ...)` ran (not just declared). Also confirm `androidx.activity.ComponentActivity` is the activity base class — `PlaybackActivity` extends `FragmentActivity`, which inherits from `ComponentActivity`, so the dispatcher is available.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sinema/ui/PlaybackActivity.kt
git commit -m "feat(player): BACK shows controller when paused-and-hidden

OnBackPressedCallback short-circuits the activity finish only in the
paused-and-controller-hidden case. Anywhere else (paused-visible,
playing, mid-seek, after a key press that surfaced the controller),
BACK still finishes the activity. Re-arms the 3s hide timer on show."
```

---

### Task 4: Full feature smoke pass and push

**Files:** none changed in this task — verification + push only.

- [ ] **Step 1: Full smoke pass against the spec's test list**

Run each item from the spec's "Testing" section and confirm:

1. Play → pause → controls hide after ~3 s, no dim remains.
2. Paused-hidden, any D-pad / media key → controls reappear, dim back, fresh countdown.
3. Paused-hidden, BACK → controls reappear, no exit.
4. Paused-visible, BACK → activity finishes.
5. Playing, default controller behavior unchanged (auto-hide still happens).
6. Play → pause → resume within 3 s → controller stays in expected state, no stuck hidden.
7. Open chapters dialog → press BACK to dismiss → playback state and timer state correct.
8. Pause → background the app (Home key) → return → no leaked runnable, controller in expected state.

If any fail, **stop and fix the previous task**. Do not push until all 8 pass.

- [ ] **Step 2: Confirm only the three intended files changed**

```bash
git diff 960d1d1..HEAD --stat
```

Expected: only `app/src/main/java/com/sinema/ui/PlaybackActivity.kt` shows as a changed source file. The spec doc (`docs/superpowers/specs/...`) was committed earlier in the design phase — already in history, not in this range unless you are sweeping too wide.

- [ ] **Step 3: Push to `origin/main`**

```bash
git push origin main
```

Expected: linear fast-forward push, no force. Three new commits land on `origin/main` (Tasks 1, 2, 3).

- [ ] **Step 4: Final commit (only if you fixed anything in Steps 1–2)**

If Steps 1–2 required fixes, commit them first with a follow-up message (Conventional Commits, e.g. `fix(player): <what you fixed>`) before pushing — never mix fixes with the push itself.

---

## Done When

- All 8 smoke items pass.
- `git push origin main` reports a clean fast-forward.
- `git log origin/main` shows three new commits in order: the paused-hide timer, the user-interaction cancel, the BACK intercept.
