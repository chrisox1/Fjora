<img width="1024" height="1024" alt="fjora-logo" src="https://github.com/user-attachments/assets/a0a500ce-8858-4bfd-a29c-99dc5b5cea86" />
# Fjora: A Third-Party Jellyfin Player

Fjora is an Android client for Jellyfin focused on movie and TV playback.
It uses Exoplayer by default, but includes option to use the MPV player for direct play only.

Fjora is a third-party Jellyfin player. It is not affiliated with, endorsed
by, or sponsored by the Jellyfin project.

Current release: `v0.1.0 beta`

## Status

This is a hobby project which i have made in my free time, with ai assistance and has only been tested on my google Pixel 8a.
Expect bugs so bug reports, feedback, and contributions are welcome.

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

## Known limitations

- Chromecast is not implemented.
- Music libraries are not a target for this app and will not be added.
- Transcoded downloads can be unreliable and slow
- Downloaded files show detected local container format and size. The bitrate
  label for transcoded downloads is the requested preset, not a verified
  post-download media analysis.
- ExoPlayer may still fail to seek some malformed or poorly indexed files.
  Use mpv for downloads or open the file in another app as a fallback.
- Direct-play-only mode can fail on files or servers that cannot provide a
  playable original stream. It intentionally does not fall back to direct
  stream or transcoding.
- First install and loading can be slow due to caching, but becomes smooth after a few minutes.
- Not tested on tablets.
- Downloads dont download external subtitles.

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


## License

Fjora is licensed under the GNU General Public License version 3. See
`LICENSE`.

Jellyfin is a trademark of the Jellyfin project. Fjora is an unofficial
third-party client and is not affiliated with, endorsed by, or sponsored by
the Jellyfin project.
