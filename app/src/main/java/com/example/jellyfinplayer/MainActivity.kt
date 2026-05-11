package com.example.jellyfinplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.MediaSource
import com.example.jellyfinplayer.api.MediaStream
import com.example.jellyfinplayer.api.Person
import com.example.jellyfinplayer.data.DownloadsStore
import com.example.jellyfinplayer.player.initialSeasonForEpisodeReturn
import com.example.jellyfinplayer.player.nextEpisodeAfter
import com.example.jellyfinplayer.ui.screens.DownloadedDetailScreen
import com.example.jellyfinplayer.ui.screens.DownloadedSeriesScreen
import com.example.jellyfinplayer.ui.screens.EpisodesScreen
import com.example.jellyfinplayer.ui.screens.LibraryScreen
import com.example.jellyfinplayer.ui.screens.LoginScreen
import com.example.jellyfinplayer.ui.screens.MovieDetailScreen
import com.example.jellyfinplayer.ui.screens.MpvPlayerScreen
import com.example.jellyfinplayer.ui.screens.PersonScreen
import com.example.jellyfinplayer.ui.screens.PlayerScreen
import com.example.jellyfinplayer.ui.screens.SettingsScreen
import com.example.jellyfinplayer.ui.theme.AppTheme

private sealed class Screen {
    data object Library : Screen()
    data object Settings : Screen()
    /** Add-account sub-flow — shows LoginScreen, returns to Settings on success. */
    data object AddAccount : Screen()
    /**
     * Detail screen for movies AND episodes. When `series` is non-null the
     * item is an episode and back-press goes to the episode list; otherwise
     * it's a movie and back goes to the library.
     */
    data class MovieDetail(val movie: MediaItem, val series: MediaItem? = null) : Screen()
    data class Episodes(val series: MediaItem, val initialSeason: Int? = null) : Screen()
    data class PersonDetail(val person: Person, val previous: Screen) : Screen()

    /**
     * Series-level view of a downloaded show — lists all downloaded episodes
     * grouped by season. Routed to from the Downloads tab when the user taps
     * a series card. Distinct from Episodes (which fetches from the server)
     * because here we render purely from local DownloadRecord data.
     */
    data class DownloadedSeries(val seriesId: String) : Screen()

    /**
     * Detail screen for a downloaded item (movie or episode). Distinct from
     * MovieDetail because we render from DownloadRecord rather than fetching
     * from the server — works fully offline.
     */
    data class DownloadedDetail(val downloadId: Long) : Screen()

    data class Player(
        val item: MediaItem,
        val series: MediaItem? = null,
        val movieDetail: MediaItem? = null,
        /**
         * Local file path when playing a downloaded item; null for normal
         * server-streamed playback. The PlayerScreen branches on this.
         */
        val localFilePath: String? = null
    ) : Screen()

    /**
     * Local-file playback via the bundled mpv player. Used when the user
     * has enabled "Use mpv for downloads" in settings, or as a fallback
     * for files ExoPlayer can't handle.
     */
    data class MpvPlayer(
        val item: MediaItem,
        val series: MediaItem? = null,
        val movieDetail: MediaItem? = null,
        val localFilePath: String? = null
    ) : Screen()
}

private fun screenDepth(screen: Screen): Int = when (screen) {
    is Screen.Library -> 0
    is Screen.Settings,
    is Screen.AddAccount -> 1
    is Screen.DownloadedSeries,
    is Screen.Episodes,
    is Screen.MovieDetail -> 1
    is Screen.PersonDetail -> 2
    is Screen.DownloadedDetail -> 2
    is Screen.Player,
    is Screen.MpvPlayer -> 3
}

internal object PlayerPresence {
    @Volatile var aspectWidth: Int = 16
    @Volatile var aspectHeight: Int = 9
    @Volatile var isPlayerOnTop: Boolean = false
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val isInPipState = mutableStateOf(false)

    /**
     * Receiver for PiP overlay action button taps (Play/Pause/Rewind/Forward).
     * Registered in onCreate, unregistered in onDestroy. Lives at the
     * Activity level since it has to survive PlayerScreen recompositions
     * during PiP transitions.
     */
    private val pipActionReceiver =
        com.example.jellyfinplayer.player.PipActionReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Register the PiP action receiver. Use RECEIVER_NOT_EXPORTED on
        // Android 14+ so only our own app can fire these intents (the
        // PendingIntents we create internally already do this, but the
        // explicit flag keeps us compliant with the security model).
        val filter = android.content.IntentFilter(
            com.example.jellyfinplayer.player.PipActionReceiver.ACTION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
        }
        setContent {
            val settings by vm.settings.collectAsState()
            AppTheme(
                themeColor = settings.appThemeColor,
                backgroundColor = settings.appBackgroundColor
            ) {
                AppNav(vm, isInPipState.value)
            }
        }
    }

    override fun onDestroy() {
        stopActivePlayback()
        runCatching { unregisterReceiver(pipActionReceiver) }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (!PlayerPresence.isPlayerOnTop) return
        val w = PlayerPresence.aspectWidth.coerceAtLeast(1)
        val h = PlayerPresence.aspectHeight.coerceAtLeast(1)
        val isPlaying =
            com.example.jellyfinplayer.player.PipActionReceiver.activeIsPlaying?.invoke() == true
        val params = com.example.jellyfinplayer.player.buildPipParamsForPlayer(
            activity = this,
            aspectWidth = w,
            aspectHeight = h,
            isPlaying = isPlaying,
            hasNext = com.example.jellyfinplayer.player.PipActionReceiver.activePlayNext != null
        )
        try {
            enterPictureInPictureMode(params)
        } catch (_: Throwable) {
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Closing the PiP window can report as "still in PiP" at the exact
        // onStop moment. Check again after Android has finished the
        // transition; active PiP remains STARTED, closed PiP does not.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val stillInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                isInPictureInPictureMode
            val stillVisibleEnough = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!stillInPip || !stillVisibleEnough) {
                stopActivePlayback()
            }
        }, 250L)
    }

    private fun stopActivePlayback() {
        runCatching {
            com.example.jellyfinplayer.player.PipActionReceiver.activeStop?.invoke()
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel, inPip: Boolean) {
    val loggedIn = vm.isLoggedIn.collectAsState().value
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    LaunchedEffect(loggedIn) { if (loggedIn == true) screen = Screen.Library }

    DisposableEffect(screen) {
        PlayerPresence.isPlayerOnTop = screen is Screen.Player || screen is Screen.MpvPlayer
        onDispose { PlayerPresence.isPlayerOnTop = false }
    }

    // Defensive cutout-mode reset. The player extends video into the notch
    // via SHORT_EDGES while playing, but if onDispose misses (e.g. activity
    // recreated mid-flight), the mode could persist into the rest of the
    // app — making library text overlap the notch. Whenever we're NOT on
    // the player screen, force DEFAULT cutout mode so all other screens
    // get their normal status-bar-safe area.
    val activity = androidx.compose.ui.platform.LocalContext.current as? Activity
    LaunchedEffect(screen, activity) {
        val inAnyPlayer = screen is Screen.Player || screen is Screen.MpvPlayer
        activity?.requestedOrientation = if (inAnyPlayer) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (!inAnyPlayer &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        ) {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }

    // While auth is being resolved, render the app's background only — no
    // login flash for already-signed-in users on cold start.
    if (loggedIn == null) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }
    if (loggedIn == false) {
        LoginScreen(vm)
        return
    }

    BackHandler(enabled = screen !is Screen.Library) {
        screen = when (val s = screen) {
            is Screen.Player -> when {
                s.localFilePath != null -> {
                    // Coming from the downloads flow — go back to the
                    // appropriate downloaded-detail or downloaded-series.
                    val rec = vm.downloads.value.firstOrNull {
                        it.itemId == s.item.id
                    }
                    when {
                        rec == null -> Screen.Library
                        rec.seriesId != null || rec.seriesName != null ->
                            Screen.DownloadedSeries(rec.seriesId ?: rec.seriesName!!)
                        else -> Screen.DownloadedDetail(rec.downloadId)
                    }
                }
                s.movieDetail != null -> Screen.MovieDetail(s.movieDetail, s.series)
                s.series != null -> Screen.Episodes(s.series, initialSeasonForEpisodeReturn(s.item))
                else -> Screen.Library
            }
            is Screen.MovieDetail -> when {
                s.series != null -> Screen.Episodes(s.series, initialSeasonForEpisodeReturn(s.movie))
                else -> Screen.Library
            }
            is Screen.Episodes -> Screen.Library
            is Screen.PersonDetail -> s.previous
            is Screen.Settings -> Screen.Library
            is Screen.AddAccount -> Screen.Settings
            is Screen.DownloadedDetail -> {
                val rec = vm.downloads.value.firstOrNull {
                    it.downloadId == s.downloadId
                }
                if (rec?.seriesId != null || rec?.seriesName != null) {
                    Screen.DownloadedSeries(rec.seriesId ?: rec.seriesName!!)
                } else {
                    Screen.Library
                }
            }
            is Screen.DownloadedSeries -> Screen.Library
            is Screen.MpvPlayer -> when {
                s.localFilePath != null -> {
                    val rec = vm.downloads.value.firstOrNull {
                        it.itemId == s.item.id
                    }
                    when {
                        rec == null -> Screen.Library
                        rec.seriesId != null || rec.seriesName != null ->
                            Screen.DownloadedSeries(rec.seriesId ?: rec.seriesName!!)
                        else -> Screen.DownloadedDetail(rec.downloadId)
                    }
                }
                s.movieDetail != null -> Screen.MovieDetail(s.movieDetail, s.series)
                s.series != null -> Screen.Episodes(s.series, initialSeasonForEpisodeReturn(s.item))
                else -> Screen.Library
            }
            is Screen.Library -> Screen.Library
        }
    }

    // Subtle screen motion keeps detail navigation from feeling like a hard
    // swap, while staying short enough not to slow repeated browsing.
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // Player↔Player transitions must be instant for two reasons:
            //   1) MPVLib is a JNI singleton — two MpvPlayerScreens overlapping
            //      during a slide animation would step on each other's create/
            //      destroy and cause crashes.
            //   2) The cutout-mode setup in onDispose runs AFTER the new screen's
            //      DisposableEffect has already set SHORT_EDGES; with an animation
            //      delay the old screen's restore lands AFTER the new screen
            //      mounted, leaving cutout=DEFAULT and shifting the video off-
            //      center against the notch.
            //   3) ExoPlayer instances accumulate (the old player isn't released
            //      until its slide-out finishes) — rapid next-episode taps OOM.
            val initialIsPlayer =
                initialState is Screen.Player || initialState is Screen.MpvPlayer
            val targetIsPlayer =
                targetState is Screen.Player || targetState is Screen.MpvPlayer
            val involvesMpv =
                initialState is Screen.MpvPlayer || targetState is Screen.MpvPlayer
            // Player↔Player needs to be instant (next-episode OOM + cutout race);
            // Anything↔MpvPlayer needs to be instant (MPVLib singleton can't have
            // two screens overlap). Library↔Player keeps its slide animation.
            if ((initialIsPlayer && targetIsPlayer) || involvesMpv) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = screenDepth(targetState) >= screenDepth(initialState)
                val slide = if (forward) 48 else -48
                val enter = slideInHorizontally(animationSpec = tween(170)) { slide } +
                    fadeIn(animationSpec = tween(140))
                val exit = slideOutHorizontally(animationSpec = tween(170)) { -slide / 2 } +
                    fadeOut(animationSpec = tween(120))
                (enter togetherWith exit).apply {
                    targetContentZIndex = if (forward) 1f else 0f
                }
            }
        },
        label = "screen_transition"
    ) { s ->
        when (s) {
            is Screen.Library -> LibraryScreen(
                vm = vm,
                onItemClick = { item ->
                    screen = when (item.type) {
                        "Movie" -> Screen.MovieDetail(item)
                        "Series" -> Screen.Episodes(item)
                        else -> Screen.MovieDetail(item)
                    }
                },
                onDownloadedMovieClick = { downloadId ->
                    screen = Screen.DownloadedDetail(downloadId)
                },
                onDownloadedSeriesClick = { seriesId ->
                    screen = Screen.DownloadedSeries(seriesId)
                },
                onSettingsClick = { screen = Screen.Settings }
            )
            is Screen.DownloadedSeries -> DownloadedSeriesScreen(
                vm = vm,
                seriesId = s.seriesId,
                onBack = { screen = Screen.Library },
                onEpisodeClick = { downloadId ->
                    screen = Screen.DownloadedDetail(downloadId)
                }
            )
            is Screen.DownloadedDetail -> DownloadedDetailScreen(
                vm = vm,
                downloadId = s.downloadId,
                onBack = {
                    // Find the record so we know whether to return to a
                    // series view or directly to library.
                    val record = vm.downloads.value.firstOrNull {
                        it.downloadId == s.downloadId
                    }
                    screen = if (record?.seriesId != null || record?.seriesName != null) {
                        val sid = record.seriesId ?: record.seriesName!!
                        Screen.DownloadedSeries(sid)
                    } else {
                        Screen.Library
                    }
                },
                onPlay = { rec ->
                    val path = rec.filePath
                    if (path != null && java.io.File(path).exists()) {
                        // Branch on the user's player preference. mpv when
                        // the toggle is on; otherwise the built-in
                        // ExoPlayer screen via Screen.Player.
                        val settings = vm.settings.value
                        val useMpv = settings.useMpvForLocal ||
                            settings.useMpvForAll ||
                            settings.directPlayOnly
                        screen = if (useMpv) {
                            Screen.MpvPlayer(
                                item = rec.toLocalMediaItem(),
                                localFilePath = path
                            )
                        } else {
                            Screen.Player(
                                item = rec.toLocalMediaItem(),
                                localFilePath = path
                            )
                        }
                    } else {
                        activity?.let {
                            android.widget.Toast.makeText(
                                it,
                                "Still downloading. Try again when it finishes.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDelete = { rec ->
                    // Cancel + delete + remove record. Same as the long-press
                    // delete on the grid.
                    val dm = activity?.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                        as? android.app.DownloadManager
                    runCatching { dm?.remove(rec.downloadId) }
                    rec.filePath?.let { p ->
                        runCatching { java.io.File(p).delete() }
                    }
                    vm.removeDownload(rec.downloadId)
                    // Pop back to series view (which will show one fewer
                    // episode) or to library if this was a movie.
                    screen = if (rec.seriesId != null || rec.seriesName != null) {
                        Screen.DownloadedSeries(rec.seriesId ?: rec.seriesName!!)
                    } else {
                        Screen.Library
                    }
                }
            )
            is Screen.Settings -> SettingsScreen(
                vm = vm,
                onBack = { screen = Screen.Library },
                onAddAccount = { screen = Screen.AddAccount },
                onSignOutAll = { vm.logout() }
            )
            is Screen.AddAccount -> LoginScreen(
                vm = vm,
                onLoginComplete = { screen = Screen.Settings }
            )
            is Screen.MovieDetail -> MovieDetailScreen(
                vm = vm,
                item = s.movie,
                onBack = {
                    screen = if (s.series != null)
                        Screen.Episodes(s.series, initialSeasonForEpisodeReturn(s.movie))
                    else Screen.Library
                },
                onPlay = { item ->
                    val settings = vm.settings.value
                    screen = if (settings.useMpvForAll || settings.directPlayOnly) {
                        Screen.MpvPlayer(item = item, series = s.series, movieDetail = item)
                    } else {
                        Screen.Player(
                            item = item,
                            series = s.series,
                            movieDetail = item
                        )
                    }
                },
                onSeriesClick = { series, season ->
                    screen = Screen.Episodes(series, season)
                },
                onPersonClick = { person ->
                    screen = Screen.PersonDetail(person, previous = s)
                }
            )
            is Screen.Episodes -> EpisodesScreen(
                vm = vm,
                series = s.series,
                initialSeason = s.initialSeason,
                onBack = { screen = Screen.Library },
                onEpisodeClick = { episode ->
                    screen = Screen.MovieDetail(episode, series = s.series)
                },
                onPersonClick = { person ->
                    screen = Screen.PersonDetail(person, previous = s)
                }
            )
            is Screen.PersonDetail -> PersonScreen(
                vm = vm,
                person = s.person,
                onBack = { screen = s.previous },
                onItemClick = { item ->
                    screen = when (item.type) {
                        "Series" -> Screen.Episodes(item)
                        "Episode" -> Screen.MovieDetail(item)
                        else -> Screen.MovieDetail(item)
                    }
                }
            )
            is Screen.Player -> PlayerScreen(
                vm = vm,
                item = s.item,
                inPip = inPip,
                nextEpisode = if (s.localFilePath == null) {
                    nextEpisodeFor(vm, s.item, s.series)
                } else {
                    null
                },
                onPlayNext = { next ->
                    val settings = vm.settings.value
                    screen = if (settings.useMpvForAll || settings.directPlayOnly) {
                        Screen.MpvPlayer(item = next, series = s.series, movieDetail = next)
                    } else {
                        Screen.Player(
                            item = next,
                            series = s.series,
                            movieDetail = next,
                            localFilePath = null
                        )
                    }
                },
                onBack = {
                    screen = when {
                        // Local-file playback always goes back to Library
                        // (My Downloads tab specifically). There's no
                        // detail screen for downloaded items.
                        s.localFilePath != null -> Screen.Library
                        s.movieDetail != null -> Screen.MovieDetail(s.movieDetail, s.series)
                        s.series != null -> Screen.Episodes(
                            s.series,
                            initialSeasonForEpisodeReturn(s.movieDetail ?: s.item)
                        )
                        else -> Screen.Library
                    }
                },
                onLocalPlaybackNeedsMpv = s.localFilePath?.let { path ->
                    {
                        screen = Screen.MpvPlayer(
                            item = s.item,
                            series = s.series,
                            movieDetail = s.movieDetail,
                            localFilePath = path
                        )
                    }
                },
                localFilePath = s.localFilePath
            )
            is Screen.MpvPlayer -> MpvPlayerScreen(
                vm = vm,
                item = s.item,
                localFilePath = s.localFilePath,
                nextEpisode = if (s.localFilePath == null) {
                    nextEpisodeFor(vm, s.item, s.series)
                } else {
                    null
                },
                onPlayNext = { next ->
                    screen = Screen.MpvPlayer(item = next, series = s.series, movieDetail = next)
                },
                inPip = inPip,
                onBack = {
                    screen = when {
                        s.localFilePath != null -> Screen.Library
                        s.movieDetail != null -> Screen.MovieDetail(s.movieDetail, s.series)
                        s.series != null -> Screen.Episodes(
                            s.series,
                            initialSeasonForEpisodeReturn(s.movieDetail ?: s.item)
                        )
                        else -> Screen.Library
                    }
                }
            )
        }
    }
}

private fun DownloadsStore.DownloadRecord.toLocalMediaItem(): MediaItem {
    val subtitleStreams = subtitlePaths.mapIndexedNotNull { idx, path ->
        val file = java.io.File(path)
        if (!file.exists()) return@mapIndexedNotNull null
        val language = file.nameWithoutExtension.substringAfterLast('_', "")
            .takeIf { it.isNotBlank() && !it.startsWith("sub", ignoreCase = true) }
        MediaStream(
            index = idx,
            type = "Subtitle",
            codec = file.extension.ifBlank { "srt" },
            language = language,
            displayTitle = language?.uppercase() ?: "External subtitle ${idx + 1}",
            title = file.nameWithoutExtension,
            isExternal = true,
            deliveryUrl = file.absolutePath
        )
    }

    return MediaItem(
        id = itemId,
        name = title,
        type = if (seriesName != null) "Episode" else "Movie",
        productionYear = productionYear,
        overview = overview,
        runTimeTicks = runtimeMinutes?.let { it.toLong() * 60L * 10_000_000L },
        seriesName = seriesName,
        seriesId = seriesId,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        mediaSources = listOf(
            MediaSource(
                id = "local",
                mediaStreams = subtitleStreams
            )
        )
    )
}

@Composable
private fun nextEpisodeFor(
    vm: AppViewModel,
    current: MediaItem,
    series: MediaItem?
): MediaItem? {
    val seriesId = series?.id ?: current.seriesId ?: return null
    val episodes by produceState<List<MediaItem>>(emptyList(), seriesId) {
        value = runCatching { vm.loadEpisodes(seriesId) }.getOrDefault(emptyList())
    }
    if (episodes.isEmpty()) return null
    return nextEpisodeAfter(current, episodes)
}
