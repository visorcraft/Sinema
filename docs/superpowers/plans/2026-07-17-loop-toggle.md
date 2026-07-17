# Loop-One Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an always-visible loop button to the playback screen that toggles `REPEAT_MODE_ONE` for the current video, persisted across launches.

**Architecture:** A new vector drawable for the loop arrow, an ImageButton overlay placed in the bottom-right corner of `activity_playback.xml` (outside Media3's controller overlay so it's not part of the autohide UX), a `loopEnabled` boolean in `Prefs`, and ~15 lines in `PlaybackActivity` wiring the button to `exo.repeatMode` + tint + Prefs. The existing `onPlaybackStateChanged(STATE_ENDED)` queue-advance path is guarded so Media3's auto-seek-to-0 wins when looping.

**Tech Stack:** Kotlin, AndroidX AppCompat, Media3 `Player` (`Player.REPEAT_MODE_ONE` / `OFF`), `androidx.core.content.ContextCompat`, project-internal `Prefs` (SharedPreferences-backed), Material-style SVG vector drawable (XML).

**Spec:** `docs/superpowers/specs/2026-07-17-loop-toggle-design.md`

## Global Constraints

- Five files touched exactly: `app/src/main/res/drawable/ic_loop.xml` (CREATE), `app/src/main/res/values/strings.xml` (modify, one new entry), `app/src/main/res/layout/activity_playback.xml` (modify, add ImageButton), `app/src/main/java/com/sinema/util/Prefs.kt` (modify, one new property), `app/src/main/java/com/sinema/ui/PlaybackActivity.kt` (modify, ~15 lines wiring).
- No new dependencies. No layout/dependency/manifest changes beyond what is listed above.
- No new automated tests. UI behavior tightly coupled to Media3 `PlayerView`; Robolectric/instrumentation out of scope per project policy.
- Conventional Commits commit messages. NO AI/agent attribution anywhere — no `Co-Authored-By`, no "Generated with…", no Claude/Anthropic mentions.
- Loop state must persist in `Prefs` and survive app restarts.
- Loop cycle is `OFF` ↔ `REPEAT_MODE_ONE` ONLY. Never `REPEAT_MODE_ALL`.
- When `REPEAT_MODE_ONE` is on, the existing `onPlaybackStateChanged(STATE_ENDED)` queue-advance block MUST be skipped (otherwise Media3's auto-seek loses the race with the queue advance and the loop breaks).
- **NO device install or launch.** The user explicitly forbade it for this work. `./gradlew compileDebugKotlin` is the verification command; do NOT run `installDebug`/`connectedAndroidTest`/launch any AVD or hardware device. Manual smoke pass lives with the user.

---

### Task 1: Visual layer — drawable, string, layout

**Files:**
- Create: `app/src/main/res/drawable/ic_loop.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/activity_playback.xml`

**Interfaces produced (consumed by Task 2):**
- A vector drawable referenced as `@drawable/ic_loop`. 24dp viewport, fill="#FFFFFFFF" so runtime tint takes effect.
- A string `R.string.loop_video` with value `"Loop this video"` (content description).
- A `View` with id `R.id.loop_button` (an `ImageButton`) placed inside the existing `FrameLayout` in `activity_playback.xml` as a sibling of the `PlayerView`. `layout_gravity="bottom|end"`, `layout_marginEnd="24dp"`, `layout_marginBottom="24dp"`, `layout_width="48dp"`, `layout_height="48dp"`, `padding="12dp"`, `background="?attr/selectableItemBackgroundBorderless"`, `src="@drawable/ic_loop"`, `contentDescription="@string/loop_video"`.

- [ ] **Step 1: Create `ic_loop.xml`**

Create `app/src/main/res/drawable/ic_loop.xml` with this exact content (Material `ic_loop` loop-arrow vector; fill is white so the activity's runtime tint drives the final color):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2V17z" />
</vector>
```

- [ ] **Step 2: Add the content-description string**

Edit `app/src/main/res/values/strings.xml`. Add this single `<string>` entry directly after the existing `app_name` line:

```xml
    <string name="loop_video">Loop this video</string>
```

The file should now contain exactly:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Sinema</string>
    <string name="loop_video">Loop this video</string>
</resources>
```

- [ ] **Step 3: Add the ImageButton to `activity_playback.xml`**

Edit `app/src/main/res/layout/activity_playback.xml`. Insert the following `ImageButton` directly AFTER the closing `</androidx.media3.ui.PlayerView>` tag and BEFORE the closing `</FrameLayout>`:

```xml
    <ImageButton
        android:id="@+id/loop_button"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="24dp"
        android:layout_marginBottom="24dp"
        android:padding="12dp"
        android:src="@drawable/ic_loop"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/loop_video" />
```

The final `activity_playback.xml` should look like:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- CC button toggles/picks text tracks; gear menu (default controller) provides speed + audio track selection -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:show_subtitle_button="true" />

    <ImageButton
        android:id="@+id/loop_button"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="24dp"
        android:layout_marginBottom="24dp"
        android:padding="12dp"
        android:src="@drawable/ic_loop"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/loop_video" />
</FrameLayout>
```

- [ ] **Step 4: Compile-check the resources**

Run `./gradlew compileDebugKotlin` from the repo root. Expected: `BUILD SUCCESSFUL`. The XML files are resolved during compilation; a typo in any attribute, a missing resource, or a bad vector path will surface here.

Do NOT run `installDebug`, `bundleDebug`, `assembleDebug`, or anything that talks to a device.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_loop.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/layout/activity_playback.xml
git commit -m "feat(player): loop-one toggle button (visual)

Adds a small always-visible ImageButton overlay bottom-right of the
playback PlayerView, outside Media3's controller overlay so it stays
visible regardless of the autohide UX. Uses a Material loop-arrow
vector. Wiring (Prefs + Player.repeatMode + click + STATE_ENDED guard)
lands in the next commit."
```

---

### Task 2: Prefs property, click handler, initial tint

**Files:**
- Modify: `app/src/main/java/com/sinema/util/Prefs.kt`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

**Interfaces consumed from Task 1:**
- `R.id.loop_button` (the ImageButton in `activity_playback.xml`).
- `R.drawable.ic_loop`, `R.string.loop_video` (used internally by the layout).

**Interfaces produced (consumed by Task 3):**
- `SinemaApp.instance.prefs.loopEnabled` getter/setter Boolean property.
- `loopButton: ImageButton` field on `PlaybackActivity`, populated in `onCreate`.
- `applyLoopTint(enabled: Boolean)` private helper on `PlaybackActivity`.

- [ ] **Step 1: Add `loopEnabled` to `Prefs.kt`**

Edit `app/src/main/java/com/sinema/util/Prefs.kt`. Add this property directly after the existing `channelsEnabled` property (line ~46 in the source — keep the existing block intact):

```kotlin
    var loopEnabled: Boolean
        get() = prefs.getBoolean("loop_enabled", false)
        set(value) = prefs.edit().putBoolean("loop_enabled", value).apply()
```

Default is `false` — first install starts with loop off, matching the rest of the codebase's "no value, default conservative" pattern (compare `channelsEnabled`).

- [ ] **Step 2: Add the field, helper, and initial tint hookup in `PlaybackActivity.kt`**

Add this import alongside the other `androidx.core.*` / `androidx.*` imports near the top of `PlaybackActivity.kt`:

```kotlin
import androidx.core.content.ContextCompat
```

Add a new import for the ImageButton:

```kotlin
import android.widget.ImageButton
```

Add two new private fields near the existing `isControllerVisible` field:

```kotlin
    private lateinit var loopButton: ImageButton
    private val prefs by lazy { SinemaApp.instance.prefs }
```

Add a private `applyLoopTint` function near the existing private helpers (`savePlayback`, `buildMediaItem`):

```kotlin
    private fun applyLoopTint(enabled: Boolean) {
        val color = ContextCompat.getColor(
            this,
            if (enabled) android.R.color.holo_blue_bright else android.R.color.darker_gray
        )
        loopButton.setColorFilter(color)
    }
```

In `onCreate()`, directly after the existing `markers = SceneIntents.markersFrom(intent)` line, add:

```kotlin
        loopButton = findViewById(R.id.loop_button)
        applyLoopTint(prefs.loopEnabled)
```

- [ ] **Step 3: Wire the click handler**

In `PlaybackActivity.kt`, append the click handler block directly AFTER the existing `initPlayer()` closing brace. Find the `releasePlayer()` function and insert this block before it:

```kotlin
    private fun wireLoopButton() {
        loopButton.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            val nowEnabled = !prefs.loopEnabled
            prefs.loopEnabled = nowEnabled
            exo.repeatMode = if (nowEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            applyLoopTint(nowEnabled)
        }
    }
```

(`return@setOnClickListener` is the idiomatic Kotlin exit for an OnClickListener lambda.)

In `onCreate()`, directly after the `applyLoopTint(prefs.loopEnabled)` line you just added, add:

```kotlin
        wireLoopButton()
```

- [ ] **Step 4: Compile-check**

Run `./gradlew compileDebugKotlin`. Expected: `BUILD SUCCESSFUL`. No device commands.

If you see "Unresolved reference: loop_button" — confirm the `<ImageButton android:id="@+id/loop_button" ...>` from Task 1 is in `activity_playback.xml`. If you see "Unresolved reference: prefs" — confirm the `SinemaApp.instance.prefs` accessor exists; if not, find how other activities grab Prefs (e.g., `SettingsActivity.kt`) and mirror that pattern. If you see "Unresolved reference: setColorFilter" — make sure the ImageButton import is in place.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sinema/util/Prefs.kt \
        app/src/main/java/com/sinema/ui/PlaybackActivity.kt
git commit -m "feat(player): wire loop-one toggle click + persist state

Adds a loopEnabled Boolean Prefs property and a click handler that
flips it, sets exo.repeatMode to ONE/OFF, and re-tints the button.
The handler no-ops while the player is null (pre-onStart or
post-onStop). The button's initial tint is restored from Prefs in
onCreate."
```

---

### Task 3: Apply initial `repeatMode` in `initPlayer`, guard STATE_ENDED

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

**Interfaces consumed:**
- `prefs.loopEnabled` (Task 2).
- `Player.REPEAT_MODE_ONE` and `Player.REPEAT_MODE_OFF` constants from `androidx.media3.common.Player`.
- The existing `onPlaybackStateChanged(state: Int)` override.

**Why this is its own task:** with `REPEAT_MODE_ONE` enabled, the existing `STATE_ENDED` path calls `savePlayback()` AND races `PlaybackQueue.next()` against Media3's auto-seek-to-0. Without the guard, the queue advance wins and the loop is broken. This is the highest-risk line of the feature; isolating it as its own commit and review task reduces the chance the guard is forgotten.

- [ ] **Step 1: Apply initial `repeatMode` in `initPlayer`**

In `initPlayer()`, find the line `exo.playWhenReady = true` (currently near the end of the player-builder block). Insert the following line directly AFTER `exo.playWhenReady = true`:

```kotlin
                exo.repeatMode = if (prefs.loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
```

(Exact indentation: 16 spaces, matching the surrounding lines inside the `exo.also { ... }` block.)

- [ ] **Step 2: Guard `onPlaybackStateChanged(STATE_ENDED)` against queue advance when looping**

In `onPlaybackStateChanged(state: Int)`, find the existing implementation:

```kotlin
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_ENDED) return
                        handler.removeCallbacks(hideRunnable)
                        savePlayback()
                        val wasActive = PlaybackQueue.isActive
                        val nextId = PlaybackQueue.next()
                        ...
                    }
```

Replace it with:

```kotlin
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_ENDED) return
                        handler.removeCallbacks(hideRunnable)
                        savePlayback()
                        // Skip the queue advance when loop-one is on; Media3's
                        // auto-seek-to-0 will replay this video and the
                        // queue advance would race with it and break the loop.
                        if (exo.repeatMode != Player.REPEAT_MODE_OFF) return
                        val wasActive = PlaybackQueue.isActive
                        val nextId = PlaybackQueue.next()
                        ...
                    }
```

Do not change any line below the `val nextId = PlaybackQueue.next()` line. The `...` above represents that you must leave the rest of the existing block (the `if (nextId == null) { if (wasActive) finish(); return }` and the `lifecycleScope.launch { ... }` block) UNCHANGED.

- [ ] **Step 3: Compile-check**

Run `./gradlew compileDebugKotlin`. Expected: `BUILD SUCCESSFUL`.

Common failures to watch for:
- "Unresolved reference: REPEAT_MODE_ONE" — `Player.REPEAT_MODE_ONE` lives in `androidx.media3.common.Player` which is already imported at the top of `PlaybackActivity.kt` (line 12 of the current file). If you somehow lost that import, restore it.
- "Unresolved reference: exo" inside the listener — the existing listener captures `exo` from the outer `also { exo -> ... }` block. The new `exo.repeatMode` line should still be inside that block.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sinema/ui/PlaybackActivity.kt
git commit -m "feat(player): apply loop state to player; skip queue advance when looping

initPlayer sets exo.repeatMode from Prefs at build time so the loop
state is honored from the first frame. onPlaybackStateChanged(STATE_ENDED)
now short-circuits before the PlaybackQueue.next() call when loop-one is
on so Media3's auto-seek-to-0 produces an uninterrupted loop instead of
racing with a queue advance that would break it."
```

---

### Task 4: Whole-branch review and ship

This task is performed by the human (you), not by an agent.

- [ ] **Step 1: Verify only the five intended files changed**

```bash
git diff 51d6fe5..HEAD --stat
```

Expected: 5 files: `ic_loop.xml` (new), `strings.xml`, `activity_playback.xml`, `Prefs.kt`, `PlaybackActivity.kt`. Net additions ~40-60 lines. No layout/manifest/dependency changes beyond `activity_playback.xml`.

If anything else shows up, stop and fix before pushing.

- [ ] **Step 2: Pre-flight checks**

```bash
./gradlew compileDebugKotlin
git log 51d6fe5..HEAD --format='%H %s'
git log 51d6fe5..HEAD --format=%B | grep -iE 'claude|anthropic|copilot|gemini|co-authored|generated with'
```

The first should print `BUILD SUCCESSFUL`. The second lists three commits in the order they were created. The third (grep) should produce NO output (exit 1) — confirming zero AI attribution in any commit body.

- [ ] **Step 3: Bump version, tag, and push**

Per the v1.15.0 release flow:

```bash
# Bump versionCode (currently 20) and versionName (currently "1.15.0") in app/build.gradle.kts.
# For this release: versionCode = 21, versionName = "1.16.0".
git add app/build.gradle.kts
git commit -m "Prepare release 1.16.0"
git push origin main
git tag -a v1.16.0 -m "Sinema v1.16.0"
git push origin v1.16.0
```

The tag push triggers `.github/workflows/release.yml` (run lint, assemble release APK + AAB, publish GitHub release). The release will appear under https://github.com/visorcraft/Sinema/releases/tag/v1.16.0 once the workflow finishes (~4 minutes typical).

- [ ] **Step 4: Manual smoke pass (user) — defer to the user**

The project's no-device-installs rule applies. The user will verify the loop toggle on their TV against the spec's Testing section (seven items). If any fails, file a fix and ship `1.16.1`.

---

## Done When

- Three new commits on `main` (Tasks 1, 2, 3) plus the version bump.
- `git push origin v1.16.0` produces a CI run that ends with a published release.
- User confirms the loop works on hardware.
