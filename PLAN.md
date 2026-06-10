# Sinema Feature Roadmap — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve Sinema from a folder-based Stash browser into a full library client: metadata browsing (tags/performers/studios), sort pickers, richer scene details, subtitles & track selection, markers-as-chapters, play-all/autoplay, Android TV home channels, and multi-server profiles.

**Architecture:** Keep the existing pattern — plain Activities + Leanback fragments, `SinemaApi` hand-rolled GraphQL over OkHttp, `SinemaApp` singleton, `Prefs` for storage. Phase 0 first extracts shared helpers (scene intents, grid fragment base, GraphQL scene-fields constant) so the seven feature phases reuse instead of copy-paste. Every phase ends with a refactor/de-dup pass, full verification, and an `opencode` peer-review gate.

**Tech Stack:** Kotlin 1.9, AndroidX Leanback 1.0, Media3 1.2.1, OkHttp 4.12, Gson, Glide, JUnit 4. One new dependency in Phase 8 only (`androidx.tvprovider`).

---

## Progress Overview

- [x] **Phase 0** — Foundations: de-duplication groundwork & shared helpers
- [x] **Phase 1** — API & model groundwork (metadata, entities, sort plumbing, full-scene query)
- [x] **Phase 2** — Sort pickers on grids
- [x] **Phase 3** — Tag / Performer / Studio browsing
- [x] **Phase 4** — Scene detail metadata (chips, rating, date) → **Release v1.11.0**
- [ ] **Phase 5** — Player: subtitles, audio tracks, playback speed
- [ ] **Phase 6** — Scene markers as chapters
- [ ] **Phase 7** — Play All & autoplay next → **Release v1.12.0**
- [ ] **Phase 8** — Android TV home-screen channels (Watch Next + Recently Added)
- [ ] **Phase 9** — Multi-server profiles → **Release v1.13.0**
- [ ] **Phase 10** — Final hardening, docs, full-roadmap review

Dependency order matters: 0 → 1 → (2, 3) → 4; 1 → 5 → 6 → 7; 8 and 9 only need 0–1. Don't start a phase before its prerequisites are merged.

---

## Conventions (read once, apply to every phase)

### Build, test, smoke-test commands

```bash
cd /work/repos/visorcraft/Sinema
./gradlew :app:testDebugUnitTest        # unit tests — must pass
./gradlew :app:lintDebug                # lint — no NEW warnings vs main
./gradlew :app:assembleDebug            # must build

# Sideload smoke test on the test TV (G08 Smart TV Pro):
adb connect 192.168.68.121:5555
adb -s 192.168.68.121:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.68.121:5555 shell monkey -p com.sinema -c android.intent.category.LEANBACK_LAUNCHER 1
```

### Git & commits

- Each phase happens on a branch: `git checkout -b feature/phase-N-<slug>` from up-to-date `main`. Merge to `main` only after the phase's review gate passes.
- Small, atomic commits — one logical change each (matches existing repo history).
- **Never** add AI/agent attribution: no `Co-Authored-By` trailers, no "Generated with…" lines, no AI names in code, comments, tests, or fixtures. Before every commit: `git diff --cached | grep -iE 'claude|anthropic|copilot|gemini|co-authored|generated with'` must return nothing.

### Peer Review Gate (run at the END of every phase — referenced below as "Review Gate")

All phase work must be committed first (the reviewer agent can edit files; a clean tree lets you detect and revert that).

```bash
cd /work/repos/visorcraft/Sinema
git diff main..HEAD -- app/ > _review_diff.txt    # diff MUST live inside the repo

cat > /tmp/prompt.txt <<'EOF'
READ-ONLY: do not modify/create/delete files. Read ./_review_diff.txt and any files it
references under app/src/. Review this Android TV (Leanback, Kotlin, Media3) change for:
1. Correctness bugs and crash risks (null JSON fields, unhandled exceptions in coroutines).
2. Auth-mode regressions: every media/GraphQL request must work in BOTH "apikey" and
   "session" modes (see SinemaApi.mediaAuthHeaders).
3. Coroutine/lifecycle misuse (lifecycleScope vs appScope, leaks on back-press).
4. D-pad navigation traps (unfocusable views, lost focus).
5. Duplicated logic that should reuse existing helpers (SceneIntents, SceneGridFragment,
   SinemaApi internals).
End with EXACTLY: VERDICT approved issues=N
EOF

# Backgrounded (slow); MUST pipe </dev/null or it hangs forever on a permission prompt.
timeout 280 opencode run "$(cat /tmp/prompt.txt)" --variant high < /dev/null > /tmp/oc.out 2>&1 &
```

Then, once it exits:

```bash
cat /tmp/oc.out      # must be non-empty and end with: VERDICT approved issues=N
                     # empty output = it never reached the model — RERUN, do not treat as a pass
rm _review_diff.txt
git status           # must be clean; if the agent edited anything: git restore .
```

If `issues > 0`: triage each finding with technical rigor (verify it's real before changing code — see superpowers:receiving-code-review), fix the real ones, commit, and re-run the gate until `issues=0` or every remaining issue is explicitly dismissed with a written reason in the phase notes.

### Refactor & De-dup Pass (run at the END of every phase — referenced below as "Refactor Pass")

Re-read every file touched in the phase and check:

1. No copy-pasted GraphQL field lists — scene queries go through `SCENE_FIELDS` / `findScenesInternal` (Phase 0).
2. No hand-rolled `intent.putExtra("scene_*", …)` blocks — use `SceneIntents` (Phase 0).
3. No new grid fragment re-implementing padding/presenter/click boilerplate — extend `SceneGridFragment` (Phase 0).
4. No function > ~60 lines or file > ~400 lines introduced without a written justification.
5. No dead code, commented-out blocks, or debug `Log.d` left from development.
6. Error handling matches house style: catch `CancellationException` and rethrow; user-facing `Toast` on failure; `Log.e("Sinema", …)`.
7. Run `./gradlew :app:lintDebug` and confirm no new warnings vs `main`.

Fix violations before the Review Gate (so the reviewer sees clean code).

### Phase Close-out Checklist (referenced below as "Close-out")

- [ ] Refactor Pass completed (all 7 checks)
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:assembleDebug` builds; sideload smoke test on the TV passes (phase-specific manual checks listed in each phase)
- [ ] All work committed; attribution grep clean
- [ ] Review Gate passed (`VERDICT approved issues=0` or dismissals documented)
- [ ] Branch merged to `main`; branch deleted

---

# Phase 0 — Foundations: de-duplication groundwork

**Why first:** Scene-detail intent wiring is copy-pasted in 4+ places (`MainActivity.kt:115-125`, `FavoritesActivity.kt:78-88`, `SceneDetailActivity.kt:226-236`, plus Search/FolderBrowse), the scene GraphQL field list appears ~10 times in `SinemaApi.kt`, and the vertical-grid boilerplate in `FavoritesActivity.kt:36-62` is about to be needed by three more screens. Every later phase multiplies this debt if not extracted now.

### Task 0.1: `SceneIntents` helper

**Files:**
- Create: `app/src/main/java/com/sinema/util/SceneIntents.kt`
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt:113-126`
- Modify: `app/src/main/java/com/sinema/ui/FavoritesActivity.kt:76-90`
- Modify: `app/src/main/java/com/sinema/ui/SceneDetailActivity.kt:36-46` and `:224-237`
- Modify: `app/src/main/java/com/sinema/ui/SearchActivity.kt` (scene click handler)
- Modify: `app/src/main/java/com/sinema/ui/FolderBrowseActivity.kt` and `BrowseFoldersActivity.kt` (scene click handlers)

- [x] **Step 1: Create the helper**

```kotlin
package com.sinema.util

import android.content.Context
import android.content.Intent
import com.sinema.model.Scene
import com.sinema.ui.SceneDetailActivity

/** Single source of truth for passing a Scene between activities via Intent extras. */
object SceneIntents {
    fun detail(context: Context, scene: Scene): Intent =
        Intent(context, SceneDetailActivity::class.java).apply {
            putExtra("scene_id", scene.id)
            putExtra("scene_title", scene.title)
            putExtra("scene_path", scene.path)
            putExtra("scene_duration", scene.duration)
            putExtra("scene_size", scene.size)
            putExtra("scene_width", scene.width)
            putExtra("scene_height", scene.height)
            putExtra("scene_play_count", scene.playCount)
            putExtra("scene_rating100", scene.rating100 ?: -1)
        }

    fun sceneFrom(intent: Intent): Scene = Scene(
        id = intent.getStringExtra("scene_id") ?: "",
        title = intent.getStringExtra("scene_title") ?: "",
        path = intent.getStringExtra("scene_path") ?: "",
        duration = intent.getDoubleExtra("scene_duration", 0.0),
        size = intent.getLongExtra("scene_size", 0L),
        width = intent.getIntExtra("scene_width", 0),
        height = intent.getIntExtra("scene_height", 0),
        playCount = intent.getIntExtra("scene_play_count", 0),
        rating100 = intent.getIntExtra("scene_rating100", -1).takeIf { it != -1 }
    )
}
```

- [x] **Step 2: Replace every call site.** Each `is Scene ->` click handler becomes `startActivity(SceneIntents.detail(requireContext(), item))`. `SceneDetailActivity.onCreate` scene construction becomes `scene = SceneIntents.sceneFrom(intent)`. Grep to confirm zero stragglers:

```bash
grep -rn 'putExtra("scene_path"' app/src/main/java/  # expect: only SceneIntents.kt
```

- [x] **Step 3: Build + run unit tests** (`./gradlew :app:testDebugUnitTest :app:assembleDebug`) — both green.

- [x] **Step 4: Commit** — `refactor: centralize scene intent extras in SceneIntents`

### Task 0.2: `SceneGridFragment` base class

**Files:**
- Create: `app/src/main/java/com/sinema/ui/SceneGridFragment.kt`
- Modify: `app/src/main/java/com/sinema/ui/FavoritesActivity.kt` (FavoritesGridFragment shrinks to ~20 lines)

- [x] **Step 1: Extract the base.** Move everything generic out of `FavoritesGridFragment` (`FavoritesActivity.kt:32-111`): overscan padding + `findGridView` hack, presenter setup, click-to-detail, load-with-error-handling.

```kotlin
package com.sinema.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Shared vertical-grid screen: overscan padding, card presenter, click-to-detail,
 * and error handling. Subclasses provide a title and an item loader.
 */
abstract class SceneGridFragment : VerticalGridSupportFragment() {
    protected val app get() = SinemaApp.instance
    protected lateinit var gridAdapter: ArrayObjectAdapter

    protected open val columns = 3
    abstract val gridTitle: String
    abstract val emptyMessage: String
    abstract suspend fun loadItems(): List<Any>

    /** Subclasses with non-Scene items override to handle their own clicks. */
    protected open fun onItemClicked(item: Any) {
        if (item is Scene) startActivity(SceneIntents.detail(requireContext(), item))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = gridTitle
        badgeDrawable = resources.getDrawable(R.drawable.sinema_logo, null)
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = columns
        setGridPresenter(gridPresenter)
        gridAdapter = ArrayObjectAdapter(CardPresenter(app.api))
        adapter = gridAdapter
        setOnItemViewClickedListener { _, item, _, _ -> onItemClicked(item) }
        reload()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val vPad = (27 * density).toInt()
        view.setPadding(hPad, vPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        view.viewTreeObserver.addOnGlobalLayoutListener {
            findGridView(view as? ViewGroup)?.let { grid ->
                if (grid.paddingLeft != hPad) {
                    grid.setPadding(hPad, vPad, hPad, vPad)
                    grid.clipToPadding = false
                }
            }
        }
    }

    protected fun reload() {
        lifecycleScope.launch {
            try {
                val items = loadItems()
                gridAdapter.clear()
                items.forEach { gridAdapter.add(it) }
                if (items.isEmpty()) {
                    Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e // back-press cancels the scope; not a real error
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findGridView(vg: ViewGroup?): androidx.leanback.widget.VerticalGridView? {
        if (vg == null) return null
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)
            if (child is androidx.leanback.widget.VerticalGridView) return child
            if (child is ViewGroup) findGridView(child)?.let { return it }
        }
        return null
    }
}
```

- [x] **Step 2: Rewrite `FavoritesGridFragment` as a subclass:**

```kotlin
class FavoritesGridFragment : SceneGridFragment() {
    override val gridTitle = "Favorites"
    override val emptyMessage = "No favorites yet"
    override suspend fun loadItems(): List<Any> = app.api.findFavoriteScenes()
}
```

- [x] **Step 3: Build, unit tests, sideload.** Manually verify on the TV: Favorites opens, padding/zoom unchanged, click opens detail, back works.

- [x] **Step 4: Commit** — `refactor: extract SceneGridFragment base from FavoritesGridFragment`

### Task 0.3: De-duplicate scene queries in `SinemaApi`

**Files:**
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt`
- Test: `app/src/test/java/com/sinema/api/SinemaApiTest.kt`

- [x] **Step 1: Add the shared field list and internal query helper** (private, near the top of the class):

```kotlin
companion object {
    // Single source of truth for scene list payloads. Extended fields
    // (tags/performers/etc.) live in SCENE_FIELDS_FULL — see findSceneFull.
    internal const val SCENE_FIELDS =
        "id title play_count rating100 files { path size duration width height }"
}

private suspend fun findScenesInternal(
    page: Int = 1,
    perPage: Int = 100,
    sort: String = "path",
    direction: String = "ASC",
    searchTerm: String? = null,
    sceneFilter: Map<String, Any?>? = null,
    fields: String = SCENE_FIELDS
): Pair<Int, List<Scene>> {
    val query = """
        query(${"$"}filter: FindFilterType, ${"$"}scene_filter: SceneFilterType) {
            findScenes(filter: ${"$"}filter, scene_filter: ${"$"}scene_filter) {
                count
                scenes { $fields }
            }
        }
    """.trimIndent()
    val filter = mutableMapOf<String, Any?>(
        "page" to page, "per_page" to perPage, "sort" to sort, "direction" to direction
    )
    if (searchTerm != null) filter["q"] = searchTerm
    val result = graphql(query, mapOf("filter" to filter, "scene_filter" to sceneFilter))
    val data = result.getAsJsonObject("data")?.getAsJsonObject("findScenes")
        ?: return Pair(0, emptyList())
    val count = data.get("count").asInt
    val scenes = data.getAsJsonArray("scenes").map { parseScene(it.asJsonObject) }
    return Pair(count, scenes)
}
```

- [x] **Step 2: Rewrite the existing scene queries as thin delegates** — `findAllScenes`, `findScenesByPath`, `searchScenes`, `findFavoriteScenes`, `findRecentScenes`, `findRecentlyPlayed`, `findScenesInFolderDirect` all become 3–6 line calls into `findScenesInternal` with their existing filter/sort values (e.g. `findScenesByPath` passes `sceneFilter = mapOf("path" to mapOf("value" to pathPrefixRegex(pathPrefix), "modifier" to "MATCHES_REGEX"))`). `findContinuePlaying` keeps its own query (it reads `resume_time` per row) but uses `$SCENE_FIELDS resume_time` as its field list. Public signatures must not change in this task.

- [x] **Step 3: Verify behavior is identical.** Run existing unit tests, then sideload and click through: Home rows populate, Search works, Browse Folders works, Favorites works — in BOTH auth modes if feasible (at minimum apikey mode against http://192.168.68.129:6969).

- [x] **Step 4: Commit** — `refactor: route all scene list queries through findScenesInternal`

### Phase 0 Close-out

- [x] Run the **Refactor Pass** (Conventions)
- [x] Run the **Close-out** checklist (Conventions). Manual smoke focus: every existing screen still works (Home, Search, Favorites, Browse Folders, deep folder, scene detail, playback, images, settings, PIN). *Note: on-device smoke testing skipped for all phases per maintainer instruction (no device installs without explicit permission) — verification is build + unit tests + lint + reviews; opencode verdict: approved issues=0.*
- [x] Run the **Review Gate** (Conventions)

---

# Phase 1 — API & model groundwork

**Goal:** Everything later phases need from Stash, in one reviewed layer: metadata-bearing models, entity queries (tags/performers/studios), entity-filtered scene queries, sort options, and a full single-scene query (metadata + captions + markers + resume time).

### Task 1.1: Extend models

**Files:**
- Modify: `app/src/main/java/com/sinema/model/Models.kt`

- [x] **Step 1: Add reference types and extend `Scene` with defaulted fields** (no call-site breakage):

```kotlin
data class TagRef(val id: String, val name: String)
data class PerformerRef(val id: String, val name: String)
data class StudioRef(val id: String, val name: String)
data class CaptionRef(val languageCode: String, val captionType: String)
data class MarkerRef(val id: String, val title: String, val seconds: Double, val primaryTag: String)

/** A browsable Stash entity (tag, performer, or studio). */
data class EntityItem(
    val id: String,
    val name: String,
    val sceneCount: Int,
    val imagePath: String?,   // absolute URL from Stash, needs auth headers
    val kind: Kind
) {
    enum class Kind(val label: String) { TAG("Tags"), PERFORMER("Performers"), STUDIO("Studios") }
}

/** Full single-scene payload for the detail screen and player. */
data class SceneDetails(
    val scene: Scene,
    val resumeTime: Double,
    val markers: List<MarkerRef>
)
```

`Scene` gains (all defaulted): `val date: String? = null`, `val studio: StudioRef? = null`, `val tags: List<TagRef> = emptyList()`, `val performers: List<PerformerRef> = emptyList()`, `val captions: List<CaptionRef> = emptyList()`.

- [x] **Step 2: Build + tests green. Commit** — `feat: add metadata models (tags, performers, studios, captions, markers)`

### Task 1.2: `SortOption` + persisted per-screen sort

**Files:**
- Create: `app/src/main/java/com/sinema/model/SortOption.kt`
- Modify: `app/src/main/java/com/sinema/util/Prefs.kt`
- Test: `app/src/test/java/com/sinema/model/SortOptionTest.kt`

- [x] **Step 1: Write the failing test first:**

```kotlin
package com.sinema.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SortOptionTest {
    @Test
    fun `random sort embeds seed for stable pagination`() {
        assertEquals("random_42", SortOption.RANDOM.apiSort(42))
    }

    @Test
    fun `non-random sort ignores seed`() {
        assertEquals("created_at", SortOption.ADDED_DESC.apiSort(42))
    }

    @Test
    fun `fromName falls back to default on unknown value`() {
        assertEquals(SortOption.PATH_ASC, SortOption.fromName("garbage"))
    }
}
```

Run: `./gradlew :app:testDebugUnitTest` — expect FAIL (class missing).

- [x] **Step 2: Implement:**

```kotlin
package com.sinema.model

/** Sort choices exposed in grid screens, mapped to Stash FindFilterType sort fields. */
enum class SortOption(val label: String, val field: String, val direction: String) {
    PATH_ASC("Name (A–Z)", "path", "ASC"),
    PATH_DESC("Name (Z–A)", "path", "DESC"),
    ADDED_DESC("Recently added", "created_at", "DESC"),
    DATE_DESC("Release date", "date", "DESC"),
    DURATION_DESC("Longest first", "duration", "DESC"),
    RATING_DESC("Highest rated", "rating", "DESC"),
    PLAYS_DESC("Most played", "play_count", "DESC"),
    RANDOM("Random", "random", "ASC");

    /** Stash paginates random sorts stably when the seed is part of the field name. */
    fun apiSort(seed: Int): String = if (this == RANDOM) "random_$seed" else field

    companion object {
        fun fromName(name: String?): SortOption =
            entries.firstOrNull { it.name == name } ?: PATH_ASC
    }
}
```

- [x] **Step 3: Tests pass.** Add persistence to `Prefs.kt`:

```kotlin
fun sortFor(screen: String): String =
    prefs.getString("sort_$screen", "") ?: ""

fun setSortFor(screen: String, optionName: String) =
    prefs.edit().putString("sort_$screen", optionName).apply()
```

- [x] **Step 4: Commit** — `feat: add SortOption enum with persisted per-screen sort`

### Task 1.3: Entity queries (tags / performers / studios)

**Files:**
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt`

- [x] **Step 1: Add one query per entity kind plus a parser.** All three follow this shape (shown for tags; performers use `findPerformers`/`performers`, studios use `findStudios`/`studios` — same selection set `id name scene_count image_path`):

```kotlin
suspend fun findEntities(kind: EntityItem.Kind, page: Int = 1, perPage: Int = 100): Pair<Int, List<EntityItem>> {
    val (queryName, listKey) = when (kind) {
        EntityItem.Kind.TAG -> "findTags" to "tags"
        EntityItem.Kind.PERFORMER -> "findPerformers" to "performers"
        EntityItem.Kind.STUDIO -> "findStudios" to "studios"
    }
    val query = """
        query(${"$"}filter: FindFilterType) {
            $queryName(filter: ${"$"}filter) {
                count
                $listKey { id name scene_count image_path }
            }
        }
    """.trimIndent()
    val variables = mapOf(
        "filter" to mapOf("page" to page, "per_page" to perPage, "sort" to "scenes_count", "direction" to "DESC")
    )
    val result = graphql(query, variables)
    val data = result.getAsJsonObject("data")?.getAsJsonObject(queryName) ?: return Pair(0, emptyList())
    val count = data.get("count").asInt
    val items = data.getAsJsonArray(listKey).map { el ->
        val obj = el.asJsonObject
        EntityItem(
            id = obj.get("id").asString,
            name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
            sceneCount = obj.get("scene_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            imagePath = obj.get("image_path")?.takeIf { !it.isJsonNull }?.asString,
            kind = kind
        )
    }
    return Pair(count, items)
}
```

- [x] **Step 2: Add entity-filtered scene query** (delegates to `findScenesInternal` from Task 0.3):

```kotlin
suspend fun findScenesForEntity(
    kind: EntityItem.Kind, entityId: String,
    page: Int = 1, perPage: Int = 100,
    sort: String = "path", direction: String = "ASC"
): Pair<Int, List<Scene>> {
    val sceneFilter = when (kind) {
        EntityItem.Kind.TAG ->
            mapOf("tags" to mapOf("value" to listOf(entityId), "modifier" to "INCLUDES", "depth" to 0))
        EntityItem.Kind.PERFORMER ->
            mapOf("performers" to mapOf("value" to listOf(entityId), "modifier" to "INCLUDES"))
        EntityItem.Kind.STUDIO ->
            mapOf("studios" to mapOf("value" to listOf(entityId), "modifier" to "INCLUDES", "depth" to 0))
    }
    return findScenesInternal(page, perPage, sort, direction, sceneFilter = sceneFilter)
}
```

- [x] **Step 3: Add sort passthrough to path/search queries.** `findScenesByPath`, `searchScenes`, and `findScenesInFolderDirect` gain `sort: String = "path", direction: String = "ASC"` parameters forwarded to `findScenesInternal`. Existing callers compile unchanged (defaults).

- [x] **Step 4: Manual API verification against the live server** (Stash at http://192.168.68.129:6969) — quickest via curl before wiring UI:

```bash
curl -s -H "ApiKey: $STASH_KEY" -H "Content-Type: application/json" \
  -d '{"query":"{ findTags(filter:{per_page:3,sort:\"scenes_count\",direction:DESC}){count tags{id name scene_count image_path}} }"}' \
  http://192.168.68.129:6969/graphql | head -c 600
```

Expect `count` and three tags. Repeat mentally for performers/studios (same shape). If `scenes_count` is rejected as a sort field by this Stash version, fall back to `"name"`/`ASC` and note it.

- [x] **Step 5: Commit** — `feat: add tag/performer/studio entity queries and entity-filtered scenes`

### Task 1.4: `findSceneFull` — single-scene metadata payload

**Files:**
- Modify: `app/src/main/java/com/sinema/api/SinemaApi.kt`

- [x] **Step 1: Implement query + extended parser.** Extend `parseScene` to read the optional fields when present (`date`, `studio`, `tags`, `performers`, `captions`) — all guarded with null/`isJsonNull` checks so lean list queries keep working. Then:

```kotlin
suspend fun findSceneFull(sceneId: String): SceneDetails? {
    val query = """
        query(${"$"}id: ID!) {
            findScene(id: ${"$"}id) {
                $SCENE_FIELDS
                date resume_time
                studio { id name }
                tags { id name }
                performers { id name }
                captions { language_code caption_type }
                scene_markers { id title seconds primary_tag { id name } }
            }
        }
    """.trimIndent()
    val result = graphql(query, mapOf("id" to sceneId))
    val obj = result.getAsJsonObject("data")?.getAsJsonObject("findScene") ?: return null
    val markers = obj.getAsJsonArray("scene_markers")?.map { m ->
        val mo = m.asJsonObject
        MarkerRef(
            id = mo.get("id").asString,
            title = mo.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
            seconds = mo.get("seconds")?.asDouble ?: 0.0,
            primaryTag = mo.getAsJsonObject("primary_tag")?.get("name")?.asString ?: ""
        )
    } ?: emptyList()
    return SceneDetails(
        scene = parseScene(obj),
        resumeTime = obj.get("resume_time")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
        markers = markers
    )
}

fun getCaptionUrl(sceneId: String, c: CaptionRef): String =
    "$serverUrl/scene/$sceneId/caption?lang=${c.languageCode}&type=${c.captionType}"
```

- [x] **Step 2: Curl-verify** `findScene` with one real scene id (grab an id from the Recently Added query) — confirm `captions` and `scene_markers` shapes parse.

- [x] **Step 3: Commit** — `feat: add findSceneFull with metadata, captions, markers, resume time`

### Phase 1 Close-out

- [x] **Refactor Pass** — extra attention: `findEntities` must be ONE function with a `when`, not three near-identical copies; `parseScene` stays under control (extract `parseRefList` helpers if it grows past ~50 lines).
- [x] **Close-out** checklist. Manual smoke: all existing screens still work (parser changes touch every list).
- [x] **Review Gate.**

---

# Phase 2 — Sort pickers on grids

**Goal:** A D-pad-friendly sort dialog on Favorites, deep folder browse, and Search results; choice persisted per screen; Random uses a per-visit seed.

### Task 2.1: Sort dialog helper

**Files:**
- Create: `app/src/main/java/com/sinema/ui/SortDialog.kt`

- [x] **Step 1: Implement** (AlertDialog single-choice is fully D-pad navigable on TV):

```kotlin
package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import com.sinema.model.SortOption

object SortDialog {
    fun show(context: Context, current: SortOption, onChosen: (SortOption) -> Unit) {
        val options = SortOption.entries
        AlertDialog.Builder(context)
            .setTitle("Sort by")
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

- [x] **Step 2: Commit** — `feat: add reusable D-pad sort dialog`

### Task 2.2: Wire sorting into `SceneGridFragment` and its screens

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/SceneGridFragment.kt`
- Modify: `app/src/main/java/com/sinema/ui/FavoritesActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/FolderBrowseActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/SearchActivity.kt`

- [x] **Step 1: Add sort state to the base fragment.** `SceneGridFragment` gains:

```kotlin
/** Prefs key for persisting this screen's sort; null = screen is not sortable. */
protected open val sortScreenKey: String? = null
protected var sort: SortOption = SortOption.PATH_ASC
protected val randomSeed: Int = (0..99_999_999).random()

fun showSortDialog() {
    val key = sortScreenKey ?: return
    SortDialog.show(requireContext(), sort) { chosen ->
        sort = chosen
        app.prefs.setSortFor(key, chosen.name)
        reload()
    }
}
```

Initialize in `onCreate` (before `reload()`): `sort = SortOption.fromName(app.prefs.sortFor(sortScreenKey ?: ""))`. Update the grid `title` to show the active sort, e.g. `"$gridTitle  •  ${sort.label}"`, refreshed on change.

- [x] **Step 2: Open the dialog from the remote's MENU key.** In each host activity (Favorites first):

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
        (supportFragmentManager.findFragmentById(R.id.main_frame) as? SceneGridFragment)
            ?.showSortDialog()
        return true
    }
    return super.onKeyDown(keyCode, event)
}
```

**Remote caveat:** verify the G08 remote has a MENU key during the smoke test. If not, also trigger on long-press of DPAD_CENTER on the grid title row, or add a "Sort" first-card item to the grid — decide on-device, implement one fallback, and note the choice in the commit message.

- [x] **Step 3: Pass sort through to the API.** `FavoritesGridFragment.loadItems()` switches from `findFavoriteScenes()` to a sorted variant: add `sort`/`direction` params to `findFavoriteScenes` (delegating via `findScenesInternal`, favorite filter preserved) and call `app.api.findFavoriteScenes(sort = sort.apiSort(randomSeed), direction = sort.direction)`. Same pattern for `FolderBrowseActivity`'s scene loading (`findScenesInFolderDirect`) and `SearchActivity` (`searchScenes`). Screen keys: `"favorites"`, `"folder"`, `"search"`.

- [x] **Step 4: Sideload + verify on TV:** change sort on Favorites → order changes and survives app restart; Random gives a stable order while paging but reshuffles next visit; Search results sortable.

- [x] **Step 5: Commit** — `feat: per-screen sort picker on Favorites, folder browse, and search`

### Phase 2 Close-out

- [x] **Refactor Pass** — sort wiring must live ONCE in `SceneGridFragment`/`SortDialog`; host activities only forward the key event.
- [x] **Close-out** checklist. Manual smoke: each sortable screen × at least 3 sort options.
- [x] **Review Gate.**

---

# Phase 3 — Tag / Performer / Studio browsing

**Goal:** Home screen gains "Tags", "Performers", "Studios" entries → entity grid → entity's scenes grid (sortable, reusing Phase 2).

### Task 3.1: Entity cards in `CardPresenter`

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/CardPresenter.kt`

- [x] **Step 1: Read `CardPresenter.kt` fully first** (it already renders `Scene`, and folder items). Add an `is EntityItem ->` branch in `onBindViewHolder`: title = `item.name`, content = `"${item.sceneCount} scene(s)"` (pluralized like the folder-card counts), image = `item.imagePath` loaded through the existing authenticated Glide path (`GlideAuth.url(api, item.imagePath)`) with a neutral placeholder drawable when `imagePath == null`. Follow the file's existing binding style exactly.

- [x] **Step 2: Build green. Commit** — `feat: render tag/performer/studio cards in CardPresenter`

### Task 3.2: `EntityGridActivity` (lists tags OR performers OR studios)

**Files:**
- Create: `app/src/main/java/com/sinema/ui/EntityGridActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] **Step 1: Implement using the Phase 0 base:**

```kotlin
package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.EntityItem

class EntityGridActivity : FragmentActivity() {
    companion object {
        fun intent(context: Context, kind: EntityItem.Kind): Intent =
            Intent(context, EntityGridActivity::class.java).putExtra("kind", kind.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val kind = EntityItem.Kind.valueOf(intent.getStringExtra("kind") ?: EntityItem.Kind.TAG.name)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, EntityGridFragment.create(kind))
                .commit()
        }
    }
}

class EntityGridFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind) = EntityGridFragment().apply {
            arguments = Bundle().apply { putString("kind", kind.name) }
        }
    }

    private val kind get() = EntityItem.Kind.valueOf(requireArguments().getString("kind")!!)
    override val columns = 4
    override val gridTitle get() = kind.label
    override val emptyMessage get() = "No ${kind.label.lowercase()} found"

    override suspend fun loadItems(): List<Any> = app.api.findEntities(kind, perPage = 200).second

    override fun onItemClicked(item: Any) {
        if (item is EntityItem) startActivity(EntityScenesActivity.intent(requireContext(), item))
    }
}
```

Register in the manifest like the other activities (`android:screenOrientation="landscape"`).

- [x] **Step 2: Commit** — `feat: add entity grid screen for tags, performers, studios`

### Task 3.3: `EntityScenesActivity` (scenes for one entity)

**Files:**
- Create: `app/src/main/java/com/sinema/ui/EntityScenesActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] **Step 1: Implement** — same Activity+Fragment shape as Task 3.2. The fragment:

```kotlin
class EntityScenesFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind, id: String, name: String) = EntityScenesFragment().apply {
            arguments = Bundle().apply {
                putString("kind", kind.name); putString("id", id); putString("name", name)
            }
        }
    }

    private val kind get() = EntityItem.Kind.valueOf(requireArguments().getString("kind")!!)
    override val sortScreenKey = "entity_scenes"
    override val gridTitle get() = requireArguments().getString("name") ?: ""
    override val emptyMessage = "No scenes"

    override suspend fun loadItems(): List<Any> =
        app.api.findScenesForEntity(
            kind, requireArguments().getString("id")!!,
            perPage = 200, sort = sort.apiSort(randomSeed), direction = sort.direction
        ).second
}
```

The host `EntityScenesActivity` provides `intent(context, item: EntityItem)` (kind/id/name extras) and forwards MENU to `showSortDialog()` like Phase 2. Register in manifest.

- [x] **Step 2: Commit** — `feat: add per-entity scenes grid`

### Task 3.4: Home screen entry points

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt:184-192` (top row) and the `is String ->` click handler at `:127-159`

- [x] **Step 1:** Add `"Tags"`, `"Performers"`, `"Studios"` to `topAdapter` after `"Browse Folders"`, and handle the clicks: `"Tags" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.TAG))` etc.

- [x] **Step 2: Sideload + verify on TV:** Home → Tags → tag grid with counts/images → tag → sorted scenes → detail → playback. Same for Performers and Studios. Check D-pad focus never gets trapped, and entity images load in both auth modes (image_path URLs carry their own host — confirm `GlideAuth` headers still apply).

- [x] **Step 3: Commit** — `feat: surface tag/performer/studio browsing on home screen`

### Phase 3 Close-out

- [x] **Refactor Pass** — `EntityGridFragment`/`EntityScenesFragment` must contain no duplicated grid plumbing (it all lives in `SceneGridFragment`); the two new activities should be near-trivial shells.
- [x] **Close-out** checklist.
- [x] **Review Gate.**

---

# Phase 4 — Scene detail metadata

**Goal:** `SceneDetailActivity` shows date, rating, studio, tags, and performers — each tag/performer/studio chip is focusable and jumps to its `EntityScenesActivity` grid. One release (v1.11.0) ships after this phase.

### Task 4.1: Fetch and render metadata

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/SceneDetailActivity.kt`
- Modify: `app/src/main/res/layout/activity_scene_detail.xml`

- [x] **Step 1: Replace the two ad-hoc fetches with one.** `SceneDetailActivity.onCreate`/`onResume` currently call `findContinuePlaying()` and `getSceneRating()` separately (`SceneDetailActivity.kt:73-92, 112-120, 254-275`). Replace both with a single `app.api.findSceneFull(scene.id)` call in a `loadDetails()` function invoked from `onResume`: it drives the Resume button (`details.resumeTime > 5.0`), the Favorite button state (`details.scene.isFavorite`), and the new metadata views. Keep the existing error-handling style (catch, log, Toast only where the old code toasted).

- [x] **Step 2: Layout.** In `activity_scene_detail.xml`, under the existing path text, add: a `detail_meta` TextView (date • studio • ★rating when present) and two horizontal `LinearLayout` chip rows (`detail_tags_row`, `detail_performers_row`) inside `HorizontalScrollView`s, initially `visibility="gone"`. Match the screen's existing text styles/colors.

- [x] **Step 3: Chips.** In `loadDetails()`, populate chip rows with focusable `Button`s (same style as the existing action buttons, smaller padding):

```kotlin
private fun addChip(row: LinearLayout, label: String, onClick: () -> Unit) {
    val chip = Button(this).apply {
        text = label
        isFocusable = true
        setOnClickListener { onClick() }
    }
    row.addView(chip)
}
// tags:       addChip(tagsRow, "#${tag.name}") { startActivity(EntityScenesActivity.intent(this, EntityItem(tag.id, tag.name, 0, null, EntityItem.Kind.TAG))) }
// performers: same with Kind.PERFORMER
// studio:     one chip on the meta line with Kind.STUDIO
```

Clear rows before repopulating (onResume runs repeatedly). Show rows only when non-empty.

- [x] **Step 4: Sideload + verify:** a scene with tags/performers shows chips; D-pad reaches chips from the button row and back; chip click opens the entity grid; a metadata-less scene renders exactly like today (no empty gaps).

- [x] **Step 5: Commit** — `feat: show date, rating, studio, tag and performer chips on scene detail`

### Task 4.2: Release v1.11.0

- [x] **Step 1:** Bump `versionCode = 15`, `versionName = "1.11.0"` in `app/build.gradle.kts`. Commit — `Bump version to 1.11.0`.
- [ ] **Step 2:** Push `main` and tag per the repo's existing release flow (`release.yml` signs from the `SINEMA_KEYSTORE_B64` secret). Verify the in-app update prompt offers 1.11.0 on the TV afterwards. *Deferred to maintainer: pushing/tagging publishes a release; awaiting explicit go-ahead (same for the on-TV verification).*

### Phase 4 Close-out

- [x] **Refactor Pass** — `SceneDetailActivity` was 291 lines before this phase; after consolidating the duplicated resume-button logic into `loadDetails()` it should NOT have grown past ~320. If it has, extract a `SceneDetailBinder` helper.
- [x] **Close-out** checklist.
- [x] **Review Gate** (run before the release steps in Task 4.2).

---

# Phase 5 — Player: subtitles, audio tracks, playback speed

**Goal:** Stash captions appear as selectable subtitle tracks; Media3's built-in controller exposes subtitle toggle, audio track selection, and playback speed — all D-pad reachable.

### Task 5.1: Sideload captions into the player

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/SceneDetailActivity.kt` (pass captions through)

- [ ] **Step 1: Pass captions via intent extras** (small strings — no binder-size risk). In `SceneIntents`, add:

```kotlin
fun putCaptions(intent: Intent, captions: List<CaptionRef>) {
    intent.putExtra("caption_langs", captions.map { it.languageCode }.toTypedArray())
    intent.putExtra("caption_types", captions.map { it.captionType }.toTypedArray())
}

fun captionsFrom(intent: Intent): List<CaptionRef> {
    val langs = intent.getStringArrayExtra("caption_langs") ?: return emptyList()
    val types = intent.getStringArrayExtra("caption_types") ?: return emptyList()
    return langs.zip(types).map { (l, t) -> CaptionRef(l, t) }
}
```

`SceneDetailActivity` calls `SceneIntents.putCaptions(intent, details.scene.captions)` when launching playback (both Play and Resume paths — they should share one `launchPlayback(resumeMs: Long)` helper; create it now if Phase 4 didn't).

- [ ] **Step 2: Build the MediaItem with subtitle configurations** in `PlaybackActivity.initPlayer()`:

```kotlin
import androidx.media3.common.C
import androidx.media3.common.MimeTypes

val captions = SceneIntents.captionsFrom(intent)
val subs = captions.map { c ->
    MediaItem.SubtitleConfiguration.Builder(Uri.parse(app.api.getCaptionUrl(sceneId, c)))
        .setMimeType(if (c.captionType.equals("vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
        .setLanguage(c.languageCode)
        .setLabel("${c.languageCode.uppercase()} (${c.captionType})")
        .build()
}
val mediaItem = MediaItem.Builder()
    .setUri(Uri.parse(streamUrl))
    .setMediaId(sceneId)
    .setSubtitleConfigurations(subs)
    .build()
exo.setMediaItem(mediaItem)
```

Caption HTTP requests reuse the same `DefaultHttpDataSource.Factory` and therefore the same auth headers — no extra work, but verify in session mode.

- [ ] **Step 3: Commit** — `feat: load Stash captions as subtitle tracks`

### Task 5.2: Controller buttons — subtitles, audio, speed

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`
- Modify: `app/src/main/res/layout/activity_playback.xml`

- [ ] **Step 1: Enable Media3's built-in controls.** On the `PlayerView` in `activity_playback.xml` set `app:show_subtitle_button="true"`. Media3 1.2.1's default controller already includes the settings (gear) menu with playback speed and audio track selection; the subtitle (CC) button toggles/picks text tracks. No custom dialogs unless the on-device check fails.

- [ ] **Step 2: Sideload + verify on TV with a captioned scene** (add an `.srt` next to a file in Stash if none exists; Stash picks it up on scan):
  - DPAD_DOWN/center brings up the controller; CC button reachable and toggles subs.
  - Gear menu → speed 1.5x audibly works; audio track list shows for multi-audio files.
  - Subtitles render during playback; resume/play-count behavior unchanged.
  - **If** the default controller proves unusable by D-pad on this device, fall back to: `dispatchKeyEvent` on `KEYCODE_MENU` → `AlertDialog` listing "Subtitles / Audio / Speed", each applying `TrackSelectionOverride` / `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, …)` / `setPlaybackSpeed(…)`. Implement the fallback only if needed; record the outcome in the commit message.

- [ ] **Step 3: Commit** — `feat: expose subtitle, audio track, and playback speed controls`

### Phase 5 Close-out

- [ ] **Refactor Pass** — `PlaybackActivity` should still be a single small file (~150 lines); `initPlayer` stays under 40 lines (extract `buildMediaItem()`).
- [ ] **Close-out** checklist. Manual smoke: playback in both auth modes, with and without captions.
- [ ] **Review Gate.**

---

# Phase 6 — Scene markers as chapters

**Goal:** Stash markers become jump points: a Chapters dialog in the player and next/previous-chapter on media keys.

### Task 6.1: Markers into the player

**Files:**
- Modify: `app/src/main/java/com/sinema/util/SceneIntents.kt` (marker extras)
- Modify: `app/src/main/java/com/sinema/ui/SceneDetailActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

- [ ] **Step 1: Pass markers through the intent.** Mirror Task 5.1's pattern in `SceneIntents`: `putMarkers(intent, markers)` / `markersFrom(intent)` using parallel arrays (`marker_titles: Array<String>`, `marker_seconds: DoubleArray`); label = `marker.title.ifBlank { marker.primaryTag }`. `SceneDetailActivity.launchPlayback` adds them from `details.markers` (sorted by `seconds`).

- [ ] **Step 2: Chapters dialog + media-key navigation** in `PlaybackActivity`:

```kotlin
private fun showChapters() {
    val markers = SceneIntents.markersFrom(intent)
    if (markers.isEmpty()) return
    val labels = markers.map { "${formatSeconds(it.seconds)}  ${it.title}" }  // label merged at put-time
    android.app.AlertDialog.Builder(this)
        .setTitle("Chapters")
        .setItems(labels.toTypedArray()) { _, which ->
            player?.seekTo((markers[which].seconds * 1000).toLong())
        }
        .show()
}

private fun seekToAdjacentMarker(forward: Boolean) {
    val exo = player ?: return
    val markers = SceneIntents.markersFrom(intent)
    val posSec = exo.currentPosition / 1000.0
    val target = if (forward) markers.firstOrNull { it.seconds > posSec + 1 }
                 else markers.lastOrNull { it.seconds < posSec - 3 }
    target?.let { exo.seekTo((it.seconds * 1000).toLong()) }
}

override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
        KeyEvent.KEYCODE_MENU -> { showChapters(); return true }
        KeyEvent.KEYCODE_MEDIA_NEXT -> { seekToAdjacentMarker(true); return true }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { seekToAdjacentMarker(false); return true }
    }
    return super.dispatchKeyEvent(event)
}
```

`formatSeconds` reuses the `formatMs`-style helper — move that formatter to a shared `com.sinema.util.TimeFormat` object now (it's already duplicated between `Models.kt:19-26` and `SceneDetailActivity.kt:283-290`) and delegate both call sites to it.

- [ ] **Step 3: Show marker count on scene detail.** If `details.markers.isNotEmpty()`, append `" • ${markers.size} chapters"` to the meta line from Phase 4.

- [ ] **Step 4: Sideload + verify** with a scene that has markers (create one in Stash's web UI if needed): MENU lists chapters, selecting seeks; next/prev media keys hop markers; scenes without markers behave exactly as before.

- [ ] **Step 5: Commit** — `feat: scene markers as player chapters with media-key navigation`

### Phase 6 Close-out

- [ ] **Refactor Pass** — confirm the `TimeFormat` consolidation landed and no third duration formatter exists (`grep -rn "%d:%02d" app/src/main/java/` → only TimeFormat).
- [ ] **Close-out** checklist.
- [ ] **Review Gate.**

---

# Phase 7 — Play All & autoplay next

**Goal:** "Play All" from a folder or entity grid plays scenes back-to-back; per-scene resume/play-count bookkeeping still correct. Release v1.12.0 after this phase.

### Task 7.1: `PlaybackQueue`

**Files:**
- Create: `app/src/main/java/com/sinema/util/PlaybackQueue.kt`
- Test: `app/src/test/java/com/sinema/util/PlaybackQueueTest.kt`

- [ ] **Step 1: Failing tests first:**

```kotlin
package com.sinema.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {
    @Test
    fun `next walks the queue and returns null at the end`() {
        PlaybackQueue.start(listOf("1", "2", "3"), startAt = 0)
        assertEquals("2", PlaybackQueue.next())
        assertEquals("3", PlaybackQueue.next())
        assertNull(PlaybackQueue.next())
    }

    @Test
    fun `clear deactivates the queue`() {
        PlaybackQueue.start(listOf("1", "2"), startAt = 0)
        PlaybackQueue.clear()
        assertNull(PlaybackQueue.next())
    }

    @Test
    fun `start mid-list resumes from that position`() {
        PlaybackQueue.start(listOf("a", "b", "c"), startAt = 1)
        assertEquals("c", PlaybackQueue.next())
    }
}
```

Run — expect FAIL (class missing).

- [ ] **Step 2: Implement** (in-process singleton, same pattern the image viewer already uses to dodge binder limits):

```kotlin
package com.sinema.util

/** In-process playback queue; survives activity recreation, dies with the process (fine for TV). */
object PlaybackQueue {
    private var ids: List<String> = emptyList()
    private var index: Int = -1

    val isActive: Boolean get() = index in ids.indices

    @Synchronized
    fun start(sceneIds: List<String>, startAt: Int) {
        ids = sceneIds.toList()
        index = startAt.coerceIn(-1, ids.size)
    }

    @Synchronized
    fun next(): String? = if (index + 1 < ids.size) ids[++index] else null.also { clearInternal() }

    @Synchronized
    fun clear() = clearInternal()

    private fun clearInternal() { ids = emptyList(); index = -1 }
}
```

- [ ] **Step 3: Tests pass. Commit** — `feat: add PlaybackQueue for sequential playback`

### Task 7.2: Auto-advance in the player

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt`

- [ ] **Step 1: Advance on STATE_ENDED.** Add a `Player.Listener` in `initPlayer()`:

```kotlin
exo.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
        if (state != Player.STATE_ENDED) return
        savePlayback()                       // finishes current scene (clears resume, counts play)
        val nextId = PlaybackQueue.next() ?: run { finish(); return }
        sceneId = nextId
        resumePositionMs = 0L
        playCountSent = false
        startTimeMs = System.currentTimeMillis()
        releasePlayer()
        initPlayer()
    }
})
```

Caveat to handle: when advancing, the next scene's captions/markers aren't in the intent. Fetch them inline — `lifecycleScope.launch { app.api.findSceneFull(nextId) }` before `initPlayer()`, storing captions/markers in fields that `buildMediaItem()`/`showChapters()` read (refactor those to read fields populated either from the intent on first launch or from the fetch on advance). On fetch failure, play without captions/markers rather than aborting.

`PlaybackQueue.clear()` in `onStop` ONLY when the user backs out (`isFinishing == true`), so HDMI-CEC screen-off doesn't kill the queue but back does.

- [ ] **Step 2: Commit** — `feat: auto-advance playback through the queue`

### Task 7.3: "Play All" entry points

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/FolderBrowseActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/EntityScenesActivity.kt` (fragment)
- Modify: `app/src/main/java/com/sinema/ui/SceneGridFragment.kt`

- [ ] **Step 1: Add a queue launcher to the base fragment:**

```kotlin
protected fun playAll(scenes: List<Scene>, startAt: Int = 0) {
    if (scenes.isEmpty()) return
    PlaybackQueue.start(scenes.map { it.id }, startAt)
    val first = scenes[startAt]
    val intent = Intent(requireContext(), PlaybackActivity::class.java)
    intent.putExtra("scene_id", first.id)
    intent.putExtra("resume_position_ms", 0L)
    startActivity(intent)
}
```

- [ ] **Step 2: Surface it.** In the MENU dialog for sortable grids, switch from the plain sort dialog to a two-item action dialog ("Sort by…", "▶ Play All") — extend `SortDialog` into `GridMenuDialog.show(context, current, onSort, onPlayAll)`; folder browse and entity scenes pass their currently loaded scene list to `playAll`. Cap the queue at 500 ids (`scenes.take(500)`) and Toast `"Queued first 500"` when capped — no silent truncation.

- [ ] **Step 3: Sideload + verify:** Play All in a small folder plays through and returns to the grid at the end; each finished scene shows watched; backing out mid-queue stops the queue; starting a single scene from detail is unaffected (`PlaybackQueue` inactive).

- [ ] **Step 4: Commit** — `feat: Play All from folder and entity grids`

### Task 7.4: Release v1.12.0

- [ ] Bump `versionCode = 16`, `versionName = "1.12.0"`, commit `Bump version to 1.12.0`, push/tag per release flow, verify update on TV.

### Phase 7 Close-out

- [ ] **Refactor Pass** — `PlaybackActivity` has accumulated three phases of changes; verify the field-based captions/markers refactor (Task 7.2) removed the intent-only paths instead of duplicating them.
- [ ] **Close-out** checklist (run `PlaybackQueueTest` etc.).
- [ ] **Review Gate** (before release in 7.4).

---

# Phase 8 — Android TV home-screen channels

**Goal:** Opt-in launcher integration: a Watch Next row (Continue Watching) and a "Sinema — Recently Added" channel, with deep links into the app. **Privacy-first: OFF by default** — this app has a PIN lock precisely because the content may be private; surfacing thumbnails on the shared launcher must be a deliberate user choice.

### Task 8.1: Dependency, deep link, and settings toggle

**Files:**
- Modify: `app/build.gradle.kts` (add `implementation("androidx.tvprovider:tvprovider:1.0.0")`)
- Create: `app/src/main/java/com/sinema/ui/DeepLinkActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/sinema/util/Prefs.kt` (`var channelsEnabled: Boolean`, plain prefs, default `false`)
- Modify: `app/src/main/java/com/sinema/ui/SettingsActivity.kt` (toggle row "Home screen channels" with warning subtitle "Shows thumbnails on the TV home screen, outside the PIN lock")

- [ ] **Step 1: Deep link activity** — resolves `sinema://scene/<id>`:

```kotlin
class DeepLinkActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sceneId = intent?.data?.lastPathSegment
        if (sceneId.isNullOrBlank()) { finish(); return }
        lifecycleScope.launch {
            try {
                val details = SinemaApp.instance.api.findSceneFull(sceneId)
                if (details != null) startActivity(SceneIntents.detail(this@DeepLinkActivity, details.scene))
            } catch (e: Exception) {
                Log.e("Sinema", "Deep link failed", e)
            } finally { finish() }
        }
    }
}
```

Manifest entry: `exported="true"` with an intent filter for `android.intent.action.VIEW`, categories DEFAULT + BROWSABLE, `<data android:scheme="sinema" android:host="scene" />`. **PIN interaction:** launching `SceneDetailActivity` directly would bypass the PIN; instead route through `MainActivity` when a PIN is set and unverified (`if (prefs.hasPinSet() && !app.pinVerifiedThisSession) { startActivity(Intent(this, MainActivity::class.java)); finish(); return }` before resolving) — the user lands on PIN entry, and the channel row simply doesn't deep-link past the lock.

- [ ] **Step 2: Commit** — `feat: add deep link activity and home-channels setting (default off)`

### Task 8.2: Channel sync

**Files:**
- Create: `app/src/main/java/com/sinema/util/TvChannels.kt`
- Modify: `app/src/main/java/com/sinema/ui/MainActivity.kt` (call sync after `loadContent` succeeds)
- Modify: `app/src/main/java/com/sinema/ui/PlaybackActivity.kt` (refresh Watch Next after `savePlayback`)

- [ ] **Step 1: Implement `TvChannels`** (all guarded by `prefs.channelsEnabled`; every TvProvider call wrapped in try/catch — some launchers lack the provider):
  - `syncWatchNext(context, continuePairs: List<Pair<Scene, Double>>)` — clear app's previous WatchNext rows, insert up to 10 `WatchNextProgram`s (`WATCH_NEXT_TYPE_CONTINUE`, title, duration, `lastPlaybackPositionMillis = (resume * 1000)`, intent URI `sinema://scene/<id>`).
  - `syncRecentlyAdded(context, scenes: List<Scene>)` — create-or-find one `PreviewChannel` ("Sinema — Recently Added", app icon as channel logo), replace its programs with up to 20 entries.
  - Artwork: launcher cannot send our auth headers. In apikey mode use `"$serverUrl/scene/$id/screenshot?apikey=$key"` (Stash accepts the query param). In session mode, or if the user declines, fall back to the app banner drawable. Document in a comment that the URL (incl. key) is stored in the launcher's TvProvider DB — acceptable only behind the explicit opt-in toggle.
  - Channels are user-visible only after the launcher approves them; call `TvContractCompat.requestChannelBrowsable` for the recently-added channel on first creation.
- [ ] **Step 2: Hook the sync points.** `MainFragment.loadContent` already fetches `continuePairs` and `recentScenes` — pass both to `TvChannels` (on `appScope`, fire-and-forget). After playback save, re-sync Watch Next only.
- [ ] **Step 3: Handle `ACTION_INITIALIZE_PROGRAMS`** — manifest-registered receiver calling the same sync (no-op when disabled).
- [ ] **Step 4: Sideload + verify on TV:** toggle ON → channel appears after launcher approval; Continue Watching row shows after stopping mid-scene; clicking a program with PIN set lands on PIN entry, without PIN lands on scene detail; toggle OFF → rows disappear (sync with empty lists + delete channel on disable).
- [ ] **Step 5: Commit** — `feat: optional Watch Next and Recently Added launcher channels`

### Phase 8 Close-out

- [ ] **Refactor Pass** — TvProvider plumbing stays entirely inside `TvChannels.kt`; activities only call `sync*`.
- [ ] **Close-out** checklist. Extra check: with the toggle OFF (default), zero TvProvider writes occur (verify via logcat).
- [ ] **Review Gate** — ask the reviewer explicitly to scrutinize the PIN-bypass and apikey-in-URL surfaces.

---

# Phase 9 — Multi-server profiles

**Goal:** Multiple named Stash servers; switch from Settings; existing single-server installs migrate transparently. Release v1.13.0 after this phase.

### Task 9.1: Profile model + storage + migration

**Files:**
- Create: `app/src/main/java/com/sinema/model/ServerProfile.kt`
- Modify: `app/src/main/java/com/sinema/util/Prefs.kt`
- Test: `app/src/test/java/com/sinema/util/ServerProfileTest.kt`

- [ ] **Step 1: Model:**

```kotlin
package com.sinema.model

data class ServerProfile(
    val id: String,          // UUID
    val name: String,        // user label, e.g. "Living room NAS"
    val serverUrl: String,
    val apiKey: String,
    val sessionCookie: String,
    val authMode: String,    // "apikey" | "session"
    val stashUsername: String,
    val stashPassword: String
)
```

- [ ] **Step 2: Failing test for serialization round-trip + migration logic** (pure Gson + list manipulation, no Android deps — put the list-handling in a small `ProfileCodec` object so it's unit-testable):

```kotlin
class ServerProfileTest {
    @Test
    fun `round trips profile list through json`() {
        val list = listOf(ServerProfile("u1", "A", "http://a", "k", "", "apikey", "", ""))
        assertEquals(list, ProfileCodec.fromJson(ProfileCodec.toJson(list)))
    }

    @Test
    fun `fromJson tolerates garbage`() {
        assertEquals(emptyList<ServerProfile>(), ProfileCodec.fromJson("not json"))
    }
}
```

- [ ] **Step 3: Implement `ProfileCodec`** (Gson `TypeToken`, try/catch returning `emptyList()`), then `Prefs` additions — profiles in **secure** prefs (they contain keys/passwords):

```kotlin
var profiles: List<ServerProfile>
    get() = ProfileCodec.fromJson(getSecureString("server_profiles"))
    set(value) = putSecureString("server_profiles", ProfileCodec.toJson(value))

var activeProfileId: String
    get() = prefs.getString("active_profile_id", "") ?: ""
    set(value) = prefs.edit().putString("active_profile_id", value).apply()

/** Migrate legacy single-server fields into a profile on first use. */
fun migrateToProfilesIfNeeded() {
    if (profiles.isNotEmpty() || !isConfigured) return
    val p = ServerProfile(
        id = java.util.UUID.randomUUID().toString(), name = "Default",
        serverUrl = serverUrl, apiKey = apiKey, sessionCookie = sessionCookie,
        authMode = authMode, stashUsername = stashUsername, stashPassword = stashPassword
    )
    profiles = listOf(p)
    activeProfileId = p.id
}

val activeProfile: ServerProfile?
    get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

/** Persist the active profile's fields back into the legacy accessors so all existing code keeps working. */
fun applyProfile(p: ServerProfile) {
    serverUrl = p.serverUrl; apiKey = p.apiKey; sessionCookie = p.sessionCookie
    authMode = p.authMode; stashUsername = p.stashUsername; stashPassword = p.stashPassword
    activeProfileId = p.id
}
```

Design note: keeping the legacy accessors as the live config (with profiles as the switcher behind them) means zero changes in `SinemaApi`/`SinemaApp` consumers — `applyProfile` + existing `refreshApi()` does everything. Session-cookie refreshes must write back: update `SinemaApp.configureApi`'s `onSessionRefreshed` to also persist the cookie into the active profile entry.

- [ ] **Step 4: Call `migrateToProfilesIfNeeded()`** in `SinemaApp.onCreate` after `prefs` init. Tests pass. Commit — `feat: server profiles with legacy single-server migration`

### Task 9.2: Settings UI — list, add, switch, delete

**Files:**
- Modify: `app/src/main/java/com/sinema/ui/SettingsActivity.kt`
- Modify: `app/src/main/java/com/sinema/ui/SetupActivity.kt`

- [ ] **Step 1: Read both files fully before editing** (SetupActivity is 864 lines; find its save path — the code that writes `prefs.serverUrl`/`apiKey`/etc. on success). Add a "Servers" section to Settings: one focusable row per profile (`name — serverUrl`, "✓ active" marker) plus "+ Add server".
- [ ] **Step 2: Switch:** clicking an inactive profile → confirm dialog → `prefs.applyProfile(p); app.refreshApi()` → restart task (`Intent(this, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)`), so every screen reloads against the new server.
- [ ] **Step 3: Add:** launch `SetupActivity` with `putExtra("add_profile", true)`. In SetupActivity's success path: when that extra is set, wrap the just-entered credentials into a new `ServerProfile` (name = host portion of the URL, editable later), append to `prefs.profiles`, `applyProfile` it, and return to Settings instead of Main. The normal first-run path also creates a profile (it already will, via migration on next launch — but create it explicitly for cleanliness).
- [ ] **Step 4: Delete:** long-press (or a "Remove" sub-option dialog) on a profile row → confirm → remove from list; refuse to delete the last remaining profile; if the active one was deleted, `applyProfile` the first remaining and restart as in Step 2. **Rename:** same dialog offers "Rename" with a text input.
- [ ] **Step 5: Sideload + verify:** existing install migrates silently (config intact after update); add a second server (can be the same Stash with a different name for testing); switch back and forth — home rows change, playback works on both, PIN unaffected; delete the test profile; kill/restart the app — active profile persists; session-mode profile survives a cookie refresh.
- [ ] **Step 6: Commit** — `feat: add, switch, rename, and delete server profiles in Settings`

### Task 9.3: Release v1.13.0

- [ ] Bump `versionCode = 17`, `versionName = "1.13.0"`, commit `Bump version to 1.13.0`, push/tag, verify in-place update on TV **and that the update preserves the migrated profiles**.

### Phase 9 Close-out

- [ ] **Refactor Pass** — `SettingsActivity` (~245 lines pre-phase) likely doubles; if it exceeds ~400 lines, extract `ServerListSection` into its own file.
- [ ] **Close-out** checklist.
- [ ] **Review Gate** — ask the reviewer explicitly to check: secrets never land in plain prefs, migration idempotency, and the cookie-refresh write-back path.

---

# Phase 10 — Final hardening & docs

- [ ] **Task 10.1: Full regression sweep on the TV** — one sitting, both auth modes where relevant: setup wizard (all 3 paths), PIN lock, home rows, search + sort, favorites + sort, folder browse (top, deep, images, Play All), tags/performers/studios browse → entity scenes → detail chips, playback (resume, captions, tracks, speed, chapters, queue auto-advance, watched bookkeeping), channels toggle on/off + deep link + PIN interaction, profile add/switch/delete, Web Setup server still works, update check.
- [ ] **Task 10.2: Docs** — README feature list updated (tags/performers/studios, subtitles, chapters, Play All, channels, multi-server); CONTRIBUTING gains one paragraph pointing new code at `SceneIntents`, `SceneGridFragment`, `findScenesInternal`, `TimeFormat`.
- [ ] **Task 10.3: Cross-roadmap de-dup audit** — repo-wide pass with fresh eyes:

```bash
grep -rn 'putExtra("scene_' app/src/main/java/        # only SceneIntents.kt
grep -rn 'VerticalGridPresenter' app/src/main/java/   # only SceneGridFragment.kt
grep -rn 'findScenes(filter' app/src/main/java/       # only findScenesInternal + findContinuePlaying + findScenesByIds
grep -rn '"%d:%02d' app/src/main/java/                # only TimeFormat.kt
```

  Fix any stragglers; also delete now-dead `Prefs` legacy code if unused (`getFavorites`/`setFavorite`/`getRecentlyWatched`/`getResumePosition` family predate server-side state — remove only the ones `grep` proves unreferenced).
- [ ] **Task 10.4: Final Review Gate** over the whole roadmap diff (`git diff <pre-roadmap-tag>..HEAD -- app/`) — since this diff is large, run TWO passes: one prompted for correctness/crashes/auth, one prompted for duplication/architecture drift. Both must reach `VERDICT approved`.
- [ ] **Task 10.5: Close out** — update this PLAN.md's checkboxes to all-done, final commit `docs: complete feature roadmap (PLAN.md)`.

---

## Risk Register (read before starting any phase)

| Risk | Phase | Mitigation |
|---|---|---|
| Stash GraphQL field/sort names differ across server versions (`scenes_count`, `captions`, `scene_markers`) | 1 | Curl-verify every new query against the live server (Tasks 1.3/1.4) before writing UI |
| G08 remote lacks MENU key | 2, 5, 6 | On-device check is an explicit step; fallback designs specified in-place |
| Media3 1.2.1 default controller not D-pad friendly | 5 | Fallback dialog path specified in Task 5.2 Step 2 |
| Launcher artwork can't authenticate | 8 | apikey query param behind explicit opt-in; banner fallback in session mode |
| Channels leak private content past the PIN | 8 | Default OFF, warning text, deep links route through PIN gate |
| Profile migration corrupts existing installs | 9 | Migration is additive (legacy fields stay authoritative); idempotent; tested over an in-place update on the real TV |
| `opencode` review silently never runs (empty output) | all | Conventions: empty `/tmp/oc.out` = rerun, never a pass |
