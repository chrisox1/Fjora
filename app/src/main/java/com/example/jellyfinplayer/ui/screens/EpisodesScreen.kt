package com.example.jellyfinplayer.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Cache of the last loaded episode list per series, plus the matching
 * enriched series details. Module-level so it survives EpisodesScreen being
 * unmounted while the user is on the player. On return from the player the
 * screen reads from the cache and renders instantly — same "Latest Movies →
 * back" feel as the LibraryScreen. A silent refresh runs in the background
 * to pick up any progress / watched-state updates.
 */
private object EpisodesCache {
    var seriesId: String? = null
    var episodes: List<MediaItem> = emptyList()
    var details: MediaItem? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
    vm: AppViewModel,
    series: MediaItem,
    initialSeason: Int? = null,
    initialEpisodeId: String? = null,
    onBack: () -> Unit,
    onEpisodeClick: (MediaItem) -> Unit,
    onPersonClick: (Person) -> Unit = {}
) {
    // Seed from cache if available so the list renders instantly. The
    // LaunchedEffect below still refreshes in the background.
    val cached = if (EpisodesCache.seriesId == series.id) EpisodesCache else null
    var episodes by remember { mutableStateOf(cached?.episodes ?: emptyList()) }
    // Series detail with full cast/crew data — initialized with the slim
    // `series` object so the hero renders immediately, then enriched
    // silently when loadItemDetails returns. Render-with-stale-data
    // pattern; the screen never blank-flashes during the fetch.
    var seriesDetails by remember { mutableStateOf(cached?.details ?: series) }
    var loading by remember { mutableStateOf(cached == null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Seed selectedSeason from the cache so the season tab strip is correct
    // immediately on a back-from-player remount.
    var selectedSeason by remember {
        mutableStateOf<Int?>(
            cached?.episodes?.let { list ->
                initialSeason?.takeIf { wanted -> list.any { it.seasonNumber == wanted } }
                    ?: list.firstOrNull {
                        val played = it.userData?.played == true
                        val started = (it.userData?.playbackPositionTicks ?: 0L) > 0L
                        !played || started
                    }?.seasonNumber
                    ?: list.mapNotNull { it.seasonNumber }.filter { it > 0 }.minOrNull()
                    ?: list.firstOrNull()?.seasonNumber
            }
        )
    }

    LaunchedEffect(series.id) {
        // If we have a cached list we don't gate on `loading` — the user sees
        // the cached episodes immediately and the refresh below silently
        // replaces them when it returns.
        loading = EpisodesCache.seriesId != series.id
        error = null

        // Fire the series-detail fetch in its own coroutine. If it fails
        // or hangs, it can't block the episode list — that's the critical
        // path for this screen.
        launch {
            try {
                val full = vm.loadItemDetails(series.id)
                seriesDetails = full
                if (EpisodesCache.seriesId == series.id) EpisodesCache.details = full
            } catch (_: Throwable) {
                // Stay with the slim series. Cast row just won't appear.
            }
        }

        // Episodes are the real content. If this fails, show the error.
        try {
            val list = vm.loadEpisodes(series.id)
            episodes = list
            // Cache the result for the next mount of this screen (e.g. after
            // returning from the player) so the list renders instantly.
            EpisodesCache.seriesId = series.id
            EpisodesCache.episodes = list
            // Smart default: jump to the season containing the next unwatched
            // episode so the user doesn't always have to scroll back to where
            // they were. Falls back to the lowest-numbered non-special season.
            selectedSeason = initialSeason?.takeIf { wanted ->
                list.any { it.seasonNumber == wanted }
            } ?: list.firstOrNull {
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
                        "",
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
                initialEpisodeId = initialEpisodeId,
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
            episodes = episodes,
            onDismiss = { showDownloadAllDialog = false },
            onConfirm = { selectedEpisodes, maxBitrate ->
                showDownloadAllDialog = false
                // Enqueue every episode in the loaded list. DownloadManager
                // serializes these internally so the device isn't slammed
                // by N parallel TCP connections, but the records all show
                // up in My Downloads immediately so the user can see what's
                // queued.
                selectedEpisodes.forEach { ep ->
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
                    "Queued ${selectedEpisodes.size} episodes for download",
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
    initialEpisodeId: String?,
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
    val listState = rememberLazyListState()
    var scrolledToInitialEpisode by remember(initialEpisodeId) { mutableStateOf(false) }

    LaunchedEffect(loading, visible, initialEpisodeId, series.people, series.overview) {
        val targetId = initialEpisodeId ?: return@LaunchedEffect
        if (loading || scrolledToInitialEpisode || visible.isEmpty()) return@LaunchedEffect
        val episodeIndex = visible.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        val rowsBeforeEpisodes = 2 +
            if (!series.overview.isNullOrBlank()) 1 else 0 +
            if (series.people.isNotEmpty()) 1 else 0 +
            2
        // Instant scroll — coming back from the player, the user wants to
        // land exactly on the episode they tapped, not watch the list scroll
        // there. An animated scroll here read as a "swipe down" jolt.
        listState.scrollToItem((rowsBeforeEpisodes + episodeIndex - 2).coerceAtLeast(0))
        scrolledToInitialEpisode = true
    }

    LazyColumn(
        state = listState,
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
                SeriesOverview(series.overview)
            }
        }
        // Cast & crew strip for the series (people who appear across the
        // show, not per-episode). Renders silently when seriesDetails has
        // been enriched with the People list; before that it's empty and
        // CastRow returns early without taking layout space.
        if (series.people.isNotEmpty()) {
            item {
                CastRow(
                    vm = vm,
                    people = series.people,
                    onPersonClick = onPersonClick,
                    compact = true
                )
            }
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
                    EpisodeSectionHeader(
                        selectedSeason = selectedSeason,
                        count = visible.size
                    )
                }
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
            .height(520.dp)
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
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.40f to Color.Black.copy(alpha = 0.05f),
                        0.70f to cs.background.copy(alpha = 0.42f),
                        1f to cs.background
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.70f to Color.Transparent
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .tabletContentWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Text(
                series.name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                series.productionYear?.let { add(it.toString()) }
                series.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                series.runtimeMinutes?.let { add("${it}m") }
                series.communityRating?.let { add("Rating ${"%.1f".format(it)}") }
            }
            if (meta.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(meta) { value ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.40f),
                            contentColor = Color.White
                        ) {
                            Text(
                                value,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            if (playTarget != null) {
                val started = (playTarget.userData?.playbackPositionTicks ?: 0L) > 0L
                val targetLabel = if (started) "Resume" else "Play"
                val episodeLabel = playTarget.takeIf { it.type == "Episode" }?.let { ep ->
                    val s = ep.seasonNumber?.let { "S$it" } ?: ""
                    val e = ep.episodeNumber?.let { "E$it" } ?: ""
                    listOf(s, e).filter { it.isNotEmpty() }.joinToString("")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (episodeLabel.isNullOrBlank()) targetLabel else episodeLabel,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    HeroActionChip {
                        Icon(
                            com.example.jellyfinplayer.ui.icons.DownloadIconVector,
                            contentDescription = "Download"
                        )
                    }
                    HeroActionChip {
                        Icon(Icons.Default.Check, contentDescription = "Watched")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroActionChip(content: @Composable BoxScope.() -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.42f),
        contentColor = Color.White,
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun SeriesOverview(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tabletContentWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .animateContentSize()
    ) {
        Text(
            "Overview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

@Composable
private fun EpisodeSectionHeader(selectedSeason: Int?, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tabletContentWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Episodes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (selectedSeason == 0) "Specials" else "$count episode${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .tabletContentWidth()
    ) {
        items(seasons, key = { it }) { season ->
            val isSelected = season == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onSelect(season) }
            ) {
                Text(
                    if (season == 0) "Specials" else "Season $season",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun EpisodeRow(vm: AppViewModel, ep: MediaItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val played = ep.userData?.played == true
    val progress = ep.playedFraction ?: 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tabletContentWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .width(150.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
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
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
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
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val number = ep.episodeNumber?.let { "E$it" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (number != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = cs.surfaceVariant.copy(alpha = 0.70f),
                            contentColor = cs.onSurfaceVariant
                        ) {
                            Text(
                                number,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        ep.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (played) cs.onSurfaceVariant else cs.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    ep.runtimeMinutes?.let {
                        Text(
                            "$it min",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    ep.communityRating?.let {
                        Text(
                            "Rating ${"%.1f".format(it)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                ep.overview?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(5.dp))
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
    episodes: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<MediaItem>, Long?) -> Unit
) {
    val options: List<Pair<Long?, String>> = listOf(
        null to "Original quality (recommended)",
        20_000_000L to "1080p (~20 Mbps)",
        8_000_000L to "720p (~8 Mbps)",
        3_000_000L to "480p (~3 Mbps)",
        1_000_000L to "360p (~1 Mbps)"
    )
    var selected by remember { mutableStateOf<Long?>(null) }
    val seasons = remember(episodes) {
        episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeasons by remember(seasons) {
        mutableStateOf(seasons.toSet())
    }
    val selectedEpisodes = remember(episodes, selectedSeasons) {
        if (seasons.isEmpty()) {
            episodes
        } else {
            episodes.filter { it.seasonNumber in selectedSeasons }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download all episodes", fontWeight = FontWeight.SemiBold) },
        confirmButton = {
            TextButton(
                enabled = selectedEpisodes.isNotEmpty(),
                onClick = { onConfirm(selectedEpisodes, selected) }
            ) {
                Text("Download ${selectedEpisodes.size}", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "This will download ${selectedEpisodes.size} episodes. Original " +
                        "quality is fastest and uses no server CPU; other " +
                        "qualities require the server to transcode every " +
                        "episode, which can take a long time and consume " +
                        "significant server resources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (seasons.size > 1) {
                    Text(
                        "Seasons",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    seasons.forEach { season ->
                        val checked = season in selectedSeasons
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSeasons = if (checked) {
                                        selectedSeasons - season
                                    } else {
                                        selectedSeasons + season
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedSeasons = if (isChecked) {
                                        selectedSeasons + season
                                    } else {
                                        selectedSeasons - season
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            val count = episodes.count { it.seasonNumber == season }
                            Text(
                                "${if (season == 0) "Specials" else "Season $season"} ($count)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
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
