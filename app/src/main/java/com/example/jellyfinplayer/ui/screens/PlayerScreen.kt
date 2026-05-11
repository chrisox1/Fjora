package com.example.jellyfinplayer.ui.screens

import android.app.Activity
import android.media.AudioManager
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as ExoC
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.PlayerPresence
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.MediaSegment
import com.example.jellyfinplayer.api.MediaStream
import com.example.jellyfinplayer.api.ResolvedStream
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class FillMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom")
}

/**
 * Tag we attach to our own sideloaded subtitle Format.id so we can find it
 * later in the player's track list. Without this, an MKV with embedded text
 * tracks would have multiple text groups and we couldn't reliably tell which
 * one is OURS to override-select.
 */
private const val SIDELOAD_SUBTITLE_ID = "jellyfin-sideload-subtitle"

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    vm: AppViewModel,
    item: MediaItem,
    inPip: Boolean,
    onBack: () -> Unit,
    nextEpisode: MediaItem? = null,
    onPlayNext: ((MediaItem) -> Unit)? = null,
    /**
     * Local file path for offline playback. When non-null, the player
     * skips the entire server-side resolution pipeline (no PlaybackInfo
     * call, no transcode negotiation, no progress reporting) and plays
     * the file directly from disk. The item parameter still provides
     * metadata for the top-of-screen title display.
     */
    localFilePath: String? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }

    // Direct ExoPlayer — no MediaSessionService, no MediaController IPC.
    // Sideloaded subtitle configurations are handled by the same instance
    // that reads the MediaItem, so they actually reach DefaultMediaSourceFactory
    // and the resulting MergingMediaSource. With the IPC controller they were
    // being silently stripped during round-tripping.
    val player = remember {
        // Buffer config — bigger than ExoPlayer's defaults for smoother
        // streaming, but bounded so we don't accumulate enormous in-memory
        // buffers on long videos.
        //   minBufferMs = 50s    — minimum target buffer (default 50s)
        //   maxBufferMs = 300s   — buffer up to 5 minutes ahead. 600s caused
        //                          GC pauses on 25 Mbps remuxes (~1.9 GB
        //                          worth of bytes); 300s is plenty for
        //                          typical Wi-Fi stalls and keeps memory
        //                          pressure manageable.
        //   bufferForPlaybackMs = 2.5s — responsive on tap-to-play
        //   bufferForPlaybackAfterRebufferMs = 5s — after a stall, wait for
        //                                           5s of buffer to avoid
        //                                           immediate re-stall
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 50_000,
                /* maxBufferMs = */ 300_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            // Cap target buffer bytes so high-bitrate sources don't push
            // memory into territory that triggers long GC pauses (visible
            // as playback hiccups). 96 MB ≈ 30 seconds of 25 Mbps content,
            // which is enough headroom; the rest of the buffer falls back
            // to disk-backed allocation.
            .setTargetBufferBytes(96 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // HTTP data source with longer connect/read timeouts than ExoPlayer's
        // 8s defaults. On flaky Wi-Fi or with a busy server, the default
        // timeouts can fire mid-buffer-fill and produce visible stutter as
        // the player rebuffers from scratch. 30s is a generous floor that
        // tolerates typical hiccups without holding state forever on a
        // genuinely-dead connection.
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Fjora/0.1.0 (Android)")
        // Wrap the HTTP factory in a DefaultDataSource. This lets the player
        // handle BOTH:
        //   - http(s):// URIs (server streaming, transcoded HLS) — go through
        //     httpDataSourceFactory above
        //   - file:// URIs (downloaded files in app-private storage) — go
        //     through FileDataSource transparently
        // Without this wrapper, attempting to play a downloaded file failed
        // because HttpDataSource doesn't understand file:// scheme.
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )
        val mediaSourceFactory = androidx.media3.exoplayer.source
            .DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(ExoC.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(ExoC.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(ExoC.WAKE_MODE_NETWORK)
            .build()
    }

    // Set before navigating to the next episode so onDispose doesn't show the
    // system bars — showing then immediately re-hiding them causes them to stick.
    var isNavigatingAway by remember { mutableStateOf(false) }

    // Register PiP-overlay action handlers so the play/pause/skip buttons in
    // the PiP overlay drive THIS ExoPlayer. Cleared on disposal so a stale
    // reference can't accidentally drive a released player.
    DisposableEffect(player, nextEpisode, onPlayNext) {
        val pip = com.example.jellyfinplayer.player.PipActionReceiver
        pip.activeIsPlaying = { player.isPlaying }
        pip.activeTogglePlayPause = {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.playWhenReady = true
                player.play()
            }
        }
        pip.activeRewind = {
            val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
            player.seekTo(target)
        }
        pip.activeForward = {
            val dur = player.duration
            val target = player.currentPosition + 30_000L
            val capped = if (dur > 0) target.coerceAtMost(dur) else target
            player.seekTo(capped)
        }
        pip.activeStop = {
            player.playWhenReady = false
            player.pause()
            player.stop()
        }
        pip.activePlayNext =
            if (nextEpisode != null && onPlayNext != null) {
                {
                    android.widget.Toast.makeText(
                        context,
                        "Playing next episode: ${nextEpisode.name}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    isNavigatingAway = true
                    onPlayNext(nextEpisode)
                }
            } else {
                null
            }
        onDispose {
            pip.activeIsPlaying = null
            pip.activeTogglePlayPause = null
            pip.activeRewind = null
            pip.activeForward = null
            pip.activeStop = null
            pip.activePlayNext = null
        }
    }

    var details by remember { mutableStateOf<MediaItem?>(null) }
    var resolved by remember { mutableStateOf<ResolvedStream?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }

    var showSubMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var selectedSubtitleIndex by remember { mutableStateOf<Int?>(null) }
    var selectedAudioIndex by remember { mutableStateOf<Int?>(null) }
    val userSettings = vm.settings.collectAsState().value
    var selectedBitrate by remember { mutableStateOf<Long?>(userSettings.defaultMaxBitrate) }
    // Tracks whether the user EXPLICITLY chose a quality during this player
    // session via the in-player picker, vs. just inheriting their default
    // setting. Matters because attempt 0 ignores the inherited default
    // (preferring direct play) but respects an explicit pick — when the
    // user actively says "I want 720p", that's a hard cap they chose.
    var userPickedQuality by remember { mutableStateOf(false) }
    var fillMode by remember { mutableStateOf(FillMode.FIT) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    var chromeVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // Slider scrub state. The watchdog below clears isDragging if Android
    // misses onValueChangeFinished, which otherwise leaves the bar frozen.
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastDragWallMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    // True once the first video frame has been rendered to the surface. Used to
    // show the Play icon (not Pause) while the player is technically "ready" but
    // no frame is visible yet — prevents the Pause-bars flash before video appears.
    var firstFrameRendered by remember { mutableStateOf(false) }
    var mediaSegments by remember { mutableStateOf<List<MediaSegment>>(emptyList()) }
    var startReported by remember { mutableStateOf(false) }
    var playWhenFirstFrameRenders by remember { mutableStateOf(false) }
    val lastGoodPositionMs = remember { mutableStateOf(0L) }
    val currentNextEpisode by rememberUpdatedState(nextEpisode)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)
    val currentSubtitleIndex by rememberUpdatedState(selectedSubtitleIndex)

    var playbackAttempt by remember { mutableStateOf(0) }
    val maxAttempts = 4

    // Step 1: load full item details so we know audio streams.
    // For local playback we skip this — there's no server to ask, and the
    // file's tracks are read by ExoPlayer directly.
    LaunchedEffect(
        item.id,
        localFilePath,
        userSettings.alwaysPlaySubtitles,
        userSettings.preferredSubtitleLanguage
    ) {
        resolved = null
        resolveError = null
        startReported = false
        lastGoodPositionMs.value = 0L
        playbackAttempt = 0
        selectedAudioIndex = null
        selectedSubtitleIndex = null
        if (localFilePath != null) {
            details = item
            selectedSubtitleIndex = pickPreferredSubtitle(
                item = item,
                alwaysPlaySubtitles = userSettings.alwaysPlaySubtitles,
                preferredLanguage = userSettings.preferredSubtitleLanguage
            )?.index
            return@LaunchedEffect
        }
        details = null
        try {
            val d = vm.loadItemDetails(item.id)
            details = d
            selectedAudioIndex = pickPrimaryAudio(d)?.index
            selectedSubtitleIndex = pickPreferredSubtitle(
                item = d,
                alwaysPlaySubtitles = userSettings.alwaysPlaySubtitles,
                preferredLanguage = userSettings.preferredSubtitleLanguage
            )?.index
        } catch (_: Throwable) {
            details = item
        }
    }

    LaunchedEffect(item.id, localFilePath) {
        mediaSegments = if (localFilePath == null) {
            runCatching { vm.loadIntroSkipperSegments(item.id) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    // Poll at 16 ms (~60 fps). ExoPlayer.currentPosition() is O(1) and
    // thread-safe so the overhead is negligible; it makes the seek bar and
    // time display advance smoothly without any interpolation layer.
    LaunchedEffect(player) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration
                .takeIf { it != ExoC.TIME_UNSET && it > 0L }
                ?: 0L
            isPlaying = player.isPlaying
            delay(16)
        }
    }

    LaunchedEffect(isDragging, lastDragWallMs) {
        if (!isDragging) return@LaunchedEffect
        delay(2_000)
        if (isDragging && System.currentTimeMillis() - lastDragWallMs >= 1_900L) {
            isDragging = false
        }
    }

    LaunchedEffect(chromeVisible, controlsLocked) {
        if (chromeVisible && !controlsLocked) {
            delay(4_000)
            chromeVisible = false
        }
    }

    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            delay(700)
            gestureFeedback = null
        }
    }

    // Step 2: resolve the stream URL whenever inputs change.
    // Skipped entirely for local playback — we already have a file path.
    LaunchedEffect(details, selectedBitrate, selectedAudioIndex, playbackAttempt) {
        if (localFilePath != null) return@LaunchedEffect
        val d = details ?: return@LaunchedEffect
        // Bitrate / force-transcode policy across attempts.
        //
        // attempt 0 (preferred path): original quality unless the user picked
        //   a quality inside this player session. An explicit 360p / 1 Mbps
        //   pick is a hard transcode request, not a hint.
        //
        // attempt 1: keep direct play OFF (force transcode), still no
        //   bitrate cap. Used when direct play started but failed mid-
        //   playback (e.g. an extractor crashed) — let the server transcode
        //   at the source's full bitrate first, since that's still better
        //   quality than a hard-capped re-encode.
        //
        // attempt 2: cap to user's preferred quality (or 8 Mbps if they
        //   haven't picked one). Last-resort attempt to get something
        //   playable on a slow link or weak server.
        //
        // attempt 3: cap to 1 Mbps. If even that fails, we surface the
        //   error to the user — usually a network or server problem the
        //   client can't solve.
        val (effectiveBitrate, attemptForce) = when (playbackAttempt) {
            // Attempt 0: prefer direct play. Only apply a bitrate cap if the
            // user EXPLICITLY picked one in this session's in-player picker;
            // a passively-inherited "Default quality" from settings is
            // treated as a fallback target, not a hard cap on direct play.
            0 -> {
                val explicitCap = if (userPickedQuality) selectedBitrate else null
                explicitCap to (explicitCap != null)
            }
            1 -> null to true
            2 -> (selectedBitrate ?: 8_000_000L) to true
            else -> 1_000_000L to true
        }
        // The user's "Force transcoding" setting is OR'd with the attempt's
        // force flag — once it's on, every request gets forceTranscode=true,
        // which guarantees seeking works even on files whose container or
        // server-side range support is iffy. Most files seek fine on direct
        // play; the toggle is for users with chronically problematic catalogs.
        val force = !userSettings.directPlayOnly &&
            (attemptForce || userSettings.forceTranscoding)
        try {
            resolved = vm.resolveStream(
                item = d,
                maxBitrate = effectiveBitrate,
                audioStreamIndex = selectedAudioIndex,
                subtitleStreamIndex = null,
                forceTranscode = force,
                directPlayOnly = userSettings.directPlayOnly
            )
            resolveError = null
        } catch (t: Throwable) {
            resolveError = t.message ?: "Couldn't get a playable stream from the server."
        }
    }

    // Hide system bars + extend video into the display cutout (notch).
    DisposableEffect(view, activity, inPip) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
                ?: android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        } else 0
        if (!inPip) {
            insets?.let {
                it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsetsCompat.Type.systemBars())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window?.attributes = window?.attributes?.apply {
                    layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        onDispose {
            // Skip both bar-show and cutout-restore when navigating to another
            // player screen — the new screen needs the same window setup, and
            // restoring DEFAULT cutout here causes the next episode's video to
            // shift off-center against the notch.
            if (!isNavigatingAway) {
                insets?.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window?.attributes = window?.attributes?.apply {
                        layoutInDisplayCutoutMode = previousCutoutMode
                    }
                }
            }
            window?.attributes = window?.attributes?.apply {
                screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Player listener — stop/progress reporting and the fallback chain.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    PlayerPresence.aspectWidth = size.width
                    PlayerPresence.aspectHeight = size.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                if (playWhenFirstFrameRenders) {
                    playWhenFirstFrameRenders = false
                    player.playWhenReady = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Belt-and-braces subtitle selection. If the user picked a sub
                // and any text track group exists, force-select it via override.
                // This complements the SubtitleConfiguration we attached to
                // the MediaItem — between the two paths, subs get displayed
                // regardless of whether the source is direct play or HLS.
                if (resolved == null) return
                if (currentSubtitleIndex == null) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(ExoC.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(ExoC.TRACK_TYPE_TEXT, true)
                        .build()
                    return
                }
                val textGroup = tracks.groups
                    .filter { it.type == ExoC.TRACK_TYPE_TEXT && it.length > 0 }
                    .firstOrNull { group ->
                        // Prefer the sideloaded VTT (we tagged it with a known
                        // ID so we can find it among any embedded text tracks).
                        (0 until group.length).any { i ->
                            group.getTrackFormat(i).id == SIDELOAD_SUBTITLE_ID
                        }
                    }
                    ?: tracks.groups.firstOrNull {
                        // Fallback: no sideload (e.g. user picked a sub on a
                        // file with only embedded subs). Pick first text group.
                        it.type == ExoC.TRACK_TYPE_TEXT && it.length > 0
                    }
                    ?: return
                val override = TrackSelectionOverride(textGroup.mediaTrackGroup, 0)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(ExoC.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(override)
                    .build()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (userSettings.directPlayOnly) {
                    resolveError = "Direct play failed for this file."
                    return
                }
                if (playbackAttempt < maxAttempts - 1) {
                    playbackAttempt += 1
                } else {
                    resolveError = "This file couldn't be played even after " +
                        "transcoding. The server may not have ffmpeg properly " +
                        "configured, or the file is corrupt."
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val r = resolved
                if (r != null) {
                    val pos = player.currentPosition
                    if (pos > 0) lastGoodPositionMs.value = pos
                    vm.reportProgress(
                        itemId = item.id,
                        mediaSourceId = r.mediaSourceId,
                        playSessionId = r.playSessionId,
                        positionMs = pos,
                        isPaused = !isPlaying,
                        playMethod = r.playMethod
                    )
                }
                // Refresh PiP params when the play state changes so the
                // overlay's play/pause icon stays in sync. Only matters
                // while we're actually in PiP — the controls are inside
                // the PiP window.
                if (inPip && activity != null &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ) {
                    runCatching {
                        activity.setPictureInPictureParams(
                            com.example.jellyfinplayer.player.buildPipParamsForPlayer(
                                activity = activity,
                                aspectWidth = player.videoSize.width,
                                aspectHeight = player.videoSize.height,
                                isPlaying = isPlaying,
                                hasNext = nextEpisode != null && onPlayNext != null
                            )
                        )
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Detect "seek collapsed to start" — ExoPlayer's MKV
                // extractor has a known bug (Media3 #1143) where files
                // missing proper Cues/SeekHead can't seek; any seek attempt
                // returns the playhead to ~0 instead of the requested
                // position. The user sees the seek bar fly back to start.
                //
                // Auto-fallback: bump playbackAttempt to 1 which forces a
                // transcode on the next resolveStream pass.
                val r = resolved ?: return
                if (userSettings.directPlayOnly) return
                val isDirect = r.playMethod == "DirectPlay" ||
                    r.playMethod == "DirectStream"
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                if (!isDirect) return
                if (playbackAttempt != 0) return
                if (newPosition.positionMs < 2_000L &&
                    oldPosition.positionMs > 5_000L
                ) {
                    lastGoodPositionMs.value = oldPosition.positionMs
                    playbackAttempt = 1
                }
            }

            // Called whenever the timeline updates. For progressive sources
            // the timeline is set after preparation when the SeekMap is
            // resolved; that's the earliest reliable moment to know whether
            // seeking is possible.
            //
            // We check seekability HERE rather than only on STATE_READY
            // because some files report "ready to play" before the seek
            // info is available, and PlayerView's seek bar has already
            // greyed out by the time STATE_READY fires.
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                checkSeekabilityAndFallback()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                val r = resolved
                if (playbackState == Player.STATE_READY && !startReported && r != null) {
                    startReported = true
                    vm.reportStart(
                        itemId = item.id,
                        mediaSourceId = r.mediaSourceId,
                        playSessionId = r.playSessionId,
                        positionMs = player.currentPosition,
                        playMethod = r.playMethod
                    )
                }
                if (playbackState == Player.STATE_READY) {
                    // Re-check on every STATE_READY in case timeline updates
                    // happen async — some MKV extractors only finalize their
                    // SeekMap after a few sample reads.
                    checkSeekabilityAndFallback()
                }
                if (playbackState == Player.STATE_ENDED) {
                    val next = currentNextEpisode
                    val playNext = currentOnPlayNext
                    if (next != null && playNext != null) {
                        android.widget.Toast.makeText(
                            context,
                            "Playing next episode: ${next.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isNavigatingAway = true
                        playNext(next)
                    } else {
                        onBack()
                    }
                }
            }

            /**
             * Centralized check: if we're on direct play AND the player
             * reports the source is not seekable, force a transcode reload.
             * The transcoded HLS stream always supports seeking via its
             * segment boundaries.
             *
             * No `duration > 0` gate — duration may not be known yet, but
             * if the player tells us we can't seek, that's already a hard
             * signal regardless of duration. Better to fall back early.
             */
            fun checkSeekabilityAndFallback() {
                val r = resolved ?: return
                if (userSettings.directPlayOnly) return
                if (playbackAttempt != 0) return
                val isDirect = r.playMethod == "DirectPlay" ||
                    r.playMethod == "DirectStream"
                if (!isDirect) return
                // Need a non-empty timeline before we can read seekability.
                if (player.currentTimeline.isEmpty) return
                if (!player.isCurrentMediaItemSeekable) {
                    playbackAttempt = 1 // → forceTranscode = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            // Final stop report BEFORE we release the player (release nukes
            // currentPosition). Then release everything we own.
            val pos = if (player.currentPosition > 0) player.currentPosition
                      else lastGoodPositionMs.value
            val r = resolved
            vm.reportStop(item.id, r?.mediaSourceId, r?.playSessionId, pos)
            player.removeListener(listener)
            player.release()
            vm.refreshHomeRows()
        }
    }

    // Step 3 (local): for downloaded files, set up the player from a local
    // URI directly and attach any downloaded text subtitle sidecar the user
    // picked from the subtitle menu.
    LaunchedEffect(player, localFilePath, selectedSubtitleIndex) {
        if (localFilePath == null) return@LaunchedEffect
        val d = details ?: item

        val file = java.io.File(localFilePath)
        if (!file.exists() || file.length() == 0L) {
            // Surface a clear error rather than letting ExoPlayer fail
            // with a generic message. The downloaded file is gone (perhaps
            // the user cleared the app's data or the download never
            // finished writing).
            resolveError = "Downloaded file is missing. It may have been deleted."
            return@LaunchedEffect
        }

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            // Default text disabled so we don't surprise the user with subs
            // they didn't ask for. They can flip them on via the subtitle
            // picker if the file has embedded text tracks.
            .setTrackTypeDisabled(ExoC.TRACK_TYPE_TEXT, selectedSubtitleIndex == null)
            .setSelectUndeterminedTextLanguage(true)
            .clearOverridesOfType(ExoC.TRACK_TYPE_TEXT)
            .build()

        val selectedSubtitle = selectedSubtitleIndex?.let { index ->
            d.mediaSources.firstOrNull()?.mediaStreams
                ?.firstOrNull { it.type == "Subtitle" && it.index == index }
        }
        val uri = android.net.Uri.fromFile(file)
        val builder = ExoMediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(d.name)
                    .setArtist(d.seriesName ?: d.productionYear?.toString() ?: "")
                    .build()
            )
        val subtitlePath = selectedSubtitle?.deliveryUrl
        if (!subtitlePath.isNullOrBlank()) {
            val subFile = java.io.File(subtitlePath)
            if (subFile.exists()) {
                val sub = ExoMediaItem.SubtitleConfiguration.Builder(
                    android.net.Uri.fromFile(subFile)
                )
                    .setMimeType(localSubtitleMimeType(subFile.extension))
                    .setSelectionFlags(ExoC.SELECTION_FLAG_DEFAULT)
                    .setLanguage(selectedSubtitle.language ?: "und")
                    .setId(SIDELOAD_SUBTITLE_ID)
                    .build()
                builder.setSubtitleConfigurations(listOf(sub))
            }
        }
        val seekTo = player.currentPosition.takeIf { it > 0L } ?: 0L
        player.setMediaItem(builder.build(), seekTo)
        firstFrameRendered = false
        playWhenFirstFrameRenders = true
        player.prepare()
        player.playWhenReady = false
    }

    // Step 3: feed a MediaItem (with sideloaded subtitle if any) to the
    // embedded ExoPlayer. Because there's no IPC, the SubtitleConfiguration
    // actually reaches the player intact.
    LaunchedEffect(player, resolved, selectedSubtitleIndex) {
        if (localFilePath != null) return@LaunchedEffect
        val r = resolved ?: return@LaunchedEffect
        val d = details ?: return@LaunchedEffect
        val selectedSubtitle = selectedSubtitleIndex?.let { index ->
            d.mediaSources.firstOrNull()?.mediaStreams
                ?.firstOrNull { it.type == "Subtitle" && it.index == index }
        }
        val streamCanCarryEmbeddedSubtitles = r.playMethod == "DirectPlay" ||
            r.playMethod == "DirectStream"
        val useNativeSubtitle = selectedSubtitle?.codec
            ?.lowercase()
            ?.let { (it == "ass" || it == "ssa") && streamCanCarryEmbeddedSubtitles } == true
        val subtitleUrl = selectedSubtitleIndex?.takeUnless { useNativeSubtitle }?.let {
            vm.subtitleUrl(d.id, r.mediaSourceId, it)
        }

        // Set track-selection params BEFORE setMediaItem so the initial track
        // selection pass during prepare uses them.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(ExoC.TRACK_TYPE_TEXT, selectedSubtitleIndex == null)
            .setSelectUndeterminedTextLanguage(true)
            .clearOverridesOfType(ExoC.TRACK_TYPE_TEXT)
            .build()

        val builder = ExoMediaItem.Builder()
            .setUri(r.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(d.name)
                    .setArtist(d.seriesName ?: d.productionYear?.toString() ?: "")
                    .build()
            )

        if (subtitleUrl != null) {
            // Always VTT: server's Stream.vtt endpoint converts every source
            // format. Tag with a known id so onTracksChanged can find this
            // specific track among any embedded text tracks the file contains
            // (we want to render only the sideload, never both).
            val sub = ExoMediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.parse(subtitleUrl)
            )
                .setMimeType(MimeTypes.TEXT_VTT)
                .setSelectionFlags(ExoC.SELECTION_FLAG_DEFAULT)
                .setLanguage("und")
                .setId(SIDELOAD_SUBTITLE_ID)
                .build()
            builder.setSubtitleConfigurations(listOf(sub))
        }

        val currentPositionBeforeReload = player.currentPosition.takeIf { it > 0 }
        if (currentPositionBeforeReload != null) {
            lastGoodPositionMs.value = currentPositionBeforeReload
        }
        val seekTo = if (!startReported) {
            if (userSettings.autoResume) {
                d.resumePositionMs?.takeIf { it > 5_000 } ?: 0L
            } else 0L
        } else {
            currentPositionBeforeReload ?: lastGoodPositionMs.value
        }

        player.setMediaItem(builder.build(), seekTo)
        firstFrameRendered = false
        playWhenFirstFrameRenders = true
        player.prepare()
        player.playWhenReady = false
    }

    LaunchedEffect(playWhenFirstFrameRenders, player) {
        if (!playWhenFirstFrameRenders) return@LaunchedEffect
        delay(1_500)
        if (playWhenFirstFrameRenders) {
            playWhenFirstFrameRenders = false
            // Re-seek to flush the subtitle renderer from the correct position.
            // Without this the subtitle track can start from t=0 instead of
            // the position we seeked to when rebuilding the MediaItem.
            val syncPos = lastGoodPositionMs.value
            if (syncPos > 0) player.seekTo(syncPos)
            player.playWhenReady = true
        }
    }

    // Periodic 10s progress + last-known-position update. Only fires when
    // there's a server stream to report TO — local playback doesn't talk
    // to any server.
    LaunchedEffect(player, resolved, localFilePath) {
        if (localFilePath != null) return@LaunchedEffect
        val r = resolved ?: return@LaunchedEffect
        while (true) {
            delay(10_000)
            val pos = player.currentPosition
            if (pos > 0) lastGoodPositionMs.value = pos
            if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                vm.reportProgress(
                    itemId = item.id,
                    mediaSourceId = r.mediaSourceId,
                    playSessionId = r.playSessionId,
                    positionMs = pos,
                    isPaused = !player.isPlaying,
                    playMethod = r.playMethod
                )
            }
        }
    }

    BackHandler {
        if (controlsLocked) controlsLocked = false else onBack()
    }

    val activeIntroSegment = mediaSegments.activeSegment(
        currentPositionMs,
        "intro",
        "introduction"
    )
    val activeCreditsSegment = mediaSegments.activeSegment(
        currentPositionMs,
        "credit",
        "credits",
        "outro"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerBrightnessVolumeGestures(
                activity = activity,
                audioManager = audioManager,
                enabled = false,
                onFeedback = { gestureFeedback = it }
            )
    ) {
        when {
            resolveError != null -> {
                Text(
                    resolveError ?: "",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            localFilePath == null && resolved == null -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowSubtitleButton(false)
                            resizeMode = fillMode.toResizeMode()
                            this.player = player
                            subtitleView?.apply {
                                setApplyEmbeddedStyles(false)
                                setApplyEmbeddedFontSizes(false)
                                setStyle(
                                    androidx.media3.ui.CaptionStyleCompat(
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT,
                                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                        android.graphics.Color.BLACK,
                                        null
                                    )
                                )
                                setFractionalTextSize(0.06f)
                            }
                        }
                    },
                    update = { pv ->
                        pv.useController = false
                        pv.resizeMode = fillMode.toResizeMode()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Transparent overlay — tapping toggles chrome visibility.
        // When locked, any tap just reveals the lock button in the top bar.
        if (!inPip) {
            Box(
                Modifier
                    .matchParentSize()
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (controlsLocked) chromeVisible = true
                        else chromeVisible = !chromeVisible
                    }
            )
        }

        // ── Top bar ──────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = chromeVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(3f)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.75f),
                            0.6f to Color.Black.copy(alpha = 0.3f),
                            1f to Color.Transparent
                        )
                    )
                    .statusBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (!controlsLocked) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (controlsLocked) {
                            PlayerLockButton(
                                locked = true,
                                onToggle = { controlsLocked = false; chromeVisible = true }
                            )
                        } else {
                            if (activity != null && supportsPip(activity)) {
                                IconButton(onClick = {
                                    enterPip(
                                        activity = activity,
                                        player = player,
                                        hasNext = nextEpisode != null && onPlayNext != null
                                    )
                                }) {
                                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in picture")
                                }
                            }
                            if (localFilePath == null) {
                                IconButton(onClick = { showQualityMenu = true }) {
                                    Icon(Icons.Default.HighQuality, contentDescription = "Quality")
                                }
                            }
                            PlayerLockButton(
                                locked = false,
                                onToggle = { controlsLocked = true; chromeVisible = false }
                            )
                            IconButton(onClick = {
                                fillMode = FillMode.values()[(fillMode.ordinal + 1) % FillMode.values().size]
                            }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Sizing: ${fillMode.label}")
                            }
                            IconButton(onClick = { showSpeedMenu = true }) {
                                Icon(Icons.Default.Speed, contentDescription = "Playback speed")
                            }
                            IconButton(onClick = { showAudioMenu = true }) {
                                Icon(Icons.Default.Audiotrack, contentDescription = "Audio track")
                            }
                            IconButton(onClick = { showSubMenu = true }) {
                                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }

        // ── Center transport controls ─────────────────────────────────────────
        // Hidden during loading (no first frame yet, or buffering) so the user
        // sees only the spinner — no stale Pause icon flash.
        val exoLoading = !firstFrameRendered || isBuffering || playWhenFirstFrameRenders
        AnimatedVisibility(
            visible = chromeVisible && !inPip && !controlsLocked && !exoLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(3f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.seekTo(0L) }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Restart",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { player.seekTo((currentPositionMs - 10_000L).coerceAtLeast(0L)) }) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (player.isPlaying && firstFrameRendered) player.pause() else player.play() },
                    contentAlignment = Alignment.Center
                ) {
                    // Only show Pause after the first frame is rendered — before that,
                    // ExoPlayer can be technically "playing" but no video is visible yet.
                    val showPause = isPlaying && firstFrameRendered
                    Icon(
                        imageVector = if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (showPause) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { player.seekTo(currentPositionMs + 10_000L) }) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val next = nextEpisode ?: return@IconButton
                        isNavigatingAway = true
                        onPlayNext?.invoke(next)
                    },
                    enabled = nextEpisode != null && onPlayNext != null,
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Play next episode",
                        tint = if (nextEpisode != null && onPlayNext != null) Color.White
                               else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── Bottom time + seekbar ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = chromeVisible && !inPip && !controlsLocked && !exoLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(4f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.80f)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                val displayMs = if (isDragging && durationMs > 0L)
                    (dragFraction * durationMs).toLong() else currentPositionMs
                Text(
                    "${formatMs(displayMs)}  -  ${formatMs(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                val sliderValue = if (isDragging) dragFraction
                    else if (durationMs > 0L)
                        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f
                PlayerSeekBar(
                    fraction = sliderValue,
                    enabled = durationMs > 0L,
                    onScrub = { fraction ->
                        isDragging = true
                        lastDragWallMs = System.currentTimeMillis()
                        dragFraction = fraction
                    },
                    onSeek = { fraction ->
                        if (durationMs > 0L) {
                            val targetMs = (fraction * durationMs).toLong()
                                .coerceIn(0L, durationMs)
                            dragFraction = fraction
                            currentPositionMs = targetMs
                            player.seekTo(targetMs)
                        }
                        isDragging = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Buffering / initial-load spinner. Shown whenever the player is
        // waiting for data — covers the "paused logo during startup" case
        // (playWhenFirstFrameRenders) and mid-playback stalls after seeking.
        // Rendered outside the chrome AnimatedVisibility so it's always
        // visible even when controls are hidden.
        if (!inPip && (isBuffering || playWhenFirstFrameRenders)) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
        }

        PlayerGestureFeedback(
            text = gestureFeedback,
            modifier = Modifier.align(Alignment.Center)
        )

        if (!controlsLocked && !inPip && activeIntroSegment != null) {
            Button(
                onClick = { player.seekTo(activeIntroSegment.endMs) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 96.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Skip intro", color = Color.Black)
            }
        }

        val creditsNext = nextEpisode
        val creditsPlayNext = onPlayNext
        if (!controlsLocked && !inPip && activeCreditsSegment != null &&
            creditsNext != null && creditsPlayNext != null
        ) {
            Button(
                onClick = { isNavigatingAway = true; creditsPlayNext(creditsNext) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 96.dp)
            ) {
                Text("Next episode")
            }
        }

        if (showAudioMenu && !inPip) {
            val audioStreams = details?.mediaSources?.firstOrNull()?.mediaStreams
                ?.filter { it.type == "Audio" } ?: emptyList()
            if (audioStreams.isEmpty()) {
                SimpleInfoDialog(
                    title = "Audio",
                    message = "This title doesn't have any audio tracks.",
                    onDismiss = { showAudioMenu = false }
                )
            } else {
                StreamPickerDialog(
                    title = "Audio track",
                    options = audioStreams.map { stream ->
                        stream.index to formatAudioLabel(stream)
                    },
                    selected = selectedAudioIndex,
                    onSelect = { picked ->
                        selectedAudioIndex = picked
                        playbackAttempt = 0
                        showAudioMenu = false
                    },
                    onDismiss = { showAudioMenu = false }
                )
            }
        }

        if (showSubMenu && !inPip) {
            val subs = details?.mediaSources?.firstOrNull()?.mediaStreams
                ?.filter { it.type == "Subtitle" } ?: emptyList()
            StreamPickerDialog(
                title = "Subtitles",
                options = listOf(null to "Off") + subs.map {
                    it.index to (it.displayTitle ?: it.language ?: "Track ${it.index}")
                },
                selected = selectedSubtitleIndex,
                onSelect = {
                    selectedSubtitleIndex = it
                    playbackAttempt = 0
                    showSubMenu = false
                },
                onDismiss = { showSubMenu = false }
            )
        }

        if (showQualityMenu && !inPip) {
            StreamPickerDialog(
                title = "Quality",
                options = listOf(
                    null to "Original (let server decide)",
                    20_000_000L to "1080p (~20 Mbps)",
                    8_000_000L to "720p (~8 Mbps)",
                    3_000_000L to "480p (~3 Mbps)",
                    1_000_000L to "360p (~1 Mbps)"
                ),
                selected = selectedBitrate,
                onSelect = {
                    selectedBitrate = it
                    userPickedQuality = true
                    playbackAttempt = 0
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false }
            )
        }

        if (showSpeedMenu && !inPip) {
            SpeedPickerDialog(
                current = playbackSpeed,
                onSelect = { speed ->
                    playbackSpeed = speed
                    player.setPlaybackParameters(
                        androidx.media3.common.PlaybackParameters(speed)
                    )
                },
                onDismiss = { showSpeedMenu = false }
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun FillMode.toResizeMode(): Int = when (this) {
    FillMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    FillMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    FillMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

private fun List<MediaSegment>.activeSegment(
    positionMs: Long,
    vararg names: String
): MediaSegment? = firstOrNull { segment ->
    names.any { segment.type.contains(it, ignoreCase = true) } &&
        positionMs in segment.startMs..segment.endMs
}

private fun supportsPip(activity: Activity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}

private fun enterPip(activity: Activity, player: ExoPlayer?, hasNext: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val params = com.example.jellyfinplayer.player.buildPipParamsForPlayer(
        activity = activity,
        aspectWidth = player?.videoSize?.width ?: 16,
        aspectHeight = player?.videoSize?.height ?: 9,
        isPlaying = player?.isPlaying == true,
        hasNext = hasNext
    )
    try {
        activity.enterPictureInPictureMode(params)
    } catch (_: Throwable) {
    }
}

internal fun pickPrimaryAudio(item: MediaItem): MediaStream? {
    val source = item.mediaSources.firstOrNull() ?: return null
    val audio = source.mediaStreams.filter { it.type == "Audio" }
    if (audio.isEmpty()) return null
    return audio.firstOrNull { !looksLikeCommentaryStream(it) } ?: audio.first()
}

private fun looksLikeCommentaryStream(stream: MediaStream): Boolean {
    val text = listOfNotNull(stream.title, stream.displayTitle).joinToString(" ").lowercase()
    return text.contains("comment") || text.contains("director")
}

private fun formatAudioLabel(stream: MediaStream): String {
    val parts = mutableListOf<String>()
    val title = stream.displayTitle ?: stream.title
    if (!title.isNullOrBlank()) parts += title
    if (!stream.language.isNullOrBlank() && stream.language != "und") {
        parts += stream.language.uppercase()
    }
    if (parts.isEmpty()) parts += "Track ${stream.index}"
    return parts.joinToString(" - ")
}

@Composable
private fun PlayerSeekBar(
    fraction: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var activeFraction by remember { mutableFloatStateOf(fraction.coerceIn(0f, 1f)) }
    val density = LocalDensity.current
    val thumbSize = 20.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    fun fractionForX(x: Float): Float {
        val width = size.width.coerceAtLeast(1)
        return (x / width.toFloat()).coerceIn(0f, 1f)
    }

    val safeFraction = fraction.coerceIn(0f, 1f)
    LaunchedEffect(safeFraction) {
        activeFraction = safeFraction
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { size = it }
            .pointerInput(enabled, size) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek(fractionForX(offset.x))
                }
            }
            .pointerInput(enabled, size) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        activeFraction = fractionForX(offset.x)
                        onScrub(activeFraction)
                    },
                    onDragEnd = {
                        onSeek(activeFraction)
                    },
                    onDragCancel = {
                        onSeek(activeFraction)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        activeFraction = fractionForX(change.position.x)
                        onScrub(activeFraction)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = if (enabled) 0.28f else 0.16f))
        )
        Box(
            Modifier
                .fillMaxWidth(safeFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
        Box(
            Modifier
                .offset {
                    val maxX = (size.width - thumbPx).coerceAtLeast(0f)
                    val centeredX = (size.width * safeFraction) - (thumbPx / 2f)
                    val x = centeredX.coerceIn(0f, maxX).roundToInt()
                    IntOffset(x, 0)
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

private fun pickPreferredSubtitle(
    item: MediaItem,
    alwaysPlaySubtitles: Boolean,
    preferredLanguage: String?
): MediaStream? {
    if (!alwaysPlaySubtitles) return null
    val subtitles = item.mediaSources.firstOrNull()?.mediaStreams
        ?.filter { it.type == "Subtitle" }
        ?: emptyList()
    if (subtitles.isEmpty()) return null
    val preferred = preferredLanguage?.takeIf { it.isNotBlank() } ?: return subtitles.first()
    return subtitles.firstOrNull { it.matchesSubtitleLanguage(preferred) } ?: subtitles.first()
}

private fun MediaStream.matchesSubtitleLanguage(preferredLanguage: String): Boolean {
    val wanted = subtitleLanguageAliases(preferredLanguage)
    val haystack = listOfNotNull(language, displayTitle, title)
        .joinToString(" ")
        .lowercase()
    return wanted.any { it in haystack }
}

private fun localSubtitleMimeType(extension: String): String = when (extension.lowercase()) {
    "srt" -> "application/x-subrip"
    "ass", "ssa" -> "text/x-ssa"
    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
    else -> "application/x-subrip"
}

private fun subtitleLanguageAliases(language: String): Set<String> {
    return when (language.lowercase()) {
        "eng", "en", "english" -> setOf("eng", "en", "english")
        "dan", "da", "danish", "dansk" -> setOf("dan", "da", "danish", "dansk")
        "nor", "no", "nob", "norwegian", "norsk" -> setOf("nor", "no", "nob", "norwegian", "norsk")
        "swe", "sv", "swedish", "svenska" -> setOf("swe", "sv", "swedish", "svenska")
        "fin", "fi", "finnish", "suomi" -> setOf("fin", "fi", "finnish", "suomi")
        "deu", "ger", "de", "german", "deutsch" -> setOf("deu", "ger", "de", "german", "deutsch")
        "fra", "fre", "fr", "french", "francais", "français" -> setOf("fra", "fre", "fr", "french", "francais", "français")
        "spa", "es", "spanish", "espanol", "español" -> setOf("spa", "es", "spanish", "espanol", "español")
        "ita", "it", "italian", "italiano" -> setOf("ita", "it", "italian", "italiano")
        "nld", "dut", "nl", "dutch", "nederlands" -> setOf("nld", "dut", "nl", "dutch", "nederlands")
        "por", "pt", "portuguese", "portugues", "português" -> setOf("por", "pt", "portuguese", "portugues", "português")
        "pol", "pl", "polish", "polski" -> setOf("pol", "pl", "polish", "polski")
        "tur", "tr", "turkish", "turkce", "türkçe" -> setOf("tur", "tr", "turkish", "turkce", "türkçe")
        "jpn", "ja", "japanese" -> setOf("jpn", "ja", "japanese")
        "kor", "ko", "korean" -> setOf("kor", "ko", "korean")
        "zho", "chi", "zh", "chinese" -> setOf("zho", "chi", "zh", "chinese")
        else -> setOf(language.lowercase())
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun SpeedPickerDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                speeds.forEach { speed ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed); onDismiss() }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = speed == current, onClick = { onSelect(speed); onDismiss() })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (speed == 1.0f) "1.0× (normal)" else "${speed}×",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (speed == current) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SimpleInfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun <T> StreamPickerDialog(
    title: String,
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            // LazyColumn so a long subtitle list (Star Wars has 8, foreign-
            // dub releases sometimes have 20+) actually scrolls. The plain
            // Column we used before clipped at AlertDialog's content max
            // height and showed a "ghost" half-row at the bottom.
            // Capped at 360dp so the dialog doesn't take the whole screen
            // when there are many entries.
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(options) { (value, label) ->
                    val isSelected = selected == value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    )
}
