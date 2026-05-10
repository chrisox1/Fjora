package com.example.jellyfinplayer.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.Person
import com.example.jellyfinplayer.ui.components.CastRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
    vm: AppViewModel,
    series: MediaItem,
    onBack: () -> Unit,
    onEpisodeClick: (MediaItem) -> Unit,
    onPersonClick: (Person) -> Unit = {}
) {
    var episodes by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // Series detail with full cast/crew data — initialized with the slim
    // `series` object so the hero renders immediately, then enriched
    // silently when loadItemDetails returns. Render-with-stale-data
    // pattern; the screen never blank-flashes during the fetch.
    var seriesDetails by remember { mutableStateOf(series) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(series.id) {
        loading = true
        error = null

        // Fire the series-detail fetch in its own coroutine. If it fails
        // or hangs, it can't block the episode list — that's the critical
        // path for this screen.
        launch {
            try {
                val full = vm.loadItemDetails(series.id)
                seriesDetails = full
            } catch (_: Throwable) {
                // Stay with the slim series. Cast row just won't appear.
            }
        }

        // Episodes are the real content. If this fails, show the error.
        try {
            val list = vm.loadEpisodes(series.id)
            episodes = list
            // Smart default: jump to the season containing the next unwatched
            // episode so the user doesn't always have to scroll back to where
            // they were. Falls back to the lowest-numbered non-special season.
            selectedSeason = list.firstOrNull {
                val played = it.userData?.played == true
                val started = (it.userData?.playbackPositionTicks ?: 0L) > 0L
                !played || started
            }?.seasonNumber
                ?: list.mapNotNull { it.seasonNumber }.filter { it > 0 }.minOrNull()
                ?: list.firstOrNull()?.seasonNumber
        } catch (t: Throwable) {
            error = t.message ?: "Failed to load episodes"
        } finally {
            loading = false
        }
    }

    val cs = MaterialTheme.colorScheme

    // The episode the Play CTA on the hero should launch.
    val playTarget = remember(episodes) {
        episodes.firstOrNull { (it.userData?.playbackPositionTicks ?: 0L) > 0L }
            ?: episodes.firstOrNull { it.userData?.played != true }
            ?: episodes.firstOrNull()
    }

    var showDownloadAllDialog by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        containerColor = cs.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        series.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Download all episodes button — only enabled once the
                    // episode list has loaded so we know how many there
                    // would actually be.
                    if (episodes.isNotEmpty()) {
                        IconButton(onClick = { showDownloadAllDialog = true }) {
                            Icon(
                                imageVector = com.example.jellyfinplayer.ui.icons.DownloadIconVector,
                                contentDescription = "Download all episodes"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Render-with-stale-data: the series hero and overview don't
            // need the episodes API call — we already have all of that on
            // the `series` object passed in. Show that immediately and only
            // gate the episode list itself on the load state.
            EpisodesContent(
                vm = vm,
                series = seriesDetails,
                episodes = episodes,
                playTarget = playTarget,
                selectedSeason = selectedSeason,
                onSeasonChange = { selectedSeason = it },
                onEpisodeClick = onEpisodeClick,
                onPersonClick = onPersonClick,
                loading = loading,
                error = error
            )
        }
    }

    if (showDownloadAllDialog) {
        DownloadAllEpisodesDialog(
            episodeCount = episodes.size,
            onDismiss = { showDownloadAllDialog = false },
            onConfirm = { maxBitrate ->
                showDownloadAllDialog = false
                // Enqueue every episode in the loaded list. DownloadManager
                // serializes these internally so the device isn't slammed
                // by N parallel TCP connections, but the records all show
                // up in My Downloads immediately so the user can see what's
                // queued.
                episodes.forEach { ep ->
                    startDownload(
                        ctx = ctx,
                        vm = vm,
                        item = ep,
                        url = vm.downloadUrl(ep, maxBitrate),
                        isOriginal = maxBitrate == null,
                        maxBitrate = maxBitrate
                    )
                }
                android.widget.Toast.makeText(
                    ctx,
                    "Queued ${episodes.size} episodes for download",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

@Composable
private fun EpisodesContent(
    vm: AppViewModel,
    series: MediaItem,
    episodes: List<MediaItem>,
    playTarget: MediaItem?,
    selectedSeason: Int?,
    onSeasonChange: (Int) -> Unit,
    onEpisodeClick: (MediaItem) -> Unit,
    onPersonClick: (Person) -> Unit,
    loading: Boolean,
    error: String?
) {
    val seasons = remember(episodes) {
        episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    val visible = remember(episodes, selectedSeason) {
        episodes.filter { (it.seasonNumber ?: 0) == (selectedSeason ?: 0) }
            .sortedBy { it.episodeNumber ?: 0 }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            SeriesHero(
                vm = vm,
                series = series,
                playTarget = playTarget,
                onPlayClick = { playTarget?.let(onEpisodeClick) }
            )
        }
        item {
            AnimatedVisibility(
                visible = loading && episodes.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
        if (!series.overview.isNullOrBlank()) {
            item {
                Text(
                    series.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .animateContentSize()
                )
            }
        }
        // Cast & crew strip for the series (people who appear across the
        // show, not per-episode). Renders silently when seriesDetails has
        // been enriched with the People list; before that it's empty and
        // CastRow returns early without taking layout space.
        if (series.people.isNotEmpty()) {
            item { CastRow(vm = vm, people = series.people, onPersonClick = onPersonClick) }
        }
        // Episode list section — shows loading state inline so the hero
        // stays visible during the fetch. Empty/error states only apply
        // here, not to the whole screen.
        when {
            loading && episodes.isEmpty() -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null && episodes.isEmpty() -> item {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(20.dp)
                )
            }
            episodes.isEmpty() -> item {
                Text(
                    "No episodes found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            else -> {
                item {
                    SeasonTabs(
                        seasons = seasons,
                        selected = selectedSeason,
                        onSelect = onSeasonChange
                    )
                }
                items(visible, key = { it.id }) { ep ->
                    EpisodeRow(vm, ep, onClick = { onEpisodeClick(ep) })
                }
            }
        }
    }
}

@Composable
private fun SeriesHero(
    vm: AppViewModel,
    series: MediaItem,
    playTarget: MediaItem?,
    onPlayClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(cs.surfaceVariant) // placeholder under the backdrop
    ) {
        val backdrop = vm.backdropUrl(series, maxWidth = 1280)
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(cs.surfaceVariant))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.55f to Color.Transparent,
                        1f to cs.background
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                series.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                series.productionYear?.let { add(it.toString()) }
                series.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                series.communityRating?.let { add("Rating ${"%.1f".format(it)}") }
            }.joinToString(" - ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (playTarget != null) {
                Spacer(Modifier.height(12.dp))
                val started = (playTarget.userData?.playbackPositionTicks ?: 0L) > 0L
                val targetLabel = if (started) "Resume" else "Play"
                val episodeLabel = playTarget.takeIf { it.type == "Episode" }?.let { ep ->
                    val s = ep.seasonNumber?.let { "S$it" } ?: ""
                    val e = ep.episodeNumber?.let { "E$it" } ?: ""
                    listOf(s, e).filter { it.isNotEmpty() }.joinToString("")
                }
                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (episodeLabel.isNullOrBlank()) targetLabel else "$targetLabel $episodeLabel",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonTabs(
    seasons: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    if (seasons.size <= 1) {
        Spacer(Modifier.height(8.dp))
        return
    }
    val selectedIndex = seasons.indexOf(selected).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        edgePadding = 12.dp,
        divider = {}
    ) {
        seasons.forEachIndexed { idx, s ->
            Tab(
                selected = idx == selectedIndex,
                onClick = { onSelect(s) },
                text = {
                    Text(
                        if (s == 0) "Specials" else "Season $s",
                        fontWeight = if (idx == selectedIndex) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EpisodeRow(vm: AppViewModel, ep: MediaItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val played = ep.userData?.played == true
    val progress = ep.playedFraction ?: 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(cs.surfaceVariant)
        ) {
            val url = vm.posterUrl(ep, maxHeight = 240)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
            // Watched checkmark badge sits in the top-right corner.
            if (played) {
                Surface(
                    color = cs.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = cs.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(2.dp)
                    )
                }
            }
            // Progress sliver for in-progress episodes (unwatched + position > 0).
            if (!played && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = cs.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val number = ep.episodeNumber?.let { "E$it" }
            Text(
                buildString {
                    if (number != null) append("$number  ")
                    append(ep.name)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (played) cs.onSurfaceVariant else cs.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ep.runtimeMinutes?.let {
                Text(
                    "$it min",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Confirmation dialog for "Download all episodes" on a series. Lets the user
 * pick a quality (with the same trade-offs as single-item downloads) and
 * shows a clear warning about the size and server load implications.
 *
 * Same option set as the single-item dialog so the user's mental model is
 * consistent — but the warning text is more emphatic since downloading
 * a whole season can mean dozens of files and tens of GB.
 */
@Composable
private fun DownloadAllEpisodesDialog(
    episodeCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val options: List<Pair<Long?, String>> = listOf(
        null to "Original quality (recommended)",
        20_000_000L to "1080p (~20 Mbps)",
        8_000_000L to "720p (~8 Mbps)",
        3_000_000L to "480p (~3 Mbps)",
        1_000_000L to "360p (~1 Mbps)"
    )
    var selected by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download all episodes", fontWeight = FontWeight.SemiBold) },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Download $episodeCount", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column {
                Text(
                    "This will download $episodeCount episodes. Original " +
                        "quality is fastest and uses no server CPU; other " +
                        "qualities require the server to transcode every " +
                        "episode, which can take a long time and consume " +
                        "significant server resources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { selected = value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )
}
