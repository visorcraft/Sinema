<!-- SPDX-FileCopyrightText: 2026 VisorCraft LLC -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Security and Privacy Policy

Sinema is a TV-side client for a self-hosted Stash server. It is
designed for personal use on a private network and is deliberately
built around the assumption that **your collection is nobody's
business but yours**. This document records the disclosure process,
the threat model, and the assumptions Sinema makes about its
deployment.

## Reporting a vulnerability

**Do not file a public GitHub issue or pull request for security
problems.** Instead:

- Email the security contact at **security@visorcraft.com**.
- Include reproduction steps, the Sinema version (Settings → About,
  or the `versionName` in `app/build.gradle.kts`), the Android TV
  device and OS version, and the Stash version it is connecting to.
- We aim to acknowledge within 72 hours and to publish a patch +
  advisory within two weeks of confirming a fix.

Please give us a 30-day coordinated-disclosure window before going
public, unless the vulnerability is already actively exploited.

## Supported versions

The latest stable Sinema release is supported with security fixes.
Older versions receive fixes only when the patch is trivial to
backport. The "About" screen and the GitHub Releases page are the
canonical version sources.

Sinema requires Android API 24 (Android 7) or later. We do not
backport fixes to older Android API levels.

## Telemetry policy

**Sinema ships zero telemetry.** No analytics, no crash reports, no
ping-home behavior. The app makes network requests in exactly two
situations:

1. **Talking to your Stash server** over GraphQL (and to the HLS /
   MP4 stream endpoints Stash hands back for playback).
2. **The Web Setup short-code flow**, which talks only to the same
   Stash server — see *Onboarding* below.

If a future opt-in diagnostics feature is ever added, it must be
off by default, redact paths and titles before submission, surface a
one-time consent dialog, and target a documented, versioned
endpoint.

## Credential storage

API keys, session cookies, and the optional PIN are stored in
**`EncryptedSharedPreferences`** from `androidx.security:security-crypto`,
which uses the Android Keystore as the master key source. On Android
TV devices that lack a hardware-backed Keystore, the master key falls
back to a software-backed key that is still scoped to the app's
sandbox.

Sensitive values are excluded from:

- `Log.*` calls in production builds (the API client never logs
  credentials or the PIN).
- Crash reports (there are none — see *Telemetry policy*).
- `adb backup` (Sinema declares `android:allowBackup="false"` for
  this reason; do not re-enable it without a redaction pass).
- The Web Setup pairing payload — see below.

If the encrypted preferences store cannot initialize (corrupted
keystore, OEM-stripped security provider), the onboarding flow
surfaces the error verbatim and refuses to fall back to plaintext.

## Onboarding and Web Setup

Sinema offers three onboarding paths and each has a different trust
profile:

1. **Sign in to Stash (username / password)** — the credentials are
   sent directly to the Stash server over the configured transport
   (HTTPS if the operator has configured it; see *Transport
   security* below). They are never stored on the device — the
   session cookie returned by Stash is what gets persisted, encrypted.
2. **Web Setup (short-code pairing)** — the TV displays a short
   code. The user enters it in a browser on a trusted device; that
   browser then sends Stash URL + API key to the TV through the
   Stash server itself. The pairing payload is short-lived and
   one-shot — once the TV consumes it, the code is invalidated.
3. **Manual setup** — the user types the Stash URL and API key on
   the TV with the remote. Useful for headless deploys, awful for
   D-pads with long API keys.

In every case, what ends up on the device is the minimum required to
re-authenticate against Stash: an API key or a session cookie, never
the user's Stash password.

## Transport security

Stash servers can run plain HTTP, HTTPS with a self-signed
certificate, or HTTPS behind a trusted CA. Sinema does **not**
override Android's certificate trust store — it will refuse a
self-signed certificate unless the operator installs the
certificate on the TV.

Recommendations for operators:

- **Local-only deployment** — bind Stash to the LAN and reach it
  via the LAN IP. Plain HTTP is acceptable here as long as the LAN
  is trusted.
- **Remote / WAN deployment** — front Stash with a TLS reverse
  proxy (Tailscale Serve, nginx + Let's Encrypt, Cloudflare
  Tunnel, etc.) and configure Sinema with the HTTPS URL. Do not
  expose plain HTTP to the public internet.
- **Self-signed certificates** — install the root CA on the TV via
  Android's certificate manager. Sinema will then validate the
  chain like any other HTTPS endpoint.

## PIN lock

The PIN lock is **convenience-grade**, not a forensic boundary.
It is a 4-digit gate that:

- Blocks the app's UI until entered.
- Is enforced again after **Log Out** (the user must enter the PIN
  before any saved server settings are exposed).
- Is stored hashed in `EncryptedSharedPreferences`.

What the PIN does **not** do:

- It does not encrypt the on-device session token or API key beyond
  what `EncryptedSharedPreferences` already does — anyone with
  root on the TV can in principle read the keystore-backed blob.
- It does not protect playback once it has started — playback is
  hardware-accelerated by ExoPlayer and not gated per-frame on the
  PIN.

Operators who need a stronger boundary should pair the PIN with
device-level controls (Android TV user profiles, parental controls,
or simply not leaving the device unlocked).

## Playback and resume state

- Playback resume positions, play counts, and rating changes are
  **server-side** state in Stash. Sinema writes them via the same
  authenticated channel it reads scenes through; it does not
  persist a parallel local copy beyond the in-memory state of the
  current session.
- This means clearing Sinema's app data on the TV will not erase
  watch history — that lives in Stash. Operators who need to wipe
  history should do so in Stash directly.

## Threat model summary

| Threat | Mitigation |
| --- | --- |
| Roommate / guest picks up the TV remote | PIN lock; **Log Out** action also forces PIN re-entry. |
| Credentials exposed in app backups / `adb backup` | `android:allowBackup="false"` in the manifest; reviewer must reject any PR that re-enables it. |
| Plain HTTP credentials over an untrusted network | The README + onboarding wizard call out HTTPS as the recommendation for non-LAN deployments. Sinema respects the OS trust store and does not silently accept self-signed certs. |
| Web Setup short code intercepted | Codes are short-lived, single-use, and only meaningful when paired against the user's own Stash server. |
| Compromised Stash server returns crafted JSON / GraphQL response | The Gson parser is non-evaluating; the data classes are typed; the HLS/MP4 stream endpoints are handed to ExoPlayer which expects opaque bytes. The app never executes server-provided payloads. |
| Credentials leaking into logs | The API client and EncryptedSharedPreferences layer do not log values; production builds strip debug logging. |
| Reverse-engineered debug-signed APK is repackaged | The release build is signed with the debug key for simplicity; users who need a verifiable signature should build from source with their own keystore. We treat the debug-signed release as untrusted-by-default and rely on the user controlling install access (ADB / sideload). |
| TV is sold or returned with Sinema still installed | Operators should wipe app data + sign out before disposal. The README's onboarding section flags this. |

## Dependency hygiene

- The Gradle dependency tree is reviewed manually before each
  release; the curated list lives in `app/build.gradle.kts`.
- Android Gradle Plugin, Kotlin, Media3, OkHttp, Glide, and the
  Jetpack Security crypto library are tracked against upstream
  security advisories.
- Dependabot is not currently enabled; if you propose enabling it,
  point it at the Gradle ecosystem and review the resulting PRs
  rather than auto-merging.
