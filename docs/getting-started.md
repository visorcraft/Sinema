# Getting Started

This guide covers installing Sinema and connecting it to Stash for the first time.

## Requirements

- A running Stash server that the Android TV device can reach over the network.
- Stash v0.24 or newer is recommended.
- Media scanned into Stash. Sinema reads Stash's indexed scenes, images, folders, tags, performers, studios, captions, scene markers, ratings, resume times, and play history.
- An Android TV device, or an Android device with Leanback support.
- A way to install the APK, usually Android Studio or ADB.
- For command-line release builds, Docker or a local Android/Gradle toolchain.

Stash normally runs on port `6969`, so a typical server URL looks like:

```text
http://192.168.1.100:6969
```

Use `http://` or `https://` in the URL. Sinema removes a trailing slash when saving the setting.

## Build the APK

From the repository root:

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To build the release variant:

```bash
./gradlew :app:assembleRelease
```

The release APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The repository also includes `Dockerfile.build` for building without installing Android Studio locally:

```bash
docker build -t sinema-builder -f Dockerfile.build .
docker run --rm -v "$(pwd)":/project -v sinema-gradle-cache:/root/.gradle sinema-builder \
  bash -c "cd /project && ./gradlew assembleRelease --no-daemon"
```

## Install with ADB

Enable developer options and network debugging on the TV, then connect and install:

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch Sinema:

```bash
adb -s <TV_IP>:5555 shell monkey -p com.sinema -c android.intent.category.LEANBACK_LAUNCHER 1
```

Use `install -r` when updating so Android preserves the saved Sinema settings.

## First launch

On first launch, Sinema opens the setup wizard. Choose one of three methods.

### Sign in with Stash

Use this when your Stash server has username/password authentication enabled.

1. Select `Sign in with Stash`.
2. Enter the Stash server URL.
3. Enter the Stash username and password.
4. Choose how Sinema should stay connected.

Connection choices:

- Permanent sign-in: Sinema logs in, asks Stash to generate an API key, and stores that key. This is the most convenient long-term option, but Stash's `generateAPIKey` mutation can replace the existing Stash API key. Other apps or scripts using the old key may need to be updated.
- Session sign-in: Sinema stores the session cookie plus the username and password, then automatically logs in again when a Stash GraphQL request reports an expired session. This keeps the current Stash API key untouched.

### Web Setup

Use this when entering a long API key with a TV remote is inconvenient.

1. Select `Web Setup`.
2. Enter the Stash server URL on the TV.
3. Select `Start Web Setup`.
4. On a phone or computer on the same network, open the URL shown on the TV. Sinema serves this form from the TV on port `8888`.
5. The browser form includes the TV's one-time setup token automatically.
6. Enter the server URL and API key in the browser form.
7. Submit the form.

The setup token prevents stale or unrelated submissions. Leaving the setup screen stops the temporary web server and invalidates the token.

### Manual setup

Use this when you already have a Stash API key.

1. Select `Enter API Key Manually`.
2. Enter the Stash server URL.
3. Enter the API key.
4. Select `Save & Continue`.

Manual setup always uses API-key authentication.

## Stash setup checklist

In Stash:

- Confirm authentication is enabled or disabled as you intend.
- If using an API key, generate or copy it from Stash's security/authentication settings.
- Scan the media library before expecting content to appear in Sinema.
- Add metadata such as tags, performers, studios, scene markers, captions, and ratings if you want those Sinema features to appear.
- Confirm the Android TV can reach the Stash URL in the same network context. If Stash is on another machine, avoid `localhost` in Sinema because `localhost` means the TV itself.

## After setup

Sinema opens the main screen and loads rows from Stash:

- Continue Playing
- Recently Played
- Recently Added
- Favorites

If the server returns an authentication error, Sinema sends you back to setup so you can update credentials.
