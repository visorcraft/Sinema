# User Guide

This guide explains the browsing and library features in Sinema.

## Main screen

When a scene is partially watched, a hero banner sits at the very top: a large backdrop for the newest Continue Playing scene with its title, details, and a one-click `RESUME` (or `PLAY`) action.

By default, the Browse row below Quick actions contains:

- Search
- Surprise Me: plays a random scene from your library right away.
- Favorites
- Browse Folders
- Groups
- Galleries
- Tags
- Performers
- Studios
- Markers
- Stats
- Saved Filters

The content rows are loaded from Stash:

- Continue Playing: scenes with `resume_time` greater than zero.
- Recently Played: scenes with a play count and no active resume time, sorted by last played. Sinema fetches the complete filtered result once per Home load to order missing timestamps safely, returns at most 25 scenes, and shares that result with Because You Watched.
- Recently Added: recently created scenes in Stash.
- Favorites: rated scenes, where `rating100` is greater than zero.

Scene cards show at a glance:

- A heart when the scene is a favorite.
- A checkmark when the scene has been played.
- A thin progress bar along the bottom edge when a scene is partially watched.

The Quick actions row sits above Browse and contains Log Out (only when a PIN is set), Refresh, Settings, and About. Log Out is always the left-most command and exits immediately without changing the configured PIN. Refresh reloads the home rows, Settings opens preferences, and About shows version and update information.

Pressing Back on Home opens **Exit Sinema?** with two choices. **Minimize** backgrounds Sinema and preserves the current session, subject to the normal Auto-Lock timeout. **Logout & Exit** clears current-session verification and closes Sinema without changing the configured PIN. Dismissing the dialog keeps Sinema open.

When no hero or retry action is shown, Home starts focus on the first visible Browse item. Down from the hero or Up from Browse enters Quick actions at Refresh, leaving Log Out one intentional Left press away.

Sinema refreshes the home screen when you return to it, so changes made in playback or scene detail screens are reflected quickly. Existing cards stay visible while rows refresh, and row headers show loading or refresh status. If a refresh fails, Sinema keeps the last loaded cards and marks that row as `Refresh failed`; if the first load fails, the row is marked `Unavailable`.

### Personalize home

Open `Settings` → `Home` → `Home Settings` to:

- Reorder, show, or hide shortcuts and content rows.
- Choose compact or large scene artwork and show or hide the hero banner.
- Group secondary shortcuts under `Library`, keeping Search, Surprise Me, and Favorites on home.
- Enable Unwatched Favorites, Recently Released, Groups, Because You Watched, and Unwatched from Favorite Performers rows.
- Choose one Stash saved filter to pin as a home row.

Every non-empty content row ends with `View All`. Scene grids provide sorting and filtering where applicable; Groups opens the full Group browser, and Because You Watched opens its bounded recommendation grid. Changes apply when you return home.

### Tonight's Mix

The `🌙 Tonight's Mix` card on the shortcut row builds a ~30-minute queue from your unwatched favorites (falling back to recent unwatched scenes) and starts playback immediately. The selection is reshuffled every run.

### Privacy

`Settings` → `Privacy` exposes (Incognito also toggles from the playback menu):

- **PIN Lock** with **Auto-Lock** (never / 5 / 15 / 30 minutes idle) — Sinema re-asks for the PIN after the selected idle time.
- **Decoy PIN** — entering it unlocks a blank "No content" shell instead of your library; Back from the shell backgrounds the app. Set a different PIN than your real one.
- **Discreet Mode** — Scene, Group, Gallery, Entity, Folder, Image, and Marker cards show neutral labels (for example `Scene 4271`), no overlays, and no artwork until focused. Focus reveals the normal card; navigation cards such as View All keep their labels, and the home hero banner is hidden. A passing glance reveals no content metadata.
- **Incognito (pause watch history)** — stops writing resume positions, play counts, and play durations to Stash and skips Watch Next syncing and search-history recording. Server data stays untouched from this device.
- **Block Screenshots** — sets `FLAG_SECURE` on every screen, blocking screenshots and recents thumbnails.

### Backup and restore

`Settings` → `System` → `Export Settings Backup` writes a `sinema-backup.json` file with every setting and your server profiles. **The file contains credentials** — store it safely. `Import Settings Backup` restores it after confirmation and immediately reconciles Global Search, launcher channels, and the active server; no restart is required.

### Ambient gallery

Enable `Ambient Gallery` in `Settings` → `Home` and leave the home screen idle for five minutes: a slow Ken Burns slideshow of your top-rated scenes takes over with a clock in the corner. Any key or touch exits. It never starts while a PIN is unverified or while Discreet Mode is on, and it respects Block Screenshots.

### Finish Watching

`Library` → `⏳ Finish Watching` lists every partially-watched scene with its progress bar. Long-press for mark-watched / remove-from-continue; the grid menu offers bulk actions and Play All. Scenes within 30 seconds of the end are treated as finished and hidden.

### Untagged scenes

`Library` → `🧹 Untagged Scenes` lists scenes with no tags and no performers — the curation backlog. Long-press a card and choose `🏷 Assign tags` to apply existing tags (multi-select); the scene leaves the grid once tagged. Requires a Stash version that supports null filters (≥ 0.26); older servers show an error.

### Server dashboard

`Library` → `🖥 Server` shows the Stash version, library totals, play totals, running Stash jobs (scan/generate/clean when your server exposes the job queue), and the most recent errors Sinema recorded.

### Jumping big grids

In View All grids, press Menu → `🔤 Jump to letter…` and pick a letter (or `0-9`). Title sorts filter by scene title; other sorts filter by file path. `All` clears the filter, which combines with sorting, filtering, and paging. Title A–Z and Z–A are available alongside the existing path-based Name sorts.

Page Up and Page Down scroll one visible screen at a time. Menu → `↑ Back to top` returns to the first card. While scrolling, a temporary indicator shows the visible result range and server total.

Grids show inline loading and empty-state messages. A failed first load offers a D-pad-accessible `Retry`; failed refreshes or page loads keep the scenes already on screen. Search, Folder Browser, scene details, and Server Dashboard use the same recovery pattern. Errors explain likely network, authentication, certificate, or server-address problems.

`Settings` → `Accessibility` offers larger interface text, a high-visibility focus outline, reduced motion, and the built-in Remote Control Guide. TalkBack labels cover focused controls, ratings, queue actions, favorite/watched state, and progress. The guide documents D-pad, Menu, long-press, paging, playback, queues, images, and accessibility controls.

### Favorite entities

Long-press a performer, tag, or studio card in their browser grids to favorite or unfavorite it (heart overlay; same `rating100` rule as scenes). Requires a Stash whose entity updates accept ratings; older servers show an error.

## Search

Open `Search` from the main screen.

- Start typing with the Android TV keyboard or a connected keyboard.
- If your remote has a microphone, select the mic button in the search bar to search by voice. The first use asks for microphone permission; it is only used while searching.
- Sinema searches after at least two characters.
- Results are grouped into Scenes, Tags, Performers, Studios, and Groups with their Stash result counts.
- Select a scene to open its details, an entity to browse its scenes, or a Group to open its ordered scenes.
- With an empty query, the last ten searches that produced results appear as a `Recent searches` row — select one to run it again, or `Clear` to remove them all.
- Use the on-screen Sort button to choose a scene sort order.
- Use Filter to show all, watched, or unwatched scenes and optionally limit scene results to favorites. Filters do not change entity results.

The selected search sort and filters are saved and reused later. Filters also apply to folder and entity scene views.

## Saved Filters

Open `Saved Filters` from the main screen to browse scene filters stored on the Stash server.

- Selecting a saved filter opens a scene grid driven by the filter's own criteria, sort, and direction — exactly as Stash would run it.
- Sort and Play All work inside the filtered grid like any other grid.
- Create a filter from any scene grid's Filter dialog with `Save as Stash filter…`. The current criteria and sort are saved under a name you choose. Relative release-date choices are stored as the concrete date calculated at save time, matching Stash's saved-filter model.
- Long-press OK on a saved-filter card to rename or delete it. Deletion asks for confirmation and never deletes scenes.
- Filters remain editable in the Stash web UI and appear here on the next visit.

## Markers

Open `Markers` from the main screen to browse every scene marker ("chapter") on the server in one grid.

- Cards show the marker's title (or its primary tag when untitled), the scene, the timestamp, and the scene screenshot at that moment.
- Selecting a marker jumps straight into playback at the marker's timestamp.
- Press Menu (or the Filter button) to filter markers by tag, and choose a sort order: Recently added, Title A–Z, or Random. The sort choice is remembered.
- The Search button searches marker and scene titles; `Clear` removes the search. Tag filter and search combine.

## Stats

Open `Stats` from the main screen for a library overview:

- Server totals: total watch time, total plays, scenes, scenes played, performers, tags, and studios.
- Most played scenes.
- Most watched performers, tags, and studios, aggregated from the 200 most-played scenes (a scene played three times contributes three plays to its performers, tags, and studio). Rows are labeled accordingly; they are estimates of library-wide rankings, not exact totals.

Select any scene or entity card to jump to its details or its scene grid.

## Favorites

Favorites are backed by Stash ratings:

- A scene is a favorite when its Stash `rating100` value is greater than zero.
- Rating a scene 1–5 stars sets `rating100` to stars × 20.
- Clearing the rating clears the favorite.

Ways to use favorites:

- Open `Favorites` from the main screen to see favorite scenes.
- Rate from the star row on a scene detail screen, or press Menu during playback and choose `★ Rate scene`.
- Look for the heart overlay on scene and folder cards, and the star count on scene cards.
- Folders show a heart when they contain at least one favorited scene.

Press Menu on the Favorites screen to sort or play all visible favorite scenes.

## Browse folders

Open `Browse Folders` from the main screen.

Sinema treats `/data` as the Stash media root:

- The root folder screen lists top-level folders under `/data`.
- It also lists loose video files and pictures directly under `/data`.
- Folder cards are shown immediately by name, then count, thumbnail, and favorite state fill in as Sinema loads them.

When you open a folder, Sinema shows immediate children:

- Subfolders first.
- Video files next.
- Picture files last.

Selecting an item:

- Folder: opens that folder.
- Video: opens the scene detail screen.
- Picture: opens the full-screen image viewer.

Large folders are capped at the first 10,000 videos/pictures shown in the folder view. Sinema shows a toast when a folder is truncated.

## Galleries

Open `Galleries` from the main screen or Library to browse Stash Galleries. Search by name or sort by image count, title, update time, or release date. Cards show cover artwork and image counts.

Selecting a Gallery opens its cover, image count, available date/photographer/studio metadata, synopsis, and image grid. Select any image for full-screen viewing, or choose `Slideshow` to load the Gallery and start automatic playback. Gallery loading is capped at 10,000 images; Sinema shows a toast when it truncates the list.

## Images

Pictures in folder views and Galleries open in Sinema's full-screen image viewer.

Controls:

- D-pad Right or Down: next image; Left or Up: previous image.
- OK: show or hide the caption and viewer status.
- Menu: start/pause a slideshow, cycle 3/5/10/30-second delays, toggle shuffle, switch fit/fill, zoom in/out, or reset zoom.
- While zoomed: D-pad directions pan the image; Plus/Minus or zoom keys also adjust zoom.
- Media Next/Previous and fast-forward/rewind navigate; Play/Pause toggles the slideshow.
- Back: return to the previous screen.

The caption shows the image title or filename and position. Fit shows the whole image; Fill crops to cover the TV screen. Shuffle preserves every image exactly once and keeps the current image in place when toggled.

## Groups

Open `Groups` from the main screen or Library to browse Stash Groups (called Movies on Stash 0.24). Sinema automatically uses the legacy Movie API when Groups are unavailable.

The Group grid supports name search, sorting, and incremental loading. Cards show front artwork and scene counts. Selecting a Group opens its artwork, scene count, available date/director/studio metadata, synopsis, and scenes in Stash's Group order.

Playback actions:

- Press Menu for `Play All` or `Shuffle Play`.
- Select `Resume Group` to start at the first partially watched scene and its saved position. If no scene is partial, it selects the first unwatched scene; if all scenes are watched, it starts at the beginning.
- Both actions queue the remaining Group scenes, subject to the normal 500-scene queue cap.

## Related discovery

Scene details show up to six `More Like This` cards ranked by shared performers, studio, and tags. Recommendations are deterministic and exclude the current scene. When no metadata-based recommendations are available, Sinema falls back to scenes from the same folder.

Optional Home Settings rows provide:

- `Because You Watched`: recommendations based on the most recently completed scene, with a `View All` grid. Partially watched scenes remain in Continue Playing.
- `Unwatched from Favorite Performers`: unwatched scenes featuring performers marked as favorites in Stash. Manage performer favorites in the Stash web UI; this row also supports sorting and filtering in its full grid.

Metadata recommendations use at most three bounded Stash queries (performers, studio, and tags), then rank results locally. No external recommendation service or library-wide scan is used.

## Tags, performers, and studios

Open `Tags`, `Performers`, or `Studios` from the main screen.

Entity grids show:

- Name.
- Scene count.
- Entity image when Stash provides one.

Use Search to filter the current entity type by name. Sort offers scene count, Name A-Z, and Name Z-A. Sinema loads 100 entities at a time and appends more as you scroll near the end.

Selecting an entity opens its scenes with a detail header — entity image, name, scene count, and for performers their tag chips (selecting a tag chip opens that tag's scenes). The header also carries a Favorite toggle and `▶ Play unwatched`, which shuffle-queues the unwatched scenes currently loaded. Entity scene grids load incrementally and provide Sort and Filter controls. Play All is available through the Menu key.

Scene detail screens also show metadata chips:

- Studio chips open that studio's scenes.
- Tag chips open that tag's scenes.
- Performer chips open that performer's scenes.

## Scene detail

Selecting a scene opens its detail screen.

The detail screen shows:

- Screenshot and friendly Stash title, falling back to the filename when no title is set.
- Duration and resolution.
- Date, studio, rating, and chapter count when available.
- Synopsis and studio, tag, and performer chips when available.
- A `More Like This` row ranked from shared metadata, or `More from this folder` when no metadata recommendations are available.

Select Technical details to show or hide the filename, full path, file size, duration, and resolution.

Available actions:

- Resume: appears only when Stash has a resume position greater than five seconds.
- Play: starts from the beginning.
- Rating: pick 1–5 stars, or Clear. Any rating above zero marks the scene as a favorite in Stash (`rating100` = stars × 20); Clear removes the rating and the favorite.
- Mark Watched: clears resume time and increments the Stash play count.
- Mark Unwatched: resets the Stash play count.
- Browse Folder: opens the folder containing the scene.

## Sorting

Sortable screens provide an on-screen Sort button and also listen for the remote's Menu key.

Sortable screens include:

- Search results.
- Favorites.
- Folder scene views.
- Tag, performer, and studio grids.
- Entity scene views.

Sort choices:

- Name A-Z.
- Name Z-A.
- Recently added.
- Recently updated.
- Recently played.
- Release date.
- Longest first.
- Highest rated.
- Most played.
- Random.

Sort choices are saved per screen. Random uses a stable random seed for that screen instance so paging and reloads remain consistent within the current view.

In folder views, sorting applies to scene queries. Folder and picture ordering stays folder-oriented.

## Filtering

Scene grids (search results, favorites, folder views, and entity scene views) provide a Filter button with the same Menu-key access as Sort. Filter choices:

- Watched: All scenes, Watched, or Unwatched.
- Favorites only.
- Minimum rating: any, or ★ through ★★★★★.
- Resolution: any, under 720p, 720p or better, 1080p or better, or 4K or better.
- Markers: any, with, or without.
- Captions: any, with, or without.
- Duration: under 10 minutes, 10–30, 30–60, or over 60 minutes.
- Release date: last 30 days, last year, or over a year old. Relative dates are evaluated when the query runs.
- Video codec: H.264, HEVC, VP9, or AV1.
- Performers, Studios, and Tags: search and select multiple entities, then include any selected entity or exclude all selected entities.

Filters combine with each other, the screen's existing scope, and the current sort, and are saved per screen. The Filter button label always shows the active filter summary. At the top of the dialog, select an active-filter chip to remove that criterion, or choose `Clear all` to reset every dimension. `Save as Stash filter…` stores the working criteria and current sort on the server without changing the grid until you choose Apply.

For the two most-used filters there are quick toggles next to Filter on the same control bar: a watch button cycling All scenes → Unwatched → Watched, and a Favorites on/off button. They change the same saved filter state as the Filter dialog. While a minimum-star filter is active the Favorites button shows on (stars imply favorites); switching it off clears the whole rating filter, stars included.

## Rating scenes

Rate a scene 1–5 stars on its detail screen, or press Menu during playback and choose `★ Rate scene`. Stars map to Stash's `rating100` (stars × 20), so ratings are shared with other Stash clients. A scene with any rating counts as a favorite; clearing the rating clears the favorite. The detail screen also has a one-tap `♡ Favorite` button (sets ★★★★★) — tapping `♥ Favorited ✓` clears the rating and removes the favorite. Scene cards show their star count next to the duration.

## Play All

Press Menu on folder, favorite, or entity scene grids to open the grid menu, then choose Play All. Scene-grid menus also offer bulk curation: `👁 Mark All Watched` and `★ Rate All…` (0–5 stars or clear) apply to every scene currently loaded in the grid.

Play All behavior:

- Queues the currently loaded scenes from the current grid, respecting active scene filters.
- Does not include scenes hidden inside unopened subfolders or entity results that have not loaded yet.
- Starts playback from the first visible scene.
- Advances automatically when a scene ends.
- Caps the queue at 500 scenes and shows a toast when it truncates the queue.
- Saves a local, profile-scoped session snapshot so Home can explicitly restore the scene, local position, and queue after an app restart. Sinema uses encrypted preferences when available, with the existing fallback to regular preferences. Incognito clears and suppresses these snapshots.

`Shuffle Play` in the same menu queues the loaded scenes in random order.

## Card actions

Long-press OK on a focused scene card in any grid to open per-card actions:

- `Resume` / `Play`: starts the scene, resuming saved progress when present (a prompt offers resume or start over for scenes ≥ 10 seconds in).
- `Play from here`: queues the current grid from the selected card onward.
- `Remove from Continue Playing`: clears the saved resume point (shown only for partially watched scenes).
- `Mark Watched` / `Mark Unwatched`: same semantics as the detail-screen toggle.
- `Favorite` / `Unfavorite`: sets or clears the 5-star rating that marks a favorite.
- `Details`: opens the scene detail screen.

## O counter

Count Os recorded in Stash (`o_counter`):

- Scene detail: `🔥 O +1` next to Mark Watched.
- During playback: Menu → `🔥 O +1`.
- Scene cards show an `O×n` badge next to the duration when the count is above zero.

## Deep links

Sinema can open a scene detail screen from a deep link:

```text
com.sinema://app/scene/<scene_id>
com.sinema://app/play/<scene_id>
```

Launcher channels use these link shapes. External tools can use them too, as long as they know the Stash scene ID. `/scene/` opens the detail screen; `/play/` (used by Watch Next) resumes playback directly at the saved position without a prompt.

Deep-link behavior:

- If Sinema is not configured, setup opens first.
- If a PIN is set and not verified for the current session, Sinema asks for the PIN first.
- If the scene exists, Sinema opens its detail screen.
