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
