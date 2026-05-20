<!-- SPDX-FileCopyrightText: 2026 VisorCraft LLC -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Contributing to Sinema

Thanks for taking the time to help improve Sinema. The project is an
**Android TV** client for [Stash](https://github.com/stashapp/stash),
written in Kotlin, built with Gradle, and structured around the
**AndroidX Leanback** widget set. Changes should be small, focused,
and respect that this app runs on a TV controlled by a remote — every
new screen has to be navigable with a D-pad.

## Contribution workflow

1. Fork the repository on GitHub.
2. Clone your fork:

   ```bash
   git clone https://github.com/<you>/Sinema.git
   cd Sinema
   ```

3. Create a focused branch:

   ```bash
   git checkout -b fix-resume-position
   ```

4. Install one of the supported toolchains (see *Local development*
   below).
5. Make the smallest change that fully solves the issue.
6. Run the local gate before pushing:

   ```bash
   ./gradlew lint
   ./gradlew assembleDebug
   ```

7. Push your branch and open a pull request against `master`.

Pull requests should include a clear summary, the lint/build output
you ran, and screenshots or a short clip when the change affects the
UI. UI changes also need to confirm that **D-pad navigation still
works** — Sinema does not depend on a touchscreen.

## Project layout

- `app/` — the single Android application module.
  - `src/main/java/com/sinema/`
    - `api/` — Stash GraphQL client + queries + auth helpers.
    - `model/` — Kotlin data classes for the Stash entities Sinema
      consumes (scenes, folders, ratings, …).
    - `ui/` — Activities, Fragments, Presenters, RowAdapters,
      Glide bindings, Leanback dialogs.
    - `util/` — small reusable helpers (PIN handling, encrypted
      preferences, network status, …).
  - `src/main/res/` — Android resources (layouts, drawables, strings,
    Leanback themes).
  - `build.gradle.kts` — module-level build script.
  - `proguard-rules.pro` — R8 / ProGuard rules.
- `assets/` — README hero logo and screenshots used in
  `README.md`. Not packaged into the APK.
- `Dockerfile.build` — reproducible build container.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradle.properties`,
  `gradlew`, `gradlew.bat` — Gradle wrapper + root configuration.
- `.github/workflows/build.yml` — CI that runs on every `v*` tag
  (lint + debug build + APK upload).

Keep Stash-talking logic in `api/`. UI code should call into the API
client and present results — it should not assemble GraphQL queries
inline.

## Local development

You can build Sinema two ways. Pick the one that fits your machine.

### Docker-based build (no Android Studio needed)

```bash
docker build -t sinema-builder -f Dockerfile.build .
docker run --rm \
  -v "$(pwd)":/project \
  -v sinema-gradle-cache:/root/.gradle \
  sinema-builder bash -c "cd /project && ./gradlew assembleDebug --no-daemon"
```

### Native build (Android Studio or command-line SDK)

Install the toolchain versions Sinema targets:

| Component | Version |
| --- | --- |
| JDK | **17** (Temurin or any GraalVM-free build) |
| Android Gradle Plugin | **8.7.3** |
| Gradle wrapper | bundled (`./gradlew`) |
| Kotlin | **1.9.24** |
| compileSdk / targetSdk | **36** |
| minSdk | **24** |

Then:

```bash
# Generate the debug keystore the build expects on first run.
keytool -genkey -v -keystore debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"

./gradlew lint               # static analysis
./gradlew assembleDebug      # debug APK to app/build/outputs/apk/debug/
./gradlew assembleRelease    # release APK
```

### Installing on a TV

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <TV_IP>:5555 shell am start -n com.sinema/.ui.MainActivity
```

`-r` preserves data on re-install, which matters because PIN +
encrypted preferences are stored on the device.

## Coding standards

- Target **Kotlin 1.9.24** with Java 17 source/target compatibility.
- Use Kotlin idioms (data classes, sealed classes, scope functions,
  null-safety) — avoid Java-style getters/setters or `Object`-typed
  fields.
- Network I/O and any blocking work must run on a background
  dispatcher (`Dispatchers.IO`). Never block the main thread.
- Use **kotlinx-coroutines** + `viewLifecycleScope` /
  `lifecycleScope` for async work. Cancel scopes on lifecycle
  destruction.
- Use **Glide** for image loading. Do not decode bitmaps directly
  in UI code.
- Use **OkHttp + Gson** for HTTP/JSON. Do not introduce a second
  HTTP client.
- Use **Media3 / ExoPlayer** for playback. Do not introduce a
  parallel video stack.
- Use **EncryptedSharedPreferences** for any credential or PIN
  storage. Plaintext `SharedPreferences` is only for
  non-sensitive UI state.
- Use string resources (`R.string.…`) for every user-facing string.
  No hard-coded English in layouts or Kotlin.
- All UI must be navigable with a D-pad. Set proper `nextFocus*`
  attributes when stock Leanback heuristics aren't enough.
- Follow the existing package layout (`api/`, `model/`, `ui/`,
  `util/`). Do not introduce a fifth top-level package without
  discussion.

Every new source file must include the SPDX short header used by the
repository:

```text
SPDX-FileCopyrightText: 2026 VisorCraft LLC
SPDX-License-Identifier: GPL-3.0-only
```

Use the comment syntax appropriate for the file type (`//` for
Kotlin, `<!-- ... -->` for XML resources).

## UI changes

- Stick with **Leanback** widgets — `BrowseFragment`,
  `RowsFragment`, `DetailsFragment`, `Presenter`, `GuidedStepFragment`
  for forms. Build your own only when none of these fit.
- Test focus traversal with a D-pad before submitting. Use the
  emulator's D-pad keys (`5/4/6/8/2` on the numpad) or a real
  remote.
- Light/dark theming: Sinema runs in a dark theme by default; new
  drawables must be readable on dark backgrounds.
- Animations should respect `Settings → Developer options →
  Animator duration scale` and degrade gracefully when set to 0.

## Stash API changes

- Every new GraphQL query lives next to the existing ones in
  `com.sinema.api`. Wrap queries in a typed function so callers
  never see the raw GraphQL string.
- New mutations that change Stash server-side state
  (`sceneSaveActivity`, ratings, …) must be **explicitly user-
  initiated** — never run them in response to passive UI events
  like scroll or focus.
- If you bump the minimum Stash version, update both `README.md`
  ("Stash Backend Requirements" table) and the in-app onboarding
  flow.

## Tests

Sinema currently relies on lint + manual TV testing. If you add a
testable utility (parser, formatter, encryption helper, …),
co-locate JUnit tests under `app/src/test/`. UI / Leanback tests
that need a connected device belong in `app/src/androidTest/`.

Before opening a PR, run at minimum:

```bash
./gradlew lint
./gradlew assembleDebug
```

CI runs the same gate on every `v*` tag push. If you add unit tests,
also wire them into the CI workflow in the same PR.

## Pull request expectations

A good pull request:

- Has one clear purpose.
- Describes user-visible behavior changes.
- Calls out compatibility risks (Stash version, Android API level,
  Leanback widget changes).
- Includes screenshots or a clip for UI changes, and a note
  confirming D-pad navigation still works.
- Updates `README.md` when behavior, features, or requirements
  change.
- Passes `./gradlew lint` and `./gradlew assembleDebug`.
- Avoids unrelated formatting or refactoring churn.

Maintainers may ask for smaller commits, additional tests, or docs
updates before merging.

## Dependency policy

Sinema is GPL-3.0-only. New Gradle dependencies must use licenses
compatible with GPL-3.0 (Apache-2.0, MIT, BSD-*, MPL-2.0 case-by-case,
etc.). If a dependency needs license clarification, explain the
reason in the pull request.

Avoid new dependencies unless they clearly reduce complexity or
provide well-tested domain behavior that should not be maintained
locally. Pulling in another HTTP client, image loader, or video
stack alongside the existing one is **not** acceptable.

## Security

Do not report security issues through public issues or pull requests.
Follow the disclosure policy in [SECURITY.md](SECURITY.md).
