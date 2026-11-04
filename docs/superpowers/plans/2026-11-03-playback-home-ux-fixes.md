# Playback and Home UX Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Resume, Home spacing, paused scrubbing, and playback Settings behave consistently without changing unrelated playback or Home behavior.

**Architecture:** Carry an explicit resume-confirmation bit through `SceneIntents`, derive Home spacing from the laid-out logo, isolate cancellable two-second dwell scheduling in a small controller class, and route an app-owned Settings button to the existing player dialog. Keep playback networking, Media3 transport controls, and Leanback rows intact.

**Tech Stack:** Kotlin, Android Views, AndroidX Leanback, Media3 ExoPlayer, JUnit 4, Robolectric, Gradle.

## Global Constraints

- Use the approved design at `docs/superpowers/specs/2026-11-03-playback-home-ux-fixes-design.md`.
- Follow test-driven development for every behavioral fix; preserve both authentication modes and all existing destinations.
- Keep all controls focusable and D-pad reachable. Do not add dependencies or replace Leanback/Media3 UI frameworks.
- Do not alter screenshots or image assets. Do not bump versions, push, or create a release.
- Preserve and do not stage the branch's existing uncommitted profile-isolation files.
- Prefix every shell command with `rtk`.

---

### Task 1: Confirmed Resume versus ordinary Play

**Files:**
- Modify: `app/src/main/java/com/sinema/util/SceneIntents.kt`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/SceneDetailActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/MarkerBrowserActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/SceneGridFragment.kt`
- Modify: `app/src/main/java/com/sinema/ui/GroupScenesActivity.kt`
- Test: `app/src/test/java/com/sinema/util/SceneIntentsTest.kt`
- Test: `app/src/test/java/com/sinema/ui/PlaybackControlsStateTest.kt`

**Interfaces:**
- Produces: `SceneIntents.playback(..., resumeConfirmed: Boolean = false)` and `SceneIntents.resumeConfirmedFrom(intent): Boolean`.
- Produces: `SceneIntents.playAll(..., resumeConfirmed: Boolean = false)` for explicit group Resume.
- Consumes: existing `shouldPromptResume(resumeMs)` and playback initialization seek behavior.

- [ ] **Step 1: Write failing intent and prompt-decision tests**

Create `SceneIntentsTest.kt` with Robolectric's application context:

```kotlin
package com.sinema.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SceneIntentsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun `ordinary play does not confirm resume`() {
        assertFalse(SceneIntents.resumeConfirmedFrom(SceneIntents.playback(context, "scene", 45_000L)))
    }

    @Test fun `confirmed resume survives intent transport`() {
        val intent = SceneIntents.playback(context, "scene", 45_000L, resumeConfirmed = true)
        assertTrue(SceneIntents.resumeConfirmedFrom(intent))
    }
}
```

Add prompt-decision coverage to `PlaybackControlsStateTest.kt` using the production decision extracted in Step 3:

```kotlin
@Test
fun `confirmed resume bypasses only the prompt`() {
    assertFalse(shouldShowResumePrompt(promptAllowed = true, resumeResolved = true, resumeMs = 45_000L))
    assertTrue(shouldShowResumePrompt(promptAllowed = true, resumeResolved = false, resumeMs = 45_000L))
    assertFalse(shouldShowResumePrompt(promptAllowed = true, resumeResolved = false, resumeMs = 0L))
}
```

- [ ] **Step 2: Run the focused tests and confirm the new API is missing**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.SceneIntentsTest' --tests 'com.sinema.ui.PlaybackControlsStateTest' --no-daemon`
Expected: compilation failure for `resumeConfirmedFrom`, `resumeConfirmed`, and `shouldShowResumePrompt`.

- [ ] **Step 3: Implement the explicit confirmation bit and startup decision**

Add `KEY_RESUME_CONFIRMED`, a trailing `resumeConfirmed: Boolean = false` parameter to `playback`, the corresponding `putExtra`, and:

```kotlin
fun resumeConfirmedFrom(intent: Intent): Boolean =
    intent.getBooleanExtra(KEY_RESUME_CONFIRMED, false)
```

Add the same trailing flag to `playAll` and pass it to its `playback` call. In `PlaybackControlsState.kt` add:

```kotlin
fun shouldShowResumePrompt(promptAllowed: Boolean, resumeResolved: Boolean, resumeMs: Long): Boolean =
    promptAllowed && !resumeResolved && shouldPromptResume(resumeMs)
```

In `PlaybackActivity`, add `private var resumeConfirmed = false`, initialize it from the intent before choosing the startup branch, and use:

```kotlin
val promptResume = shouldShowResumePrompt(
    promptAllowed = promptAllowed,
    resumeResolved = resumePromptResolved || resumeConfirmed,
    resumeMs = resumePositionMs
)
```

Inside successful `loadInitialMetadata`, populate an unconfirmed ordinary Play from freshly fetched metadata so Play can offer the choice even when its button passed zero:

```kotlin
if (!resumeConfirmed) resumePositionMs = (d.scene.resumeTime * 1000).toLong()
```

Do not overwrite marker or explicitly confirmed Resume positions.

- [ ] **Step 4: Mark every explicit Resume/timestamp entry point**

Use `resumeConfirmed = true` for Home hero Resume, `MarkerBrowserActivity`, the Scene Detail Resume button, Scene Detail chapter cards, and grid long-press actions whose label is Resume. Change Scene Detail's helper to:

```kotlin
private fun launchPlayback(resumeMs: Long, resumeConfirmed: Boolean = false) {
    val intent = SceneIntents.playback(
        this, scene.id, resumeMs, scene.width, scene.height, resumeConfirmed
    ).apply {
        SceneIntents.putCaptions(this, captions)
        SceneIntents.putMarkers(this, markers)
    }
    startActivity(intent)
}
```

Call it with `launchPlayback(resumeMs, resumeConfirmed = true)` for Resume and chapters. Leave ordinary Play unconfirmed. Pass `resumeConfirmed = true` from `GroupScenesFragment.playResume`; ordinary `playAll` remains unconfirmed.

- [ ] **Step 5: Run focused tests and inspect all launch call sites**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.SceneIntentsTest' --tests 'com.sinema.ui.PlaybackControlsStateTest' --tests 'com.sinema.ui.HomeCommandRowIntegrationTest' --no-daemon`
Expected: PASS.

Run: `rtk git diff --check -- app/src/main/java/com/sinema app/src/test/java/com/sinema`
Expected: no whitespace errors.

- [ ] **Step 6: Commit only Task 1 files**

```bash
rtk git add app/src/main/java/com/sinema/util/SceneIntents.kt app/src/main/java/com/sinema/ui/PlaybackActivity.kt app/src/main/java/com/sinema/ui/SceneDetailActivity.kt app/src/main/java/com/sinema/ui/MainActivity.kt app/src/main/java/com/sinema/ui/MarkerBrowserActivity.kt app/src/main/java/com/sinema/ui/SceneGridFragment.kt app/src/main/java/com/sinema/ui/GroupScenesActivity.kt app/src/test/java/com/sinema/util/SceneIntentsTest.kt app/src/test/java/com/sinema/ui/PlaybackControlsStateTest.kt
rtk git commit -m "fix: distinguish resume from play"
```

---

### Task 2: Home logo clearance

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt`
- Modify: `app/src/main/res/values/ids.xml`
- Test: `app/src/test/java/com/sinema/ui/HomeCommandRowIntegrationTest.kt`

**Interfaces:**
- Produces: `<item name="home_logo" type="id" />` and `R.id.home_logo` for layout regression inspection.
- Consumes: existing fragment root and `verticalGridView` padding, logo scroll translation, and `HomeCommandRowIntegrationTest.layoutHome`.

- [ ] **Step 1: Add a failing laid-out clearance test**

In `HomeCommandRowIntegrationTest`, launch and lay out Home, focus the first Quick Action, and compare actual geometry. The test must assert both reserved padding and visible bounds:

```kotlin
@Test
fun `quick actions stay below rendered logo with focus clearance`() {
    val activity = launch(pin = true)
    val logo = activity.findViewById<android.widget.ImageView>(com.sinema.R.id.home_logo)
    val quick = itemView(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT)
    focusItem(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT)
    layoutHome(activity)
    val logoBounds = android.graphics.Rect()
    val quickBounds = android.graphics.Rect()
    logo.getGlobalVisibleRect(logoBounds)
    quick.getGlobalVisibleRect(quickBounds)
    val focusedQuickTop = quickBounds.top + ((quick.height * (quick.scaleY - 1f)) / 2f).toInt()
    assertTrue(mainFragment(activity).requireView().paddingTop >= logo.bottom)
    assertTrue(focusedQuickTop >= logoBounds.bottom)
}
```

- [ ] **Step 2: Verify the regression before implementation**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.HomeCommandRowIntegrationTest.quick actions stay below rendered logo with focus clearance' --no-daemon`
Expected: failure because `home_logo` does not exist or the focused Quick Action intersects the fixed inset region.

- [ ] **Step 3: Derive top padding after logo layout**

Add `<item name="home_logo" type="id" />` to `app/src/main/res/values/ids.xml`, then set `logo.id = R.id.home_logo`. Keep the current asset, width, margins, and scroll listener. After adding the logo, register a layout listener that computes `logo.bottom + 24dp` and applies that value to both existing padding destinations:

```kotlin
fun applyLogoClearance(bottom: Int) {
    val contentTop = bottom + (24 * density).toInt()
    view.setPadding(hPad, contentTop, hPad, vPad)
    verticalGridView?.setPadding(hPad, contentTop, hPad, vPad)
}
logo.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
    applyLogoClearance(bottom)
}
```

Keep `clipToPadding = false` and the existing initial padding as a pre-layout fallback. The 24dp clearance is for focused row content growing upward; do not scale or move the logo itself.

- [ ] **Step 4: Run all Home integration tests**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.HomeCommandRowIntegrationTest' --no-daemon`
Expected: PASS, including existing cold-launch focus and D-pad edge tests.

- [ ] **Step 5: Commit only Task 2 files**

```bash
rtk git add app/src/main/java/com/sinema/ui/MainActivity.kt app/src/main/res/values/ids.xml app/src/test/java/com/sinema/ui/HomeCommandRowIntegrationTest.kt
rtk git commit -m "fix: keep home logo clear of quick actions"
```

---

### Task 3: Paused two-second scrub seek and time-only preview

**Files:**
- Create: `app/src/main/java/com/sinema/ui/PausedSeekDwell.kt`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackControlsState.kt`
- Modify: `app/src/main/res/layout/playback_controller.xml`
- Test: `app/src/test/java/com/sinema/ui/PausedSeekDwellTest.kt`
- Test: `app/src/test/java/com/sinema/ui/PlaybackControlsStateTest.kt`

**Interfaces:**
- Produces: `PausedSeekDwell.onPositionChanged(positionMs: Long)`, `cancel()`, and `clampSeekPositionMs(positionMs, durationMs)`.
- Consumes: existing main-thread `Handler`, `MarkerTimeBar.ScrubPreviewListener`, `player.playWhenReady`, and `player.seekTo`.

- [ ] **Step 1: Write deterministic failing dwell tests**

Create a Robolectric test with a paused main looper, a mutable `playWhenReady`, and a recorded seek list. Cover all timing and cancellation behavior:

```kotlin
@Test fun `stationary paused target seeks once after two seconds without playing`() {
    dwell.onPositionChanged(42_000L)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
    assertTrue(seeks.isEmpty())
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
    assertEquals(listOf(42_000L), seeks)
    assertFalse(playWhenReady)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
    assertEquals(listOf(42_000L), seeks)
}

@Test fun `movement replaces pending target and cancel prevents seek`() {
    dwell.onPositionChanged(10_000L)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
    dwell.onPositionChanged(20_000L)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
    assertTrue(seeks.isEmpty())
    dwell.cancel()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
    assertTrue(seeks.isEmpty())
}

@Test fun `play request prevents pending dwell seek`() {
    dwell.onPositionChanged(20_000L)
    playWhenReady = true
    dwell.cancel()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    assertTrue(seeks.isEmpty())
}
```

Also replace the old bucket test with exact/clamped target assertions:

```kotlin
assertEquals(7_200L, clampSeekPositionMs(7_200L, 60_000L))
assertEquals(0L, clampSeekPositionMs(-100L, 60_000L))
assertEquals(60_000L, clampSeekPositionMs(90_000L, 60_000L))
assertEquals(0L, clampSeekPositionMs(10_000L, 0L))
```

- [ ] **Step 2: Run the tests and confirm failure**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.PausedSeekDwellTest' --tests 'com.sinema.ui.PlaybackControlsStateTest' --no-daemon`
Expected: compilation failure for the new dwell class and clamp function.

- [ ] **Step 3: Implement the single-flight dwell scheduler**

Create `PausedSeekDwell.kt`:

```kotlin
package com.sinema.ui

import android.os.Handler

internal class PausedSeekDwell(
    private val handler: Handler,
    private val playWhenReady: () -> Boolean,
    private val seekTo: (Long) -> Unit
) {
    private var targetMs: Long? = null
    private val seek = Runnable {
        val target = targetMs ?: return@Runnable
        targetMs = null
        if (!playWhenReady()) seekTo(target)
    }

    fun onPositionChanged(positionMs: Long) {
        handler.removeCallbacks(seek)
        targetMs = positionMs
        handler.postDelayed(seek, 2_000L)
    }

    fun cancel() {
        handler.removeCallbacks(seek)
        targetMs = null
    }
}
```

Replace `previewBucketMs` with `clampSeekPositionMs`, returning the exact clamped target or zero for unknown duration.

- [ ] **Step 4: Bind dwell scheduling and remove screenshot preview loads**

Instantiate the dwell scheduler with the activity handler, `{ player?.playWhenReady == true }`, and `{ player?.seekTo(it) }`. `showSeekPreview` must display the exact clamped position and call `onPositionChanged`; it must contain no Glide/API screenshot request. `hideSeekPreview` must call `cancel` before hiding the container. Cancel through `hideSeekPreview` on preview stop, controller hide, playback request, scene switch, pause, and player release.

Add `Player.Listener.onPlayWhenReadyChanged` and hide/cancel immediately when `playWhenReady` becomes true. Do not set `playWhenReady` in the dwell callback; Center/Play remains the only resume action.

Delete `previewBucket`, `previewSceneId`, and preview Glide listener code. Remove `playback_preview_image` from the controller layout and make `playback_preview_time` a compact, centered, wrap-content time indicator with its existing readable background and text styling.

- [ ] **Step 5: Run playback state, dwell, key-routing, and controller tests**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.PausedSeekDwellTest' --tests 'com.sinema.ui.PlaybackControlsStateTest' --tests 'com.sinema.ui.PlaybackKeyRoutingTest' --tests 'com.sinema.ui.PlaybackControllerContractTest' --no-daemon`
Expected: PASS and no references to `playback_preview_image` or `previewBucketMs`.

- [ ] **Step 6: Commit only Task 3 files**

```bash
rtk git add app/src/main/java/com/sinema/ui/PausedSeekDwell.kt app/src/main/java/com/sinema/ui/PlaybackActivity.kt app/src/main/java/com/sinema/ui/PlaybackControlsState.kt app/src/main/res/layout/playback_controller.xml app/src/test/java/com/sinema/ui/PausedSeekDwellTest.kt app/src/test/java/com/sinema/ui/PlaybackControlsStateTest.kt
rtk git commit -m "fix: seek after paused scrub dwell"
```

---

### Task 4: Centered playback Settings dialog

**Files:**
- Modify: `app/src/main/res/layout/playback_controller.xml`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`
- Test: `app/src/test/java/com/sinema/ui/PlaybackControllerContractTest.kt`

**Interfaces:**
- Produces: `R.id.playback_settings_button` bound directly to `showPlayerMenu()`.
- Consumes: existing `showPlayerMenu()` and `playerMenuDialog` lifecycle handling.

- [ ] **Step 1: Add a failing controller contract test**

Add a test that reads the real layout/activity and asserts an app-owned Settings ID, no Media3 `exo_settings` ID, and a direct listener to the existing dialog:

```kotlin
@Test
fun `settings uses Sinema centered player menu instead of Media3 submenu`() {
    val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/playback_controller.xml")))
    val activity = String(Files.readAllBytes(Paths.get("src/main/java/com/sinema/ui/PlaybackActivity.kt")))
    assertTrue(layout.contains("@+id/playback_settings_button"))
    assertFalse(layout.contains("@id/exo_settings"))
    assertTrue(activity.contains("R.id.playback_settings_button"))
    assertTrue(activity.contains("setOnClickListener { showPlayerMenu() }"))
}
```

- [ ] **Step 2: Verify the new contract fails on the old wiring**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.PlaybackControllerContractTest.settings uses Sinema centered player menu instead of Media3 submenu' --no-daemon`
Expected: failure on the old `exo_settings` path.

- [ ] **Step 3: Replace the ID and bind the existing dialog**

In `playback_controller.xml`, keep the same Settings style and position but use `@+id/playback_settings_button`. In `bindPlaybackControls`, add:

```kotlin
playerView.findViewById<View>(R.id.playback_settings_button).setOnClickListener { showPlayerMenu() }
```

Do not add another dialog or change the entries in `showPlayerMenu`.

- [ ] **Step 4: Run controller and dialog-related playback tests**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.PlaybackControllerContractTest' --tests 'com.sinema.ui.PlaybackKeyRoutingTest' --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit only Task 4 files**

```bash
rtk git add app/src/main/res/layout/playback_controller.xml app/src/main/java/com/sinema/ui/PlaybackActivity.kt app/src/test/java/com/sinema/ui/PlaybackControllerContractTest.kt
rtk git commit -m "fix: center playback settings dialog"
```

---

### Task 5: Combined review and verification

**Files:**
- Review only the four commits from Tasks 1–4 and their design/plan documents.
- Preserve all unrelated modified/untracked hardening files.

**Interfaces:**
- Consumes: all public interfaces above.
- Produces: verified UX commits ready to return to the broader hardening sequence.

- [ ] **Step 1: Run the complete debug unit suite**

Run: `rtk ./gradlew :app:testDebugUnitTest --no-daemon`
Expected: PASS with no failed tests.

- [ ] **Step 2: Run Android lint and assemble the debug app**

Run: `rtk ./gradlew :app:lintDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

Run: `rtk ./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL and a debug APK produced.

- [ ] **Step 3: Request the required independent peer review**

Run `rtk opencode run` with the four-commit diff, approved spec, and a request to inspect behavior, lifecycle cancellation, D-pad navigation, test quality, and unintended changes. If the CLI cannot authenticate or execute, use a separate Kimi reviewer. Resolve actionable findings with regression tests and rerun affected checks.

- [ ] **Step 4: Audit the final diff and workspace**

Run: `rtk git diff --check 732f181..HEAD`
Expected: no whitespace errors.

Run: `rtk git status --short`
Expected: only the pre-existing profile-isolation modifications/untracked files and no generated build artifacts.

Run: `rtk git diff --stat 732f181..HEAD`
Expected: only approved UX code, resources, tests, and documentation; no assets, dependencies, versions, or screenshots.
