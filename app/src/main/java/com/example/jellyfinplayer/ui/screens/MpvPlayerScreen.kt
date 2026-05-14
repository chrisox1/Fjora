package com.example.jellyfinplayer.ui.screens

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.MediaSegment
import com.example.jellyfinplayer.api.MediaStream
import com.example.jellyfinplayer.api.ResolvedStream
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class MpvFillMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom")
}

/**
 * Compose wrapper around libmpv. Plays a downloaded local file using the
 * bundled mpv engine — used as a fallback when ExoPlayer can't seek a
 * given MKV, or as the user's default player for downloads when the
 * "Use mpv for downloads" setting is on.
 *
 * mpv talks to its own SurfaceView via JNI; we don't share the ExoPlayer
 * pipeline at all. Lifecycle-managed: surface attached on START, detached
 * on STOP, mpv destroyed on screen disposal.
 *
 * Limited UI in this v1 — back button, play/pause, scrub bar, time
 * display. mpv's full feature surface (audio/subtitle switching, decoder
 * selection) is reachable via `MPVLib.command(...)` but not yet wired into
 * a picker UI; that's deliberate scope-cutting since the primary value
 * here is "the file actually plays and seeks."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpvPlayerScreen(
    vm: AppViewModel,
    item: MediaItem,
    localFilePath: String? = null,
    nextEpisode: MediaItem? = null,
    onPlayNext: ((MediaItem) -> Unit)? = null,
    inPip: Boolean = false,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val audioManager = remember(ctx) {
        ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // Smooth seek bar state — interpolated between 500ms polls.
    var smoothPositionMs by remember { mutableLongStateOf(0L) }
    var lastPollWallMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // True once loadfile has been issued. Prevents re-issuing loadfile when
    // the SurfaceView surface is destroyed and recreated (e.g. going home and
    // returning, or the system briefly reclaiming the surface in PiP).
    var fileLoaded by remember { mutableStateOf(false) }
    var mpvBuffering by remember { mutableStateOf(false) }
    // True once MPV has finished its initial load + resume-seek + unpause.
    // Used to hide the play/pause/skip controls during the loading phase
    // (the user only sees the spinner — Findroid-style). Reset on item change.
    var mpvFirstFrameReady by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var gestureFeedback by remember { mutableStateOf<PlayerGestureFeedbackState?>(null) }
    var mediaSegments by remember { mutableStateOf<List<MediaSegment>>(emptyList()) }
    var initError by remember { mutableStateOf<String?>(null) }
    var sourceUrl by remember(localFilePath) { mutableStateOf(localFilePath) }
    var details by remember { mutableStateOf<MediaItem?>(null) }
    var resolved by remember { mutableStateOf<ResolvedStream?>(null) }
    var mpvReady by remember { mutableStateOf(false) }
    var surfaceReady by remember { mutableStateOf(false) }
    var startReported by remember { mutableStateOf(false) }
    var endedHandled by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var externalSubId by remember { mutableStateOf(-1) }
    // Set before any playNext() call so onRelease and surfaceDestroyed skip
    // their MPVLib teardown — the new screen will own MPVLib at that point.
    var isNavigatingAway by remember { mutableStateOf(false) }
    var selectedAudioIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSubtitleIndex by remember { mutableStateOf<Int?>(null) }
    var resumeSurfaceRefreshKey by remember { mutableIntStateOf(0) }
    // Custom Compose subtitle renderer state — used for text-based subs (SRT, ASS, VTT)
    // because the jdtech mpv-android build doesn't include libass so MPV can't render them.
    // Image-based subs (PGS, DVDSUB) still go through MPV's internal sid selector.
    var subtitleCues by remember { mutableStateOf<List<SubCue>>(emptyList()) }
    var currentSubCue by remember { mutableStateOf<SubCue?>(null) }
    var fillMode by remember { mutableStateOf(MpvFillMode.FIT) }
    val subScope = androidx.compose.runtime.rememberCoroutineScope()
    val latestResolved by rememberUpdatedState(resolved)
    val latestPositionMs by rememberUpdatedState(positionMs)

    // Existence check — if the file vanished between download and play,
    // surface a clean error rather than letting mpv fail silently.
    val fileExists = remember(localFilePath) {
        localFilePath == null || File(localFilePath).exists()
    }
    val settings = vm.settings.collectAsState().value

    // Hide system bars + extend video into the display cutout (notch).
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
                ?: android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        } else 0
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes = window?.attributes?.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        onDispose {
            // Skip bar-show and cutout-restore when navigating to another player
            // screen so the next episode keeps the same window setup. Restoring
            // DEFAULT cutout here was causing the next episode's video to shift
            // off-center against the notch on alternating episode transitions.
            if (!isNavigatingAway) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
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

    LaunchedEffect(activity, inPip) {
        if (inPip) return@LaunchedEffect
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    DisposableEffect(lifecycleOwner, activity, inPip) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !inPip) {
                val window = activity?.window ?: return@LifecycleEventObserver
                val controller = WindowCompat.getInsetsController(window, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                if (fileLoaded) resumeSurfaceRefreshKey += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-hide chrome after 4 seconds of no activity.
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(4_000)
            chromeVisible = false
        }
    }

    LaunchedEffect(
        item.id,
        localFilePath,
        settings.directPlayOnly,
        settings.alwaysPlaySubtitles,
        settings.preferredSubtitleLanguage
    ) {
        isNavigatingAway = false
        startReported = false
        endedHandled = false
        fileLoaded = false
        mpvFirstFrameReady = false
        positionMs = 0L
        durationMs = 0L
        subtitleCues = emptyList()
        currentSubCue = null
        resolved = null
        if (localFilePath != null) {
            details = item
            selectedSubtitleIndex = pickPreferredMpvSubtitle(
                item = item,
                alwaysPlaySubtitles = settings.alwaysPlaySubtitles,
                preferredLanguage = settings.preferredSubtitleLanguage
            )?.let { stream ->
                val subtitles = item.mediaSources.firstOrNull()?.mediaStreams
                    ?.filter { it.type == "Subtitle" }
                    ?: emptyList()
                subtitles.indexOfFirst { it.index == stream.index }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            }
            sourceUrl = localFilePath
            return@LaunchedEffect
        }
        sourceUrl = null
        initError = null
        runCatching {
            val loaded = vm.loadItemDetails(item.id)
            val effectiveItem = if (item.forceStartFromBeginning()) {
                loaded.copy(userData = item.userData)
            } else {
                loaded
            }
            details = effectiveItem
            selectedSubtitleIndex = pickPreferredMpvSubtitle(
                item = effectiveItem,
                alwaysPlaySubtitles = settings.alwaysPlaySubtitles,
                preferredLanguage = settings.preferredSubtitleLanguage
            )?.let { stream ->
                val subtitles = effectiveItem.mediaSources.firstOrNull()?.mediaStreams
                    ?.filter { it.type == "Subtitle" }
                    ?: emptyList()
                subtitles.indexOfFirst { it.index == stream.index }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            }
            vm.resolveStream(
                item = effectiveItem,
                maxBitrate = null,
                forceTranscode = false,
                directPlayOnly = settings.directPlayOnly,
                useMpvProfile = true
            )
        }.onSuccess {
            resolved = it
            sourceUrl = it.url
            com.example.jellyfinplayer.data.DiagnosticLog.record(
                ctx,
                "MPV resolved ${(details ?: item).name}: playMethod=${it.playMethod}, directPlayOnly=${settings.directPlayOnly}"
            )
        }.onFailure {
            initError = it.message ?: "Couldn't get a playable stream from the server."
            com.example.jellyfinplayer.data.DiagnosticLog.record(
                ctx,
                "MPV resolve failed for ${item.name}: ${it.message ?: it::class.java.simpleName}"
            )
        }
    }

    LaunchedEffect(item.id, localFilePath) {
        mediaSegments = if (localFilePath == null) {
            runCatching { vm.loadIntroSkipperSegments(item.id) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            delay(700)
            gestureFeedback = null
        }
    }

    // Poll MPV properties every 500 ms — JNI calls are cheap enough but
    // calling them at 60 fps would add unnecessary overhead.
    LaunchedEffect(Unit) {
        while (isActive) {
            runCatching {
                val pos = MPVLib.getPropertyDouble("time-pos")
                val dur = MPVLib.getPropertyDouble("duration")
                val pause = MPVLib.getPropertyBoolean("pause")
                val caching = MPVLib.getPropertyBoolean("paused-for-cache") ?: false
                if (pos != null) {
                    positionMs = (pos * 1000.0).toLong()
                    lastPollWallMs = System.currentTimeMillis()
                }
                if (dur != null) durationMs = (dur * 1000.0).toLong()
                if (pause != null) isPlaying = !pause
                mpvBuffering = caching
                if (subtitleCues.isNotEmpty()) {
                    val nowMs = positionMs + settings.subtitleDelayMs
                    currentSubCue = subtitleCues.firstOrNull { it.startMs <= nowMs && nowMs < it.endMs }
                }
            }
            delay(500)
        }
    }

    // 60 fps loop that interpolates between polls so the seek bar and
    // time display advance smoothly instead of jumping every 500 ms.
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                smoothPositionMs = if (isPlaying && durationMs > 0) {
                    val elapsed = System.currentTimeMillis() - lastPollWallMs
                    (positionMs + (elapsed * playbackSpeed).toLong())
                        .coerceIn(0L, durationMs)
                } else {
                    positionMs
                }
            }
            delay(16)
        }
    }

    LaunchedEffect(mpvReady, surfaceReady, sourceUrl, resumeSurfaceRefreshKey) {
        val source = sourceUrl ?: return@LaunchedEffect
        if (!mpvReady || !surfaceReady) return@LaunchedEffect
        if (fileLoaded) {
            // Surface was destroyed and recreated (going to background then returning).
            // MPV is still running internally but paused (surfaceDestroyed paused it).
            // Seek to the current time-pos after the VO has finished re-initializing on
            // the new surface — this forces MPV to decode and render the paused frame.
            // We retry a few times because time-pos may still be null/0 right after attach.
            runCatching { refreshMpvVideoOutput() }
            return@LaunchedEffect
        }
        fileLoaded = true
        runCatching {
            val resumeMs = if (localFilePath == null) {
                details?.resumePositionMs?.takeIf { it > 5_000L }
            } else null

            // Load without start= option — using start= in the options string
            // causes some MPV builds to report 0/0 duration until the full
            // index is read, making the player appear stuck. Seeking after
            // the demuxer opens is more reliable.
            // Keep paused during setup and the demuxer-open wait. We only
            // unpause at the very end so the user never sees 0–2 s of play
            // from the start before the resume seek kicks in.
            MPVLib.command(arrayOf("loadfile", source))
            MPVLib.setPropertyBoolean("pause", true)
            resetMpvSubtitleTiming()
            applyMpvFillMode(fillMode)
            selectedAudioIndex?.let {
                MPVLib.setPropertyString("aid", mpvTrackPropertyValue(it, "auto"))
            }

            // Route subtitle rendering: image-based subs (PGS, DVDSUB, XSUB) go through
            // MPV's internal sid selector because they're bitmap tracks requiring no libass.
            // Text-based subs (SRT, ASS, VTT, SubRip) are downloaded and rendered via our
            // custom Compose overlay because the jdtech mpv-android build lacks libass.
            val subIdx = selectedSubtitleIndex
            val subStream = if (subIdx != null && subIdx > 0) {
                details?.mediaSources?.firstOrNull()?.mediaStreams
                    ?.filter { it.type == "Subtitle" }
                    ?.getOrNull(subIdx - 1)
            } else null
            if (subStream?.isImageBasedSubtitle() == true) {
                MPVLib.setPropertyString("sid", subIdx.toString())
                MPVLib.setPropertyBoolean("sub-visibility", true)
            } else {
                MPVLib.setPropertyString("sid", "no")
                MPVLib.setPropertyBoolean("sub-visibility", false)
                if (subStream != null && localFilePath == null) {
                    val r = latestResolved
                    if (r != null) {
                        val subUrl = vm.subtitleUrl(item.id, r.mediaSourceId, subStream.index, "srt")
                        subScope.launch {
                            subtitleCues = loadSubtitleCues(ctx, subUrl)
                        }
                    }
                } else if (subStream != null && localFilePath != null) {
                    val path = subStream.deliveryUrl
                    if (!path.isNullOrBlank()) {
                        subScope.launch {
                            subtitleCues = loadLocalSubtitleCues(path)
                        }
                    }
                }
            }
            resetMpvSubtitleTiming()

            endedHandled = false

            if (resumeMs != null) {
                // Wait for the demuxer to open before seeking — using start= in the
                // loadfile options string causes 0/0 duration on some builds.
                smoothPositionMs = resumeMs
                delay(1_500)
                runCatching {
                    MPVLib.command(arrayOf("seek", (resumeMs / 1000.0).toString(), "absolute"))
                    positionMs = resumeMs
                    lastPollWallMs = System.currentTimeMillis()
                }
            }
            // Unpause only after the seek — this prevents the brief 0→resumeMs
            // play-from-beginning that was visible before.
            MPVLib.setPropertyBoolean("pause", false)
            mpvFirstFrameReady = true
        }.onFailure {
            initError = "Couldn't start mpv: ${it.message}"
            com.example.jellyfinplayer.data.DiagnosticLog.record(
                ctx,
                "MPV start failed for ${item.name}: ${it.message ?: it::class.java.simpleName}"
            )
        }
    }

    LaunchedEffect(resolved, localFilePath, item.id) {
        if (localFilePath != null) return@LaunchedEffect
        val r = resolved ?: return@LaunchedEffect
        if (!startReported) {
            startReported = true
            vm.reportStart(item.id, r.mediaSourceId, r.playSessionId, positionMs, r.playMethod)
        }
        while (isActive) {
            delay(10_000)
            vm.reportProgress(
                itemId = item.id,
                mediaSourceId = r.mediaSourceId,
                playSessionId = r.playSessionId,
                positionMs = positionMs,
                isPaused = !isPlaying,
                playMethod = r.playMethod
            )
        }
    }

    DisposableEffect(item.id, localFilePath) {
        onDispose {
            if (localFilePath == null) {
                val r = latestResolved
                vm.reportStop(item.id, r?.mediaSourceId, r?.playSessionId, latestPositionMs)
                vm.refreshHomeRows()
            }
        }
    }

    LaunchedEffect(positionMs, durationMs, nextEpisode, onPlayNext) {
        if (endedHandled) return@LaunchedEffect
        if (durationMs <= 0 || positionMs <= 0) return@LaunchedEffect
        if (durationMs - positionMs > 1_500) return@LaunchedEffect
        endedHandled = true
        val next = nextEpisode
        val playNext = onPlayNext
        if (next != null && playNext != null) {
            Toast.makeText(ctx, "Playing next episode: ${next.name}", Toast.LENGTH_SHORT).show()
            isNavigatingAway = true
            playNext(next)
        } else {
            onBack()
        }
    }

    // Register PiP-overlay action handlers so the play/pause/skip buttons
    // in the PiP overlay drive MPV. Cleared on dispose.
    DisposableEffect(item.id, nextEpisode, onPlayNext, activity, inPip) {
        val pip = com.example.jellyfinplayer.player.PipActionReceiver
        pip.activeIsPlaying = {
            runCatching { MPVLib.getPropertyBoolean("pause") == false }
                .getOrDefault(false)
        }
        pip.activeTogglePlayPause = {
            subScope.launch {
                val paused = MPVLib.getPropertyBoolean("pause") ?: false
                if (paused) refreshMpvVideoOutput()
                runCatching { MPVLib.setPropertyBoolean("pause", !paused) }
            }
        }
        pip.activeRewind = {
            runCatching {
                MPVLib.command(arrayOf("seek", "-10", "relative"))
                resetMpvSubtitleTiming()
            }
        }
        pip.activeForward = {
            runCatching {
                MPVLib.command(arrayOf("seek", "30", "relative"))
                resetMpvSubtitleTiming()
            }
        }
        pip.activeStop = {
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
        }
        pip.activeRefreshPip = {
            if (inPip && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    activity.setPictureInPictureParams(
                        com.example.jellyfinplayer.player.buildPipParamsForPlayer(
                            activity = activity,
                            aspectWidth = com.example.jellyfinplayer.PlayerPresence.aspectWidth,
                            aspectHeight = com.example.jellyfinplayer.PlayerPresence.aspectHeight,
                            isPlaying = runCatching {
                                MPVLib.getPropertyBoolean("pause") == false
                            }.getOrDefault(false),
                            hasNext = nextEpisode != null && onPlayNext != null
                        )
                    )
                }
            }
        }
        pip.activePlayNext =
            if (nextEpisode != null && onPlayNext != null) {
                { onPlayNext(nextEpisode) }
            } else null
        onDispose {
            pip.activeIsPlaying = null
            pip.activeTogglePlayPause = null
            pip.activeRewind = null
            pip.activeForward = null
            pip.activeStop = null
            pip.activePlayNext = null
            pip.activeRefreshPip = null
        }
    }

    // Lifecycle-aware mpv: pause when activity stops (so audio doesn't
    // continue in background after PiP closure or screen-off), resume
    // wasn't auto since user might prefer paused.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    runCatching { MPVLib.setPropertyBoolean("pause", true) }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    BackHandler {
        if (controlsLocked) controlsLocked = false else onBack()
    }

    val activeIntroSegment = mediaSegments.activeSegment(
        positionMs,
        "intro",
        "introduction"
    )
    val activeCreditsSegment = mediaSegments.activeSegment(
        positionMs,
        "credit",
        "credits",
        "outro"
    )

    // Auto-hide flags for the Skip-intro and Next-episode action buttons.
    // While the corresponding segment is active they show for 10 s, then
    // smoothly fade away. Any tap (chromeVisible flip) restarts the timer.
    var skipIntroHidden by remember(item.id) { mutableStateOf(false) }
    var nextEpisodeHidden by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(activeIntroSegment != null, chromeVisible) {
        if (activeIntroSegment != null) {
            skipIntroHidden = false
            delay(10_000L)
            skipIntroHidden = true
        }
    }
    LaunchedEffect(activeCreditsSegment != null, chromeVisible) {
        if (activeCreditsSegment != null) {
            nextEpisodeHidden = false
            delay(10_000L)
            nextEpisodeHidden = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerBrightnessVolumeGestures(
                activity = activity,
                audioManager = audioManager,
                enabled = false,
                onFeedback = { gestureFeedback = it }
            )
    ) {
        if (!fileExists) {
            Text(
                "Downloaded file is missing.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        } else if (initError != null) {
            Text(
                initError!!,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        } else {
            // mpv SurfaceView. Created exactly once and kept alive for the
            // screen's lifetime. We initialize MPVLib here in factory and
            // tear it down in the AndroidView disposal.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val surfaceView = SurfaceView(context)
                    try {
                        // Destroy any stale singleton left by a previous
                        // session — MPVLib is a native singleton and must be
                        // in a clean state before create().
                        runCatching { MPVLib.destroy() }
                        MPVLib.create(context.applicationContext)
                        MPVLib.setOptionString("vo", "gpu")
                        MPVLib.setOptionString("gpu-context", "android")
                        // mediacodec-copy: hardware decode + copy frame to GPU
                        // buffer so vo=gpu can composite it.
                        MPVLib.setOptionString("hwdec", "mediacodec-copy")
                        MPVLib.setOptionString("cache", "yes")
                        MPVLib.setOptionString("demuxer-max-bytes", "64MiB")
                        MPVLib.setOptionString("demuxer-max-back-bytes", "16MiB")
                        MPVLib.setOptionString("ao", "audiotrack")
                        MPVLib.setOptionString("osd-level", "0")
                        // DO NOT set force-window here. force-window=yes causes
                        // MPV to create an EGL context during init() before any
                        // surface is attached, producing a 1×1 dummy window.
                        // When attachSurface() is called later MPV ignores it
                        // and renders to the dummy — black screen. Let MPV
                        // create its context when the real surface is attached.
                        MPVLib.setOptionString("idle", "yes")
                        MPVLib.init()
                        mpvReady = true
                        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                            // Standard mpv-android lifecycle: on create, attach + reactivate
                            // the gpu vo; on size change, update android-surface-size; on
                            // destroy, set vo=null + force-window=no, then detach. Without
                            // this dance the video stays black after a surface re-creation
                            // (multi-window / split-screen / PiP-style overlay return).
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                runCatching {
                                    MPVLib.attachSurface(holder.surface)
                                    MPVLib.setOptionString("force-window", "yes")
                                    MPVLib.setOptionString("vo", "gpu")
                                }
                                surfaceReady = true
                            }
                            override fun surfaceChanged(
                                holder: SurfaceHolder, format: Int, width: Int, height: Int
                            ) {
                                runCatching {
                                    MPVLib.setPropertyString(
                                        "android-surface-size",
                                        "${width}x${height}"
                                    )
                                }
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceReady = false
                                if (!isNavigatingAway) {
                                    runCatching {
                                        // Tell MPV there's no usable video output before
                                        // detaching the surface, otherwise it can keep
                                        // rendering into a torn-down surface and end up
                                        // black after re-attach.
                                        MPVLib.setPropertyString("vo", "null")
                                        MPVLib.setOptionString("force-window", "no")
                                        MPVLib.detachSurface()
                                    }
                                }
                            }
                        })
                    } catch (t: Throwable) {
                        initError = "Couldn't start mpv: ${t.message}"
                    }
                    surfaceView
                },
                onRelease = {
                    if (!isNavigatingAway) runCatching { MPVLib.destroy() }
                    mpvReady = false
                    surfaceReady = false
                }
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .playerBrightnessVolumeGestures(
                    activity = activity,
                    audioManager = audioManager,
                    enabled = !controlsLocked,
                    onFeedback = { gestureFeedback = it }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (controlsLocked) chromeVisible = true
                    else chromeVisible = !chromeVisible
                }
        )

        // ── Top bar ──────────────────────────────────────────────────────────
        // Suppressed in PiP — the title bar and action icons would otherwise
        // render at full size inside the tiny PiP window for the second or two
        // before they auto-hide, which read as "huge logos".
        AnimatedVisibility(
            visible = chromeVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                            PlayerLockButton(
                                locked = false,
                                onToggle = { controlsLocked = true; chromeVisible = false }
                            )
                            IconButton(onClick = {
                                val nextMode = MpvFillMode.values()[
                                    (fillMode.ordinal + 1) % MpvFillMode.values().size
                                ]
                                fillMode = nextMode
                                runCatching { applyMpvFillMode(nextMode) }
                            }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Sizing: ${fillMode.label}", tint = Color.White)
                            }
                            IconButton(onClick = { showSpeedMenu = true }) {
                                Icon(Icons.Default.Speed, contentDescription = "Playback speed", tint = Color.White)
                            }
                            IconButton(onClick = { showAudioMenu = true }) {
                                Icon(Icons.Default.Audiotrack, contentDescription = "Audio track", tint = Color.White)
                            }
                            IconButton(onClick = { showSubMenu = true }) {
                                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
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
        // Hidden during the loading phase (before first frame ready or while
        // buffering) so the user only sees the spinner — no stale Pause icon.
        val mpvLoading = !mpvFirstFrameReady || mpvBuffering ||
            (fileLoaded && durationMs == 0L)
        AnimatedVisibility(
            visible = chromeVisible && !controlsLocked && !mpvLoading && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        runCatching {
                            MPVLib.command(arrayOf("seek", "0", "absolute"))
                            resetMpvSubtitleTiming()
                        }
                    }
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Restart", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                IconButton(
                    onClick = {
                        runCatching {
                            MPVLib.command(arrayOf("seek", "-10", "relative"))
                            resetMpvSubtitleTiming()
                        }
                    }
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            subScope.launch {
                                val paused = MPVLib.getPropertyBoolean("pause") ?: false
                                if (paused) refreshMpvVideoOutput()
                                runCatching { MPVLib.setPropertyBoolean("pause", !paused) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = {
                        runCatching {
                            MPVLib.command(arrayOf("seek", "10", "relative"))
                            resetMpvSubtitleTiming()
                        }
                    }
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                IconButton(
                    onClick = {
                        val next = nextEpisode
                        val playNext = onPlayNext
                        if (next != null && playNext != null && !endedHandled) {
                            endedHandled = true
                            isNavigatingAway = true
                            playNext(next)
                        }
                    },
                    enabled = nextEpisode != null && onPlayNext != null,
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Play next episode",
                        tint = if (nextEpisode != null && onPlayNext != null) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── Bottom time + seekbar ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = chromeVisible && !controlsLocked && !mpvLoading && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.80f)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                val displayMs = if (isDragging && durationMs > 0)
                    (dragFraction * durationMs).toLong() else smoothPositionMs
                Text(
                    "${formatMs(displayMs)}  -  ${formatMs(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = if (isDragging) dragFraction
                    else if (durationMs > 0)
                        (smoothPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f,
                    onValueChange = { fraction ->
                        isDragging = true
                        dragFraction = fraction
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0) {
                            val targetSec = (dragFraction * durationMs / 1000.0)
                            runCatching {
                                MPVLib.command(arrayOf("seek", targetSec.toString(), "absolute"))
                                resetMpvSubtitleTiming()
                                positionMs = (dragFraction * durationMs).toLong()
                                smoothPositionMs = positionMs
                                lastPollWallMs = System.currentTimeMillis()
                            }
                        }
                        isDragging = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Buffering spinner — shown when MPV is paused waiting for the
        // network cache (seeking stall) or when the file is loading
        // (fileLoaded=true but duration not yet reported).
        val showMpvSpinner = mpvBuffering || (fileLoaded && durationMs == 0L && sourceUrl != null)
        if (showMpvSpinner) {
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
            feedback = gestureFeedback,
            modifier = Modifier.matchParentSize()
        )

        // Custom subtitle overlay — text cues downloaded from Jellyfin and
        // rendered here because MPV's text subtitle support requires libass
        // which is not present in the jdtech mpv-android build.
        val activeCue = currentSubCue
        if (activeCue != null) {
            val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
            val baseSubtitleSp = if (inPip) {
                (screenHeightDp * 0.04f).coerceIn(14f, 20f)
            } else {
                (screenHeightDp * 0.05f).coerceIn(17f, 36f)
            }
            val scaledSubtitleFontSize = (baseSubtitleSp * settings.subtitleTextScale).sp
            val scaledSubtitleLineHeight = (baseSubtitleSp * 1.25f * settings.subtitleTextScale).sp
            // User-controlled vertical position (fraction of screen height from
            // the bottom edge). In PiP we ignore the setting and use a tight
            // bottom margin so subtitles don't get pushed off the tiny window.
            val subtitleBottomPadding = if (inPip) {
                10.dp
            } else {
                (screenHeightDp * settings.subtitlePositionFraction).dp
            }
            val subtitleSidePadding = if (inPip) 8.dp else 24.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = subtitleBottomPadding,
                        start = subtitleSidePadding,
                        end = subtitleSidePadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeCue.text,
                    fontSize = scaledSubtitleFontSize,
                    lineHeight = scaledSubtitleLineHeight,
                    fontWeight = FontWeight.Normal,
                    color = subtitleComposeColor(settings.subtitleColor),
                    textAlign = TextAlign.Center,
                    maxLines = if (inPip) 2 else Int.MAX_VALUE,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (settings.subtitleBackground) {
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                    .padding(vertical = 2.dp)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // Overlays follow the player chrome: visible only when controls are
        // visible AND the first frame has rendered. Hidden while MPV is still
        // loading. Auto-fades after 10 s; tap-to-reveal restarts the timer.
        AnimatedVisibility(
            visible = chromeVisible && mpvFirstFrameReady &&
                !controlsLocked && !inPip &&
                activeIntroSegment != null && !skipIntroHidden,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 96.dp)
        ) {
            val segment = activeIntroSegment ?: return@AnimatedVisibility
            Button(
                onClick = {
                    runCatching {
                        MPVLib.command(
                            arrayOf("seek", (segment.endMs / 1000.0).toString(), "absolute")
                        )
                        resetMpvSubtitleTiming()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Skip intro", color = Color.Black)
            }
        }

        val creditsNext = nextEpisode
        val creditsPlayNext = onPlayNext
        AnimatedVisibility(
            visible = chromeVisible && mpvFirstFrameReady &&
                !controlsLocked && !inPip &&
                activeCreditsSegment != null && !nextEpisodeHidden &&
                creditsNext != null && creditsPlayNext != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 96.dp)
        ) {
            if (creditsNext == null || creditsPlayNext == null) return@AnimatedVisibility
            Button(
                onClick = {
                    if (!endedHandled) {
                        endedHandled = true
                        isNavigatingAway = true
                        creditsPlayNext(creditsNext)
                    }
                }
            ) {
                Text("Next episode")
            }
        }

        if (showAudioMenu) {
            val audioStreams = details?.mediaSources?.firstOrNull()?.mediaStreams
                ?.filter { it.type == "Audio" } ?: emptyList()
            if (audioStreams.isEmpty()) {
                MpvInfoDialog(
                    title = "Audio",
                    message = "No audio tracks were reported for this title.",
                    onDismiss = { showAudioMenu = false }
                )
            } else {
                MpvStreamPickerDialog(
                    title = "Audio track",
                    options = listOf<Pair<Int?, String>>(-1 to "Auto") +
                        audioStreams.mapIndexed { trackIndex, stream ->
                            (trackIndex + 1) to formatMpvStreamLabel(stream, trackIndex + 1)
                        },
                    selected = selectedAudioIndex,
                    onSelect = { picked ->
                        selectedAudioIndex = picked
                        runCatching { MPVLib.setPropertyString("aid", mpvTrackPropertyValue(picked, "auto")) }
                        showAudioMenu = false
                    },
                    onDismiss = { showAudioMenu = false }
                )
            }
        }

        if (showSubMenu) {
            val subs = details?.mediaSources?.firstOrNull()?.mediaStreams
                ?.filter { it.type == "Subtitle" } ?: emptyList()
            MpvStreamPickerDialog(
                title = "Subtitles",
                options = listOf<Pair<Int?, String>>(null to "Off", -1 to "Auto") +
                    subs.mapIndexed { trackIndex, stream ->
                        (trackIndex + 1) to formatMpvSubtitleLabel(stream, trackIndex + 1)
                    },
                selected = selectedSubtitleIndex,
                onSelect = { picked ->
                    selectedSubtitleIndex = picked
                    subtitleCues = emptyList()
                    currentSubCue = null
                    // Clear any previously loaded external sub track from MPV.
                    runCatching {
                        if (externalSubId >= 0) {
                            MPVLib.command(arrayOf("sub-remove", externalSubId.toString()))
                            externalSubId = -1
                        }
                        MPVLib.setPropertyString("sid", "no")
                        MPVLib.setPropertyBoolean("sub-visibility", false)
                        resetMpvSubtitleTiming()
                    }
                    // Image-based subs → MPV sid. Text subs → custom Compose renderer.
                    if (picked != null && picked > 0) {
                        val subStream = details?.mediaSources?.firstOrNull()?.mediaStreams
                            ?.filter { it.type == "Subtitle" }
                            ?.getOrNull(picked - 1)
                        if (subStream?.isImageBasedSubtitle() == true) {
                            runCatching {
                                MPVLib.setPropertyString("sid", picked.toString())
                                MPVLib.setPropertyBoolean("sub-visibility", true)
                            }
                        } else if (subStream != null && localFilePath == null) {
                            val r = latestResolved
                            if (r != null) {
                                val subUrl = vm.subtitleUrl(item.id, r.mediaSourceId, subStream.index, "srt")
                                subScope.launch {
                                    subtitleCues = loadSubtitleCues(ctx, subUrl)
                                }
                            }
                        } else if (subStream != null && localFilePath != null) {
                            val path = subStream.deliveryUrl
                            if (!path.isNullOrBlank()) {
                                subScope.launch {
                                    subtitleCues = loadLocalSubtitleCues(path)
                                }
                            }
                        }
                    }
                    showSubMenu = false
                },
                onDismiss = { showSubMenu = false }
            )
        }

        if (showSpeedMenu) {
            MpvSpeedPickerDialog(
                current = playbackSpeed,
                onSelect = { speed ->
                    playbackSpeed = speed
                    runCatching { MPVLib.setPropertyDouble("speed", speed.toDouble()) }
                },
                onDismiss = { showSpeedMenu = false }
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

private fun MediaItem.forceStartFromBeginning(): Boolean =
    userData?.playbackPositionTicks == 0L && userData.playedPercentage == 0.0

private fun applyMpvFillMode(mode: MpvFillMode) {
    when (mode) {
        MpvFillMode.FIT -> {
            MPVLib.setPropertyString("panscan", "0")
            MPVLib.setPropertyString("video-zoom", "0")
        }
        MpvFillMode.FILL -> {
            MPVLib.setPropertyString("panscan", "1")
            MPVLib.setPropertyString("video-zoom", "0")
        }
        MpvFillMode.ZOOM -> {
            MPVLib.setPropertyString("panscan", "0")
            MPVLib.setPropertyString("video-zoom", "0.22")
        }
    }
}

private suspend fun refreshMpvVideoOutput() {
    runCatching { MPVLib.setOptionString("force-window", "yes") }
    runCatching { MPVLib.setOptionString("vo", "gpu") }
    runCatching { MPVLib.setPropertyString("vo", "gpu") }
    // Try the redraw seek IMMEDIATELY, then poll fast — the previous 120ms
    // gap between retries made PiP-exit feel slow (≈2 s of black). MPV's
    // time-pos is almost always valid in <100 ms after attachSurface.
    repeat(8) { attempt ->
        if (attempt > 0) delay(40)
        val pos = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
        if (pos != null && pos >= 0.0) {
            runCatching { MPVLib.command(arrayOf("seek", pos.toString(), "absolute+exact")) }
            return
        }
    }
}

private fun resetMpvSubtitleTiming() {
    MPVLib.setPropertyString("sub-delay", "0")
    MPVLib.setPropertyString("audio-delay", "0")
}

private fun List<MediaSegment>.activeSegment(
    positionMs: Long,
    vararg names: String
): MediaSegment? = firstOrNull { segment ->
    names.any { segment.type.contains(it, ignoreCase = true) } &&
        positionMs in segment.startMs..segment.endMs
}

private fun mpvTrackPropertyValue(track: Int?, offValue: String): String {
    return when (track) {
        null -> offValue
        -1 -> "auto"
        else -> track.toString()
    }
}

private fun formatMpvStreamLabel(stream: MediaStream, trackNumber: Int): String {
    val parts = mutableListOf<String>()
    val title = stream.displayTitle ?: stream.title
    if (!title.isNullOrBlank()) parts += title
    if (!stream.language.isNullOrBlank() && stream.language != "und") {
        parts += stream.language.uppercase()
    }
    if (parts.isEmpty()) parts += "Track $trackNumber"
    return parts.joinToString(" - ")
}

private fun formatMpvSubtitleLabel(stream: MediaStream, trackNumber: Int): String {
    return stream.displayTitle
        ?: stream.title
        ?: stream.language?.takeIf { it.isNotBlank() && it != "und" }?.uppercase()
        ?: "Track $trackNumber"
}

private fun MediaStream.isImageBasedSubtitle(): Boolean = when (codec?.lowercase()?.trim()) {
    "pgssub", "pgs", "hdmv_pgs_subtitle", "dvdsub", "dvd_subtitle", "xsub", "dvbsub" -> true
    else -> false
}

private fun MediaStream.mpvSubtitleFormat(): String {
    return when (val normalized = codec?.lowercase()?.trim()) {
        "ass", "ssa", "srt" -> normalized
        "subrip" -> "srt"
        "vtt", "webvtt" -> "vtt"
        else -> "vtt"
    }
}

private fun pickPreferredMpvSubtitle(
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
    return subtitles.firstOrNull { it.matchesMpvSubtitleLanguage(preferred) } ?: subtitles.first()
}

private fun MediaStream.matchesMpvSubtitleLanguage(preferredLanguage: String): Boolean {
    val wanted = mpvSubtitleLanguageAliases(preferredLanguage)
    val haystack = listOfNotNull(language, displayTitle, title)
        .joinToString(" ")
        .lowercase()
    return wanted.any { it in haystack }
}

// ── Custom subtitle renderer ──────────────────────────────────────────────────
// Used for text-based subtitle formats (SRT, ASS, VTT) because the jdtech
// mpv-android build doesn't ship libass, so MPV silently renders nothing for
// those tracks. We download the subtitle from Jellyfin as SRT, parse it, and
// draw it as a Compose Text overlay positioned above the seek bar.

private data class SubCue(val startMs: Long, val endMs: Long, val text: String)

private fun parseSrtTimestamp(t: String): Long {
    // Format: HH:MM:SS,mmm
    val parts = t.trim().split(":", ",")
    if (parts.size < 4) return 0L
    val h = parts[0].toLongOrNull() ?: 0L
    val m = parts[1].toLongOrNull() ?: 0L
    val s = parts[2].toLongOrNull() ?: 0L
    val ms = parts[3].toLongOrNull() ?: 0L
    return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
}

private val htmlTagRegex = Regex("<[^>]+>")
private val assOverrideRegex = Regex("\\{[^}]*\\}")

/**
 * SRT cue text often contains HTML-style tags (<i>, <b>, <font ...>) and
 * sometimes ASS override blocks ({\an8}, {\pos(...)}). Compose's Text doesn't
 * render any of them, so they'd show as literal "</i>" garbage. Strip them.
 */
private fun cleanSubtitleText(text: String): String {
    return text
        .replace(htmlTagRegex, "")
        .replace(assOverrideRegex, "")
        .replace("\\N", "\n")  // ASS line break
        .replace("\\h", " ")   // ASS hard space
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
}

private fun parseSrt(text: String): List<SubCue> {
    val cues = mutableListOf<SubCue>()
    val blocks = text.trim().split(Regex("\r?\n\r?\n"))
    for (block in blocks) {
        val lines = block.trim().lines()
        if (lines.size < 3) continue
        val timingLine = lines.getOrNull(1) ?: continue
        val arrowIdx = timingLine.indexOf("-->")
        if (arrowIdx < 0) continue
        val startMs = parseSrtTimestamp(timingLine.substring(0, arrowIdx).trim())
        val endMs = parseSrtTimestamp(
            timingLine.substring(arrowIdx + 3).trim().substringBefore(" ")
        )
        val cueText = cleanSubtitleText(lines.drop(2).joinToString("\n"))
        if (cueText.isNotBlank() && endMs > startMs) cues += SubCue(startMs, endMs, cueText)
    }
    return cues
}

private suspend fun loadSubtitleCues(ctx: android.content.Context, url: String): List<SubCue> {
    val path = downloadSubtitleToCache(ctx, url) ?: return emptyList()
    return runCatching { parseSrt(java.io.File(path).readText()) }.getOrDefault(emptyList())
}

private suspend fun loadLocalSubtitleCues(path: String): List<SubCue> = withContext(Dispatchers.IO) {
    runCatching {
        val file = java.io.File(path)
        if (!file.exists()) return@runCatching emptyList()
        parseSrt(file.readText())
    }.getOrDefault(emptyList())
}

/**
 * Download a subtitle URL to the app's cache dir using Android's networking
 * (bypasses MPV's internal HTTP client, which may reject Jellyfin URLs on
 * some devices due to certificate or protocol restrictions).
 * Returns the local file path, or null if the download failed.
 */
private suspend fun downloadSubtitleToCache(
    ctx: android.content.Context,
    url: String
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val ext = url.substringAfterLast("Stream.", "vtt").substringBefore("?")
        val tmp = java.io.File.createTempFile("fjora_sub_", ".$ext", ctx.cacheDir)
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
        conn.disconnect()
        tmp.absolutePath
    }.getOrNull()
}

private fun mpvSubtitleLanguageAliases(language: String): Set<String> {
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

@Composable
private fun MpvInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun MpvStreamPickerDialog(
    title: String,
    options: List<Pair<Int?, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun MpvSpeedPickerDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed); onDismiss() }
                            .padding(vertical = 10.dp),
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
        },
        shape = MaterialTheme.shapes.large
    )
}
