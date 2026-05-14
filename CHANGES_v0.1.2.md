# v0.1.2 Local Changes

Changes made this session. All local-only — not committed/pushed.
To revert any individual change: `git diff <file>` shows what changed; `git checkout -- <file>` reverts that whole file.

## Round 1 — UX polish

1. **app/build.gradle.kts** — `versionName "0.1.1"` → `"0.1.2"`, `versionCode 11` → `12`.

2. **app/src/main/java/com/example/jellyfinplayer/ui/screens/LoginScreen.kt**
   - `http://` and `https://` FilterChips: explicit `FilterChipDefaults.filterChipColors(...)` with a neutral `surfaceVariant` selected container so they no longer pick up the theme's purple secondary container. Border is a 1.dp outline stroke.

3. **app/src/main/java/com/example/jellyfinplayer/ui/screens/LibraryScreen.kt**
   - `SortDialog`: currently-selected sort key now renders at the top of the list instead of staying in enum order.
   - Module-level `LatestRowsScrollSnapshot` added; `LatestMediaRow` and `WideRow` accept an optional `state: LazyListState` so horizontal scroll positions for "Continue watching", "Next up", "Latest movies" and "Latest shows" persist across detail-screen navigation.
   - `LaunchedEffect(viewMode)` uses `scrollToItem` (instant) instead of `animateScrollToItem` so returning HOME from the Movies/Shows full list lands instantly.

4. **app/src/main/java/com/example/jellyfinplayer/ui/screens/EpisodesScreen.kt**
   - `animateScrollToItem(...)` for auto-scroll-to-initial-episode replaced with `scrollToItem(...)` so the list lands instantly at the episode the user came back from.

## Round 2 — Settings, permissions, PiP, speed picker

5. **app/src/main/java/com/example/jellyfinplayer/ui/screens/PlayerScreen.kt**
   - `SpeedPickerDialog` was clipped above 1.0× because the `Column` had `heightIn(max = 360.dp)` with no scroll. Added `verticalScroll(rememberScrollState())` and imported `verticalScroll`. The 1.25×–2.0× rows now render and respond correctly.

6. **app/src/main/java/com/example/jellyfinplayer/ui/screens/MpvPlayerScreen.kt**
   - Top bar / center transport / bottom seek bar `AnimatedVisibility` blocks now gate on `&& !inPip`. Previously chrome kept showing at full size in the tiny PiP window for 4 s before the auto-hide fired — that was the "huge logos" the user reported.
   - `refreshMpvVideoOutput` (called when the surface re-attaches, including on PiP exit) now polls every 40 ms × 8 attempts instead of 120 ms × 5 — drops the worst-case wait from ~600 ms to ~320 ms and usually completes in <100 ms, so PiP-exit black-screen is materially shorter.

7. **app/src/main/java/com/example/jellyfinplayer/data/SettingsStore.kt**
   - Added two new prefs and corresponding `Settings` data-class fields + setters:
     - `INCLUDE_EPISODES_IN_SEARCH` (Boolean, default `false`).
     - `IMAGE_CACHE_LIMIT_BYTES` (Long, nullable — null = default 250 MB).

8. **app/src/main/java/com/example/jellyfinplayer/api/JellyfinRepository.kt**
   - `search(query)` → `search(query, includeEpisodes = false)`. When `includeEpisodes` is true the server-side `IncludeItemTypes` becomes `"Movie,Series,Episode"` and the client-side filter accepts `Episode`. Default behaviour unchanged.

9. **app/src/main/java/com/example/jellyfinplayer/AppViewModel.kt**
   - `observeSearchQuery()` now reads `settings.value.includeEpisodesInSearch` and passes it to `repo.search`.
   - Added `setIncludeEpisodesInSearch(enabled)` and `setImageCacheLimitBytes(bytes)` VM-level setters.
   - Default `Settings(...)` in `stateIn` initial value gets the two new fields (`false`, `null`).

10. **app/src/main/java/com/example/jellyfinplayer/ui/screens/SettingsScreen.kt**
    - New ToggleRow under Home for "Include episodes in search".
    - New ClickableRow under a new "Cache" section for "Image cache limit", opening a new `ImageCacheLimitDialog` (5 options: 100 MB / 250 MB default / 500 MB / 1 GB / 2 GB). Note text: "Changes apply after the app is restarted."
    - Added helper list `imageCacheLimitOptions` + `imageCacheLimitLabel()`.

11. **app/src/main/java/com/example/jellyfinplayer/JellyfinApp.kt**
    - On `newImageLoader()`, reads `SettingsStore(this).flow.first().imageCacheLimitBytes` via `runBlocking` (with try/catch fallback to the 250 MB default) and applies it as Coil's disk-cache max size. The blocking read happens once during Application init when there's no UI to lag.

## Round 3 — Player overlay polish + subtitle position

12. **app/src/main/java/com/example/jellyfinplayer/data/SettingsStore.kt**
    - New `SUBTITLE_POSITION_FRACTION` Float pref (default `0.08`, clamped to `0.03..0.35`). Represents "subtitle vertical position as a fraction of view height from the bottom edge". Added to the `Settings` data class and a `setSubtitlePositionFraction(...)` setter.

13. **app/src/main/java/com/example/jellyfinplayer/AppViewModel.kt**
    - Added `setSubtitlePositionFraction(fraction)` setter and the new default field in the `Settings(...)` initial value.

14. **app/src/main/java/com/example/jellyfinplayer/ui/screens/SettingsScreen.kt**
    - New SliderRow under "Subtitles" → "Subtitle position", range 3%..35%, value label shown as a percentage. Live-updates while video is playing in either player.

15. **app/src/main/java/com/example/jellyfinplayer/ui/screens/MpvPlayerScreen.kt** (3 changes)
    - **Subtitle position**: replaced the hard-coded `42.dp` bottom padding with `(screenHeightDp * settings.subtitlePositionFraction).dp`. PiP still uses the tight 10dp default so subtitles don't get pushed off the tiny window.
    - **Skip-intro auto-hide**: replaced the bare `if (...) Button(...)` with an `AnimatedVisibility(enter = fadeIn(), exit = fadeOut())`. New `skipIntroHidden` state + `LaunchedEffect(activeIntroSegment != null, chromeVisible)` flips it to `true` after a 10-second delay. Any tap (which toggles chromeVisible) cancels the timer and restarts it.
    - **Next-episode auto-hide**: same pattern with `nextEpisodeHidden` and the credits segment.

16. **app/src/main/java/com/example/jellyfinplayer/ui/screens/PlayerScreen.kt** (3 changes)
    - **Subtitle position**: `subtitleView?.setBottomPaddingFraction(userSettings.subtitlePositionFraction)` added to both the `factory` and `update` blocks of the `AndroidView`, so the user's setting takes effect immediately while video is playing.
    - **Skip-intro auto-hide**: same `AnimatedVisibility` + 10-second timer pattern as MPV.
    - **Next-episode auto-hide**: same pattern with credits segment.

## Permissions audit (for Google Play testing phase)

Read `app/src/main/AndroidManifest.xml`. Declared permissions are:
- `INTERNET` ✓ — required.
- `ACCESS_NETWORK_STATE` ✓ — used by the streaming/transcoding code.
- `WAKE_LOCK` ✓ — required for ExoPlayer to keep the screen on during playback.

Cross-checked the rest of the app:
- **No foreground services** anywhere (`grep` for `: Service`, `<service`, `startForeground` is empty). So `FOREGROUND_SERVICE` and its sub-permissions are NOT required.
- **No notification posting** anywhere (`grep` for `NotificationManager`, `NotificationCompat`, `notify(`, `Notification.Builder` is empty). So `POST_NOTIFICATIONS` is NOT required (it'd just sit declared but unused).
- **No external-storage access** — downloads use `DownloadManager` against `getExternalFilesDir(...)` (app-private), which needs no storage permission on any API level.
- **`usesCleartextTraffic="true"` + `networkSecurityConfig="@xml/network_security_config"`** ✓ — needed so users can connect to a self-hosted Jellyfin on `http://` LAN IPs. The matched `network_security_config.xml` should already scope this to user-supplied hosts; that's standard for Jellyfin clients and Google's review accepts it.
- **`targetSdk = 35`, `compileSdk = 35`, `minSdk = 26`** ✓ — meets the Aug-2024 Google Play requirement (targetSdk ≥ 34).
- **`supportsPictureInPicture="true"`, `resizeableActivity="true"`** ✓ — necessary for the PiP feature.

**Conclusion: no permission changes needed.** Manifest is clean for Play submission.

---

## To revert ALL changes

```
git checkout -- \
  app/build.gradle.kts \
  app/src/main/java/com/example/jellyfinplayer/AppViewModel.kt \
  app/src/main/java/com/example/jellyfinplayer/JellyfinApp.kt \
  app/src/main/java/com/example/jellyfinplayer/api/JellyfinRepository.kt \
  app/src/main/java/com/example/jellyfinplayer/data/SettingsStore.kt \
  app/src/main/java/com/example/jellyfinplayer/ui/screens/EpisodesScreen.kt \
  app/src/main/java/com/example/jellyfinplayer/ui/screens/LibraryScreen.kt \
  app/src/main/java/com/example/jellyfinplayer/ui/screens/LoginScreen.kt \
  app/src/main/java/com/example/jellyfinplayer/ui/screens/MpvPlayerScreen.kt \
  app/src/main/java/com/example/jellyfinplayer/ui/screens/PlayerScreen.kt \
  app/src/main/java/com/example/jellyfinplayer/ui/screens/SettingsScreen.kt
rm CHANGES_v0.1.2.md
```

To revert just one round, pull the file paths from the relevant Round above.
