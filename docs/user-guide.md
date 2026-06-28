# User Guide

This guide explains the browsing and library features in Sinema.

## Main screen

The top shortcut row contains:

- Search
- Favorites
- Browse Folders
- Tags
- Performers
- Studios
- Log Out, only when a PIN is set

The content rows are loaded from Stash:

- Continue Playing: scenes with `resume_time` greater than zero.
- Recently Played: scenes with a play count and no active resume time, sorted by last played.
- Recently Added: recently created scenes in Stash.
- Favorites: rated scenes, where `rating100` is greater than zero.

The bottom row contains:

- Refresh: reloads the home screen rows.
- Settings: opens server, profile, PIN, channel, and update settings.
- About: shows the installed Sinema version and can manually check for updates.

Sinema refreshes the home screen when you return to it, so changes made in playback or scene detail screens are reflected quickly.

## Search

Open `Search` from the main screen.

- Start typing with the Android TV keyboard or a connected keyboard.
- Sinema searches after at least two characters.
- Results are loaded from Stash and shown as scene cards.
- Select a result to open its scene detail screen.
- Press Menu, if available on your remote, to choose a sort order.

The selected search sort is saved and reused later.

## Favorites

Favorites are backed by Stash ratings:

- A scene is a favorite when its Stash `rating100` value is greater than zero.
- Toggling favorite on a scene sets the rating to `100`.
- Toggling favorite off clears the rating.

Ways to use favorites:

- Open `Favorites` from the main screen to see favorite scenes.
- Use the Favorite button on a scene detail screen.
- Look for the heart overlay on scene and folder cards.
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

Sinema currently loads up to 200 tags, performers, or studios per entity grid, sorted by scene count descending.

Selecting an entity opens the scenes for that entity. Entity scene grids are sortable and support Play All through the Menu key.

Scene detail screens also show metadata chips:

- Studio chips open that studio's scenes.
- Tag chips open that tag's scenes.
- Performer chips open that performer's scenes.

## Scene detail

Selecting a scene opens its detail screen.

The detail screen shows:

- Screenshot.
- File name.
- Duration.
- File size.
- Resolution.
- Full path.
- Date, studio, rating, and chapter count when available.
- Studio, tag, and performer chips when available.
- A small `More from this folder` row when related scenes are found.

Available actions:

- Resume: appears only when Stash has a resume position greater than five seconds.
- Play: starts from the beginning.
- Favorite/Unfavorite: updates the Stash rating.
- Mark Watched: clears resume time and increments the Stash play count.
- Mark Unwatched: resets the Stash play count.
- Browse Folder: opens the folder containing the scene.

## Sorting

Sortable screens listen for the remote's Menu key.

Sortable screens include:

- Search results.
- Favorites.
- Folder scene views.
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

## Play All

Press Menu on folder, favorite, or entity scene grids to open the grid menu, then choose Play All.

Play All behavior:

- Queues the visible scenes from the current grid.
- Does not include scenes hidden inside unopened subfolders.
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
