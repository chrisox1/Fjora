# Fjora: A Third-Party Jellyfin Player

<img width="1024" height="1024" alt="fjora-logo" src="https://github.com/user-attachments/assets/a0a500ce-8858-4bfd-a29c-99dc5b5cea86" />


Fjora is an Android client for Jellyfin focused on movie and TV playback.
It uses Exoplayer by default, but includes option to use the MPV player for direct play only.

Fjora is a third-party Jellyfin player. It is not affiliated with, endorsed
by, or sponsored by the Jellyfin project.

Current release: `v0.1.0 beta`

## Status

Fjora is an early beta project currently tested primarily on a Google Pixel 8a.

While already working well in daily use, some features may still have bugs or rough edges. Feedback, bug reports, and contributions are very welcome.

Screenshots:
<p align="center">
  <img src="https://github.com/user-attachments/assets/1dda2dc8-df57-4899-9b26-bed6a0946d0e" width="19%" />
  <img src="https://github.com/user-attachments/assets/965c340a-4b21-4566-a937-4ec01e0eab5c" width="19%" />
  <img src="https://github.com/user-attachments/assets/a810a737-34b3-487f-ad9b-1d7753217b7b" width="19%" />
  <img src="https://github.com/user-attachments/assets/a29a88fd-608e-4b9e-ac4b-2cbb379d8c24" width="19%" />
  <img src="https://github.com/user-attachments/assets/ba5f2c7a-2460-4398-9fd3-70dc09bf6a0e" width="19%" />
</p>

## Features

- Use multiple accounts on different servers.
- Movie and TV library browsing.
- Local downloads, with option to download transcoded MP4 quality or original quality.
- Direct play mode only using MPV.
- Movie and episode detail screens with metadata, artwork, overview, runtime,
  ratings, genres, cast, and technical media information.
- Continue Watching and Next Up rows.
- Playback progress reporting to Jellyfin.
- Resume from the last server-reported playback position in Both MPV and Exoplayer.
- Automatic next episode playback.
- Picture in picture for both Exoplayer and MPV player, but works best with exoplayer
- Quality selection for streaming: original, 1080p, 720p, 480p, and 360p.
- Optional "always transcode" setting.
- Audio track selection.
- Subtitle track selection using server-provided VTT where possible.
- Downloaded file detail views showing detected container format, file size,
  and requested quality.
- Downloaded entire series grouping for locally saved episodes.
- Optional bundled mpv player for downloaded files that ExoPlayer cannot seek
  or play reliably.
- Open downloaded files in another installed video app.
- Quickconnect

## Known limitations

- Chromecast is not implemented.
- Music libraries are not a target for this app currently.
- Direct-play-only mode can fail on files or servers that cannot provide a
  playable original stream. It intentionally does not fall back to direct
  stream or transcoding.

## Upcoming work

- Chromecast and google play store application.
- Additional tablet and TV layout polish.

## Android requirements

- Android 8.0 or newer, API 26+.
- A Jellyfin server reachable from the device.
- A Jellyfin account with access to movie or TV libraries.


## Privacy and security notes

- Credentials are exchanged directly with the configured Jellyfin server.
- Passwords are used only for the login request and are not stored by Fjora.
- Jellyfin access tokens are stored in Android DataStore encrypted with an
  Android Keystore-backed AES-GCM key.

## Download

APK releases are available here:

https://github.com/chrisox1/Fjora/releases

## Bug Reports

Please report bugs and feature requests through GitHub Issues.

## Privacy

Fjora does not collect analytics or send telemetry to the developer or any third party.

The app communicates only with the Jellyfin server configured by the user for:

* authentication
* media browsing
* playback
* subtitles
* downloads
* playback progress synchronization

Passwords are not stored.

Jellyfin access tokens are stored locally using Android Keystore-backed encryption.


## License

Fjora is licensed under the GNU General Public License version 3. See
`LICENSE`.

Jellyfin is a trademark of the Jellyfin project. Fjora is an unofficial
third-party client and is not affiliated with, endorsed by, or sponsored by
the Jellyfin project.
