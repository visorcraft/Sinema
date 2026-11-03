# Privacy, Profile Isolation, and Reliability Hardening

**Date:** 2026-11-03
**Status:** Approved design
**Base:** `f06e3be` (`main`, Sinema 1.43.0)

## Context

A repository-wide peer review found targeted privacy, profile-isolation, playback, recency, integration-state, and authentication issues. Sinema already has shared grids, discreet rebinding, profile-aware session routing, playback persistence, and strong focused tests. This effort improves those boundaries without another broad UI or networking rewrite.

The selected approach is phased, test-first hardening in this order:

1. Privacy boundaries and profile isolation.
2. Small playback and recency fixes.
3. Integration-state coordination and single-flight login.
4. De-duplication and dead-code removal.

Alternatives considered and rejected:

- A single broad privacy/auth rewrite: too much simultaneous regression risk.
- Fixes without de-duplication: leaves divergent authentication and watched-state behavior in place.
- A generic searchable/sorted grid abstraction: poor benefit relative to Leanback regression risk.

## Global constraints

- Preserve API-key and session-cookie authentication, per-profile TLS policy, and origin-aware session-cookie routing.
- Preserve PIN behavior, decoy behavior, immediate Log Out, home focus ownership, and all existing destinations.
- Keep Stash requests inside the API layer. API wire tests use `withGraphqlServer` and `testScene` rather than new ad-hoc servers.
- Keep all interactive controls focusable and D-pad reachable. Do not replace Leanback presenters or introduce a new UI framework.
- No new dependencies, broad grid/home rewrite, screenshot changes, version bump, push, or release are included.
- Follow test-driven development for every behavioral fix. Rethrow `CancellationException`; retain existing Toast/log conventions.
- Regenerate the committed startup baseline if startup-critical code materially changes. Use only the documented Android TV emulator, never a physical TV.

## 1. Privacy boundaries

### Global Search lock gate

`GlobalSearchProvider.query` must return no suggestions when any of these is true:

- Global Search is disabled or no server is configured.
- `app.decoySession` is true.
- A PIN is configured and `app.pinVerifiedThisSession` is false.

With no configured PIN, the existing opt-in behavior remains unchanged. A verified real-PIN session retains normal suggestions. Selecting a suggestion continues through `DeepLinkActivity`'s existing PIN gate.

This controls new provider responses; Sinema does not claim to erase results already cached by Android TV. Update privacy documentation to state that suggestions are suppressed while locked or in decoy mode.

Tests must cover disabled, unconfigured, locked PIN, verified PIN, no-PIN, and decoy states, including proof that locked/decoy queries do not call the API.

### Discreet card rendering

Extend the existing focus-sensitive discreet rendering to all content card types currently rendered by `CardPresenter`, `FolderCardPresenter`, and marker cards:

- Group, Gallery, Entity, Folder, Image, Scene, and Marker content.
- Unfocused discreet cards show a stable neutral label, neutral secondary text where applicable, no sensitive overlays, and no content artwork.
- Focused cards reveal their normal title, metadata, overlays, and artwork.
- Recycled holders must not leak the previous item's title, artwork, focus state, or overlays.
- Navigation-only cards such as Home View All retain their normal labels.
- Existing `DiscreetRebind` generation handling must rebind all affected cards when the setting changes on resume.

Do not expand this phase into details screens, playback, launcher artwork, or other non-card surfaces. Add focused/unfocused and recycling tests for each presenter family, including Group and Entity Home/grid cards.

## 2. Profile and launcher isolation

### Active-server transitions

Every operation that changes the active server identity or endpoint must clear stale Sinema launcher content before new content can publish:

- Switching profiles.
- Removing the active profile.
- Editing the active profile's server URL.
- Importing a backup that changes the active profile or endpoint.

Rename-only and TLS-only changes do not require a server-identity transition, although their existing API refresh behavior remains intact.

### Provider identity and synchronization

- New Watch Next and Recently Added internal provider IDs use `sinema:<profileId>:<sceneId>`.
- Cleanup continues to recognize legacy `sinema:<sceneId>` entries and all Sinema-prefixed entries.
- `TvChannels.clearAll` remains the single cleanup path for Watch Next and the Sinema Recently Added channel.
- Channel clear and sync operations are serialized on one private lock.
- Each sync captures the active profile ID before doing work and checks it again after acquiring the lock. A stale sync returns without deleting or inserting content for the newer profile.
- If an old sync completes while a profile transition waits, the subsequent clear removes its entries before the transition finishes.

Extract pure helpers for provider IDs and stale-profile checks and unit-test them. Add Robolectric coverage for active-profile transitions and coordinator behavior, including legacy cleanup and stale-sync suppression; do not require a physical TV.

## 3. Playback, recency, and lifecycle fixes

### Recently Played

Make `findRecentlyPlayed(perPage)` use the same complete-filtered-result, nulls-last ordering and slicing as `findRecentlyPlayedPaged(page = 1, perPage = perPage)`. Remove the duplicate limited-fetch implementation rather than adding a third recency path.

Preserve the existing API signature and Home call sites. Add a wire-level regression with more than 25 matching scenes and null timestamps proving that a newer scene outside the first server page appears in Home's result. Document the complete-result fetch tradeoff already used by paged recency sorting.

### Queue metadata

Clear captions, markers, and any outgoing-scene-only metadata before fetching the next scene. Null, failure, and successful responses must never retain the outgoing scene's captions or markers. Preserve serialized scene switching and cancellation behavior.

Tests cover null, thrown failure, and successful queue transitions.

### Small correctness cleanup

- Capture `val api = app.api` before launching the app-scope O-counter mutation so the scene ID and API client originate from the same profile.
- Remove the owned Home logo and its scroll listener in `onDestroyView`; clear `logoView` so repeated PIN fragment replacement cannot accumulate views.
- Snapshot `recentErrors` inside `synchronized(recentErrorsBuffer)`.
- Reject setting or changing a decoy PIN to the same value as the primary PIN, with a focusable error and no credential changes. Existing stored PINs remain usable; this is not a migration.

Each item gets a focused regression test where the existing Android/test infrastructure permits.

## 4. Integration-state coordination

Create one small utility, `SystemIntegrations`, with narrowly scoped operations:

- `applyGlobalSearchState(context)`: enable or disable `GlobalSearchProvider` and `GlobalSearchActivity` together from `Prefs.globalSearchEnabled` using `DONT_KILL_APP`.
- `reconcileAfterImport(context, previousProfileId, previousServerUrl)`: apply Global Search state, clear launcher entries when channels are disabled, and call `onActiveServerChanged` when the imported active profile ID or trimmed server URL differs from the captured pre-import values.
- `onActiveServerChanged(context)`: clear launcher content through the serialized `TvChannels` path.

Use the coordinator from:

- The Global Search Settings toggle.
- Backup import.
- `SinemaApp.onCreate` for component/preference reconciliation.
- Active-profile switch, active-profile removal, and active-server URL editing.

Do not make this a general settings event bus. Preserve the backup message's restart guidance only for settings that genuinely still require restart.

Tests prove imported true/false Global Search values produce matching component states after import and after a fresh application start, and imported server changes cannot leave old launcher entries.

## 5. Single-flight session renewal

Add a private coroutine `Mutex` to each `SinemaApi` instance.

When a session-mode GraphQL request receives 401:

1. Capture the cookie used by the failed request.
2. Acquire the refresh mutex.
3. If another request already replaced that cookie with a nonblank value, reuse it without logging in again.
4. Otherwise perform one login and publish the refreshed cookie through the existing origin-aware callback.
5. Release the mutex and retry the original GraphQL request once with the available refreshed cookie.

If no refreshed cookie is available, preserve the original 401 handling. Do not loop retries or change API-key behavior. Concurrent requests sharing one `SinemaApi` instance must produce one login; separate profile API instances remain isolated.

Extend the shared test server fixture as needed to coordinate concurrent 401s and `/login` responses. Tests prove one login for concurrent failures, one callback per actual renewal, successful retries, no renewal without stored credentials, and no API-key renewal.

## 6. API de-duplication

### Shared Stash authentication

Add an API-layer `StashAuth` helper used by setup and `SinemaApi` renewal:

- Session login through the existing OkHttp/TLS configuration.
- API-key generation through Stash GraphQL with the session cookie.
- Structured Gson parsing for `generateAPIKey`; no string-offset JSON extraction.
- Existing setup error messages for invalid credentials, missing cookies, transport failures, and malformed responses remain actionable.

`SetupActivity` delegates network authentication to this helper. `SinemaApi.reLogin` delegates its login request to the same path while retaining its current null-on-failure contract. Tests cover login redirects/statuses, self-signed client reuse, cookie extraction, API-key parsing, and malformed responses.

### Watched-state mutation

Add `SinemaApi.setSceneWatched(sceneId, watched)`:

- Watched: clear resume time, then increment play count.
- Unwatched: reset play count.

Use it from `SceneGridFragment` and `SceneDetailActivity`; leave local UI updates and `WatchedToggle` state transitions in the UI. Tests assert operation order and request semantics. No unrelated favorite/rating consolidation.

## 7. Dead code and documentation cleanup

Remove confirmed unused production APIs and their obsolete tests or update tests to cover live equivalents:

- `PlaybackQueue.jumpTo`; retain stable-ID `jumpToItem` coverage.
- `PlaybackWatchClock.reset`.
- `lastInteractionAfterActivityTransition`.
- `SinemaApi.updateConfig`; construct configured API instances in tests and make configuration fields immutable where renewal does not require mutation.
- `SavedFilterScenesFragment.loadItems` when it exactly duplicates the base implementation.

Correct documentation for:

- Watch Next deep-linking directly to playback.
- Actual launcher artwork behavior.
- Encrypted interrupted-session persistence.
- Release logging/minification claims.
- Recently Played complete-result recency behavior, Global Search lock behavior, expanded Discreet Mode cards, and integration reconciliation.

Update `AGENTS.md` only if the changed architecture makes its source map or binding guidance stale. Do not alter existing screenshots.

## Verification

Run focused tests after each test-first change and the full debug unit suite after each phase. Request independent spec-and-quality review for each phase and a final whole-branch review; fix Critical and Important findings before proceeding.

Final local verification:

```bash
rtk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease --no-daemon
rtk git diff --check
rtk git diff --name-only -- assets
```

Confirm all tests pass with zero failures, lint has zero errors, APK/AAB outputs exist, no screenshots changed, and the working tree contains only intended files. Report physical-TV geometry, Android system-search integration, launcher rendering, and live-Stash behavior as unverified unless actually exercised.

## Non-goals

- No broad Home, grid, playback, settings, or networking rewrite.
- No new server features, navigation features, visual redesign, or dependency upgrades.
- No changes to historical tags or releases.
- No version bump, merge, push, or GitHub release without a later explicit request.
