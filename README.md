# Fjora: a Jellyfin Player

Fjora is an Android client for Jellyfin focused on movie and TV playback.
It is built with Kotlin, Jetpack Compose, Media3/ExoPlayer, and an optional
bundled mpv backend for local downloaded files.

Fjora is a third-party Jellyfin player. It is not affiliated with, endorsed
by, or sponsored by the Jellyfin project.

Current release: `v0.1.0 beta`

The launcher name on Android is `Fjora`.

The launcher icon is generated from `fjora-logo.png`, a purple
jellyfish/play mark.

## Status

Fjora is beta software. The core watch flow is usable, including login,
library browsing, playback, subtitles, downloads, Picture-in-Picture, and
basic playback reporting. Expect rough edges around device-specific playback,
server transcoding behavior, and first-run performance while caches warm up.

## Features

- Sign in with a Jellyfin server URL, username, and password.
- Multiple saved accounts with account switching.
- Movie and TV library browsing.
- Library tabs for Jellyfin views plus a local Downloads tab.
- Deferred first-run work so the login form stays responsive on fresh installs.
- Search across movies, series, and episodes.
- Movie and episode detail screens with metadata, artwork, overview, runtime,
  ratings, genres, cast, and technical media information.
- Series episode browsing grouped by season.
- Continue Watching and Next Up rows.
- Playback progress reporting to Jellyfin.
- Resume from the last server-reported playback position.
- Automatic next episode playback.
- Manual next episode button in the player and PiP controls.
- Media3/ExoPlayer streaming playback.
- PlaybackInfo negotiation with Jellyfin so the server can direct play,
  direct stream, or transcode based on device support.
- Quality selection for streaming: original, 1080p, 720p, 480p, and 360p.
- Explicit non-original streaming quality selections force server-side
  transcoding instead of allowing direct play or direct stream.
- Optional "always transcode" setting.
- Optional "direct play only" setting that disables direct stream/transcode
  requests and forces mpv playback.
- Audio track selection.
- Subtitle track selection using server-provided VTT where possible.
- Picture-in-Picture with play/pause, seek, and next episode controls.
- Fullscreen landscape playback with safe orientation reset when leaving the
  player.
- Offline downloads for movies and episodes.
- Download quality selection, including original quality or transcoded MP4
  targets.
- Downloaded file detail views showing detected container format, file size,
  and requested quality.
- Downloaded series grouping for locally saved episodes.
- Downloaded movie, episode, and series views with artwork cache prewarming for
  smoother navigation after the first load.
- Optional bundled mpv player for downloaded files that ExoPlayer cannot seek
  or play reliably.
- Optional mpv playback for all streaming media.
- Open downloaded files in another installed video app.

## Known limitations

- Chromecast and remote playback are not implemented.
- Music libraries are not a target for this beta.
- Some Jellyfin servers may ignore or partially honor transcode download
  parameters. Fjora requests explicit MP4/H.264/AAC output, bitrate, audio
  bitrate, target dimensions, direct-play/direct-stream disabled, and stream
  copy disabled, but the server ultimately controls the encoded result.
- Downloaded files show detected local container format and size. The bitrate
  label for transcoded downloads is the requested preset, not a verified
  post-download media analysis.
- ExoPlayer may still fail to seek some malformed or poorly indexed files.
  Use mpv for downloads or open the file in another app as a fallback.
- Direct-play-only mode can fail on files or servers that cannot provide a
  playable original stream. It intentionally does not fall back to direct
  stream or transcoding.
- mpv streaming playback uses the same full-screen Fjora chrome and supports
  play/pause, seek, next episode, direct-play-only mode, and Jellyfin playback
  reporting, but ExoPlayer remains the more complete UI path for audio and
  subtitle track picker controls.
- First launch after install can still feel slower while Android, Coil image
  cache, and Jellyfin library data warm up. Fjora prewarms the first library
  artwork batch after data arrives, defers unused DataStore work on the login
  screen, and includes AndroidX ProfileInstaller, but cold caches are still
  visible on slower devices or servers.
- Release signing is configured through environment variables or GitHub
  Actions secrets. No signing key is stored in this repository.

## Upcoming work

- Signed release build documentation.
- Better diagnostics for server transcode/download failures.
- More robust subtitle handling for image-based subtitle formats.
- Optional release notes/changelog file.
- Additional tablet and TV layout polish.

## Android requirements

- Android 8.0 or newer, API 26+.
- A Jellyfin server reachable from the device.
- A Jellyfin account with access to movie or TV libraries.

## Build outputs

The project uses ABI splits because mpv includes native libraries. GitHub
Actions uploads multiple APKs plus a release Android App Bundle:

- `arm64-v8a`: most modern Android phones.
- `armeabi-v7a`: older 32-bit ARM devices.
- `x86_64`: emulators and uncommon x86 Android devices.
- `app-release.aab`: Play Store upload format.

Install the APK matching your device. Shipping split APKs avoids putting every
native mpv library into every install.

## Build with GitHub Actions

1. Push this repository to GitHub.
2. Open the Actions tab.
3. Run or wait for `Build Fjora APK`.
4. Download the debug APKs, release APKs, or release AAB artifact.
5. For side-loading, unzip the APK artifact and install the APK matching your
   device ABI.

The workflow generates a Gradle wrapper if one is not checked in, sets up JDK
17 and Android API 35, builds debug APKs, release APKs, and a release AAB, and
uploads the outputs.

If release signing secrets are present, release artifacts are signed. Without
secrets, release artifacts are built unsigned, which is suitable for review and
for F-Droid-style reproducible build pipelines that sign externally.

## Build locally

Requirements:

- JDK 17.
- Android SDK.
- Gradle or a generated Gradle wrapper.

Build debug APKs:

```bash
./gradlew assembleDebug
```

Outputs are written to:

```text
app/build/outputs/apk/debug/
```

Build release APKs and a release Android App Bundle:

```bash
./gradlew assembleRelease bundleRelease
```

Release outputs are written to:

```text
app/build/outputs/apk/release/
app/build/outputs/bundle/release/
```

If this checkout does not include `gradlew`, generate it with Gradle 8.7:

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## Release signing

Fjora signs release builds from environment variables. Do not commit keystores,
passwords, or generated signing property files.

Create a release/upload keystore locally:

```bash
keytool -genkeypair -v \
  -keystore fjora-release.jks \
  -alias fjora \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Build a signed release locally:

```bash
export FJORA_RELEASE_STORE_FILE=/absolute/path/fjora-release.jks
export FJORA_RELEASE_STORE_PASSWORD=your-store-password
export FJORA_RELEASE_KEY_ALIAS=fjora
export FJORA_RELEASE_KEY_PASSWORD=your-key-password
./gradlew assembleRelease bundleRelease
```

For GitHub Actions, add these repository secrets:

```text
FJORA_RELEASE_KEYSTORE_BASE64
FJORA_RELEASE_STORE_PASSWORD
FJORA_RELEASE_KEY_ALIAS
FJORA_RELEASE_KEY_PASSWORD
```

Generate the base64 keystore secret with:

```bash
base64 -w0 fjora-release.jks
```

Google Play should receive the release `.aab`. Side-loaded releases can use the
ABI-specific release APK.

## Android permissions and store compliance

Fjora currently requests only normal Android permissions:

- `INTERNET`: connect to the configured Jellyfin server and load artwork,
  streams, subtitles, and downloads.
- `ACCESS_NETWORK_STATE`: detect network availability for playback and
  loading behavior.
- `WAKE_LOCK`: keep playback/download-related work from being interrupted
  while media is active.

Fjora does not request contacts, location, camera, microphone, phone, SMS, or
notification permissions. Downloads are written through Android DownloadManager
to app-private external storage, so no broad storage permission is required.

Cleartext HTTP is intentionally allowed because many self-hosted Jellyfin
servers run on local `http://host:8096` addresses. Users who expose a server
outside their LAN should use HTTPS.

The app targets Android 15/API 35 for Google Play submission. F-Droid-style
pipelines can build unsigned artifacts from source and sign them externally.
Official F-Droid inclusion may still require extra packaging work because
`dev.jdtech.mpv:libmpv` bundles prebuilt native mpv/FFmpeg/libass libraries;
an F-Droid recipe may need to build or substitute those native artifacts from
source.

## Installing on Android

1. Transfer the matching APK to the device.
2. Allow APK installs from the file manager or browser you are using.
3. Open the APK and install it.
4. Launch Fjora.
5. Enter the Jellyfin server URL, username, and password.

Server URL examples:

```text
http://192.168.1.10:8096
https://jellyfin.example.com
```

## Privacy and security notes

- Credentials are exchanged directly with the configured Jellyfin server.
- Passwords are used only for the login request and are not stored by Fjora.
- Jellyfin access tokens are stored in Android DataStore encrypted with an
  Android Keystore-backed AES-GCM key.
- Existing plaintext tokens from older builds are migrated to encrypted storage
  on startup.
- Account metadata such as server URL, user id, user name, active account id,
  and device id is stored locally in DataStore.
- Android backup is disabled for Fjora. Restoring Keystore-encrypted app data
  to another device would not produce usable saved sessions.
- Access tokens are still present in app memory while the app is signed in and
  are attached to Jellyfin API, image, stream, and subtitle requests as required
  by the server.
- Debug builds log basic HTTP request information in debug mode only. Do not
  share debug logs publicly if they contain private server URLs or request
  parameters.
- Release builds disable OkHttp HTTP logging through `BuildConfig.DEBUG`.
- Downloads are stored in app-private external storage and are removed by
  Android when the app is uninstalled.
- Fjora does not use third-party analytics.

## Project layout

```text
app/src/main/java/com/example/jellyfinplayer/
  MainActivity.kt                 Compose navigation and Activity lifecycle
  AppViewModel.kt                 App state, auth, library, settings, downloads
  JellyfinApp.kt                  Application setup and image cache
  api/
    JellyfinApi.kt                Retrofit API interface
    JellyfinRepository.kt         Auth headers, PlaybackInfo, URLs
    Models.kt                     Kotlin serialization DTOs
  data/
    AuthStore.kt                  Account and auth persistence
    TokenCipher.kt                Keystore-backed token encryption
    DownloadsStore.kt             Download metadata persistence
    SettingsStore.kt              User settings persistence
  player/
    DeviceCodecs.kt               Device codec probing
    PipActionReceiver.kt          PiP action bridge
    PipParams.kt                  PiP RemoteAction construction
  ui/
    components/                   Shared UI components
    icons/                        Custom icon vectors
    screens/                      Login, library, detail, downloads, player, settings
    theme/                        Compose theme
```

## License

Fjora is licensed under the GNU General Public License version 3. See
`LICENSE`.

The project depends on `dev.jdtech.mpv:libmpv`, which bundles native mpv and
related components. Review the licenses of mpv, FFmpeg, libass, and other
native components before distributing release builds.

Jellyfin is a trademark of the Jellyfin project. Fjora is an unofficial
third-party client and is not affiliated with, endorsed by, or sponsored by
the Jellyfin project.
