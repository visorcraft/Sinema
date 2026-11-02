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

`HomeLayoutSettings` persists normalized shortcut/row order, visibility, card size, hero visibility, Library grouping, and the pinned saved filter in `Prefs`. `HomeSettingsActivity` and `HomeOrderActivity` edit that value; `LibraryActivity` presents grouped secondary shortcuts. `HomeRowScenesActivity` provides paged View All grids, while optional dashboard rows use the same independent `RowResult` error isolation as the original four rows. Recently Played's default recency sort fetches the complete filtered result before applying nulls-last ordering and slicing pages because Stash orders null timestamps unpredictably; other sorts remain server-paged. Returning to the home fragment within `HOME_RELOAD_TTL_MS` (15 s, elapsedRealtime-based `shouldReloadHome`) skips the reload; `Refresh` and layout changes force it.

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

### Groups and related discovery

`GroupGridActivity` provides paged, searchable Stash Group browsing. `GroupScenesActivity` shows group artwork/metadata and preserves Stash's scene order for Play All, Shuffle Play, and resume progression. `SinemaApi.findGroups` and `findGroupDetails` use Groups on current Stash and retry with legacy Movies only when the server reports that the Groups query field is absent (Stash 0.24); transport and unrelated GraphQL errors are not retried. `GroupPlayback` selects the first partial scene and its saved timestamp, then the first unwatched scene.

`RelatedScenes` is the pure deterministic recommendation ranker: six points per shared performer, four for the studio, and two per shared tag, with rating and scene ID tie-breakers. `SinemaApi.findRelatedScenes` issues at most three 40-item full-metadata queries (performers, studio, tags), isolates candidate-query failures, deduplicates by scene ID, and never scans the whole library. `SceneDetailActivity` shows the top six as More Like This, with folder scenes as an empty-result fallback. `RelatedScenesActivity` supplies the personalized Home row's View All grid; Unwatched from Favorite Performers uses Stash's `performer_favorite` and `play_count` criteria through the normal paged scene grid.

`HomeLayoutSettings.optionalRowsVersion` performs a one-time migration that hides newly introduced optional rows for existing users without preventing those rows from being enabled later.

### Galleries and image viewing

`GalleryGridActivity` provides paged, searchable Gallery browsing with Stash 0.24-safe fields and cover thumbnails. `GalleryImagesActivity` shows Gallery metadata and paged image cards. `ImageViewActivity` loads Gallery images in bounded pages (10,000 maximum) or accepts folder images through the existing static handoff. Authenticated full images use `GlideAuth`; pure `ImageViewerState` controls bounded zoom/pan, fit/fill, navigation, shuffle, and slideshow timing.

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

### Screen states and accessibility

`GridLoadState` and `GridStateViews` provide shared loading, empty, error, and Retry rendering for scene grids, Search, Folder Browser, and scene details. `UiErrorMessage` maps transport, authentication, TLS, missing-endpoint, and generic failures to actionable copy. Home retains its independent row-level states and persistent empty-dashboard retry. Server Dashboard preserves loaded data during refresh failures and appends a retry action.

`SinemaActivity` applies the persisted `InterfaceTextScale` through configuration contexts and installs an optional high-visibility foreground ring through a global focus listener, without replacing screen-specific focus handlers. `TvMotion` centralizes Leanback zoom choices; reduced motion also disables Ambient Gallery pan, zoom, and crossfades. `RemoteGuideActivity` provides D-pad-scrollable remote help. Accessibility Settings lives beside Privacy and System.

### Sorting and filtering

Scene sort options live in `SortOption.kt` and map to Stash `FindFilterType` sort fields. Entity sort options live in `EntityGridActivity.kt`.

`SceneFilterOptions.kt` maps watched state to `play_count`, favorites to `rating100`, and advanced duration/date/codec/entity criteria to their Stash inputs. Entity filters support multi-select `INCLUDES`/`EXCLUDES`; tags and studios use hierarchical criteria with depth zero. `SceneFilterComposition.kt` keeps screen scope and advanced criteria ANDed when their keys overlap. Per-screen preferences serialize as JSON while retaining the legacy colon-delimited parser. `FilterDialog` provides removable chips, clear-all, and Save Current; `EntityFilterDialog` handles searchable multi-select choices. `SavedFilterActions` and the Saved Filters screen use Stash `saveFilter`/`destroySavedFilter` for create, rename, and delete. The Menu key remains available through `SortableScreen.handleSortMenuKey`.

## Playback UI

`playback_controller.xml` keeps Media3 control IDs and uses `MarkerTimeBar` (`DefaultTimeBar`) for native scrubbing plus marker ticks. `PlaybackActivity.bindPlaybackControls()` reattaches custom actions after every PlayerView mount. `SeekPreset` persists quick-seek intervals (applied asymmetrically to D-pad scrubbing via `MarkerTimeBar.setKeyTimeIncrements`); `previewBucketMs` throttles authenticated timestamp screenshot requests to five-second buckets. Marker jump buttons use custom ids (`playback_prev_marker_button` / `playback_next_marker_button`) because Media3's `exo_prev`/`exo_next` are disabled when the single-item player has no previous/next media item. PlayerView remains responsible for actual seeks, track selection, stream fallback, and queue playback.

### Deep links and Watch Next

`DeepLinks` routes `com.sinema://app/scene/<id>` to the detail screen and `com.sinema://app/play/<id>` (published by `TvChannels.syncWatchNext`) straight to playback. The `/play/` route sets `SceneIntents.playbackAutoResume`; `PlaybackActivity.resolveAutoResume()` fetches the full scene once (resume_time, dimensions, captions, markers) and resumes silently — the resume prompt never appears on this path. When the initial dashboard load fails with no content to show, `shouldShowServerErrorRow` drives a persistent `Retry Connection` row at the top of home.

### Privacy and ambient

`Prefs` holds `discreetMode` / `incognitoMode` / `blockScreenshots` / `ambientGallery`, plus PIN hardening: `autoLockMinutes` (re-lock via the MainActivity idle watcher) and a separate decoy PIN hash (`PinActivity` verifies the real PIN first, then the decoy, which unlocks `DecoyActivity` — a blank shell that backgrounds on Back and re-arms via `SinemaApp.decoySession`). Discreet rendering lives in `CardPresenter.applySceneDisplay` (neutral `DiscreetMode.neutralTitle`, artwork hidden until focus); incognito short-circuits `savePlayback` and search-history writes; `SinemaApp` applies `FLAG_SECURE` to every activity via lifecycle callbacks so toggles take effect on the next activity start. `TonightMix` (util) is the pure ~30-minute selector behind the home shortcut. `AmbientActivity` (started by `MainActivity`'s idle watcher, 5 min, PIN-guarded) runs the Ken Burns gallery over top-rated favorites. `SinemaApi.findUntaggedScenes` uses `IS_NULL` tag/performer criteria (Stash ≥ 0.26) and `sceneUpdateTags` writes `tag_ids` through `sceneUpdate`.

### Insights, entities, backup, server, alpha jump

`TasteInsights` (util, pure) summarizes your 14-day plays and ranks the tags/performers/studios those recently played scenes carry (per-scene counting — Stash has no per-play history, so lifetime play counts are never attributed to the window); it reads `last_played_at` from the most-played snapshot (`findTopPlayedScenes`). Date-part string comparisons keep it timezone-safe. Entity favorites ride `EntityItem.rating100` (list queries select it; `entitySetRating` dispatches to `performerUpdate`/`tagUpdate`/`studioUpdate` with serializeNulls). `Prefs.exportSnapshot`/`importSnapshot` round-trip a versioned JSON backup (regular prefs + encrypted server-profile store — credentials included) through SAF dialogs in Settings → System. `ServerDashboardActivity` reads `getServerVersion`, `getStats`, and `getJobQueue` (null → unsupported), plus `SinemaApp.noteError`'s 20-entry ring buffer. Alpha jump adds an anchored `MATCHES_REGEX` criterion via `pathPrefixRegex` and `alphaField`, using `title` for title sorts and `path` otherwise, threaded through the home-row paged queries (`HomeRowScenesFragment` opts in through `supportsAlphaJump`).

### Playback queue

`PlaybackQueue` (util) keeps queued items with id/title/duration/play_count and exposes `next`, `jumpToItem`, `remaining`, seeded `shuffleRemaining`, and `filterRemainingUnwatched` (the dialog's Unwatched-only button); the controller's `Up Next · N` chip opens a jump/shuffle/filter/exit dialog, and the resume prompt (`shouldPromptResume`, 10 s threshold) asks before seeking into saved progress. `QueuePlanning` is the pure planning step (start-at, shuffle, 500 cap) behind `SceneIntents.playAll`; grid long-press actions live in `SceneGridFragment.showCardActions`. O-counter increments (`SinemaApi.sceneIncrementO`) run on the app scope from the playback menu so they survive an immediate exit.

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

The suite includes pure Kotlin tests and Robolectric activity/fragment smoke tests, including cold-launch grid coverage. No emulator is required.

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
- Folder views and full-screen Gallery loading cap displayed images at 10,000 items.
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
