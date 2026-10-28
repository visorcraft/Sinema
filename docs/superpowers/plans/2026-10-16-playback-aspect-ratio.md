# Playback Aspect-Ratio Override Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persisted, D-pad-accessible Auto/16:9/4:3/9:16 aspect-ratio override that always fits playback without cropping while preserving Media3's existing Settings, speed, and audio controls.

**Architecture:** A pure Kotlin enum owns labels, fixed ratios, and safe preference parsing. A small `PlayerView` subclass applies the selected ratio through Media3's content-aspect-ratio hook, while a Sinema dialog and a dedicated controller button expose the choice without replacing Media3's private gear menu.

**Tech Stack:** Kotlin, Android Views, AndroidX Media3 1.10.1 `PlayerView`/`AspectRatioFrameLayout`, `SharedPreferences` through `Prefs`, JUnit 4, Gradle.

**Design spec:** `docs/superpowers/specs/2026-10-16-playback-aspect-ratio-design.md`

## Global Constraints

- Choices are exactly **Auto (source)**, **16:9**, **4:3**, and **9:16**; every choice uses `RESIZE_MODE_FIT` and never intentionally crops.
- Auto preserves Media3's source geometry. Manual ratios apply immediately and persist player-wide until changed, including queue auto-advance.
- Null, empty, and unknown persisted values fall back to Auto.
- Keep Media3 1.10.1 and add no dependencies.
- Do not override `exo_settings` or replace stock speed/audio handlers. Preserve subtitles, repeat, chapters, resume saving, authentication, and queue behavior.
- All new interactive controls must be focusable and reachable with the D-pad.
- Prefix every shell command with `rtk` as required by `AGENTS.md`.
- Do not install or launch on a physical TV unless the user explicitly requests it. Report on-device rendering acceptance separately from automated verification.
- Do not add AI attribution or generated-by trailers.

## File Structure

- Create `app/src/main/java/com/sinema/model/PlaybackAspectRatio.kt`: pure ratio selection and preference parsing.
- Create `app/src/test/java/com/sinema/model/PlaybackAspectRatioTest.kt`: JUnit coverage for ratio and parsing behavior.
- Create `app/src/main/java/com/sinema/ui/AspectRatioPlayerView.kt`: apply Auto or fixed ratios to Media3's content frame.
- Create `app/src/main/java/com/sinema/ui/AspectRatioDialog.kt`: single-choice TV dialog returning the selected enum.
- Modify `app/src/main/java/com/sinema/util/Prefs.kt`: persist the selected enum name.
- Create `app/src/main/res/layout/playback_controller.xml`: Media3-compatible controller with one additional button.
- Create `app/src/main/res/drawable/ic_aspect_ratio.xml`: button icon.
- Modify `app/src/main/res/values/strings.xml`: button content description.
- Modify `app/src/main/res/layout/activity_playback.xml`: use the custom player view and controller.
- Modify `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`: restore/apply/save selections, manage the dialog, and log video geometry.
- Modify `docs/playback.md`: document the new control and persistence.

---

### Task 1: Pure Aspect-Ratio Model

**Files:**
- Create: `app/src/main/java/com/sinema/model/PlaybackAspectRatio.kt`
- Create: `app/src/test/java/com/sinema/model/PlaybackAspectRatioTest.kt`

**Interfaces:**
- Consumes: Nothing outside the Kotlin standard library and JUnit 4.
- Produces: `enum class PlaybackAspectRatio`, `val label: String`, `fun effectiveRatio(sourceRatio: Float): Float`, and `fun fromName(name: String?): PlaybackAspectRatio` for Tasks 2 and 3.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/sinema/model/PlaybackAspectRatioTest.kt`:

```kotlin
package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAspectRatioTest {
    @Test
    fun `auto returns source ratio unchanged`() {
        assertEquals(2.35f, PlaybackAspectRatio.AUTO.effectiveRatio(2.35f), 0f)
    }

    @Test
    fun `fixed ratios ignore source ratio`() {
        assertEquals(16f / 9f, PlaybackAspectRatio.RATIO_16_9.effectiveRatio(0.5f), 0f)
        assertEquals(4f / 3f, PlaybackAspectRatio.RATIO_4_3.effectiveRatio(0.5f), 0f)
        assertEquals(9f / 16f, PlaybackAspectRatio.RATIO_9_16.effectiveRatio(2f), 0f)
    }

    @Test
    fun `fromName round trips every entry`() {
        PlaybackAspectRatio.entries.forEach { option ->
            assertEquals(option, PlaybackAspectRatio.fromName(option.name))
        }
    }

    @Test
    fun `fromName falls back to auto for missing or unknown values`() {
        assertEquals(PlaybackAspectRatio.AUTO, PlaybackAspectRatio.fromName(null))
        assertEquals(PlaybackAspectRatio.AUTO, PlaybackAspectRatio.fromName(""))
        assertEquals(PlaybackAspectRatio.AUTO, PlaybackAspectRatio.fromName("garbage"))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the expected failure**

Run:

```bash
rtk ./gradlew :app:testDebugUnitTest --tests "com.sinema.model.PlaybackAspectRatioTest" --no-daemon
```

Expected: FAIL during Kotlin compilation with unresolved references to `PlaybackAspectRatio`, because the model does not exist yet.

- [ ] **Step 3: Implement the minimal model**

Create `app/src/main/java/com/sinema/model/PlaybackAspectRatio.kt`:

```kotlin
package com.sinema.model

enum class PlaybackAspectRatio(val label: String, private val fixedRatio: Float?) {
    AUTO("Auto (source)", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_9_16("9:16", 9f / 16f);

    fun effectiveRatio(sourceRatio: Float): Float = fixedRatio ?: sourceRatio

    companion object {
        fun fromName(name: String?): PlaybackAspectRatio =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
rtk ./gradlew :app:testDebugUnitTest --tests "com.sinema.model.PlaybackAspectRatioTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`; all four tests pass.

- [ ] **Step 5: Commit the tested model**

```bash
rtk git add app/src/main/java/com/sinema/model/PlaybackAspectRatio.kt app/src/test/java/com/sinema/model/PlaybackAspectRatioTest.kt
rtk git commit -m "feat(player): add aspect ratio options"
```

---

### Task 2: Player View, Dialog, and Preference Components

**Files:**
- Create: `app/src/main/java/com/sinema/ui/AspectRatioPlayerView.kt`
- Create: `app/src/main/java/com/sinema/ui/AspectRatioDialog.kt`
- Modify: `app/src/main/java/com/sinema/util/Prefs.kt:49-52`
- Test: `app/src/test/java/com/sinema/model/PlaybackAspectRatioTest.kt`

**Interfaces:**
- Consumes: `PlaybackAspectRatio.AUTO`, `PlaybackAspectRatio.entries`, `label`, `effectiveRatio(sourceRatio)`, and `fromName(name)` from Task 1.
- Produces: `AspectRatioPlayerView.playbackAspectRatio: PlaybackAspectRatio`, `AspectRatioDialog.show(context, current, onChosen): AlertDialog`, and `Prefs.playbackAspectRatio: String` for Task 3.

- [ ] **Step 1: Add the custom player view**

Create `app/src/main/java/com/sinema/ui/AspectRatioPlayerView.kt`:

```kotlin
package com.sinema.ui

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sinema.model.PlaybackAspectRatio

@OptIn(UnstableApi::class)
class AspectRatioPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {
    private var aspectRatioFrame: AspectRatioFrameLayout? = null
    private var sourceAspectRatio = 0f

    var playbackAspectRatio: PlaybackAspectRatio = PlaybackAspectRatio.AUTO
        set(value) {
            field = value
            applyAspectRatio()
        }

    override fun onContentAspectRatioChanged(
        contentFrame: AspectRatioFrameLayout?,
        aspectRatio: Float
    ) {
        aspectRatioFrame = contentFrame
        sourceAspectRatio = aspectRatio
        applyAspectRatio()
    }

    private fun applyAspectRatio() {
        aspectRatioFrame?.setAspectRatio(playbackAspectRatio.effectiveRatio(sourceAspectRatio))
    }
}
```

The override deliberately does not call `super`: calling it would first write the source ratio and could overwrite the fixed selection. `applyAspectRatio` reproduces the superclass behavior for Auto and substitutes the fixed ratio otherwise.

- [ ] **Step 2: Add the single-choice dialog**

Create `app/src/main/java/com/sinema/ui/AspectRatioDialog.kt`:

```kotlin
package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.PlaybackAspectRatio

object AspectRatioDialog {
    fun show(
        context: Context,
        current: PlaybackAspectRatio,
        onChosen: (PlaybackAspectRatio) -> Unit
    ): AlertDialog {
        val options = PlaybackAspectRatio.entries
        return AlertDialog.Builder(context)
            .setTitle("Aspect ratio")
            .setSingleChoiceItems(
                options.map { it.label }.toTypedArray(),
                options.indexOf(current)
            ) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which])
            }
            .show()
    }
}
```

- [ ] **Step 3: Add persisted storage**

In `app/src/main/java/com/sinema/util/Prefs.kt`, insert this property immediately after `loopEnabled` and before the server-profile comment:

```kotlin
    var playbackAspectRatio: String
        get() = prefs.getString("playback_aspect_ratio", "") ?: ""
        set(value) = prefs.edit().putString("playback_aspect_ratio", value).apply()
```

The surrounding code must read:

```kotlin
    var loopEnabled: Boolean
        get() = prefs.getBoolean("loop_enabled", false)
        set(value) = prefs.edit().putBoolean("loop_enabled", value).apply()

    var playbackAspectRatio: String
        get() = prefs.getString("playback_aspect_ratio", "") ?: ""
        set(value) = prefs.edit().putString("playback_aspect_ratio", value).apply()

    // Server profiles (stored in secure prefs)
```

- [ ] **Step 4: Compile the components and run unit tests**

Run:

```bash
rtk ./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`; the new Android classes compile and all existing plus new JVM tests pass. No Android runtime rendering assertion is made in this task.

- [ ] **Step 5: Commit the reusable components**

```bash
rtk git add app/src/main/java/com/sinema/ui/AspectRatioPlayerView.kt app/src/main/java/com/sinema/ui/AspectRatioDialog.kt app/src/main/java/com/sinema/util/Prefs.kt
rtk git commit -m "feat(player): add aspect ratio view and dialog"
```

---

### Task 3: Controller Integration, Diagnostics, and Documentation

**Files:**
- Create: `app/src/main/res/layout/playback_controller.xml`
- Create: `app/src/main/res/drawable/ic_aspect_ratio.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/activity_playback.xml`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`
- Modify: `docs/playback.md:57-66`
- Test: `app/src/test/java/com/sinema/model/PlaybackAspectRatioTest.kt`

**Interfaces:**
- Consumes: `AspectRatioPlayerView.playbackAspectRatio`, `AspectRatioDialog.show`, `Prefs.playbackAspectRatio`, and all `PlaybackAspectRatio` APIs from Tasks 1 and 2.
- Produces: The complete user-facing aspect-ratio control and diagnostic `VideoSize` logging.

- [ ] **Step 1: Add the button label and icon**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Sinema</string>
    <string name="playback_aspect_ratio">Aspect ratio</string>
</resources>
```

Create `app/src/main/res/drawable/ic_aspect_ratio.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#00000000"
        android:pathData="M4,5L20,5L20,19L4,19Z M8,9L16,9L16,15L8,15Z"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2" />
</vector>
```

- [ ] **Step 2: Add the Media3-compatible controller layout**

Create `app/src/main/res/layout/playback_controller.xml` with the stock Media3 1.10.1 controller structure and the additional `aspect_ratio_button` immediately before `exo_settings`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright 2020 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->
<merge xmlns:android="http://schemas.android.com/apk/res/android">

  <View android:id="@id/exo_controls_background"
      android:layout_width="0dp"
      android:layout_height="0dp"
      android:background="@color/exo_black_opacity_60"/>

  <FrameLayout android:id="@id/exo_bottom_bar"
      android:layout_width="match_parent"
      android:layout_height="@dimen/exo_styled_bottom_bar_height"
      android:layout_marginTop="@dimen/exo_styled_bottom_bar_margin_top"
      android:layout_gravity="bottom"
      android:background="@color/exo_bottom_bar_background"
      android:layoutDirection="ltr">

    <LinearLayout android:id="@id/exo_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingStart="@dimen/exo_styled_bottom_bar_time_padding"
        android:paddingEnd="@dimen/exo_styled_bottom_bar_time_padding"
        android:paddingLeft="@dimen/exo_styled_bottom_bar_time_padding"
        android:paddingRight="@dimen/exo_styled_bottom_bar_time_padding"
        android:layout_gravity="center_vertical|start"
        android:layoutDirection="ltr"
        android:importantForAccessibility="noHideDescendants">

      <TextView android:id="@id/exo_position"
          style="@style/ExoStyledControls.TimeText.Position"/>

      <TextView style="@style/ExoStyledControls.TimeText.Separator"/>

      <TextView android:id="@id/exo_duration"
          style="@style/ExoStyledControls.TimeText.Duration"/>

    </LinearLayout>

    <LinearLayout android:id="@id/exo_basic_controls"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical|end"
        android:layoutDirection="ltr">

      <ImageButton android:id="@id/exo_vr"
          style="@style/ExoStyledControls.Button.Bottom.VR"/>

      <ImageButton android:id="@id/exo_shuffle"
          style="@style/ExoStyledControls.Button.Bottom.Shuffle"/>

      <ImageButton android:id="@id/exo_repeat_toggle"
          style="@style/ExoStyledControls.Button.Bottom.RepeatToggle"/>

      <ImageButton android:id="@id/exo_subtitle"
          style="@style/ExoStyledControls.Button.Bottom.CC"/>

      <ImageButton
          android:id="@+id/aspect_ratio_button"
          style="@style/ExoStyledControls.Button.Bottom"
          android:contentDescription="@string/playback_aspect_ratio"
          android:src="@drawable/ic_aspect_ratio" />

      <ImageButton android:id="@id/exo_settings"
          style="@style/ExoStyledControls.Button.Bottom.Settings"/>

      <ImageButton android:id="@id/exo_fullscreen"
          style="@style/ExoStyledControls.Button.Bottom.FullScreen"/>

      <ImageButton android:id="@id/exo_overflow_show"
          style="@style/ExoStyledControls.Button.Bottom.OverflowShow"/>

    </LinearLayout>

    <HorizontalScrollView android:id="@id/exo_extra_controls_scroll_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical|end"
        android:visibility="invisible">

      <LinearLayout android:id="@id/exo_extra_controls"
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:layoutDirection="ltr">

        <ImageButton android:id="@id/exo_overflow_hide"
            style="@style/ExoStyledControls.Button.Bottom.OverflowHide"/>

      </LinearLayout>

    </HorizontalScrollView>

  </FrameLayout>

  <View android:id="@id/exo_progress_placeholder"
      android:layout_width="match_parent"
      android:layout_height="@dimen/exo_styled_progress_layout_height"
      android:layout_gravity="bottom"
      android:layout_marginBottom="@dimen/exo_styled_progress_margin_bottom"/>

  <LinearLayout android:id="@id/exo_minimal_controls"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:layout_gravity="bottom|end"
      android:layout_marginBottom="@dimen/exo_styled_minimal_controls_margin_bottom"
      android:orientation="horizontal"
      android:gravity="center_vertical"
      android:layoutDirection="ltr">

    <ImageButton android:id="@id/exo_minimal_fullscreen"
        style="@style/ExoStyledControls.Button.Bottom.FullScreen"/>

  </LinearLayout>

  <LinearLayout
      android:id="@id/exo_center_controls"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:layout_gravity="center"
      android:background="@android:color/transparent"
      android:gravity="center"
      android:padding="@dimen/exo_styled_controls_padding"
      android:clipToPadding="false"
      android:layoutDirection="ltr">

    <ImageButton android:id="@id/exo_prev"
        style="@style/ExoStyledControls.Button.Center.Previous"/>

    <include layout="@layout/exo_player_control_rewind_button" />

    <ImageButton android:id="@id/exo_play_pause"
        style="@style/ExoStyledControls.Button.Center.PlayPause"/>

    <include layout="@layout/exo_player_control_ffwd_button" />

    <ImageButton android:id="@id/exo_next"
        style="@style/ExoStyledControls.Button.Center.Next"/>

  </LinearLayout>

  <LinearLayout
      android:id="@+id/exo_top_controls"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:layout_gravity="top|end"
      android:background="@android:color/transparent">
    <View
        android:id="@+id/exo_media_route_button_placeholder"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"/>
  </LinearLayout>

</merge>
```

The copied Media3 layout retains its Apache header and required IDs. The new `ImageButton` is focusable by default and inherits the stock bottom-button sizing, padding, margins, and selectable background.

- [ ] **Step 3: Use the custom player view and controller**

Replace `app/src/main/res/layout/activity_playback.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- Aspect button corrects display geometry; gear retains stock speed + audio controls -->
    <com.sinema.ui.AspectRatioPlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:controller_layout_id="@layout/playback_controller"
        app:repeat_toggle_modes="one"
        app:resize_mode="fit"
        app:show_subtitle_button="true" />
</FrameLayout>
```

- [ ] **Step 4: Add Activity imports and state**

In `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`:

Add `androidx.media3.common.VideoSize` after the `Player` import, and add `com.sinema.model.PlaybackAspectRatio` before `CaptionRef`:

```kotlin
import androidx.media3.common.VideoSize
import com.sinema.model.PlaybackAspectRatio
```

Change the `playerView` declaration to exactly:

```kotlin
    private lateinit var playerView: AspectRatioPlayerView
```

Keep the existing `androidx.media3.ui.PlayerView` import because `initPlayer` still uses `PlayerView.ControllerVisibilityListener`.

Add these fields immediately after `chaptersDialog`:

```kotlin
    private var aspectRatioDialog: android.app.AlertDialog? = null
    private var aspectRatio = PlaybackAspectRatio.AUTO
```

The resulting declaration sequence is:

```kotlin
    private var chaptersDialog: android.app.AlertDialog? = null
    private var aspectRatioDialog: android.app.AlertDialog? = null
    private var aspectRatio = PlaybackAspectRatio.AUTO
    private var isControllerVisible = false
```

- [ ] **Step 5: Restore the preference and connect the button**

In `onCreate`, replace this existing line:

```kotlin
        playerView = findViewById(R.id.player_view)
```

with:

```kotlin
        playerView = findViewById(R.id.player_view)
        aspectRatio = PlaybackAspectRatio.fromName(prefs.playbackAspectRatio)
        playerView.playbackAspectRatio = aspectRatio
        findViewById<View>(R.id.aspect_ratio_button).setOnClickListener {
            showAspectRatioDialog()
        }
```

Keep the existing scene/caption/marker intent reads and Back callback unchanged.

- [ ] **Step 6: Add the dialog action and selection persistence**

Insert this method in `PlaybackActivity` immediately before `showChapters()`:

```kotlin
    private fun showAspectRatioDialog() {
        if (aspectRatioDialog?.isShowing == true) return
        val dialog = AspectRatioDialog.show(this, aspectRatio) { chosen ->
            aspectRatio = chosen
            prefs.playbackAspectRatio = chosen.name
            playerView.playbackAspectRatio = chosen
        }
        aspectRatioDialog = dialog
        dialog.setOnDismissListener {
            if (aspectRatioDialog === dialog) aspectRatioDialog = null
            playerView.requestFocus()
        }
    }
```

The dialog applies the fixed ratio without recreating the player or changing playback position. Dismissal returns focus to the player, matching the existing chapters-dialog pattern.

- [ ] **Step 7: Clean up the dialog with the Activity lifecycle**

In both `onPause` and `onStop`, add these two lines immediately before the existing `chaptersDialog?.dismiss()` line:

```kotlin
        aspectRatioDialog?.dismiss()
        aspectRatioDialog = null
```

For example, `onPause` becomes:

```kotlin
    override fun onPause() {
        super.onPause()
        savePlayback()
        aspectRatioDialog?.dismiss()
        aspectRatioDialog = null
        chaptersDialog?.dismiss()
        chaptersDialog = null
    }
```

Make the equivalent addition in `onStop`; keep its existing handler, player release, and queue cleanup order unchanged.

- [ ] **Step 8: Reapply the selection during player initialization**

In `initPlayer`, immediately after this existing guard:

```kotlin
        if (sceneId.isEmpty()) return
```

add:

```kotlin
        playerView.playbackAspectRatio = aspectRatio
```

This keeps the selected ratio applied when queue advancement releases and recreates the ExoPlayer.

- [ ] **Step 9: Log ExoPlayer's actual video geometry**

Inside the existing anonymous `Player.Listener`, add this override immediately before `onRepeatModeChanged`:

```kotlin
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        android.util.Log.d(
                            "Sinema",
                            "Video size ${videoSize.width}x${videoSize.height}, " +
                                "pixelRatio=${videoSize.pixelWidthHeightRatio}, " +
                                "rotation=${exo.videoFormat?.rotationDegrees}, " +
                                "aspectRatio=$aspectRatio"
                        )
                    }
```

Do not change the existing repeat, pause, completion, resume-saving, or queue logic.

- [ ] **Step 10: Document the playback control**

In `docs/playback.md`, insert this section immediately after the `## Audio tracks and playback speed` section and before `## Scene markers as chapters`:

```markdown
## Aspect ratio

Use the aspect-ratio button next to the Settings gear to choose:

- Auto (source), which uses the video geometry reported by ExoPlayer.
- 16:9.
- 4:3.
- 9:16 for portrait video.

Every option fits the selected shape on screen without cropping, with black bars filling unused space. The choice applies immediately and is reused for later videos until you change it.
```

- [ ] **Step 11: Run the complete unit-test suite**

Run:

```bash
rtk ./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`; all tests pass, including the four `PlaybackAspectRatioTest` tests.

- [ ] **Step 12: Run Android lint**

Run:

```bash
rtk ./gradlew :app:lintDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`. Review output for any new warning involving `AspectRatioPlayerView`, `playback_controller.xml`, `activity_playback.xml`, or `ic_aspect_ratio.xml`; fix any such warning before continuing.

- [ ] **Step 13: Build the debug app**

Run:

```bash
rtk ./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`, producing `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 14: Check the final diff and whitespace**

Run:

```bash
rtk git diff --check
rtk git status --short
```

Expected: `git diff --check` prints no whitespace errors. Status shows only the planned implementation files and the uncommitted implementation plan; build outputs remain ignored.

- [ ] **Step 15: Commit the integrated feature**

Stage only implementation and documentation files, not the plan document:

```bash
rtk git add app/src/main/java/com/sinema/ui/PlaybackActivity.kt app/src/main/res/layout/activity_playback.xml app/src/main/res/layout/playback_controller.xml app/src/main/res/drawable/ic_aspect_ratio.xml app/src/main/res/values/strings.xml docs/playback.md
rtk git commit -m "feat(player): add persisted aspect ratio control"
```

- [ ] **Step 16: Record the on-device verification boundary**

Do not install or launch on a physical TV unless the user explicitly requests it. In the completion report, distinguish the passing automated checks from these still-unverified device checks:

1. Auto preserves current rendering.
2. Selecting 9:16 corrects the affected 1080x1920 content if its intended display shape is 9:16.
3. Fixed 16:9 and 4:3 fit suitable media without cropping.
4. The selection survives playback restart and queue auto-advance.
5. The button and every dialog option are D-pad accessible.
6. Stock gear speed/audio, subtitles, repeat, chapters, pause, and Back still work.
7. The new `Sinema` log line exposes width, height, pixel ratio, rotation, and active option for the affected file.
