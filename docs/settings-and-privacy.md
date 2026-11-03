# Settings and Privacy

Open `Settings` from the Quick actions row of the main screen, then choose a category:

- **Server**: connection details and server profiles.
- **Home**: Home Settings and Ambient Gallery.
- **Playback**: stream quality, aspect ratio, seek buttons, subtitle appearance and language, and looping defaults.
- **Privacy**: PIN lock, Discreet Mode, Incognito, and screenshot blocking.
- **Accessibility**: interface text size, high-visibility focus, reduced motion, and the remote-control guide.
- **System**: Android TV Channels and Global Search.

Back returns from a category to the category list. URL and API-key edits remain available while moving between categories. If you press Back at the category list with unsaved server edits, Sinema offers **Save**, **Discard**, or **Keep editing**. API-key drafts are retained in memory during activity recreation and are not written into Android instance-state storage.

## Server URL and API key

Open `Settings` → `Server` to see the active connection.

To change the URL:

1. Edit `Server URL`.
2. Select `Save Server Settings`.

To change the API key:

1. Select `Change API Key` or `Set API Key`.
2. Edit the API key field.
3. Select `Save Server Settings`.

The API key field is hidden until you choose to show it. Saving trims surrounding whitespace, updates the active server profile and live API client, and returns to the category list.

## Multi-server profiles

Sinema can store multiple Stash server profiles under `Settings` → `Server`.

Each profile stores:

- Name.
- Server URL.
- API key, if using API-key auth.
- Session cookie, if using session auth.
- Auth mode.
- Stash username/password, if using session auth.

### Add a server

1. Open `Settings` → `Server`.
2. Select `+ Add Server`.
3. Complete the normal setup wizard for the new server.

The new profile name defaults to the server host when possible.

### Switch servers

1. Open `Settings` → `Server`.
2. Select the server profile row.
3. Confirm the switch.

Sinema applies the profile, refreshes the API client, and returns to the main screen.

### Rename a server

1. Open `Settings` → `Server`.
2. Move to the profile's options button.
3. Choose Rename.
4. Enter the new name and save.

### Remove a server

1. Open `Settings` → `Server`.
2. Move to the profile's options button.
3. Choose Remove.
4. Confirm removal.

Sinema will not remove the last remaining server profile. If you remove the active profile while other profiles remain, Sinema switches to the first remaining profile.

## Playback defaults

Open `Settings` → `Playback` to choose:

- **Stream quality**: Auto, direct only, or a transcoded resolution.
- **Aspect ratio**: Auto, 16:9, 4:3, or 9:16.
- **Seek buttons**: the rewind/forward increments used by player buttons and timeline scrubbing.
- **Subtitle size**: Small, Normal, Large, or Extra Large.
- **Subtitle background**: Off, 50%, 80%, or 100% black opacity.
- **Subtitle position**: Low, Middle, or High.
- **Preferred subtitles**: Auto or a preferred language; selects a matching track when available without forcing captions on.
- **Loop current scene**: repeat one scene instead of advancing through a queue.

These settings use the same saved choices as the player controls. Choosing a recovery stream after a playback failure is temporary and does not change these defaults.

## Accessibility and remote guidance

Open `Settings` → `Accessibility` to choose:

- **Interface text**: Default, Large (1.2×), or Extra Large (1.4×). The setting applies throughout Sinema and is saved on this device.
- **High-visibility focus**: draws a bright yellow outline around the currently focused control without changing other focus behavior.
- **Reduce motion**: disables card zoom and Ambient Gallery pan, zoom, and crossfade animations.
- **Remote Control Guide**: D-pad-scrollable help for navigation, grids, playback, queues, images, and accessibility controls.

TalkBack labels describe focused controls, ratings, queue actions, favorite/watched state, and watch progress. Decorative artwork is excluded from the accessibility tree.

## Privacy controls

Open `Settings` → `Privacy` for local privacy controls:

- **Discreet Mode** uses neutral labels and hides metadata, overlays, and artwork on Scene, Group, Gallery, Entity, Folder, Image, and Marker cards until focused. Navigation cards remain normal, and focusing a content card reveals it. Sinema does not change Stash metadata.
- **Incognito** pauses resume, play-count, Watch Next, and search-history writes from this device. Enabling it also clears the restorable playback-session snapshot and prevents new snapshots until it is disabled.
- **Block Screenshots** blocks screenshots and recents thumbnails throughout Sinema on this device.

## PIN lock

The PIN lock is a local four-digit lock for Sinema.

### Set a PIN

1. Open `Settings` → `Privacy`.
2. Select `Set PIN`.
3. Enter a four-digit PIN.
4. Enter the same PIN again to confirm.

After a PIN is set:

- Sinema asks for the PIN on launch until it has been verified for the current app session.
- The main screen shows `Log Out` as the left-most Quick actions command.
- Selecting `Log Out` clears only current-session verification and exits the app; it does not change the configured PIN.

### Remove a PIN

1. Open `Settings` → `Privacy`.
2. Select `Remove PIN`.
3. Enter the current PIN.

If verification succeeds, Sinema removes the PIN.

### PIN screen controls

- D-pad and Select can press the on-screen number buttons.
- Number keys enter digits directly.
- Backspace or Delete removes the last digit.
- Back during verification closes Sinema instead of bypassing the lock.
- The close button asks for confirmation before closing Sinema.

The PIN hash is salted and stored locally. Sinema does not send the PIN to Stash. The PIN does not lock the Stash web interface.

## Android TV launcher channels

On Android TV devices, `Settings` → `System` shows an Android TV Channels toggle.

When enabled, Sinema can publish:

- Watch Next entries for up to 10 Continue Playing scenes.
- A Recently Added launcher channel with up to 20 scenes.

These entries deep-link back into Sinema scene details.

Privacy behavior:

- Channels are opt-in.
- Disabling channels clears Sinema's Watch Next entries and Sinema's Recently Added channel.
- Sinema only deletes its own Watch Next entries.

Artwork behavior:

- API-key auth can include poster artwork URLs that Android TV can load.
- Session auth may omit launcher artwork because Android TV launcher requests cannot include Sinema's session cookie.

Channel sync happens when the main screen loads and when playback state is saved.

## Self-signed certificates

If your Stash server uses HTTPS with a self-signed certificate, Sinema can optionally trust it:

- During setup: check `Trust self-signed certificates` on the sign-in or manual setup screen. A confirmation warning is shown before continuing.
- Or later: `Settings` → `Server` → `...` on a profile → `Trust self-signed certificates` (also confirmed with a warning).
- Web Setup does not offer the checkbox; if your server needs it, enable the toggle in Settings after setup completes.
- The setting is per server profile and off by default. Profiles created later never inherit an earlier setup's choice.

What it affects while enabled: all traffic to that server only (API calls, images, streams, subtitles) skips TLS certificate and hostname verification. Other profiles stay strict.

Security note: disabling certificate verification means a man-in-the-middle on your network could impersonate your server. Only enable this on private networks you control.

## Global search

On Android TV devices, `Settings` → `System` shows a Global Search toggle (off by default).

When enabled:

- The Android TV system search dialog can query your Stash server for scenes and show them as results while you type.
- Selecting a result opens that scene in Sinema (the PIN lock, if set, still applies first).
- Searching "in Sinema" from the system dialog opens Sinema's own search with the query filled in.

Privacy behavior:

- Global search is strictly opt-in. Both search components ship disabled; the toggle enables or disables them together.
- When disabled, the system search dialog cannot query Sinema at all — no requests are made and no titles leave the server.
- While enabled, only the system search client can query Sinema. The suggestion provider requires Android's signature-level `GLOBAL_SEARCH` permission, so other installed apps are blocked from enumerating your library even when the feature is on.
- While enabled, each keystroke in the system search dialog can trigger a query to your Stash server (title, filename, and metadata matching), like Sinema's in-app search.
- Sinema returns no new suggestions while a configured PIN is unverified or a decoy session is active, and makes no Stash request for those queries. Android TV may retain results it cached before Sinema was locked.
- Suggestions never embed credentials. Card artwork only renders when your Stash server allows unauthenticated media access; otherwise results appear without images.

## Updates

Sinema checks GitHub releases once per app session from the main screen.

You can also check manually:

1. Select `About` from the main screen Quick actions row.
2. Select `Check for Updates`.

If a newer GitHub release has an APK asset, Sinema can download it and open Android's installer.

Your TV may ask for permission to install unknown apps from Sinema before the update can be installed.

## Local storage

Sinema stores settings on the Android device.

Sensitive values are stored in `EncryptedSharedPreferences` when AndroidX Security Crypto can create a master key:

- API keys.
- Session cookies.
- Stash username/password for session sign-in.
- Server profiles.
- Interrupted playback-session details: scene title, local position, queue, queue index, and profile association.

If encrypted preferences are unavailable on the device, Sinema falls back to regular shared preferences and logs an error.

Non-sensitive values such as active profile ID, channel toggle state, sort choices, and PIN hash metadata are stored in regular shared preferences.

## Network privacy

Sinema talks directly to:

- Your configured Stash server.
- GitHub's releases API for update checks.
- GitHub release asset URLs when downloading an update.

Sinema does not use a cloud service for browsing or playback.
