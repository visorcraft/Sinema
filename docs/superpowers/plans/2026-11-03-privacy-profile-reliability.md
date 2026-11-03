# Privacy, Profile Isolation, and Reliability Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close Sinema's privacy and server-profile leaks, correct playback/recency lifecycle behavior, coordinate Android integrations and session renewal, and remove the confirmed duplicate/dead code without broad rewrites.

**Architecture:** Keep the existing Leanback UI, `SinemaApi`, `Prefs`, and `TvChannels` architecture. Add only three narrow helpers: `SystemIntegrations` for component/server transitions, `StashAuth` for shared authentication, and small pure helpers where concurrency or rendering behavior needs deterministic tests. Work test-first in the approved phase order, with independent spec and quality review after every phase.

**Tech Stack:** Kotlin, AndroidX Leanback, Media3, OkHttp, Gson, Kotlin coroutines, JUnit 4, Robolectric, and the existing `HttpServer` GraphQL fixture.

## Global Constraints

- Preserve API-key and session-cookie authentication, per-profile TLS policy, and origin-aware session-cookie routing.
- Preserve PIN behavior, decoy behavior, immediate Log Out, home focus ownership, and all existing destinations.
- Keep Stash requests inside the API layer. API wire tests use `withGraphqlServer` and `testScene` rather than new ad-hoc servers.
- Keep all interactive controls focusable and D-pad reachable. Do not replace Leanback presenters or introduce a new UI framework.
- No new dependencies, broad grid/home rewrite, screenshot changes, version bump, push, or release are included.
- Follow test-driven development for every behavioral fix. Rethrow `CancellationException`; retain existing Toast/log conventions.
- Regenerate the committed startup baseline if startup-critical code materially changes. Use only the documented Android TV emulator, never a physical TV.
- Every shell command is prefixed with `rtk`.
- After each phase, request independent spec-compliance and code-quality reviews. Fix Critical and Important findings before starting the next phase.

## File Structure

- `app/src/main/java/com/sinema/util/GlobalSearchProvider.kt`: suppress suggestions while locked or in decoy mode before any request.
- `app/src/main/java/com/sinema/ui/CardPresenter.kt` and `MarkerBrowserActivity.kt`: focus-sensitive binding for every content card, with complete holder reset on recycling.
- `app/src/main/java/com/sinema/util/TvChannels.kt` and new `TvProviderIdentity.kt`: profile-namespaced provider IDs, legacy cleanup, stale-sync rejection, and one serialization lock.
- `app/src/main/java/com/sinema/util/SystemIntegrations.kt`: Global Search component reconciliation, import reconciliation, and active-server launcher clearing.
- `app/src/main/java/com/sinema/api/SinemaApi.kt`: complete-result recency, single-flight session renewal, shared authentication, and watched-state mutation.
- `app/src/main/java/com/sinema/api/StashAuth.kt`: structured login and API-key generation used by Setup and renewal.
- `app/src/main/java/com/sinema/ui/MainActivity.kt`: one recency request per Home load and complete logo teardown.
- `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`: profile/API snapshots and outgoing-scene metadata clearing.
- Existing focused test files remain the default; new test files are limited to new presenter, integration, transition, and authentication responsibilities.

---

### Task 1: Lock Global Search Before Any API Work

**Files:**
- Modify: `app/src/main/java/com/sinema/util/GlobalSearchProvider.kt:31-47`
- Test: `app/src/test/java/com/sinema/util/GlobalSearchProviderPrivacyTest.kt`
- Modify docs: `docs/settings-and-privacy.md`, `README.md`

**Interfaces:**
- Consumes: existing `Prefs.globalSearchEnabled`, `Prefs.isConfigured`, `Prefs.hasPinSet()`, `SinemaApp.pinVerifiedThisSession`, and `SinemaApp.decoySession`.
- Produces: no new public API; `GlobalSearchProvider.query` returns `null` for every blocked state without calling `searchScenesForSuggestions`.

- [ ] **Step 1: Write the failing provider tests**

Create a Robolectric test using the existing GraphQL fixture and a query URI whose last segment is `query`. Configure `SinemaApp.instance` and refresh its API before each case. The core assertions are:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class GlobalSearchProviderPrivacyTest {
    private val app get() = SinemaApp.instance
    private lateinit var provider: GlobalSearchProvider
    private val uri = Uri.parse("content://com.sinema.globalsearch/${SearchManager.SUGGEST_URI_PATH_QUERY}/secret")

    @Before fun setUp() {
        provider = Robolectric.buildContentProvider(GlobalSearchProvider::class.java).create().get()
        app.prefs.globalSearchEnabled = true
        app.prefs.serverUrl = "http://server"
        app.prefs.apiKey = "key"
        app.pinVerifiedThisSession = false
        app.decoySession = false
        app.prefs.removePin()
    }

    @Test fun `locked and decoy queries never reach the server`() {
        withGraphqlServer(
            responses = listOf(SequencedResponse(testSceneListResponse(testScene(id = "secret")))),
            configureSinemaApp = true
        ) { _, requests ->
            app.prefs.setPinHash("1234")
            app.pinVerifiedThisSession = false
            assertNull(query())
            app.decoySession = true
            app.pinVerifiedThisSession = true
            assertNull(query())
            assertEquals(emptyList<String>(), requests())
        }
    }

    @Test fun `disabled unconfigured locked verified no-pin and decoy states follow the gate`() {
        val cases = listOf(
            GateCase(enabled = false, configured = false, pin = false, verified = false, decoy = false, allowed = false),
            GateCase(enabled = true, configured = false, pin = false, verified = false, decoy = false, allowed = false),
            GateCase(enabled = true, configured = true, pin = true, verified = false, decoy = false, allowed = false),
            GateCase(enabled = true, configured = true, pin = true, verified = true, decoy = false, allowed = true),
            GateCase(enabled = true, configured = true, pin = false, verified = false, decoy = false, allowed = true),
            GateCase(enabled = true, configured = true, pin = true, verified = true, decoy = true, allowed = false)
        )
        cases.forEach { case -> assertGate(case) }
    }

    private fun query() = provider.query(uri, null, null, arrayOf("secret"), null)
}
```

Implement `GateCase` as a private data class with the six fields above. In `assertGate`, run each allowed case against a live `withGraphqlServer` and assert one suggestion; run each blocked case against the same fixture and assert `null` plus zero requests. Set `configured` through `serverUrl`/`apiKey`, and set or remove the PIN per case.

- [ ] **Step 2: Run the tests and confirm the locked/decoy cases fail**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.GlobalSearchProviderPrivacyTest' --no-daemon`
Expected: locked and decoy tests fail because suggestions are currently returned.

- [ ] **Step 3: Add the minimal early gate**

Place this immediately after the existing enabled/configured check and before parsing the query or entering `runBlocking`:

```kotlin
if (app.decoySession || (app.prefs.hasPinSet() && !app.pinVerifiedThisSession)) return null
```

Do not catch and suppress `CancellationException` differently, and do not alter deep-link PIN handling.

- [ ] **Step 4: Run the focused tests and update privacy copy**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.GlobalSearchProviderPrivacyTest' --tests 'com.sinema.util.GlobalSearchSuggestionsTest' --no-daemon`
Expected: PASS.

In `docs/settings-and-privacy.md` and `README.md`, state that enabled Global Search returns no new suggestions while a configured PIN is unverified or a decoy session is active; Android TV may retain previously cached results.

- [ ] **Step 5: Commit**

```bash
rtk git add app/src/main/java/com/sinema/util/GlobalSearchProvider.kt app/src/test/java/com/sinema/util/GlobalSearchProviderPrivacyTest.kt docs/settings-and-privacy.md README.md
rtk git commit -m "fix: suppress global search while locked"
```

### Task 2: Make Every Content Card Discreet and Recycle-Safe

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/CardPresenter.kt`
- Modify: `app/src/main/java/com/sinema/ui/MarkerBrowserActivity.kt:180-211`
- Modify: `app/src/main/java/com/sinema/util/DiscreetMode.kt`
- Test: `app/src/test/java/com/sinema/ui/CardPresenterDiscreetTest.kt`
- Test: `app/src/test/java/com/sinema/ui/FolderCardPresenterDiscreetTest.kt`
- Test: `app/src/test/java/com/sinema/ui/MarkerCardPresenterDiscreetTest.kt`
- Modify: `app/src/test/java/com/sinema/ui/HomeCommandRowIntegrationTest.kt`
- Modify docs: `docs/user-guide.md`, `docs/settings-and-privacy.md`, `docs/developer-reference.md`, `README.md`

**Interfaces:**
- Consumes: existing `DiscreetMode.neutralTitle(sceneId)`, `CardPresenter.discreetGeneration`, and `DiscreetRebind`.
- Produces: `DiscreetMode.neutralTitle(kind: String, id: String): String`; all presenters keep normal navigation cards unchanged and expose no new public UI API.

- [ ] **Step 1: Write focused, unfocused, navigation, and recycling tests**

Use `FrameLayout(RuntimeEnvironment.getApplication())`, create each presenter holder, and call `onBindViewHolder`. For each content item below, assert unfocused Discreet Mode shows the neutral title, `•••`, no visible heart/check/progress overlays, and `ImageView.INVISIBLE`; then call `view.requestFocus()` and assert normal title/metadata, overlays, and visible artwork. Include these exact items:

```kotlin
val contentItems = listOf(
    testScene(id = "s1", title = "Sensitive scene", rating100 = 100, resumeTimeSec = 20.0).copy(playCount = 2),
    GroupItem("g1", "Sensitive group", 3, frontImagePath = "http://server/group.jpg"),
    GalleryItem("g1", "Sensitive gallery", 4, coverImageId = "cover", rating100 = 100),
    EntityItem("e1", "Sensitive performer", 2, null, EntityItem.Kind.PERFORMER, rating100 = 100)
)
val folderItems = listOf(
    FolderItem("Sensitive folder", "/private", true, childCount = 2, firstSceneId = "s1", hasFavorites = true),
    FolderItem("Sensitive image", "/private/a.jpg", false, image = ImageItem("i1", "Private", "/a.jpg", width = 10, height = 20)),
    ImageItem("i1", "Sensitive image", "/private/a.jpg", width = 10, height = 20, rating100 = 100)
)
val marker = SceneMarkerItem("m1", "Sensitive marker", 12.0, "s1", "Sensitive scene", 1920, 1080, "t1", "Tag")
```

For every family, bind a sensitive item, call `onUnbindViewHolder`, bind a different item to the same holder with focus false, and assert no old title/drawable/overlay remains. Bind `HomeViewAll` in Discreet Mode and assert its normal `actionLabel` and `title`. Extend the existing Home/grid rebinding integration coverage so changing `CardPresenter.discreetGeneration` rebinds Group and Entity cards as well as Scene cards.

- [ ] **Step 2: Run the new tests and confirm non-scene cards fail**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests '*CardPresenterDiscreetTest' --no-daemon`
Expected: Scene coverage can pass; Group, Gallery, Entity, Folder, Image, and Marker unfocused/recycling assertions fail.

- [ ] **Step 3: Generalize neutral labels and holder binding**

Add a stable label helper without changing the existing scene result:

```kotlin
fun neutralTitle(kind: String, id: String): String {
    val safeKind = kind.ifBlank { "Item" }
    if (id.isBlank()) return "$safeKind 0"
    return "$safeKind ${abs(id.hashCode().toLong() % 10_000L)}"
}

fun neutralTitle(sceneId: String): String = neutralTitle("Scene", sceneId)
```

In each presenter, store the bound item and focus state in its holder. Before any normal text or Glide request, use this branch for content items only:

```kotlin
val hidden = SinemaApp.instance.prefs.discreetMode && !view.hasFocus() && item !is HomeViewAll
if (hidden && item != null) {
    Glide.with(view.context).clear(image)
    image.setImageDrawable(null)
    image.visibility = View.INVISIBLE
    title.text = DiscreetMode.neutralTitle(kindFor(item), idFor(item))
    content.text = "•••"
    heart?.visibility = View.GONE
    checkmark?.visibility = View.GONE
    progress?.visibility = View.GONE
    return
}
image.visibility = View.VISIBLE
```

`kindFor`/`idFor` are private presenter functions covering exactly Scene, GroupItem, GalleryItem, EntityItem, FolderItem, ImageItem, and SceneMarkerItem. A focus listener rebinds the current item so normal rendering, including artwork, runs only when revealed. `onUnbindViewHolder` clears item/focus/title/content/drawable and hides all overlays. Do not issue a Glide content-artwork request on the hidden path.

- [ ] **Step 4: Run all presenter/rebind tests**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests '*CardPresenterDiscreetTest' --tests 'com.sinema.ui.HomeCommandRowIntegrationTest' --no-daemon`
Expected: PASS, including focused reveal, recycling, navigation cards, and generation rebinding.

Update the four docs to list Scene, Group, Gallery, Entity, Folder, Image, and Marker cards; state that focus reveals normal content and navigation cards remain normal.

- [ ] **Step 5: Commit**

```bash
rtk git add app/src/main/java/com/sinema/ui/CardPresenter.kt app/src/main/java/com/sinema/ui/MarkerBrowserActivity.kt app/src/main/java/com/sinema/util/DiscreetMode.kt app/src/test/java/com/sinema/ui docs README.md
rtk git commit -m "fix: extend discreet rendering to content cards"
```

### Task 3: Namespace Launcher Entries and Reject Stale Syncs

**Files:**
- Create: `app/src/main/java/com/sinema/util/TvProviderIdentity.kt`
- Modify: `app/src/main/java/com/sinema/util/TvChannels.kt`
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt:999-1016`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt:323-340`
- Modify: `app/src/main/java/com/sinema/util/TvChannelInitReceiver.kt`
- Test: `app/src/test/java/com/sinema/util/TvProviderIdentityTest.kt`
- Test: `app/src/test/java/com/sinema/util/TvChannelsIsolationTest.kt`
- Test: `app/src/test/java/com/sinema/util/TvChannelInitReceiverTest.kt`
- Modify: `app/src/test/java/com/sinema/ui/HomeCommandRowIntegrationTest.kt`

**Interfaces:**
- Produces: `providerId(profileId: String, sceneId: String): String`, `isSinemaProviderId(value: String?): Boolean`, and `isActiveProfile(expectedProfileId: String, activeProfileId: String): Boolean`.
- Produces: `TvChannels.syncWatchNext(context, continuePairs, expectedProfileId)` and `syncRecentlyAdded(context, scenes, expectedProfileId)`; all three production call sites pass the fetch-time identity.

- [ ] **Step 1: Write pure identity and stale-sync tests**

```kotlin
class TvProviderIdentityTest {
    @Test fun `new ids contain profile and scene`() {
        assertEquals("sinema:profile-a:scene-9", providerId("profile-a", "scene-9"))
    }

    @Test fun `cleanup accepts legacy and namespaced sinema ids only`() {
        assertTrue(isSinemaProviderId("sinema:scene-9"))
        assertTrue(isSinemaProviderId("sinema:profile-a:scene-9"))
        assertFalse(isSinemaProviderId("other:scene-9"))
        assertFalse(isSinemaProviderId(null))
    }

    @Test fun `stale identity is rejected`() {
        assertTrue(isActiveProfile("profile-a", "profile-a"))
        assertFalse(isActiveProfile("profile-a", "profile-b"))
        assertFalse(isActiveProfile("", "profile-b"))
    }
}
```

Add Robolectric `TvChannels` tests with a recording content provider: preseed `sinema:legacy` and `sinema:profile-a:new`, call `clearAll`, and assert both are deleted while another app's entry remains. Call each sync with `expectedProfileId = "profile-a"` while active profile B is set; assert no delete or insert occurs. A matching sync must insert `sinema:profile-a:<sceneId>`.

- [ ] **Step 2: Run tests and confirm signature/identity failures**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.TvProviderIdentityTest' --tests 'com.sinema.util.TvChannelsIsolationTest' --no-daemon`
Expected: FAIL because helpers and `expectedProfileId` parameters do not exist.

- [ ] **Step 3: Implement identity helpers, one lock, and guarded sync**

```kotlin
package com.sinema.util

fun providerId(profileId: String, sceneId: String) = "sinema:$profileId:$sceneId"
fun isSinemaProviderId(value: String?) = value?.startsWith("sinema:") == true
fun isActiveProfile(expectedProfileId: String, activeProfileId: String) =
    expectedProfileId.isNotBlank() && expectedProfileId == activeProfileId
```

In `TvChannels`, add `private val syncLock = Any()`. Wrap the complete bodies of `clearAll`, `syncWatchNext`, and `syncRecentlyAdded` in `synchronized(syncLock)`. Inside each sync lock, before channels-enabled checks, queries, deletes, or inserts:

```kotlin
val app = SinemaApp.instance
if (!isActiveProfile(expectedProfileId, app.prefs.activeProfileId)) return
```

Use `providerId(expectedProfileId, scene.id)` for both provider columns and `isSinemaProviderId` for cleanup. Keep `clearAll` as the only shared cleanup implementation.

- [ ] **Step 4: Capture profile identity at all three data-fetch sites**

At Home load start, before any row `await`:

```kotlin
val launcherProfileId = app.prefs.activeProfileId
```

Pass it to both syncs. Playback already snapshots `profileId` before `saveQueue.enqueue`; pass that same value to `syncWatchNext(appCtx, continuePairs, profileId)`. In the receiver, capture once before the first fetch and use it for both syncs:

```kotlin
val profileId = app.prefs.activeProfileId
val continuePairs = app.api.findContinuePlaying()
TvChannels.syncWatchNext(context, continuePairs, profileId)
val recentScenes = app.api.findRecentScenes(20)
TvChannels.syncRecentlyAdded(context, recentScenes, profileId)
```

Add a receiver test that switches active profile while the first fetch is blocked and proves no old-profile rows publish. Add Home and playback regression assertions that their captured identity predates the associated data work.

- [ ] **Step 5: Run isolation tests and commit**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.TvProviderIdentityTest' --tests 'com.sinema.util.TvChannelsIsolationTest' --tests 'com.sinema.util.TvChannelInitReceiverTest' --tests 'com.sinema.ui.HomeCommandRowIntegrationTest' --no-daemon`
Expected: PASS.

```bash
rtk git add app/src/main/java/com/sinema/util/TvProviderIdentity.kt app/src/main/java/com/sinema/util/TvChannels.kt app/src/main/java/com/sinema/util/TvChannelInitReceiver.kt app/src/main/java/com/sinema/ui/MainActivity.kt app/src/main/java/com/sinema/ui/PlaybackActivity.kt app/src/test/java/com/sinema
rtk git commit -m "fix: isolate launcher sync by server profile"
```

### Task 4: Clear Launcher Content on Every Server Transition

**Files:**
- Create: `app/src/main/java/com/sinema/util/SystemIntegrations.kt`
- Modify: `app/src/main/java/com/sinema/ui/ServerProfileSection.kt`
- Modify: `app/src/main/java/com/sinema/ui/SettingsActivity.kt:105-135,316-336`
- Modify: `app/src/main/java/com/sinema/ui/SetupActivity.kt:339-435,561-580,640-660`
- Test: `app/src/test/java/com/sinema/ui/ServerProfileTransitionTest.kt`
- Test: `app/src/test/java/com/sinema/ui/SettingsServerTransitionTest.kt`
- Test: `app/src/test/java/com/sinema/ui/SetupServerTransitionTest.kt`

**Interfaces:**
- Produces: `SystemIntegrations.onActiveServerChanged(context: Context)`, which delegates only to `TvChannels.clearAll(context.applicationContext)`.
- Consumes: the serialized `TvChannels.clearAll` from Task 3.

- [ ] **Step 1: Write transition call-site tests**

Using Robolectric activities/dialogs and a recording TV provider, preseed Sinema launcher entries and exercise each operation. Assert old entries are gone before the launched Main/Setup completion can sync new ones for:

```kotlin
@Test fun `profile switch clears old launcher entries`() = assertSwitchClears()
@Test fun `active profile removal clears old launcher entries`() = assertActiveRemovalClears()
@Test fun `server url save clears old launcher entries`() = assertUrlSaveClears()
@Test fun `setup launched profile addition clears old launcher entries`() = assertActivityResultAdditionClears()
@Test fun `manual setup clears old launcher entries`() = assertManualSaveClears()
@Test fun `web setup completion clears old launcher entries`() = assertWebCompletionClears()
@Test fun `initial setup defensively clears legacy launcher entries`() = assertInitialSetupClears()
```

Also assert rename-only does not refresh the API or clear again, and TLS-only changes retain API refresh without a server-transition clear. Each helper must perform the real click/activity-result callback, not call `SystemIntegrations` directly.

- [ ] **Step 2: Run tests and confirm stale entries remain**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests '*ServerTransitionTest' --no-daemon`
Expected: all required transition tests fail.

- [ ] **Step 3: Add the narrow transition function and wire call sites**

```kotlin
package com.sinema.util

import android.content.Context

object SystemIntegrations {
    fun onActiveServerChanged(context: Context) {
        TvChannels.clearAll(context.applicationContext)
    }
}
```

Call it on confirmed profile switch, active-profile removal, active URL change before `refreshApi`, successful Setup-launched profile addition, manual save, Web Setup completion, and initial setup. For URL save compare normalized old/new values so credential-only edits do not clear. Do not call it for rename-only or TLS-only changes.

- [ ] **Step 4: Run transition and isolation tests**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests '*ServerTransitionTest' --tests 'com.sinema.util.TvChannelsIsolationTest' --no-daemon`
Expected: PASS.

- [ ] **Step 5: Review phase 1 and commit**

Request two independent reviews of Tasks 1-4: one against spec §§1-2 and global constraints, one for correctness/privacy/concurrency/test quality. Fix all Critical and Important findings and rerun:

`rtk ./gradlew :app:testDebugUnitTest --no-daemon`
Expected: zero failures.

```bash
rtk git add app/src/main/java/com/sinema/util/SystemIntegrations.kt app/src/main/java/com/sinema/ui app/src/test/java/com/sinema/ui
rtk git commit -m "fix: clear launcher content on server transitions"
```

### Task 5: Use One Complete Recency Result Per Home Load

**Files:**
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt:750-767`
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt:894-970`
- Modify: `app/src/test/java/com/sinema/api/SinemaApiTest.kt`
- Modify: `app/src/test/java/com/sinema/ui/HomeQueryTest.kt`
- Test: `app/src/test/java/com/sinema/ui/HomeRecencyIntegrationTest.kt`
- Modify docs: `docs/user-guide.md`, `docs/playback.md`, `docs/developer-reference.md`

**Interfaces:**
- Preserves: `suspend fun findRecentlyPlayed(perPage: Int = 25): List<Scene>`.
- Produces: `shouldLoadRecentlyPlayed(rowVisible: Boolean, discoveryVisible: Boolean): Boolean` for Home's single-fetch condition.

- [ ] **Step 1: Write the wire-level and sharing regressions**

Create 30 played scenes where item 30 has the newest timestamp, one middle item has a null timestamp, and server order deliberately places the newest last. Assert:

```kotlin
@Test fun `home recency fetches all orders nulls last and slices`() {
    val scenes = (1..30).map { index ->
        testScene(
            id = "$index",
            playCount = 1,
            lastPlayedAt = when (index) {
                30 -> "2026-11-03T12:00:00Z"
                2 -> null
                else -> "2026-10-${(index % 20) + 1}T00:00:00Z"
            }
        )
    }
    withGraphqlServer(testSceneListResponse(*scenes.toTypedArray())) { api, requestBody ->
        val result = runBlocking { api.findRecentlyPlayed(25) }
        assertEquals(25, result.size)
        assertEquals("30", result.first().id)
        assertTrue(result.none { it.id == "2" })
        assertTrue(requestBody().contains("\"per_page\":-1"))
    }
}
```

Add `shouldLoadRecentlyPlayed` truth-table tests for row/discovery visibility. In `HomeRecencyIntegrationTest`, enable both rows and use a request-counting fixture; assert one `findRecentlyPlayed` request, the Recently Played row uses the shared ordered list, and discovery uses its first item. Repeat with Recently Played hidden and discovery visible: exactly one recency request still occurs and discovery uses the same first item. A recency failure remains on the Recently Played row and discovery renders its empty/fallback state without a second request.

- [ ] **Step 2: Run and confirm limited-fetch/duplicate-request failures**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.SinemaApiTest' --tests 'com.sinema.ui.HomeRecencyIntegrationTest' --no-daemon`
Expected: newest item 30 is absent and Home makes two recency requests.

- [ ] **Step 3: Delegate to the existing paged recency path**

```kotlin
suspend fun findRecentlyPlayed(perPage: Int): List<Scene> =
    findRecentlyPlayedPaged(page = 1, perPage = perPage).second
```

Keep the default argument `perPage: Int = 25` in the actual declaration. Do not add another sorting implementation.

- [ ] **Step 4: Share the single Home request**

Add the pure predicate:

```kotlin
internal fun shouldLoadRecentlyPlayed(rowVisible: Boolean, discoveryVisible: Boolean) =
    rowVisible || discoveryVisible
```

Create `playedJob` whenever either row is enabled. In the discovery job, consume `playedJob.await().value.orEmpty().firstOrNull()` rather than calling the API again:

```kotlin
val playedJob = async {
    if (shouldLoadRecentlyPlayed(
            enabled(HomeContentRow.RECENTLY_PLAYED), enabled(HomeContentRow.DISCOVER)
        )
    ) loadRow { app.api.findRecentlyPlayed(25) }
    else RowResult<List<Scene>>(emptyList())
}
val discoveryJob = async {
    if (enabled(HomeContentRow.DISCOVER)) loadRow {
        val recent = playedJob.await().value.orEmpty().firstOrNull()
        val full = recent?.let { app.api.findSceneFull(it.id)?.scene }
        if (full == null) emptyList() else {
            discoverySource = full
            discoveryState = discoveryState.copy(title = "Because You Watched · ${full.displayTitle}")
            app.api.findRelatedScenes(full).take(25)
        }
    } else RowResult<List<Scene>>(emptyList())
}
```

Retain separate `RowResult` objects and existing error notes/fallback behavior.

- [ ] **Step 5: Verify and document the tradeoff**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.SinemaApiTest' --tests 'com.sinema.ui.HomeQueryTest' --tests 'com.sinema.ui.HomeRecencyIntegrationTest' --no-daemon`
Expected: PASS and one recency request per load.

Document that default Recently Played fetches the complete filtered library once per Home load for null-safe ordering, then returns at most 25 items and shares that result with Because You Watched.

- [ ] **Step 6: Commit**

```bash
rtk git add app/src/main/java/com/sinema/api/SinemaApi.kt app/src/main/java/com/sinema/ui/MainActivity.kt app/src/test/java/com/sinema/api/SinemaApiTest.kt app/src/test/java/com/sinema/ui docs README.md
rtk git commit -m "fix: share complete recency results on home"
```

### Task 6: Clear Outgoing Playback Metadata and Snapshot O-Counter API

**Files:**
- Create: `app/src/main/java/com/sinema/ui/PlaybackSceneMetadata.kt`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt:179-183,1012-1019,1094-1128`
- Test: `app/src/test/java/com/sinema/ui/PlaybackSceneMetadataTest.kt`
- Test: `app/src/test/java/com/sinema/ui/PlaybackProfileSnapshotTest.kt`

**Interfaces:**
- Produces: `suspend fun loadSceneMetadataForSwitch(clear: () -> Unit, fetch: suspend () -> SceneDetails?, apply: (SceneDetails) -> Unit)`.
- Consumes: existing `PlaybackActivity.applySceneMetadata`; O-counter mutation keeps its existing signature.

- [ ] **Step 1: Write null, failure, success, and cancellation tests**

```kotlin
@Test fun `metadata clears before null result`() = runBlocking {
    val events = mutableListOf<String>()
    loadSceneMetadataForSwitch(
        clear = { events += "clear" },
        fetch = { events += "fetch"; null },
        apply = { events += "apply" }
    )
    assertEquals(listOf("clear", "fetch"), events)
}

@Test fun `metadata stays cleared on failure`() = runBlocking {
    val events = mutableListOf<String>()
    assertFailsWith<IOException> {
        loadSceneMetadataForSwitch(
            clear = { events += "clear" },
            fetch = { events += "fetch"; throw IOException("offline") },
            apply = { events += "apply" }
        )
    }
    assertEquals(listOf("clear", "fetch"), events)
}

@Test fun `successful metadata applies only after clear`() = runBlocking {
    val details = SceneDetails(testScene("next"), 0.0, emptyList())
    val events = mutableListOf<String>()
    loadSceneMetadataForSwitch({ events += "clear" }, { details }, { events += "apply:${it.scene.id}" })
    assertEquals(listOf("clear", "apply:next"), events)
}
```

Add a cancellation test whose fetch throws `CancellationException` and assert it propagates after `clear`, with no `apply`. Add an O-counter integration test: capture server A and API A, start the app-scope mutation, switch `app.api` to server B before the queued coroutine runs, and assert only A receives `sceneIncrementO` for the original scene.

- [ ] **Step 2: Run and confirm ordering/snapshot failures**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.PlaybackSceneMetadataTest' --tests 'com.sinema.ui.PlaybackProfileSnapshotTest' --no-daemon`
Expected: missing loader fails compilation and the O-counter request can reach B.

- [ ] **Step 3: Implement the minimal loader and API snapshot**

```kotlin
package com.sinema.ui

import com.sinema.model.SceneDetails

internal suspend fun loadSceneMetadataForSwitch(
    clear: () -> Unit,
    fetch: suspend () -> SceneDetails?,
    apply: (SceneDetails) -> Unit
) {
    clear()
    fetch()?.let(apply)
}
```

Add `clearSceneMetadata()` that assigns empty captions/markers and disables/refreshes marker-dependent controls when already bound. In `playSceneNow`, call the loader around `app.api.findSceneFull(nextId)` so clearing happens before the fetch; remove the now-redundant catch-block clearing while retaining the existing warning. In `incrementOCount`, capture before launch:

```kotlin
val current = sceneId
val app = SinemaApp.instance
val appCtx = applicationContext
val api = app.api
app.appScope.launch {
    try {
        api.sceneIncrementO(current)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            Toast.makeText(appCtx, "O counted", Toast.LENGTH_SHORT).show()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("Sinema", "Failed to increment O counter", e)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            Toast.makeText(appCtx, "Failed to update O count", Toast.LENGTH_SHORT).show()
        }
    }
}
```

Retain the existing catch behavior and replace only the API receiver with the snapshot.

- [ ] **Step 4: Verify and commit**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.PlaybackSceneMetadataTest' --tests 'com.sinema.ui.PlaybackProfileSnapshotTest' --no-daemon`
Expected: PASS.

```bash
rtk git add app/src/main/java/com/sinema/ui/PlaybackSceneMetadata.kt app/src/main/java/com/sinema/ui/PlaybackActivity.kt app/src/test/java/com/sinema/ui/PlaybackSceneMetadataTest.kt app/src/test/java/com/sinema/ui/PlaybackProfileSnapshotTest.kt
rtk git commit -m "fix: isolate playback metadata and profile snapshots"
```

### Task 7: Fix Home Logo, Error Buffer, and PIN Equality Lifecycle Edges

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt:225-280,596-600`
- Modify: `app/src/main/java/com/sinema/SinemaApp.kt:24-33`
- Modify: `app/src/main/java/com/sinema/ui/PinActivity.kt:180-196,207-210`
- Test: `app/src/test/java/com/sinema/ui/HomeLogoLifecycleTest.kt`
- Test: `app/src/test/java/com/sinema/RecentErrorsConcurrencyTest.kt`
- Test: `app/src/test/java/com/sinema/ui/PinEqualityTest.kt`

**Interfaces:**
- Produces no public API; the logo listener is a fragment-owned nullable property removed in `onDestroyView`.

- [ ] **Step 1: Write lifecycle, concurrency, and bidirectional PIN tests**

For the logo test, create/destroy/recreate Main fragment view twice, count logo views under the activity root, and assert one after recreation and zero owned logo/listener references after teardown. For errors, use two bounded worker threads writing 500 unique messages while the main thread repeatedly reads `recentErrors`; assert no `ConcurrentModificationException`, size at most 20, and deterministic executor shutdown.

PIN tests launch `PinActivity` with `setup_mode` or `decoy_setup`, capture `val snapshotBefore = prefs.exportSnapshot()`, enter/confirm equal primary/decoy values through real digit buttons, and assert:

```kotlin
assertEquals("PINs must be different", activity.findViewById<TextView>(R.id.pin_error).text.toString())
assertTrue(activity.findViewById<TextView>(R.id.pin_error).isFocusable)
assertEquals(snapshotBefore, prefs.exportSnapshot())
assertTrue(activity.hasWindowFocus())
assertFalse(activity.isFinishing)
```

The unchanged backup snapshot proves neither credential store changed without adding production hash-export methods. Cover primary set/change against existing decoy and decoy set/change against existing primary; also verify different PINs still succeed and existing stored credentials still verify.

- [ ] **Step 2: Run tests and confirm failures**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.HomeLogoLifecycleTest' --tests 'com.sinema.RecentErrorsConcurrencyTest' --tests 'com.sinema.ui.PinEqualityTest' --no-daemon`
Expected: leaked logo/listener, possible buffer race, and equal-PIN acceptance failures.

- [ ] **Step 3: Implement minimal lifecycle fixes**

Store the scroll listener:

```kotlin
private var logoScrollListener: androidx.recyclerview.widget.RecyclerView.OnScrollListener? = null
```

Assign the object currently created at lines 274-280 to that property before adding it. In `onDestroyView`, before `super`:

```kotlin
logoScrollListener?.let { listener -> verticalGridView?.removeOnScrollListener(listener) }
logoScrollListener = null
logoView?.let { (it.parent as? ViewGroup)?.removeView(it) }
logoView = null
```

Synchronize the error snapshot:

```kotlin
val recentErrors: List<String>
    get() = synchronized(recentErrorsBuffer) { recentErrorsBuffer.toList() }
```

In PIN confirmation, check the opposite credential before either setter:

```kotlin
val decoySetup = intent.getBooleanExtra("decoy_setup", false)
val duplicate = if (decoySetup) app.prefs.verifyPin(currentPin) else app.prefs.verifyDecoyPin(currentPin)
if (duplicate) {
    showError("PINs must be different")
    errorText.isFocusable = true
    errorText.requestFocus()
    return
}
```

Leave the existing mismatched-confirmation and successful set flows unchanged.

- [ ] **Step 4: Run phase 2 verification and reviews**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.ui.HomeLogoLifecycleTest' --tests 'com.sinema.RecentErrorsConcurrencyTest' --tests 'com.sinema.ui.PinEqualityTest' --no-daemon`
Expected: PASS.

Request independent spec and quality reviews for Tasks 5-7. Fix Critical/Important findings, then run `rtk ./gradlew :app:testDebugUnitTest --no-daemon`; expect zero failures.

- [ ] **Step 5: Commit**

```bash
rtk git add app/src/main/java/com/sinema/ui/MainActivity.kt app/src/main/java/com/sinema/SinemaApp.kt app/src/main/java/com/sinema/ui/PinActivity.kt app/src/test/java/com/sinema
rtk git commit -m "fix: harden pin and lifecycle edge cases"
```

### Task 8: Coordinate Global Search Components and Backup Import

**Files:**
- Modify: `app/src/main/java/com/sinema/util/SystemIntegrations.kt`
- Modify: `app/src/main/java/com/sinema/SinemaApp.kt:40-47`
- Modify: `app/src/main/java/com/sinema/ui/SettingsActivity.kt:583-640,676-694`
- Test: `app/src/test/java/com/sinema/util/SystemIntegrationsTest.kt`
- Test: `app/src/test/java/com/sinema/ui/SettingsImportIntegrationTest.kt`
- Modify docs: `README.md`, `docs/settings-and-privacy.md`, `docs/developer-reference.md`

**Interfaces:**
- Produces: `applyGlobalSearchState(context: Context)`, `reconcileAfterImport(context: Context, previousProfileId: String, previousServerUrl: String)`, and internal `componentWriteState(currentState: Int, enabled: Boolean): Int?`.
- Preserves: `onActiveServerChanged(context)` from Task 4.

- [ ] **Step 1: Write component-state and import tests**

```kotlin
@Test fun `unchanged and manifest-default disabled states need no write`() {
    assertNull(componentWriteState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, false))
    assertNull(componentWriteState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, false))
    assertNull(componentWriteState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, true))
}

@Test fun `only a real state change requests a write`() {
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        componentWriteState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, true))
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        componentWriteState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, false))
}
```

Robolectric tests set imported Global Search true and false, invoke the real backup import callback, and assert both provider and activity states match immediately and after a fresh `SinemaApp.onCreate`. Import tests also cover: channels false clears entries; changed profile ID clears; changed trimmed URL clears; unchanged identity/URL does not clear. Startup with unchanged components performs no persistent component writes, verified by the pure predicate plus component-state assertions.

- [ ] **Step 2: Run and confirm missing reconciliation failures**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.SystemIntegrationsTest' --tests 'com.sinema.ui.SettingsImportIntegrationTest' --no-daemon`
Expected: missing functions and stale imported component/channel states fail.

- [ ] **Step 3: Implement focused reconciliation**

```kotlin
internal fun componentWriteState(currentState: Int, enabled: Boolean): Int? {
    val desired = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val effectiveCurrent = if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED else currentState
    return desired.takeIf { it != effectiveCurrent }
}

fun applyGlobalSearchState(context: Context) {
    val appContext = context.applicationContext
    val enabled = SinemaApp.instance.prefs.globalSearchEnabled
    val pm = appContext.packageManager
    listOf(
        ComponentName(appContext, GlobalSearchProvider::class.java),
        ComponentName(appContext, com.sinema.ui.GlobalSearchActivity::class.java)
    ).forEach { component ->
        componentWriteState(pm.getComponentEnabledSetting(component), enabled)?.let {
            pm.setComponentEnabledSetting(component, it, PackageManager.DONT_KILL_APP)
        }
    }
}

fun reconcileAfterImport(context: Context, previousProfileId: String, previousServerUrl: String) {
    val prefs = SinemaApp.instance.prefs
    applyGlobalSearchState(context)
    if (!prefs.channelsEnabled) TvChannels.clearAll(context.applicationContext)
    if (prefs.activeProfileId != previousProfileId ||
        prefs.serverUrl.trimEnd('/') != previousServerUrl.trimEnd('/')
    ) onActiveServerChanged(context)
}
```

Call `applyGlobalSearchState` after prefs migration/API initialization in `SinemaApp.onCreate`, from the Settings toggle after updating the pref, and from `reconcileAfterImport`. Capture previous profile ID/URL immediately before `importSnapshot`; call reconciliation immediately after successful import and before `refreshApi`. Replace the blanket restart Toast with `Imported $written entries` only after verifying imported API/integration state is live.

- [ ] **Step 4: Verify integration tests and docs**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.SystemIntegrationsTest' --tests 'com.sinema.ui.SettingsImportIntegrationTest' --tests '*ServerTransitionTest' --no-daemon`
Expected: PASS.

Update backup/import docs to remove obsolete mandatory-restart wording and describe immediate Global Search/channel/server reconciliation.

- [ ] **Step 5: Commit**

```bash
rtk git add app/src/main/java/com/sinema/util/SystemIntegrations.kt app/src/main/java/com/sinema/SinemaApp.kt app/src/main/java/com/sinema/ui/SettingsActivity.kt app/src/test/java/com/sinema README.md docs
rtk git commit -m "fix: reconcile android integrations after import"
```

### Task 9: Make Session Renewal Single-Flight

**Files:**
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt:38-44,271-362`
- Modify: `app/src/test/java/com/sinema/api/GraphqlTestServer.kt`
- Test: `app/src/test/java/com/sinema/api/SessionRenewalTest.kt`

**Interfaces:**
- Preserves: public GraphQL methods and `onSessionRefreshed: ((String) -> Unit)?`.
- Produces: fixture `SequencedResponse(body, status, headers = emptyMap())` and optional `onLogin: (() -> SequencedResponse)?` on the shared sequenced server helper.

- [ ] **Step 1: Extend the shared fixture before the concurrency test**

Use a bounded executor for both server overloads and deterministic cleanup:

```kotlin
val executor = Executors.newFixedThreadPool(8)
server.executor = executor
// register /graphql and /login contexts before server.start()
finally {
    server.stop(0)
    executor.shutdown()
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
}
```

`/login` reads the form body, invokes `onLogin`, records the request, and sends `SequencedResponse.headers` such as `mapOf("Set-Cookie" to "session=refreshed; Path=/")`. Existing tests remain source-compatible through default parameters.

- [ ] **Step 2: Write genuinely concurrent renewal tests**

Configure two GraphQL calls whose first responses are 401 and whose retries are successful. Use two `CountDownLatch` gates so both first requests are in flight, and a login handler that increments an atomic counter and waits until both 401s arrived:

```kotlin
val api = SinemaApi(url, "", "session=expired", "session").apply {
    stashUsername = "user"
    stashPassword = "pass"
    onSessionRefreshed = { callbacks.incrementAndGet() }
}
val results = (1..2).map { async(Dispatchers.IO) { api.findSceneFull("1") } }
assertEquals(2, results.awaitAll().size)
assertEquals(1, logins.get())
assertEquals(1, callbacks.get())
assertEquals(4, graphqlRequests.size)
assertEquals(setOf("session=refreshed"), retryCookies)
```

Add tests proving no login without stored credentials, original 401 handling when renewal fails, each request retries at most once, separate API instances each renew independently, and API-key 401 performs no login.

- [ ] **Step 3: Run and confirm duplicate logins**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.SessionRenewalTest' --no-daemon`
Expected: concurrent test observes two logins/callbacks.

- [ ] **Step 4: Implement cookie capture and per-API mutex**

Make immutable configuration fields `val`, retain only mutable credentials needed for renewal, and annotate the cookie:

```kotlin
private val serverUrl: String
private val apiKey: String
@Volatile private var sessionCookie: String
private val authMode: String
private val trustAllCerts: Boolean
private val refreshMutex = kotlinx.coroutines.sync.Mutex()
```

Keep these as constructor properties rather than duplicating declarations. Change `executeGraphql(body, cookie)` to send the supplied cookie. Capture `failedCookie = sessionCookie` before the first call, then:

```kotlin
val refreshed = refreshMutex.withLock {
    val current = sessionCookie
    if (current.isNotBlank() && current != failedCookie) current
    else reLogin()?.also { cookie ->
        sessionCookie = cookie
        onSessionRefreshed?.invoke(cookie)
    }
}
if (!refreshed.isNullOrBlank()) {
    val retry = executeGraphql(body, refreshed)
    return@withContext parseGraphqlResponse(retry.code, retry.body)
}
```

The first call uses `failedCookie`; API-key mode never enters this branch. Do not loop after retry.

- [ ] **Step 5: Run renewal and existing API tests**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.SessionRenewalTest' --tests 'com.sinema.api.SinemaApiTest' --no-daemon`
Expected: PASS with one login for concurrent failures.

- [ ] **Step 6: Commit**

```bash
rtk git add app/src/main/java/com/sinema/api/SinemaApi.kt app/src/test/java/com/sinema/api/GraphqlTestServer.kt app/src/test/java/com/sinema/api/SessionRenewalTest.kt
rtk git commit -m "fix: coalesce concurrent session renewal"
```

### Task 10: Share Structured Stash Authentication

**Files:**
- Create: `app/src/main/java/com/sinema/api/StashAuth.kt`
- Modify: `app/src/main/java/com/sinema/ui/SetupActivity.kt:140-286`
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt:334-362`
- Test: `app/src/test/java/com/sinema/api/StashAuthTest.kt`
- Modify: `app/src/test/java/com/sinema/api/SessionRenewalTest.kt`
- Modify: `app/src/test/java/com/sinema/ui/SetupWebServerLifecycleTest.kt`

**Interfaces:**
- Produces: `sealed interface LoginResult` with `Success(cookie)`, `InvalidCredentials`, `MissingCookie`, `HttpFailure(code)`, and `TransportFailure(cause)`.
- Produces: `StashAuth.login(client, serverUrl, username, password): LoginResult` and `StashAuth.generateApiKey(client, serverUrl, sessionCookie): String`.

- [ ] **Step 1: Write login outcome and API-key parsing tests**

Using the extended shared server and a self-signed-capable client fixture, cover all outcomes with assertions equivalent to:

```kotlin
assertEquals(LoginResult.InvalidCredentials, loginFor(status = 401))
assertEquals(LoginResult.MissingCookie, loginFor(status = 302, headers = emptyMap()))
assertEquals(LoginResult.HttpFailure(500), loginFor(status = 500))
assertEquals(LoginResult.Success("session=abc"), loginFor(
    status = 302, headers = mapOf("Set-Cookie" to "session=abc; Path=/")
))
assertTrue(loginFor(closedServer = true) is LoginResult.TransportFailure)
listOf(200, 302, 303, 307, 308).forEach { status ->
    assertTrue(loginFor(status = status, headers = sessionHeader) is LoginResult.Success)
}
```

For `generateApiKey`, assert success for `{"data":{"generateAPIKey":"key"}}`; assert `IOException` with actionable messages for non-2xx, GraphQL errors, malformed JSON, missing key, and blank key. Verify the request carries only the session cookie for auth and uses mutation `generateAPIKey(input: {})`. Reuse a `SinemaApi(allowSelfSigned = true).client` in the TLS test to prove the same client configuration reaches the helper.

- [ ] **Step 2: Run and confirm missing helper failures**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.StashAuthTest' --no-daemon`
Expected: FAIL because `StashAuth` and `LoginResult` do not exist.

- [ ] **Step 3: Implement structured login and Gson API-key parsing**

```kotlin
sealed interface LoginResult {
    data class Success(val cookie: String) : LoginResult
    data object InvalidCredentials : LoginResult
    data object MissingCookie : LoginResult
    data class HttpFailure(val code: Int) : LoginResult
    data class TransportFailure(val cause: Exception) : LoginResult
}

object StashAuth {
    fun login(client: OkHttpClient, serverUrl: String, username: String, password: String): LoginResult =
        try {
            val loginClient = client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
            val body = FormBody.Builder().add("username", username).add("password", password).build()
            val request = Request.Builder().url("${serverUrl.trimEnd('/')}/login").post(body).build()
            loginClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> LoginResult.InvalidCredentials
                    !in listOf(200, 302, 303, 307, 308) -> LoginResult.HttpFailure(response.code)
                    else -> response.headers("Set-Cookie")
                        .firstOrNull { it.contains("session") }
                        ?.substringBefore(';')
                        ?.takeIf { it.isNotBlank() }
                        ?.let(LoginResult::Success)
                        ?: LoginResult.MissingCookie
                }
            }
        } catch (e: Exception) {
            LoginResult.TransportFailure(e)
        }
}
```

Implement `generateApiKey` synchronously on the caller's existing IO dispatcher. Build the GraphQL body with Gson, require 2xx, parse `JsonObject`, throw `IOException` on a nonempty `errors` array, and return `data.generateAPIKey.asString` only when nonblank.

- [ ] **Step 4: Delegate Setup and renewal while preserving contracts**

Replace Setup's duplicate network methods with `withContext(Dispatchers.IO) { StashAuth.login(...) }` and map outcomes exactly:

```kotlin
LoginResult.InvalidCredentials -> "Invalid username or password"
LoginResult.MissingCookie -> "No session cookie received"
is LoginResult.HttpFailure -> "Unexpected login response: ${result.code}"
is LoginResult.TransportFailure -> "Login failed: ${result.cause.message ?: "network error"}"
```

`LoginResult.Success` proceeds to auth-mode choice. `generateApiKey` delegates to `StashAuth.generateApiKey`. `SinemaApi.reLogin` returns `(StashAuth.login(...) as? LoginResult.Success)?.cookie`, retaining blank-credential and null-on-failure behavior. Do not catch `CancellationException` inside the synchronous helper; existing coroutine callers keep their cancellation behavior.

- [ ] **Step 5: Run phase 3 verification and reviews**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.StashAuthTest' --tests 'com.sinema.api.SessionRenewalTest' --tests 'com.sinema.ui.SetupWebServerLifecycleTest' --no-daemon`
Expected: PASS.

Request independent spec and quality reviews for Tasks 8-10. Fix Critical/Important findings, then run `rtk ./gradlew :app:testDebugUnitTest --no-daemon`; expect zero failures.

- [ ] **Step 6: Commit**

```bash
rtk git add app/src/main/java/com/sinema/api/StashAuth.kt app/src/main/java/com/sinema/api/SinemaApi.kt app/src/main/java/com/sinema/ui/SetupActivity.kt app/src/test/java/com/sinema
rtk git commit -m "refactor: share structured stash authentication"
```

### Task 11: Centralize Watched-State Mutations

**Files:**
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt:690-716,1730-1738`
- Modify: `app/src/main/java/com/sinema/ui/SceneGridFragment.kt:217-223,391-402`
- Modify: `app/src/main/java/com/sinema/ui/SceneDetailActivity.kt:129-140`
- Modify: `app/src/test/java/com/sinema/api/SinemaApiTest.kt`

**Interfaces:**
- Produces: `suspend fun SinemaApi.setSceneWatched(sceneId: String, watched: Boolean)`.
- Preserves: local UI updates and `WatchedToggle` behavior.

- [ ] **Step 1: Write operation-order and request-semantic tests**

Use two sequenced successful responses for watched and one for unwatched:

```kotlin
@Test fun `watched clears resume before incrementing and unwatched resets`() {
    withGraphqlServer(listOf(
        SequencedResponse("""{"data":{"sceneSaveActivity":true}}"""),
        SequencedResponse("""{"data":{"sceneIncrementPlayCount":true}}""")
    )) { api, requests ->
        runBlocking { api.setSceneWatched("42", watched = true) }
        assertEquals(2, requests().size)
        assertTrue(requests()[0].contains("sceneSaveActivity"))
        assertTrue(requests()[0].contains("\"resume_time\":0"))
        assertTrue(requests()[1].contains("sceneIncrementPlayCount"))
    }
    withGraphqlServer("""{"data":{"sceneResetPlayCount":true}}""") { api, requestBody ->
        runBlocking { api.setSceneWatched("42", watched = false) }
        assertTrue(requestBody().contains("sceneResetPlayCount"))
        assertTrue(requestBody().contains("\"id\":\"42\""))
    }
}
```

- [ ] **Step 2: Run and confirm the API is missing**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.SinemaApiTest' --no-daemon`
Expected: compilation failure for `setSceneWatched`.

- [ ] **Step 3: Implement and replace both UI sequences**

```kotlin
suspend fun setSceneWatched(sceneId: String, watched: Boolean) {
    if (watched) {
        clearResumeTime(sceneId)
        incrementPlayCount(sceneId)
    } else {
        resetPlayCount(sceneId)
    }
}
```

Use `app.api.setSceneWatched(scene.id, !wasWatched)` in detail/toggle paths and `app.api.setSceneWatched(scene.id, true)` in mark-all. Keep existing logs/local `scene.copy`/detail state updates.

- [ ] **Step 4: Verify and commit**

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.api.SinemaApiTest' --tests 'com.sinema.ui.WatchedToggleTest' --no-daemon`
Expected: PASS.

```bash
rtk git add app/src/main/java/com/sinema/api/SinemaApi.kt app/src/main/java/com/sinema/ui/SceneGridFragment.kt app/src/main/java/com/sinema/ui/SceneDetailActivity.kt app/src/test/java/com/sinema/api/SinemaApiTest.kt
rtk git commit -m "refactor: centralize watched state mutations"
```

### Task 12: Remove Dead APIs and Duplicate Saved-Filter Loading

**Files:**
- Modify: `app/src/main/java/com/sinema/util/PlaybackQueue.kt:43-54`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackWatchClock.kt:33-39`
- Modify: `app/src/main/java/com/sinema/ui/IdleLockGate.kt`
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt:148-160`
- Modify: `app/src/main/java/com/sinema/ui/SavedFiltersActivity.kt:217`
- Modify tests: `app/src/test/java/com/sinema/util/PlaybackQueueTest.kt`, `PlaybackWatchClockTest.kt`, `IdleLockGateTest.kt`, `app/src/test/java/com/sinema/api/SinemaApiTest.kt`

**Interfaces:**
- Removes: `PlaybackQueue.jumpTo`, `PlaybackWatchClock.reset`, `lastInteractionAfterActivityTransition`, `SinemaApi.updateConfig`, and duplicate `SavedFilterScenesFragment.loadItems`.
- Retains: stable-ID `PlaybackQueue.jumpToItem` and all live configuration/auth behavior.

- [ ] **Step 1: Update tests to live behavior before deleting code**

Remove tests whose only subject is a dead method. Keep and strengthen `jumpToItem` coverage for current, unknown, valid, and stale selections. Replace the `updateConfig` test with:

```kotlin
@Test fun `configured instances retain their own media auth`() {
    val apiKey = SinemaApi("http://server-a", "old-key", "", "apikey")
    val session = SinemaApi("http://server-b", "", "session=zzz", "session")
    assertEquals(mapOf("ApiKey" to "old-key"), apiKey.mediaAuthHeaders())
    assertEquals(mapOf("Cookie" to "session=zzz"), session.mediaAuthHeaders())
}
```

Run: `rtk ./gradlew :app:testDebugUnitTest --tests 'com.sinema.util.PlaybackQueueTest' --tests 'com.sinema.ui.PlaybackWatchClockTest' --tests 'com.sinema.ui.IdleLockGateTest' --tests 'com.sinema.api.SinemaApiTest' --no-daemon`
Expected: PASS against live APIs.

- [ ] **Step 2: Delete only the confirmed dead production code**

Remove the five declarations listed above. Constructor configuration remains immutable except `@Volatile sessionCookie` and existing renewal credentials/callback. Do not remove `loadPage`; the base `SceneGridFragment.loadItems` already delegates to it.

Run: `rtk ./gradlew :app:testDebugUnitTest --no-daemon`
Expected: zero failures and no unresolved references.

- [ ] **Step 3: Review phase 4 and commit**

Request independent spec and quality reviews for Tasks 11-12. Fix Critical/Important findings and rerun the debug unit suite.

```bash
rtk git add app/src/main/java/com/sinema app/src/test/java/com/sinema
rtk git commit -m "refactor: remove dead playback and api code"
```

### Task 13: Correct User and Maintainer Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/user-guide.md`
- Modify: `docs/playback.md`
- Modify: `docs/settings-and-privacy.md`
- Modify: `docs/troubleshooting.md`
- Modify: `docs/developer-reference.md`
- Modify only if made stale: `AGENTS.md`

**Interfaces:**
- Documents the final behavior from Tasks 1-12; no screenshots or dependency/license files change.

- [ ] **Step 1: Audit claims against implementation**

Use focused searches:

```bash
rtk rg -n "restart|artwork|Recently Played|Discreet|Global Search|interrupted|encrypted|minif|Watch Next|sinema:" README.md docs AGENTS.md
```

Update the text to these exact behaviors:

- Watch Next uses `/play/` and resumes directly; Recently Added uses `/scene/` details.
- `TvChannels` currently publishes titles, duration, progress, and deep links, but no program poster/backdrop fields; do not claim API-key launcher artwork is attached. The channel logo still uses the app launcher icon.
- Interrupted playback snapshots persist locally with profile binding; encrypted preferences are used when available, with the existing conditional fallback. Incognito clears/suppresses snapshots.
- Release builds currently have `isMinifyEnabled = false`; logging claims must match actual code and must not imply R8 obfuscation/minification.
- Recently Played performs one complete filtered fetch per Home load, nulls-last sorts, slices 25, and shares with discovery.
- Global Search suppresses new suggestions while locked/decoy; cached Android results are outside Sinema's control.
- Discreet Mode covers all seven content-card families and focus reveals them.
- Backup import and startup reconcile Global Search; channels and server transitions clear stale launcher state.
- Launcher provider IDs are `sinema:<profileId>:<sceneId>` with legacy `sinema:<sceneId>` cleanup.

Update `AGENTS.md` only if the new `SystemIntegrations`/`StashAuth` architecture makes its source map or binding guidance inaccurate.

- [ ] **Step 2: Verify no assets or unrelated docs changed**

Run:

```bash
rtk git diff --check
rtk git diff --name-only -- assets
rtk git diff -- README.md docs AGENTS.md
```

Expected: no whitespace errors, no asset output, and only the intended documentation edits.

- [ ] **Step 3: Commit**

```bash
rtk git add README.md docs AGENTS.md
rtk git commit -m "docs: align privacy and integration behavior"
```

### Task 14: Final Review and Full Verification

**Files:**
- Review all changed files from `f06e3be` onward.
- Verify: debug/release unit-test, lint, APK, and AAB outputs.
- Conditionally regenerate: `app/src/release/generated/baselineProfiles/baseline-prof.txt` only if startup-critical changes materially affect the baseline and the documented emulator is available.

**Interfaces:**
- Final branch implements every requirement in the approved spec without version, tag, push, release, or screenshot changes.

- [ ] **Step 1: Run the complete required build**

```bash
rtk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`, zero test failures, zero lint errors, and debug/release APK plus release AAB outputs. Do not reuse results from earlier releases.

- [ ] **Step 2: Inspect outputs and workspace**

```bash
rtk ls -l app/build/outputs/apk/debug app/build/outputs/apk/release app/build/outputs/bundle/release
rtk git diff --check
rtk git diff --name-only -- assets
rtk git status --short --branch
```

Expected: output files exist, no whitespace errors, no screenshot changes, and only intended files/commits. Remove only generated files created by this verification that are not deliverables.

- [ ] **Step 3: Evaluate the startup baseline**

Because `SinemaApp.onCreate` and Home startup paths change, inspect the documented emulator availability and baseline instructions in `docs/developer-reference.md`. If the documented `sinema_bp` Android TV emulator is available, regenerate through the standard `:baselineprofile` task, inspect the diff, and commit the resulting baseline separately. If unavailable, leave the committed baseline unchanged and report baseline regeneration as unverified; never substitute a physical TV.

- [ ] **Step 4: Request final whole-branch independent review**

Provide the reviewer the approved spec, this plan, and `rtk git diff f06e3be...HEAD`. Request separate spec-compliance and code-quality verdicts, emphasizing privacy leaks, profile capture timing, coroutine cancellation, lock scope, request retry counts, component writes, test validity, and accidental scope expansion. Fix every Critical and Important finding and rerun all affected tests plus Step 1.

- [ ] **Step 5: Report verified and unverified behavior**

Final report must list exact passing commands, phase/final review outcomes, APK/AAB paths, clean intended workspace state, and no asset changes. Explicitly mark physical-TV geometry, Android system-search integration, launcher rendering, live-Stash integration, and emulator baseline regeneration as unverified unless actually exercised. Do not bump version, push, merge, tag, or create a GitHub release without a later explicit request.
