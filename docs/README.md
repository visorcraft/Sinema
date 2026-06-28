# Sinema Documentation

This directory contains user and maintainer documentation for Sinema, an Android TV client for a self-hosted Stash server.

## Start here

- [Getting started](getting-started.md): prerequisites, building, installing, and first-run setup.
- [User guide](user-guide.md): home screen, searching, folders, favorites, metadata browsing, scene details, images, sorting, play all, and deep links.
- [Playback guide](playback.md): video controls, resume behavior, subtitles, audio tracks, speed controls, chapters, and autoplay queues.
- [Settings and privacy](settings-and-privacy.md): server settings, multi-server profiles, PIN lock, Android TV launcher channels, updates, and local storage.
- [Troubleshooting](troubleshooting.md): fixes for common setup, authentication, playback, folder, metadata, and launcher-channel issues.
- [Developer reference](developer-reference.md): architecture, important source files, Stash API usage, and local build/test commands.
- [Credits and acknowledgements](../CREDITS.md): third-party projects Sinema builds on.
- [Third-party licenses](../THIRD_PARTY_LICENSES.md): dependency license inventory.

## What Sinema expects

Sinema is designed for Android TV and expects a reachable Stash server with media already scanned into Stash. It uses Stash GraphQL for library data and Stash media endpoints for screenshots, images, captions, and video streams.

Recommended Stash version: v0.24 or newer.

## Remote control basics

- Use the D-pad to move focus.
- Press Select or OK to open the focused item.
- Press Back to return to the previous screen.
- Press Menu on sortable screens to open sort and grid actions, when your remote provides a Menu key.
- During video playback, press Menu to open chapters when the scene has Stash scene markers.
