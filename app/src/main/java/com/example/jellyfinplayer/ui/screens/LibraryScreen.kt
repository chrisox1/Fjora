package com.example.jellyfinplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.UiState
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.ui.components.DownloadStatus
import com.example.jellyfinplayer.ui.components.rememberDownloadStatus

private enum class FilterTab(val label: String) { ALL("All"), MOVIES("Movies"), SHOWS("Shows") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    onItemClick: (MediaItem) -> Unit,
    onDownloadedMovieClick: (Long) -> Unit,
    onDownloadedSeriesClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val state = vm.uiState.collectAsState().value
    val items = vm.library.collectAsState().value
    val homeRow = vm.homeRow.collectAsState().value
    val continueWatching = vm.continueWatching.collectAsState().value
    val nextUp = vm.nextUp.collectAsState().value
    val settings = vm.settings.collectAsState().value
    val serverName = vm.serverName.collectAsState().value
    val refreshing = vm.homeLoadInFlight.collectAsState().value
    val libraries = vm.libraries.collectAsState().value
    val selectedLibraryId = vm.selectedLibraryId.collectAsState().value
    val searchQuery = vm.searchQuery.collectAsState().value
    val searchResults = vm.searchResults.collectAsState().value
    val searchInFlight = vm.searching.collectAsState().value
    val downloads = vm.downloads.collectAsState().value
    var filter by remember { mutableStateOf(FilterTab.ALL) }
    var searchOpen by remember { mutableStateOf(false) }
    var showServerInfoDialog by remember { mutableStateOf(false) }
    var refreshRequested by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }
    var showDeleteAllDownloadsConfirm by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (items.isEmpty()) vm.loadHome() }

    // Renamed for clarity: this is "user has typed something in the search
    // box" — distinct from `searchInFlight` which means "a request is
    // currently running on the server."
    val isSearchMode = searchOpen || searchQuery.isNotBlank()

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            kotlinx.coroutines.delay(50)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(refreshing) {
        if (refreshing) wasRefreshing = true
        if (!refreshing && wasRefreshing && refreshRequested) {
            snackbarHostState.showSnackbar("Library reloaded")
            refreshRequested = false
            wasRefreshing = false
        }
    }

    BackHandler(enabled = isSearchMode) {
        vm.clearSearch()
        searchOpen = false
        keyboard?.hide()
    }

    val filtered = remember(items, filter) {
        when (filter) {
            FilterTab.ALL -> items
            FilterTab.MOVIES -> items.filter { it.type == "Movie" }
            FilterTab.SHOWS -> items.filter { it.type == "Series" }
        }
    }
    val groupedDownloads = remember(downloads) {
        groupDownloadsByContainer(downloads)
    }
    val featuredItem = remember(continueWatching, nextUp, items) {
        continueWatching.firstOrNull() ?: nextUp.firstOrNull() ?: items.firstOrNull()
    }
    val gridState = rememberLazyGridState()
    val libraryTabsAtTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset < 12
        }
    }
    val showLibraryTabs = !isSearchMode &&
        (libraries.size > 1 || downloads.isNotEmpty()) &&
        libraryTabsAtTop

    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = remember(context) { coil.Coil.imageLoader(context) }

    LaunchedEffect(items, homeRow, nextUp, continueWatching) {
        val warmTargets = buildList {
            addAll(homeRow.take(8))
            addAll(continueWatching.take(6))
            addAll(nextUp.take(6))
            addAll(items.take(24))
        }.distinctBy { it.id }

        for (item in warmTargets) {
            vm.posterUrl(item, maxHeight = 360)?.let { url ->
                imageLoader.enqueue(
                    coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(270, 405)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                )
            }
            vm.backdropUrl(item, maxWidth = 600)?.let { url ->
                imageLoader.enqueue(
                    coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(600, 338)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                )
            }
            kotlinx.coroutines.delay(15)
        }
    }

    val handleClick: (MediaItem) -> Unit = { picked ->
        keyboard?.hide()
        // Eagerly warm the image cache for the detail screen's hero before
        // we navigate. By the time MovieDetail / Episodes mounts, both the
        // poster and backdrop are likely in Coil's memory cache, so the
        // AsyncImage in the hero renders instantly instead of fading in
        // after a fetch round-trip. Cheap, fire-and-forget.
        vm.backdropUrl(picked, maxWidth = 1280)?.let { url ->
            imageLoader.enqueue(
                coil.request.ImageRequest.Builder(context).data(url).build()
            )
        }
        vm.posterUrl(picked, maxHeight = 480)?.let { url ->
            imageLoader.enqueue(
                coil.request.ImageRequest.Builder(context).data(url).build()
            )
        }
        onItemClick(picked)
    }

    /**
     * Delete a downloaded file and its record. The DownloadManager entry is
     * also removed; if the download was still in progress, the system
     * cancels it.
     */
    val onDeleteDownload: (com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord) -> Unit = { rec ->
        runCatching {
            // Cancel any in-progress download AND remove the entry from
            // DownloadManager's database. dm.remove() does both in one call.
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                as? android.app.DownloadManager
            dm?.remove(rec.downloadId)
            // Delete the file if it landed on disk.
            rec.filePath?.let { java.io.File(it).delete() }
        }
        // Remove the metadata record regardless of whether file deletion
        // succeeded — orphaned records are worse than orphaned files.
        vm.removeDownload(rec.downloadId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Use safeDrawing instead of the default systemBars. systemBars only
        // accounts for the status / navigation bar, not the display cutout
        // region — so on devices with notches in landscape (or when the app
        // is in cutout-extending mode for any reason), title text could
        // overlap the cutout. safeDrawing includes both, guaranteeing no
        // overlap in any orientation.
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Surface(
                        onClick = { showServerInfoDialog = true },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            serverName.ifBlank { vm.serverUrl().ifBlank { "Fjora" } },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                },
                navigationIcon = {
                    TopBarIconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                actions = {
                    TopBarIconButton(
                        enabled = !refreshing,
                        onClick = {
                            refreshRequested = true
                            vm.loadHome(force = true)
                        }
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    TopBarIconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearchMode) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { vm.setSearchQuery(it) },
                        onClear = {
                            vm.clearSearch()
                            searchOpen = false
                            keyboard?.hide()
                        },
                        onSubmit = { keyboard?.hide() },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester)
                    )
                }
            }

            AnimatedVisibility(
                visible = showLibraryTabs,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                LibraryTabs(
                    libraries = libraries,
                    selectedId = selectedLibraryId,
                    hasDownloads = downloads.isNotEmpty(),
                    onSelect = { vm.selectLibrary(it) }
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state is UiState.Loading && items.isEmpty() -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    state is UiState.Error && items.isEmpty() -> {
                        ErrorBlock(state.message, Modifier.align(Alignment.Center)) { vm.loadHome() }
                    }
                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 138.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (isSearchMode) {
                                when {
                                    searchQuery.isBlank() -> {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Spacer(Modifier.height(1.dp))
                                        }
                                    }
                                    // Show a subtle inline spinner while a
                                    // request is in flight (or while we're
                                    // waiting for the debounce to fire).
                                    // Without this, "No matches" briefly
                                    // appears between the user finishing
                                    // typing and the results landing.
                                    searchInFlight && searchResults.isEmpty() -> {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 48.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                    searchResults.isEmpty() -> {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            EmptyBlock(
                                                "No matches",
                                                "Try a different search term."
                                            )
                                        }
                                    }
                                    else -> {
                                        items(searchResults, key = { it.id }) { item ->
                                            LibraryCard(
                                                item, vm,
                                                onClick = { handleClick(item) }
                                            )
                                        }
                                    }
                                }
                            } else if (selectedLibraryId == DOWNLOADS_TAB_ID) {
                                // Downloads tab — render the user's local
                                // download records grouped: each unique
                                // series becomes a single card (tap to see
                                // its episodes), and each movie becomes a
                                // standalone card. Mirrors the rest of the
                                // app's hierarchy so downloads feel like a
                                // first-class library, not a flat dump.
                                if (downloads.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyBlock(
                                            "No downloads yet",
                                            "Tap the download icon on any movie or episode to save it for offline viewing."
                                        )
                                    }
                                } else {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        DownloadsManagementHeader(
                                            downloads = downloads,
                                            onDeleteAll = { showDeleteAllDownloadsConfirm = true }
                                        )
                                    }
                                    items(groupedDownloads, key = { entry -> entry.key }) { entry ->
                                        when (entry) {
                                            is DownloadEntry.Movie -> DownloadCard(
                                                record = entry.record,
                                                onClick = {
                                                    warmDownloadImages(
                                                        context = context,
                                                        imageLoader = imageLoader,
                                                        records = listOf(entry.record)
                                                    )
                                                    onDownloadedMovieClick(entry.record.downloadId)
                                                },
                                                onDelete = {
                                                    onDeleteDownload(entry.record)
                                                }
                                            )
                                            is DownloadEntry.SeriesGroup -> SeriesDownloadCard(
                                                group = entry,
                                                onClick = {
                                                    warmDownloadImages(
                                                        context = context,
                                                        imageLoader = imageLoader,
                                                        records = entry.records.take(8),
                                                        extraUrl = entry.seriesPosterUrl
                                                    )
                                                    onDownloadedSeriesClick(entry.seriesId)
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                featuredItem?.let { featured ->
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        FeaturedBanner(
                                            item = featured,
                                            vm = vm,
                                            label = when (featured.id) {
                                                continueWatching.firstOrNull()?.id -> "Continue watching"
                                                nextUp.firstOrNull()?.id -> "Next up"
                                                else -> "Featured"
                                            },
                                            onClick = { handleClick(featured) }
                                        )
                                    }
                                }

                                // Two layouts based on user setting:
                                //   merged (default) — single "Continue
                                //     watching" row combining in-progress
                                //     items + next-up entries, deduped
                                //     by series.
                                //   separate — classic Jellyfin layout with
                                //     two rows. More information density;
                                //     useful when you binge several shows.
                                if (settings.showNextUpRow) {
                                    if (continueWatching.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            SectionHeader("Continue watching")
                                        }
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            WideRow(
                                                items = continueWatching,
                                                vm = vm,
                                                showProgress = true,
                                                onItemClick = handleClick
                                            )
                                        }
                                    }
                                    if (nextUp.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            SectionHeader("Next up")
                                        }
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            WideRow(
                                                items = nextUp,
                                                vm = vm,
                                                showProgress = false,
                                                onItemClick = handleClick
                                            )
                                        }
                                    }
                                } else if (homeRow.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        SectionHeader("Continue watching")
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        WideRow(
                                            items = homeRow,
                                            vm = vm,
                                            showProgress = true,
                                            onItemClick = handleClick
                                        )
                                    }
                                }

                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    FilterRow(filter = filter, onFilterChange = { filter = it })
                                }

                                if (filtered.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyBlock(
                                            "Nothing here yet",
                                            "Add movies or shows on your Jellyfin server, then refresh."
                                        )
                                    }
                                } else {
                                    items(filtered, key = { it.id }) { item ->
                                        LibraryCard(item, vm, onClick = { handleClick(item) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDownloadsConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDownloadsConfirm = false },
            title = { Text("Delete all downloads?") },
            text = {
                Text(
                    "This removes ${downloads.size} downloaded item" +
                        if (downloads.size == 1) " from this device." else "s from this device."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllDownloadsConfirm = false
                    downloads.forEach(onDeleteDownload)
                }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDownloadsConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showServerInfoDialog) {
        val name = serverName.ifBlank { "Server" }
        val url = vm.serverUrl().ifBlank { "—" }
        AlertDialog(
            onDismissRequest = { showServerInfoDialog = false },
            icon = {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(name, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "URL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerInfoDialog = false }) { Text("Close") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search movies and shows…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Close search")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun FeaturedBanner(
    item: MediaItem,
    vm: AppViewModel,
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val backdrop = vm.backdropUrl(item, maxWidth = 1280)
    val artwork = backdrop ?: vm.posterUrl(item, maxHeight = 720)
    val progress = item.playedFraction ?: 0f
    val title = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName
    } else {
        item.name
    }
    val subtitle = remember(item) { featuredSubtitle(item) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp)
            .heightIn(min = 190.dp)
            .aspectRatio(16f / 8.2f)
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (artwork != null) {
            val imageRequest = remember(artwork, context) {
                coil.request.ImageRequest.Builder(context)
                    .data(artwork)
                    .size(1280, 720)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (item.type == "Movie") Icons.Default.Movie else Icons.Default.Tv,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(64.dp)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.82f),
                        0.62f to Color.Black.copy(alpha = 0.38f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.48f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(16.dp)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (progress > 0f && progress < 0.99f) "Resume" else "Play")
            }
        }
        if (progress > 0f && progress < 0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                color = cs.primary,
                trackColor = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

private fun featuredSubtitle(item: MediaItem): String = buildList {
    if (item.type == "Episode") {
        item.seasonNumber?.let { season ->
            item.episodeNumber?.let { episode -> add("S$season E$episode") }
        }
        if (item.name.isNotBlank()) add(item.name)
    } else {
        item.productionYear?.let { add(it.toString()) }
        item.runtimeMinutes?.let { add("${it}m") }
    }
}.joinToString(" - ")

private fun episodeLabel(item: MediaItem): String = buildList {
    val season = item.seasonNumber
    val episode = item.episodeNumber
    when {
        season != null && episode != null -> add("S$season E$episode")
        episode != null -> add("E$episode")
    }
    if (item.name.isNotBlank()) add(item.name)
}.joinToString(" - ")

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun FilterRow(filter: FilterTab, onFilterChange: (FilterTab) -> Unit) {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        SectionHeader("All titles")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterTab.values().forEach { tab ->
                val selected = tab == filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(tab) },
                    label = { Text(tab.label, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(50)
                )
            }
        }
    }
}

/** Shared horizontal row of wide thumbnail cards for both home rows. */
@Composable
private fun WideRow(
    items: List<MediaItem>,
    vm: AppViewModel,
    showProgress: Boolean,
    onItemClick: (MediaItem) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(items, key = { it.id }) { item ->
            WideCard(item, vm, showProgress, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun WideCard(item: MediaItem, vm: AppViewModel, showProgress: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant)
        ) {
            val url = vm.backdropUrl(item, maxWidth = 600)
            if (url != null) {
                val imageRequest = remember(url, context) {
                    coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(600, 338)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                val hasProgress = (item.playedFraction ?: 0f) > 0f
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                // Heavier scrim only when there's a progress
                                // bar to anchor against; otherwise a light
                                // touch so the artwork stays vivid.
                                1f to Color.Black.copy(alpha = if (hasProgress) 0.7f else 0.18f)
                            )
                        )
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
            if (showProgress) {
                val progress = item.playedFraction ?: 0f
                if (progress > 0f) {
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
        }
        Spacer(Modifier.height(8.dp))
        val title = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
            item.seriesName
        } else item.name
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.type == "Episode") {
            val sub = episodeLabel(item)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryCard(item: MediaItem, vm: AppViewModel, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier = Modifier.clickable { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant)
        ) {
            val url = vm.posterUrl(item)
            if (url != null) {
                val imageRequest = remember(url, context) {
                    coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(360, 540)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.7f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f)
                            )
                        )
                )
            } else {
                Icon(
                    if (item.type == "Movie") Icons.Default.Movie else Icons.Default.Tv,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    when (item.type) {
                        "Movie" -> "Movie"
                        "Series" -> "Series"
                        "Episode" -> "Episode"
                        else -> item.type
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            val progress = item.playedFraction ?: 0f
            if (progress > 0f && progress < 0.99f) {
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
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val sub = buildList {
            item.productionYear?.let { add(it.toString()) }
            item.communityRating?.let { add("Rating ${"%.1f".format(it)}") }
        }.joinToString(" - ")
        if (sub.isNotEmpty()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyBlock(title: String, sub: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorBlock(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

private fun warmDownloadImages(
    context: android.content.Context,
    imageLoader: coil.ImageLoader,
    records: List<com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord>,
    extraUrl: String? = null
) {
    val urls = buildList {
        extraUrl?.let(::add)
        records.forEach { record ->
            record.seriesPosterUrl?.let(::add)
            record.posterUrl?.let(::add)
        }
    }.filter { it.isNotBlank() }.distinct()

    urls.forEach { url ->
        imageLoader.enqueue(
            coil.request.ImageRequest.Builder(context)
                .data(url)
                .size(480, 720)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        )
    }
}

/**
 * Horizontal chip strip for switching between the user's Jellyfin libraries.
 * Renders an "All" chip first, then one chip per library. Active chip is
 * tinted primary; inactive chips use surfaceVariant.
 */
@Composable
private fun LibraryTabs(
    libraries: List<MediaItem>,
    selectedId: String?,
    hasDownloads: Boolean,
    onSelect: (String?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "all") {
            LibraryTabChip(
                label = "All",
                selected = selectedId == null,
                onClick = { onSelect(null) }
            )
        }
        items(libraries, key = { it.id }) { lib ->
            LibraryTabChip(
                label = lib.name.ifBlank {
                    when (lib.collectionType) {
                        "movies" -> "Movies"
                        "tvshows" -> "TV Shows"
                        "boxsets" -> "Collections"
                        "homevideos" -> "Home Videos"
                        else -> "Library"
                    }
                },
                selected = selectedId == lib.id,
                onClick = { onSelect(lib.id) }
            )
        }
        // Downloads chip — visible only when there's at least one download
        // record. Uses the sentinel ID "__downloads__" which the Library
        // screen recognizes and renders specially (offline records instead
        // of remote items).
        if (hasDownloads) {
            item(key = "__downloads__") {
                LibraryTabChip(
                    label = "Downloads",
                    selected = selectedId == DOWNLOADS_TAB_ID,
                    onClick = { onSelect(DOWNLOADS_TAB_ID) }
                )
            }
        }
    }
}

/**
 * Sentinel value used as the selected library ID to mean "show the user's
 * downloaded files instead of remote library items." Picked to never
 * collide with a real Jellyfin item ID (those are GUIDs, no underscores).
 */
internal const val DOWNLOADS_TAB_ID = "__downloads__"

@Composable
private fun TopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
private fun DownloadsManagementHeader(
    downloads: List<com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord>,
    onDeleteAll: () -> Unit
) {
    val totalBytes = remember(downloads) {
        downloads.sumOf { rec ->
            rec.filePath
                ?.let { runCatching { java.io.File(it).length() }.getOrNull() }
                ?.takeIf { it > 0L }
                ?: rec.sizeBytes.takeIf { it > 0L }
                ?: 0L
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${downloads.size} item${if (downloads.size == 1) "" else "s"} - ${formatDownloadStorage(totalBytes)} used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDeleteAll) {
                Text("Delete all")
            }
        }
    }
}

private fun formatDownloadStorage(bytes: Long): String {
    if (bytes <= 0L) return "unknown storage"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.0f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

@Composable
private fun LibraryTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primary else cs.surfaceVariant,
        contentColor = if (selected) cs.onPrimary else cs.onSurfaceVariant
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Card for a downloaded item in the My Downloads grid. Mirrors the layout
 * of LibraryCard (poster on top, title below) but adds:
 *   - A "downloaded" check-mark badge in the corner so the user can see at
 *     a glance which items are saved offline.
 *   - Long-press to delete the download (with a confirm dialog).
 *   - Subtitle row showing original quality vs. transcoded quality so the
 *     user remembers what they grabbed.
 *
 * Uses combinedClickable so a regular tap plays and a long-press deletes.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadCard(
    record: com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    // Live status from DownloadManager — drives the progress overlay and
    // gates the tap-to-play action so users can't try to play a half-
    // downloaded file.
    val status = rememberDownloadStatus(record.downloadId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    // Only allow play once the file is fully downloaded.
                    // The progress overlay below tells the user why a tap
                    // does nothing on an in-flight download.
                    if (status.isComplete) onClick()
                },
                onLongClick = { showDeleteDialog = true }
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant)
        ) {
            if (!record.posterUrl.isNullOrBlank()) {
                val imageRequest = remember(record.posterUrl, context) {
                    coil.request.ImageRequest.Builder(context)
                        .data(record.posterUrl)
                        .size(360, 540)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Status overlay layers ON TOP of the poster:
            //   - while in-flight: a scrim with a percentage / spinner
            //   - on failure: a "Failed" badge in red
            //   - on success: nothing (just the offline check badge below)
            when {
                status.isInFlight -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (status.progress != null) {
                                CircularProgressIndicator(
                                    progress = status.progress,
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${(status.progress * 100).toInt()}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Starting…",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                status.state == DownloadStatus.State.Failed -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Failed",
                            color = cs.error,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            // Offline indicator badge — only shown for completed downloads
            // so it doesn't compete with the progress overlay.
            if (status.isComplete) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(cs.primary)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = com.example.jellyfinplayer.ui.icons.DownloadIconVector,
                        contentDescription = "Downloaded",
                        tint = cs.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Title — for episodes, prepend the series name and S/E label so
        // the user can tell episodes of different shows apart in the grid.
        val displayLine = when {
            record.seriesName != null && record.seasonEpisodeLabel != null ->
                "${record.seriesName} · ${record.seasonEpisodeLabel}"
            else -> record.title
        }
        Text(
            displayLine,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            downloadInfoLine(record, status),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 1
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete download?") },
            text = { Text("\"${record.title}\" will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}


/**
 * One entry in the My Downloads grid. Either a standalone movie (one record,
 * one card, tap goes to detail) or a series group (multiple records under
 * the same seriesId, one card, tap goes to a downloaded-series view).
 */
private sealed class DownloadEntry {
    /** Stable key for LazyVerticalGrid so re-grouping doesn't churn items. */
    abstract val key: String

    data class Movie(
        val record: com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord
    ) : DownloadEntry() {
        override val key: String = "movie:${record.downloadId}"
    }

    data class SeriesGroup(
        val seriesId: String,
        val seriesName: String,
        val seriesPosterUrl: String?,
        val records: List<com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord>
    ) : DownloadEntry() {
        override val key: String = "series:$seriesId"
    }
}

/**
 * Group flat download records into the structure the My Downloads UI shows.
 * Series episodes get bundled under their parent series; movies stay as
 * individual entries. Falls back to grouping orphan episodes (no seriesId)
 * by their series NAME so even malformed records still cluster sensibly.
 */
private fun groupDownloadsByContainer(
    records: List<com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord>
): List<DownloadEntry> {
    val movies = mutableListOf<DownloadEntry.Movie>()
    val seriesGroups = linkedMapOf<String, MutableList<com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord>>()

    for (rec in records) {
        val groupKey = rec.seriesId ?: rec.seriesName
        if (groupKey != null && rec.seriesName != null) {
            seriesGroups.getOrPut(groupKey) { mutableListOf() }.add(rec)
        } else {
            movies.add(DownloadEntry.Movie(rec))
        }
    }

    val seriesEntries = seriesGroups.map { (key, group) ->
        DownloadEntry.SeriesGroup(
            seriesId = key,
            seriesName = group.first().seriesName ?: "Unknown series",
            // Use the first non-null series poster from any episode in the
            // group — they should all be the same anyway.
            seriesPosterUrl = group.firstOrNull { it.seriesPosterUrl != null }
                ?.seriesPosterUrl,
            records = group
        )
    }

    // Series groups first (typically what users come back to), then movies.
    return seriesEntries + movies
}

/**
 * Card representing a downloaded series in the My Downloads grid. Renders
 * the series poster with a small badge showing the episode count.
 */
@Composable
private fun SeriesDownloadCard(
    group: DownloadEntry.SeriesGroup,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant)
        ) {
            if (!group.seriesPosterUrl.isNullOrBlank()) {
                val imageRequest = remember(group.seriesPosterUrl, context) {
                    coil.request.ImageRequest.Builder(context)
                        .data(group.seriesPosterUrl)
                        .size(360, 540)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = group.seriesName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Episode count badge top-right. Number is more useful than a
            // generic "downloaded" check here because a series can have any
            // number of episodes downloaded (1 of 30, 25 of 30, etc).
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(cs.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "${group.records.size} ep",
                    color = cs.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            group.seriesName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "Series · Downloaded",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
