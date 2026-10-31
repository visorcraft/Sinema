# Playback Guide

Sinema uses Media3 ExoPlayer for video playback and reads stream, caption, marker, resume, and activity data from Stash.

## Starting playback

You can start playback from:

- A scene detail screen with Play.
- A scene detail screen with Resume.
- Play All from a folder, favorites, or entity scene grid.

Play starts the Stash scene stream from the beginning. Resume starts from the saved Stash resume position.

## Resume and watch tracking

Sinema saves playback activity back to Stash when playback pauses or stops.

Rules:

- If you watched less than five seconds, Sinema does not save a resume position.
- If playback stops more than 30 seconds before the end, Sinema saves `resume_time`.
- If playback stops within the last 30 seconds, Sinema clears `resume_time`.
- If a scene finishes and at least five seconds were played, Sinema increments the Stash play count once for that viewing.

These Stash fields drive the home screen:

- Continue Playing uses scenes with active resume time.
- Recently Played uses scenes with play count and no active resume time.
- Watched checkmarks use play count greater than zero.

## Player controls

Standard Android TV media controls come from ExoPlayer's `PlayerView`.

Common controls:

- Select or OK: show controls or activate the focused control.
- Play/Pause: pause or resume.
- Back: leave playback and save progress.
- Seek controls: use the default player controls exposed by your Android TV device.

## Subtitles

Sinema reads Stash scene captions and attaches them to the media item before playback.

Supported caption types:

- SRT.
- VTT.
- ASS/SSA, when ExoPlayer can render them.

Use the CC button in the player controls to enable, disable, or switch subtitle tracks.

Subtitle requests use the same media auth headers as streams, so they work with both API-key and session sign-in modes.

## Audio tracks and playback speed

The default ExoPlayer controller provides the settings menu for:

- Audio track selection, when the stream exposes multiple audio tracks.
- Playback speed.

Availability depends on the media file and Android TV system player UI.

## Aspect ratio

Use the aspect-ratio button next to the Settings gear to choose:

- Auto (source), which uses the video geometry reported by ExoPlayer.
- 16:9.
- 4:3.
- 9:16 for portrait video.

Every option fits the selected shape on screen without cropping, with black bars filling unused space. The choice applies immediately and is reused for later videos until you change it.

## Stream quality

Press Menu during playback and choose `Stream quality` to override how Sinema streams from Stash:

- Auto (direct with fallback): the default — play the original file directly, and if the TV cannot decode it, retry via a compatible 480p transcode.
- Direct stream only: never transcode. Useful when you want the untouched file and accept that some codecs may not display.
- Transcoded 480p / 720p / 1080p: always start on Stash's aligned HLS transcode at that resolution. Useful for slow servers or weak Wi-Fi.

The choice applies immediately (playback resumes at the same position) and is remembered for later videos. With Auto, the player also falls back from a failed transcode to the direct stream.

## Scene markers as chapters

Sinema reads Stash scene markers and exposes them during playback.

Controls:

- Menu: open the chapter list.
- Media Next: jump to the next marker.
- Media Previous: jump to the previous marker.
- D-pad Right while the player surface is focused and controls are visible: jump to the next marker.
- D-pad Left while the player surface is focused and controls are visible: jump to the previous marker.
- When a playback button is focused, D-pad Left and Right move between buttons instead.

The chapter list label uses the marker title. If the title is blank, Sinema uses the marker's primary tag name.

If a scene has no markers, the chapter controls do nothing.

## Play All and autoplay

Play All creates an in-process queue from the visible scenes on the current grid.

Behavior:

- The queue starts with the first visible scene.
- When playback reaches the end, Sinema saves activity for the finished scene.
- Sinema loads captions and markers for the next queued scene.
- Playback continues automatically.
- When the queue finishes, playback closes.
- Finishing playback, such as pressing Back out of the player, clears the queue.

The queue is intentionally temporary. It is not saved across process death or device reboot.

## Allwinner compatibility streaming

For source dimensions that trigger the HY450 Max Allwinner AVC decoder bug, Sinema automatically requests Stash HLS with `resolution=STANDARD&align=16x8`. A Stash build with aligned-HLS support transcodes to H.264 and pads width to a multiple of 16 and height to a multiple of 8, allowing hardware decoding. Other videos continue direct playback.

Aligned HLS uses CPU transcoding on the Stash server. If the request fails, Sinema retries once with the original direct stream. If a stock Stash server returns unaligned HLS, Sinema retains software decoding and TextureView rather than showing a black screen.

Dimension-compatible originals still start with direct playback. If direct playback has been requested but produces no video frame for five seconds, Sinema retries once with aligned HLS at the current position. This covers decoder failures that stall before ExoPlayer reports ready or playing, as well as failures that do not raise an ExoPlayer error. Each fallback direction happens at most once per scene, preventing retry loops.

## Android TV Watch Next sync

When Android TV channels are enabled, saving playback can refresh Sinema's Watch Next entries with the latest Continue Playing list.

See [Settings and privacy](settings-and-privacy.md) for details about launcher channels and privacy behavior.
