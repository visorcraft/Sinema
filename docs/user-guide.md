# User Guide

This guide explains the browsing and library features in Sinema.

## Main screen

When a scene is partially watched, a hero banner sits at the very top: a large backdrop for the newest Continue Playing scene with its title, details, and a one-click `RESUME` (or `PLAY`) action.

The top shortcut row contains:

- Search
- Surprise Me: plays a random scene from your library right away.
- Favorites
- Browse Folders
- Tags
- Performers
- Studios
- Saved Filters
- Log Out, only when a PIN is set

The content rows are loaded from Stash:

- Continue Playing: scenes with `resume_time` greater than zero.
- Recently Played: scenes with a play count and no active resume time, sorted by last played.
- Recently Added: recently created scenes in Stash.
- Favorites: rated scenes, where `rating100` is greater than zero.

Scene cards show at a glance:

- A heart when the scene is a favorite.
- A checkmark when the scene has been played.
- A thin progress bar along the bottom edge when a scene is partially watched.

The bottom row contains:

- Refresh: reloads the home screen rows.
- Settings: opens server, profile, PIN, channel, and update settings.
- About: shows the installed Sinema version and can manually check for updates.

Sinema refreshes the home screen when you return to it, so changes made in playback or scene detail screens are reflected quickly.

## Search

Open `Search` from the main screen.

- Start typing with the Android TV keyboard or a connected keyboard.
- If your remote has a microphone, select the mic button in the search bar to search by voice. The first use asks for microphone permission; it is only used while searching.
- Sinema searches after at least two characters.
- Results are grouped into Scenes, Tags, Performers, and Studios with their Stash result counts.
- Select a scene to open its details, or an entity to browse its scenes.
- Use the on-screen Sort button to choose a scene sort order.
- Use Filter to show all, watched, or unwatched scenes and optionally limit scene results to favorites. Filters do not change entity results.

The selected search sort and filters are saved and reused later. Filters also apply to folder and entity scene views.

## Saved Filters

Open `Saved Filters` from the main screen to browse scene filters you saved in the Stash web UI.

- Selecting a saved filter opens a scene grid driven by the filter's own criteria, sort, and direction — exactly as Stash would run it.
- Sort and Play All work inside the filtered grid like any other grid.
- Filters are stored server-side; create or edit them in Stash and they appear here on the next visit.

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

## Images

Pictures in Stash folder views open in Sinema's full-screen image viewer.

Controls:

- D-pad Right or Down: next image.
- D-pad Left or Up: previous image.
- Back: return to the folder.

The caption at the bottom shows the file name and current position in the folder image list.

## Tags, performers, and studios

Open `Tags`, `Performers`, or `Studios` from the main screen.

Entity grids show:

- Name.
- Scene count.
- Entity image when Stash provides one.

Use Search to filter the current entity type by name. Sort offers scene count, Name A-Z, and Name Z-A. Sinema loads 100 entities at a time and appends more as you scroll near the end.

Selecting an entity opens its scenes. The entity screen starts with a detail header — entity image, name, scene count, and for performers their tag chips (selecting a tag chip opens that tag's scenes). Entity scene grids load incrementally and provide Sort and Filter controls. Play All is available through the Menu key.

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
- A small `More from this folder` row when related scenes are found.

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

Filters combine with each other and with the current sort, and are saved per screen. The Filter button label always shows the active filter summary.

## Rating scenes

Rate a scene 1–5 stars on its detail screen, or press Menu during playback and choose `★ Rate scene`. Stars map to Stash's `rating100` (stars × 20), so ratings are shared with other Stash clients. A scene with any rating counts as a favorite; clearing the rating clears the favorite. Scene cards show their star count next to the duration.

## Play All

Press Menu on folder, favorite, or entity scene grids to open the grid menu, then choose Play All.

Play All behavior:

- Queues the currently loaded scenes from the current grid, respecting active scene filters.
- Does not include scenes hidden inside unopened subfolders or entity results that have not loaded yet.
- Starts playback from the first visible scene.
- Advances automatically when a scene ends.
- Caps the queue at 500 scenes and shows a toast when it truncates the queue.
- Does not persist the queue after the app process ends.

## Deep links

Sinema can open a scene detail screen from a deep link:

```text
com.sinema://app/scene/<scene_id>
```

Launcher channels use this link shape. External tools can use it too, as long as they know the Stash scene ID.

Deep-link behavior:

- If Sinema is not configured, setup opens first.
- If a PIN is set and not verified for the current session, Sinema asks for the PIN first.
- If the scene exists, Sinema opens its detail screen.
