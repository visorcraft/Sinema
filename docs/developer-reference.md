# Developer Reference

This document summarizes the parts of the codebase that implement Sinema's user-facing functionality.

## Project shape

Sinema is a Kotlin Android TV app.

Main technologies:

- AndroidX Leanback for TV browsing screens.
- Media3 ExoPlayer for playback.
- OkHttp and Gson for Stash GraphQL and media requests.
- Glide for authenticated image loading.
- AndroidX Security Crypto for sensitive local settings.
- AndroidX TVProvider for Android TV launcher channels.

Current dependency versions and licenses are tracked in [CREDITS.md](../CREDITS.md) and [THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md).

Important entry points:

- `app/src/main/java/com/sinema/SinemaApp.kt`: application singleton, preferences, API client, app-scope background work.
- `app/src/main/java/com/sinema/api/SinemaApi.kt`: Stash GraphQL and media URL wrapper.
- `app/src/main/java/com/sinema/util/Prefs.kt`: local settings, profiles, PIN hash, sort state, and secure storage.
- `app/src/main/java/com/sinema/ui/MainActivity.kt`: launch, setup/PIN gating, home rows, update checks, channel sync.
- `app/src/main/java/com/sinema/ui/SetupActivity.kt`: first-run setup, Stash login, web setup, manual API-key entry.
- `app/src/main/java/com/sinema/ui/SettingsActivity.kt`: settings screen and profile integration.
- `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`: ExoPlayer setup, resume tracking, chapters, autoplay.

## Stash API usage

`SinemaApi` sends GraphQL requests to:

```text
<serverUrl>/graphql
```

Media URLs use Stash endpoints:

```text
<serverUrl>/scene/<sceneId>/screenshot
<serverUrl>/scene/<sceneId>/stream
<serverUrl>/scene/<sceneId>/caption?lang=<lang>&type=<type>
<serverUrl>/image/<imageId>/thumbnail
<serverUrl>/image/<imageId>/image
```

Authentication modes:

- API-key mode sends `ApiKey: <key>`.
- Session mode sends `Cookie: <session_cookie>`.

Session mode can automatically re-login when Stash GraphQL returns 401, if the profile has stored username/password credentials.

## Core screens

### Home

`MainFragment` loads:

- Shortcuts.
- Continue Playing via `findContinuePlaying`.
- Recently Played via `findRecentlyPlayed`.
- Recently Added via `findRecentScenes`.
- Favorites via `findFavoriteScenes`.
- Bottom settings row.

Each Stash-backed row has a `HomeRowState` that retains its last successful items through loading and failures. `MainFragment` keeps persistent `ListRow` instances and reconciles only changed adapters, headers, and row positions instead of clearing the dashboard. Stable header IDs preserve focus across refresh-status changes and row insertion or removal. Empty successful results hide their rows; failures retain content or show an unavailable header when no content has loaded.

`HomeLayoutSettings` persists normalized shortcut/row order, visibility, card size, hero visibility, Library grouping, and the pinned saved filter in `Prefs`. `HomeSettingsActivity` and `HomeOrderActivity` edit that value; `LibraryActivity` presents grouped secondary shortcuts. `HomeRowScenesActivity` provides paged View All grids, while optional dashboard rows use the same independent `RowResult` error isolation as the original four rows. Recently Played's default recency sort fetches the complete filtered result before applying nulls-last ordering and slicing pages because Stash orders null timestamps unpredictably; other sorts remain server-paged.

### Scene detail

`SceneDetailActivity` receives lightweight scene extras through `SceneIntents`, then fetches full metadata with `findSceneFull`.

Full details include:

- Resume time.
- Date.
- Studio.
- Tags.
- Performers.
- Captions.
- Scene markers.

### Folder browsing

`BrowseFoldersActivity` lists top-level folders under `/data`.

`FolderBrowseActivity` queries scenes and images for a path, then `FolderHelper.buildFolderContents` turns them into immediate children.

### Metadata browsing

`EntityGridActivity` lists tags, performers, or studios.

`EntityScenesActivity` lists scenes for one entity.

### Shared grids

`SceneGridFragment` centralizes:

- Vertical grid setup.
- Card presenter.
- Scene click behavior.
- Sort and watched/favorite filter handling.
- Incremental paging for entity and entity-scene grids.
- Play All for currently loaded scenes.
- Error toasts.

`activity_grid.xml` adds visible D-pad controls through `SortableScreen`. Entity grids additionally expose name search and entity-specific sorting.

### Sorting and filtering

Scene sort options live in `SortOption.kt` and map to Stash `FindFilterType` sort fields. Entity sort options live in `EntityGridActivity.kt`.

`SceneFilterOptions.kt` maps watched state to `play_count` and favorites to `rating100`, with per-screen persistence in `Prefs`. The Menu key remains available through `SortableScreen.handleSortMenuKey`.

## Playback UI

`playback_controller.xml` keeps Media3 control IDs and uses `MarkerTimeBar` (`DefaultTimeBar`) for native scrubbing plus marker ticks. `PlaybackActivity.bindPlaybackControls()` reattaches custom actions after every PlayerView mount. `SeekPreset` persists quick-seek intervals; `previewBucketMs` throttles authenticated timestamp screenshot requests to five-second buckets. PlayerView remains responsible for actual seeks, track selection, stream fallback, and queue playback.

## Playback state
`PlaybackActivity.savePlayback` writes Stash activity through `sceneSaveActivity`.

Important thresholds:

- Ignore playback under five seconds.
- Save resume time after five seconds.
- Clear resume time when within 30 seconds of the end.
- Increment play count once when finishing a scene.

Play All uses `PlaybackQueue`, an in-memory process-local queue capped by `SceneIntents.playAll` to 500 scene IDs.

## Launcher channels and deep links

`TvChannels` publishes:

- Watch Next entries with internal IDs prefixed by `sinema:`.
- A Recently Added preview channel.

Deep links are handled by `DeepLinkActivity`:

```text
com.sinema://app/scene/<sceneId>
```

Deep links honor setup and PIN requirements before opening scene detail.

## Build and test

Current toolchain:

- Gradle 9.6.1.
- Android Gradle Plugin 9.2.1.
- compileSdk 36, targetSdk 36, minSdk 24.
- Java 17 bytecode.
- Kotlin support is provided by AGP; the runtime stdlib resolves to 2.2.21.

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run lint:

```bash
./gradlew :app:lintDebug
```

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

Build release APK:

```bash
./gradlew :app:assembleRelease
```

## Current limits worth knowing

- Folder browsing assumes Stash paths are rooted at `/data`.
- Entity and entity-scene grids append 100 items per page as the user scrolls; Play All includes only scenes loaded so far.
- Folder views cap displayed videos/pictures at 10,000 items.
- Play All queues up to 500 scenes.
- Play All still requires a remote Menu key; Sort, Filter, and entity Search also have visible controls.
- Android TV launcher artwork is only attached in API-key mode.

## Baseline profiles

Release builds ship a compiled baseline profile (`assets/dexopt/baseline.prof`) that lets ART pre-compile hot code paths on install, improving cold start and reducing early jank.

How it is wired:

- `:baselineprofile` is a `com.android.test` module holding a UIAutomator-driven `BaselineProfileRule` generator (`baselineprofile/src/main/kotlin/.../BaselineProfileGenerator.kt`).
- The app module applies the `androidx.baselineprofile` plugin, depends on `androidx.profileinstaller` (installs profiles on sideloaded updates), and commits the generated human-readable rules under `app/src/release/generated/baselineProfiles/baseline-prof.txt` (`baselineProfile { saveInSrc = true }`).
- Because the rules are committed, release CI needs no emulator; R8 rewrites them for the shipped (obfuscated-safe, here non-minified) release dex at build time.

Regenerating (requires an emulator with KVM; a fresh emulator exercises the setup flow, a configured one the home rows):

```bash
# One-time: create an Android TV AVD (image already in the local SDK)
echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
  -n sinema_bp -k "system-images;android-36;android-tv;x86_64" -d tv_1080p

$ANDROID_HOME/emulator/emulator -avd sinema_bp -no-window -no-audio \
  -gpu swiftshader_indirect -no-snapshot &

# Wait for boot, then regenerate (writes to app/src/release/generated)
adb wait-for-device && adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
./gradlew :app:generateReleaseBaselineProfile
```

Commit the regenerated `baseline-prof.txt` with the change that shifts startup-critical code.
