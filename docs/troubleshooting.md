# Troubleshooting

Use this guide when setup, browsing, playback, or launcher integration does not behave as expected.

## Setup cannot reach Stash

Check:

- The server URL includes `http://` or `https://`.
- The TV and Stash server are on the same network or otherwise routed to each other.
- You are not using `localhost` unless Stash is running on the Android device itself.
- Stash is listening on the expected port, usually `6969`.
- Firewalls or VPNs are not blocking the TV.

Example:

```text
http://192.168.1.100:6969
```

## Sign in with Stash fails

Check:

- Username and password are correct.
- Stash authentication is configured the way you expect.
- The Stash `/login` route is reachable from the TV.
- Reverse proxies are forwarding cookies correctly.

If permanent sign-in fails while session sign-in works, the Stash API-key generation mutation may be disabled or unavailable on your Stash version.

## Authentication failed after setup

Sinema returns to setup when Stash responds with an auth error.

Fixes:

- Re-enter the API key.
- Use Sign in with Stash again to refresh session credentials.
- If you chose permanent sign-in earlier, remember that generating a new Stash API key can invalidate an older key used by another client, and another client may do the same to Sinema.
- Confirm the active server profile is the one you intend to use.

## Web Setup page will not open

Check:

- The phone/computer is on the same network as the TV.
- You opened the exact URL shown on the TV, including port `8888`.
- No network isolation feature is blocking device-to-device traffic.
- The setup screen is still open on the TV. Leaving it stops the temporary web server.

## No folders appear

Sinema's folder browser expects Stash's media root to include `/data`.

Check:

- Stash has scanned the library.
- The media paths in Stash start under `/data`.
- The Android TV can query Stash GraphQL successfully.

If your Stash library uses a different root path, folder browsing may need code changes before it can show that root.

## Folder counts or thumbnails appear slowly

This is expected for large libraries. Sinema first shows folder names, then enriches cards with counts, thumbnails, and favorite state in the background.

## Search shows no results

Check:

- Enter at least two characters.
- Try a simpler file-name term.
- Confirm Stash itself can find the scene.
- Try a different sort if random or metadata sorting behaves unexpectedly.

## Subtitles do not appear

Check:

- The scene has captions in Stash.
- The captions are SRT, VTT, ASS, or SSA.
- Use the CC button during playback to select a caption track.
- Confirm the caption endpoint is reachable with the same Stash auth mode.

## Chapters do not appear

Chapters come from Stash scene markers.

Check:

- The scene has scene markers in Stash.
- Press Menu during playback to open the chapters list.
- Use media next/previous or D-pad left/right while controls are visible to jump between markers.

## Audio tracks or speed controls are missing

Audio track and playback speed controls are provided by ExoPlayer's default controller.

Check:

- The media file actually contains multiple audio tracks.
- Your Android TV build exposes the player settings menu.
- The stream format is supported by ExoPlayer on the device.

## Continue Playing does not update

Sinema only saves resume time after more than five seconds of playback.

Also note:

- Stopping within the last 30 seconds clears resume time and treats the scene as finished.
- A finished scene appears in Recently Played instead of Continue Playing.
- If Stash rejects the activity save, the home rows will not update.

## Favorites do not match expectations

Sinema uses Stash ratings for favorites.

- Favorite means `rating100 > 0`.
- Favoriting in Sinema sets `rating100` to `100`.
- Unfavoriting clears the rating.

If another Stash client changes ratings, Sinema will reflect that after refresh.

## Android TV channels do not show up

Check:

- Android TV Channels is enabled in Settings.
- The device supports Android TV launcher preview channels.
- Return to the Sinema main screen to trigger a sync.
- Some launchers require approving or pinning a new channel before it is visible.

If you use session sign-in, launcher artwork may be missing because the Android TV launcher cannot attach Sinema's session cookie when loading poster URLs.

## Update install fails

Check:

- The GitHub release has an APK asset.
- The TV can reach GitHub.
- Android allows Sinema to install unknown apps.
- There is enough free storage for the downloaded APK.

You can always install an APK manually with ADB.
