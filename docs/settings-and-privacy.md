# Settings and Privacy

Open `Settings` from the bottom row of the main screen.

## Server URL and API key

Settings shows the current Stash server URL.

To change the URL:

1. Edit `Server URL`.
2. Select `Save Settings`.

To change the API key:

1. Select `Change API Key` or `Set API Key`.
2. Edit the API key field.
3. Select `Save Settings`.

The API key field is hidden until you choose to show it.

Saving settings updates the active server profile as well as the live API client.

## Multi-server profiles

Sinema can store multiple Stash server profiles.

Each profile stores:

- Name.
- Server URL.
- API key, if using API-key auth.
- Session cookie, if using session auth.
- Auth mode.
- Stash username/password, if using session auth.

### Add a server

1. Open Settings.
2. Select `+ Add Server`.
3. Complete the normal setup wizard for the new server.

The new profile name defaults to the server host when possible.

### Switch servers

1. Open Settings.
2. Select the server profile row.
3. Confirm the switch.

Sinema applies the profile, refreshes the API client, and returns to the main screen.

### Rename a server

1. Open Settings.
2. Move to the profile's options button.
3. Choose Rename.
4. Enter the new name and save.

### Remove a server

1. Open Settings.
2. Move to the profile's options button.
3. Choose Remove.
4. Confirm removal.

Sinema will not remove the last remaining server profile. If you remove the active profile while other profiles remain, Sinema switches to the first remaining profile.

## PIN lock

The PIN lock is a local four-digit lock for Sinema.

### Set a PIN

1. Open Settings.
2. Select `Set PIN`.
3. Enter a four-digit PIN.
4. Enter the same PIN again to confirm.

After a PIN is set:

- Sinema asks for the PIN on launch until it has been verified for the current app session.
- The main screen shows `Log Out`.
- Selecting `Log Out` clears the current PIN verification and exits the app.

### Remove a PIN

1. Open Settings.
2. Select `Remove PIN`.
3. Enter the current PIN.

If verification succeeds, Sinema removes the PIN.

### PIN screen controls

- D-pad and Select can press the on-screen number buttons.
- Number keys enter digits directly.
- Backspace or Delete removes the last digit.
- Back during verification closes Sinema instead of bypassing the lock.
- The close button asks for confirmation before closing Sinema.

The PIN hash is salted and stored locally. Sinema does not send the PIN to Stash.

## Android TV launcher channels

On Android TV devices, Settings shows an Android TV Channels toggle.

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

## Updates

Sinema checks GitHub releases once per app session from the main screen.

You can also check manually:

1. Open `About` from the main screen bottom row.
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

If encrypted preferences are unavailable on the device, Sinema falls back to regular shared preferences and logs an error.

Non-sensitive values such as active profile ID, channel toggle state, sort choices, and PIN hash metadata are stored in regular shared preferences.

## Network privacy

Sinema talks directly to:

- Your configured Stash server.
- GitHub's releases API for update checks.
- GitHub release asset URLs when downloading an update.

Sinema does not use a cloud service for browsing or playback.
