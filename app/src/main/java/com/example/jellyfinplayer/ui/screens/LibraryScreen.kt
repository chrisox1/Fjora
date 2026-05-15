package com.example.jellyfinplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.jellyfinplayer.data.HomeHeroSource
import com.example.jellyfinplayer.ui.components.DownloadStatus
import com.example.jellyfinplayer.ui.components.rememberDownloadStatus

private enum class LibraryViewMode(val title: String) {
    HOME(""),
    LIBRARIES("Libraries"),
    MOVIES("Movies"),
    SHOWS("Shows")
}

private object LibraryNavigationSnapshot {
    var viewMode: LibraryViewMode = LibraryViewMode.HOME
    var firstVisibleItemIndex: Int = 0
    var firstVisibleItemScrollOffset: Int = 0
    var homeReturnIndex: Int = 0
    var homeReturnOffset: Int = 0
}

/**
 * Horizontal scroll positions for the home-screen rows. Module-level so they
 * survive LibraryScreen being unmounted while the user is on a detail screen
 * — that way "go back" returns the user to the exact card they tapped on
 * inside each row.
 *
 * Stored as `(firstVisibleItemIndex, firstVisibleItemScrollOffset)` pairs.
 * Reset to `0 to 0` when the user switches libraries or logs out.
 */
private object LatestRowsScrollSnapshot {
    var continueWatching: Pair<Int, Int> = 0 to 0
    var nextUp: Pair<Int, Int> = 0 to 0
    var homeRow: Pair<Int, Int> = 0 to 0
    var latestMovies: Pair<Int, Int> = 0 to 0
    var latestShows: Pair<Int, Int> = 0 to 0
    fun reset() {
        continueWatching = 0 to 0
        nextUp = 0 to 0
        homeRow = 0 to 0
        latestMovies = 0 to 0
        latestShows = 0 to 0
    }
}

private enum class MediaSortKey(val label: String) {
    TITLE("Title"),
    IMDB_RATING("IMDB Rating"),
    PARENTAL_RATING("Parental Rating"),
    DATE_ADDED("Date Added"),
    DATE_PLAYED("Date Played"),
    RELEASE_DATE("Release Date")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
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
    val selectedLibraryId = vm.selectedLibraryId.collectAsState().value
    val libraries = vm.libraries.collectAsState().value
    val searchQuery = vm.searchQuery.collectAsState().value
    val searchResults = vm.searchResults.collectAsState().value
    val searchInFlight = vm.searching.collectAsState().value
    val downloads = vm.downloads.collectAsState().value
    var viewMode by remember { mutableStateOf(LibraryNavigationSnapshot.viewMode) }
    var sortKey by remember { mutableStateOf(MediaSortKey.TITLE) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortDialog by remember { mutableStateOf(false) }
    var fullListSearchOpen by remember { mutableStateOf(false) }
    var fullListSearchQuery by remember { mutableStateOf("") }
    var homeReturnIndex by remember {
        mutableIntStateOf(LibraryNavigationSnapshot.homeReturnIndex)
    }
    var homeReturnOffset by remember {
        mutableIntStateOf(LibraryNavigationSnapshot.homeReturnOffset)
    }
    var searchOpen by remember { mutableStateOf(false) }
    var showServerInfoDialog by remember { mutableStateOf(false) }
    var refreshRequested by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }
    var showDeleteAllDownloadsConfirm by remember { mutableStateOf(false) }
    var observedSelectedLibraryId by remember { mutableStateOf(selectedLibraryId) }
    var pendingSortScrollToTop by remember { mutableStateOf(false) }
    var searchReturnIndex by remember { mutableIntStateOf(0) }
    var searchReturnOffset by remember { mutableIntStateOf(0) }
    var pendingSearchRestore by remember { mutableStateOf(false) }
    var fullListSearchReturnIndex by remember { mutableIntStateOf(0) }
    var fullListSearchReturnOffset by remember { mutableIntStateOf(0) }
    var pendingFullListSearchRestore by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocusRequester = remember { FocusRequester() }
    val selectedLibrary = libraries.firstOrNull { it.id == selectedLibraryId }
    val showingDownloads = selectedLibraryId == DOWNLOADS_TAB_ID
    val showingLibraryItems = viewMode == LibraryViewMode.LIBRARIES &&
        selectedLibraryId != null &&
        selectedLibraryId != DOWNLOADS_TAB_ID

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

    LaunchedEffect(fullListSearchOpen) {
        if (fullListSearchOpen) {
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

    fun returnToHomeMode() {
        if (selectedLibraryId != null) {
            observedSelectedLibraryId = null
            vm.selectLibrary(null)
        }
        LibraryNavigationSnapshot.viewMode = LibraryViewMode.HOME
        LibraryNavigationSnapshot.firstVisibleItemIndex = homeReturnIndex
        LibraryNavigationSnapshot.firstVisibleItemScrollOffset = homeReturnOffset
        LibraryNavigationSnapshot.homeReturnIndex = homeReturnIndex
        LibraryNavigationSnapshot.homeReturnOffset = homeReturnOffset
        viewMode = LibraryViewMode.HOME
    }

    BackHandler(enabled = isSearchMode) {
        vm.clearSearch()
        searchOpen = false
        pendingSearchRestore = true
        keyboard?.hide()
    }

    BackHandler(enabled = !isSearchMode && fullListSearchOpen) {
        fullListSearchOpen = false
        fullListSearchQuery = ""
        pendingFullListSearchRestore = true
        keyboard?.hide()
    }

    BackHandler(enabled = !isSearchMode && !fullListSearchOpen && viewMode != LibraryViewMode.HOME) {
        returnToHomeMode()
    }

    BackHandler(enabled = !isSearchMode && !fullListSearchOpen && showingDownloads) {
        returnToHomeMode()
    }

    LaunchedEffect(selectedLibraryId) {
        if (observedSelectedLibraryId != selectedLibraryId) {
            val targetMode = if (
                selectedLibraryId != null &&
                selectedLibraryId != DOWNLOADS_TAB_ID
            ) {
                LibraryViewMode.LIBRARIES
            } else {
                LibraryViewMode.HOME
            }
            viewMode = targetMode
            showSortDialog = false
            fullListSearchOpen = false
            fullListSearchQuery = ""
            homeReturnIndex = 0
            homeReturnOffset = 0
            LibraryNavigationSnapshot.viewMode = targetMode
            LibraryNavigationSnapshot.firstVisibleItemIndex = 0
            LibraryNavigationSnapshot.firstVisibleItemScrollOffset = 0
            LibraryNavigationSnapshot.homeReturnIndex = 0
            LibraryNavigationSnapshot.homeReturnOffset = 0
            LatestRowsScrollSnapshot.reset()
            observedSelectedLibraryId = selectedLibraryId
        }
    }

    val latestMovies = remember(items) {
        items
            .filter { it.type == "Movie" }
            .sortedByDescending { it.latestSortValue() }
    }
    val latestShows = remember(items) {
        items
            .filter { it.type == "Series" }
            .sortedByDescending { it.latestSortValue() }
    }
    val fullListItems = remember(items, viewMode, sortKey, sortAscending) {
        val base = when (viewMode) {
            LibraryViewMode.LIBRARIES -> if (selectedLibraryId == null) {
                emptyList()
            } else {
                items.filter { it.type == "Movie" || it.type == "Series" }
            }
            LibraryViewMode.MOVIES -> items.filter { it.type == "Movie" }
            LibraryViewMode.SHOWS -> items.filter { it.type == "Series" }
            LibraryViewMode.HOME -> emptyList()
        }
        sortMediaItems(base, sortKey, sortAscending)
    }
    val visibleFullListItems = remember(fullListItems, fullListSearchQuery) {
        val query = fullListSearchQuery.trim()
        if (query.isBlank()) {
            fullListItems
        } else {
            fullListItems.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                    item.productionYear?.toString()?.contains(query) == true ||
                    item.communityRating?.let { "%.1f".format(it) }?.contains(query) == true
            }
        }
    }
    val visibleSearchResults = remember(
        items,
        searchResults,
        searchQuery,
        settings.includeEpisodesInSearch
    ) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            val localMatches = items.filter { item ->
                (item.type == "Movie" || item.type == "Series") &&
                    (
                        item.name.contains(query, ignoreCase = true) ||
                            item.productionYear?.toString()?.contains(query) == true ||
                            item.communityRating?.let { "%.1f".format(it) }?.contains(query) == true
                    )
            }
            if (!settings.includeEpisodesInSearch) {
                localMatches
            } else {
                (localMatches + searchResults.filter { result ->
                    result.type == "Movie" || result.type == "Series" || result.type == "Episode"
                }).distinctBy { it.id }
            }
        }
    }
    val groupedDownloads = remember(downloads) {
        groupDownloadsByContainer(downloads)
    }
    val featuredItems = remember(continueWatching, nextUp, items, settings.homeHeroSource) {
        homeHeroItems(
            source = settings.homeHeroSource,
            continueWatching = continueWatching,
            nextUp = nextUp,
            libraryItems = items
        )
    }
    val featuredLabel = remember(continueWatching, nextUp, settings.homeHeroSource) {
        homeHeroLabel(
            source = settings.homeHeroSource,
            hasResumeItems = continueWatching.isNotEmpty(),
            hasNextUpItems = nextUp.isNotEmpty()
        )
    }
    val homeGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = LibraryNavigationSnapshot.homeReturnIndex,
        initialFirstVisibleItemScrollOffset = LibraryNavigationSnapshot.homeReturnOffset
    )
    val fullListGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (LibraryNavigationSnapshot.viewMode != LibraryViewMode.HOME) {
            LibraryNavigationSnapshot.firstVisibleItemIndex
        } else {
            0
        },
        initialFirstVisibleItemScrollOffset = if (LibraryNavigationSnapshot.viewMode != LibraryViewMode.HOME) {
            LibraryNavigationSnapshot.firstVisibleItemScrollOffset
        } else {
            0
        }
    )
    val activeGridState = if (viewMode == LibraryViewMode.HOME) homeGridState else fullListGridState
    var skipInitialViewModeScroll by remember { mutableStateOf(true) }
    LaunchedEffect(viewMode) {
        if (skipInitialViewModeScroll) {
            skipInitialViewModeScroll = false
        } else if (viewMode == LibraryViewMode.HOME) {
            fullListSearchOpen = false
            fullListSearchQuery = ""
            // Instant scroll so coming back to the home grid lands exactly
            // where the user left off — animated scroll here read as a
            // "swipe down" jolt.
            homeGridState.animateScrollToItem(homeReturnIndex, homeReturnOffset)
        } else {
            fullListGridState.scrollToItem(0)
        }
    }

    // Sort change resets the grid to the top. Without this the user lands
    // somewhere in the middle of the re-sorted list (their previous index
    // points at a different item under the new sort order) which feels
    // broken. Only fires in the full-list views — HOME doesn't sort.
    LaunchedEffect(visibleFullListItems, pendingSortScrollToTop) {
        if (pendingSortScrollToTop && viewMode != LibraryViewMode.HOME) {
            withFrameNanos { }
            withFrameNanos { }
            fullListGridState.scrollToItem(0, 0)
            LibraryNavigationSnapshot.firstVisibleItemIndex = 0
            LibraryNavigationSnapshot.firstVisibleItemScrollOffset = 0
            pendingSortScrollToTop = false
        }
    }
    LaunchedEffect(isSearchMode, pendingSearchRestore) {
        if (pendingSearchRestore && !isSearchMode) {
            withFrameNanos { }
            activeGridState.scrollToItem(searchReturnIndex, searchReturnOffset)
            LibraryNavigationSnapshot.firstVisibleItemIndex = searchReturnIndex
            LibraryNavigationSnapshot.firstVisibleItemScrollOffset = searchReturnOffset
            pendingSearchRestore = false
        }
    }
    LaunchedEffect(fullListSearchOpen, pendingFullListSearchRestore) {
        if (pendingFullListSearchRestore && !fullListSearchOpen) {
            withFrameNanos { }
            fullListGridState.scrollToItem(fullListSearchReturnIndex, fullListSearchReturnOffset)
            LibraryNavigationSnapshot.firstVisibleItemIndex = fullListSearchReturnIndex
            LibraryNavigationSnapshot.firstVisibleItemScrollOffset = fullListSearchReturnOffset
            pendingFullListSearchRestore = false
        }
    }
    fun saveLibraryPosition() {
        LibraryNavigationSnapshot.viewMode = viewMode
        LibraryNavigationSnapshot.firstVisibleItemIndex = activeGridState.firstVisibleItemIndex
        LibraryNavigationSnapshot.firstVisibleItemScrollOffset = activeGridState.firstVisibleItemScrollOffset
        if (viewMode == LibraryViewMode.HOME) {
            LibraryNavigationSnapshot.homeReturnIndex = homeGridState.firstVisibleItemIndex
            LibraryNavigationSnapshot.homeReturnOffset = homeGridState.firstVisibleItemScrollOffset
        }
    }

    // Persistent horizontal scroll states for the four home-screen rows.
    // Their LazyListState is re-created when LibraryScreen unmounts (e.g.
    // user navigates to a detail screen), so we mirror their scroll position
    // into LatestRowsScrollSnapshot continuously via snapshotFlow. On
    // remount, the new states initialize from the snapshot — restoring the
    // user to the exact card they last tapped.
    val continueWatchingRowState = rememberLazyListState(
        initialFirstVisibleItemIndex = LatestRowsScrollSnapshot.continueWatching.first,
        initialFirstVisibleItemScrollOffset = LatestRowsScrollSnapshot.continueWatching.second
    )
    val nextUpRowState = rememberLazyListState(
        initialFirstVisibleItemIndex = LatestRowsScrollSnapshot.nextUp.first,
        initialFirstVisibleItemScrollOffset = LatestRowsScrollSnapshot.nextUp.second
    )
    val homeRowState = rememberLazyListState(
        initialFirstVisibleItemIndex = LatestRowsScrollSnapshot.homeRow.first,
        initialFirstVisibleItemScrollOffset = LatestRowsScrollSnapshot.homeRow.second
    )
    val latestMoviesState = rememberLazyListState(
        initialFirstVisibleItemIndex = LatestRowsScrollSnapshot.latestMovies.first,
        initialFirstVisibleItemScrollOffset = LatestRowsScrollSnapshot.latestMovies.second
    )
    val latestShowsState = rememberLazyListState(
        initialFirstVisibleItemIndex = LatestRowsScrollSnapshot.latestShows.first,
        initialFirstVisibleItemScrollOffset = LatestRowsScrollSnapshot.latestShows.second
    )
    LaunchedEffect(continueWatchingRowState) {
        snapshotFlow {
            continueWatchingRowState.firstVisibleItemIndex to
                continueWatchingRowState.firstVisibleItemScrollOffset
        }.collect { LatestRowsScrollSnapshot.continueWatching = it }
    }
    LaunchedEffect(nextUpRowState) {
        snapshotFlow {
            nextUpRowState.firstVisibleItemIndex to
                nextUpRowState.firstVisibleItemScrollOffset
        }.collect { LatestRowsScrollSnapshot.nextUp = it }
    }
    LaunchedEffect(homeRowState) {
        snapshotFlow {
            homeRowState.firstVisibleItemIndex to
                homeRowState.firstVisibleItemScrollOffset
        }.collect { LatestRowsScrollSnapshot.homeRow = it }
    }
    LaunchedEffect(latestMoviesState) {
        snapshotFlow {
            latestMoviesState.firstVisibleItemIndex to
                latestMoviesState.firstVisibleItemScrollOffset
        }.collect { LatestRowsScrollSnapshot.latestMovies = it }
    }
    LaunchedEffect(latestShowsState) {
        snapshotFlow {
            latestShowsState.firstVisibleItemIndex to
                latestShowsState.firstVisibleItemScrollOffset
        }.collect { LatestRowsScrollSnapshot.latestShows = it }
    }
    val openFullList: (LibraryViewMode) -> Unit = { mode ->
        homeReturnIndex = homeGridState.firstVisibleItemIndex
        homeReturnOffset = homeGridState.firstVisibleItemScrollOffset
        LibraryNavigationSnapshot.homeReturnIndex = homeReturnIndex
        LibraryNavigationSnapshot.homeReturnOffset = homeReturnOffset
        LibraryNavigationSnapshot.viewMode = mode
        LibraryNavigationSnapshot.firstVisibleItemIndex = 0
        LibraryNavigationSnapshot.firstVisibleItemScrollOffset = 0
        fullListSearchOpen = false
        fullListSearchQuery = ""
        showSortDialog = false
        viewMode = mode
    }
    fun openHomeFromBottomBar() {
        observedSelectedLibraryId = null
        vm.selectLibrary(null)
        vm.clearSearch()
        searchOpen = false
        fullListSearchOpen = false
        fullListSearchQuery = ""
        showSortDialog = false
        returnToHomeMode()
    }
    fun openLibrariesFromBottomBar() {
        observedSelectedLibraryId = null
        vm.selectLibrary(null)
        openFullList(LibraryViewMode.LIBRARIES)
    }
    fun openDownloadsFromBottomBar() {
        observedSelectedLibraryId = DOWNLOADS_TAB_ID
        vm.selectLibrary(DOWNLOADS_TAB_ID)
        vm.clearSearch()
        searchOpen = false
        fullListSearchOpen = false
        fullListSearchQuery = ""
        showSortDialog = false
        LibraryNavigationSnapshot.viewMode = LibraryViewMode.HOME
        viewMode = LibraryViewMode.HOME
    }
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
        saveLibraryPosition()
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
            dm?.remove(*listOf(rec.downloadId).plus(rec.subtitleDownloadIds).toLongArray())
            // Delete the file if it landed on disk.
            rec.filePath?.let { java.io.File(it).delete() }
            rec.subtitlePaths.forEach { java.io.File(it).delete() }
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
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isSearchMode && !fullListSearchOpen) {
                FjoraBottomNavigation(
                    selected = when {
                        selectedLibraryId == DOWNLOADS_TAB_ID -> BottomDestination.DOWNLOADS
                        viewMode != LibraryViewMode.HOME -> BottomDestination.LIBRARIES
                        else -> BottomDestination.HOME
                    },
                    onHomeClick = { openHomeFromBottomBar() },
                    onLibrariesClick = { openLibrariesFromBottomBar() },
                    onDownloadsClick = { openDownloadsFromBottomBar() }
                )
            }
        },
        topBar = {
            if (viewMode != LibraryViewMode.HOME || showingDownloads) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            when {
                                showingDownloads -> "Downloads"
                                showingLibraryItems -> selectedLibrary?.name ?: "Library"
                                else -> viewMode.title
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { returnToHomeMode() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (!showingDownloads && showingLibraryItems) {
                            IconButton(onClick = {
                                fullListSearchReturnIndex = fullListGridState.firstVisibleItemIndex
                                fullListSearchReturnOffset = fullListGridState.firstVisibleItemScrollOffset
                                fullListSearchOpen = true
                            }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search ${selectedLibrary?.name ?: "Library"}"
                                )
                            }
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Sort")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
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
                            pendingSearchRestore = true
                            keyboard?.hide()
                        },
                        onSubmit = { keyboard?.hide() },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester)
                    )
                }
            } else if (viewMode != LibraryViewMode.HOME && fullListSearchOpen) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SearchBar(
                        query = fullListSearchQuery,
                        onQueryChange = { fullListSearchQuery = it },
                        onClear = {
                            fullListSearchOpen = false
                            fullListSearchQuery = ""
                            pendingFullListSearchRestore = true
                            keyboard?.hide()
                        },
                        onSubmit = { keyboard?.hide() },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester)
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = {
                        val openingFullList = initialState == LibraryViewMode.HOME &&
                            targetState != LibraryViewMode.HOME
                        val distance = if (openingFullList) 48 else -48
                        (
                            slideInHorizontally(animationSpec = tween(180)) { distance } +
                                fadeIn(animationSpec = tween(140))
                            ) togetherWith (
                            slideOutHorizontally(animationSpec = tween(180)) { -distance / 2 } +
                                fadeOut(animationSpec = tween(120))
                            )
                    },
                    label = "library_view_mode",
                    modifier = Modifier.fillMaxSize()
                ) { animatedViewMode ->
                    val animatedFullListItems = remember(items, animatedViewMode, sortKey, sortAscending) {
                        val base = when (animatedViewMode) {
                            LibraryViewMode.LIBRARIES -> if (selectedLibraryId == null) {
                                emptyList()
                            } else {
                                items.filter { it.type == "Movie" || it.type == "Series" }
                            }
                            LibraryViewMode.MOVIES -> items.filter { it.type == "Movie" }
                            LibraryViewMode.SHOWS -> items.filter { it.type == "Series" }
                            LibraryViewMode.HOME -> emptyList()
                        }
                        sortMediaItems(base, sortKey, sortAscending)
                    }
                    val animatedVisibleFullListItems = remember(
                        animatedFullListItems,
                        fullListSearchQuery
                    ) {
                        val query = fullListSearchQuery.trim()
                        if (query.isBlank()) {
                            animatedFullListItems
                        } else {
                            animatedFullListItems.filter { item ->
                                item.name.contains(query, ignoreCase = true) ||
                                    item.productionYear?.toString()?.contains(query) == true ||
                                    item.communityRating?.let { "%.1f".format(it) }?.contains(query) == true
                            }
                        }
                    }

                    when {
                        state is UiState.Loading && items.isEmpty() -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        state is UiState.Error && items.isEmpty() -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ErrorBlock(state.message) { vm.loadHome() }
                            }
                        }
                        else -> {
                        LazyVerticalGrid(
                            state = if (animatedViewMode == LibraryViewMode.HOME) {
                                homeGridState
                            } else {
                                fullListGridState
                            },
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
                                    searchInFlight && items.isEmpty() &&
                                        visibleSearchResults.isEmpty() -> {
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
                                    visibleSearchResults.isEmpty() -> {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            EmptyBlock(
                                                "No matches",
                                                "Try a different search term."
                                            )
                                        }
                                    }
                                    else -> {
                                        items(visibleSearchResults, key = { it.id }) { item ->
                                            LibraryCard(
                                                item, vm,
                                                onClick = { handleClick(item) },
                                                modifier = Modifier.animateItemPlacement()
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
                                                    saveLibraryPosition()
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
                                                    saveLibraryPosition()
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
                            } else if (animatedViewMode == LibraryViewMode.LIBRARIES && selectedLibraryId == null) {
                                if (libraries.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyBlock(
                                            "No libraries found",
                                            "Refresh after adding libraries on your Jellyfin server."
                                        )
                                    }
                                } else {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            "Choose a library",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                        )
                                    }
                                    items(libraries, key = { it.id }) { library ->
                                        ServerLibraryCard(
                                            library = library,
                                            onClick = {
                                                saveLibraryPosition()
                                                observedSelectedLibraryId = library.id
                                                vm.selectLibrary(library.id)
                                                LibraryNavigationSnapshot.viewMode = LibraryViewMode.LIBRARIES
                                                viewMode = LibraryViewMode.LIBRARIES
                                            },
                                            modifier = Modifier.animateItemPlacement()
                                        )
                                    }
                                }
                            } else if (animatedViewMode != LibraryViewMode.HOME) {
                                if (animatedVisibleFullListItems.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        if (fullListSearchQuery.isBlank()) {
                                            EmptyBlock(
                                                "Nothing here yet",
                                                if (showingLibraryItems) {
                                                    "Add items to ${selectedLibrary?.name ?: "this library"} on your Jellyfin server, then refresh."
                                                } else {
                                                    "Add ${animatedViewMode.title.lowercase()} on your Jellyfin server, then refresh."
                                                }
                                            )
                                        } else {
                                            EmptyBlock(
                                                "No matches",
                                                "Try a different search term."
                                            )
                                        }
                                    }
                                } else {
                                    items(animatedVisibleFullListItems, key = { it.id }) { item ->
                                        LibraryCard(
                                            item,
                                            vm,
                                            onClick = { handleClick(item) },
                                            showTypeBadge = false,
                                            modifier = Modifier.animateItemPlacement()
                                        )
                                    }
                                }
                            } else {
                                if (featuredItems.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        FeaturedCarousel(
                                            items = featuredItems,
                                            vm = vm,
                                            label = featuredLabel,
                                            refreshing = refreshing,
                                            onSearchClick = {
                                                searchReturnIndex = activeGridState.firstVisibleItemIndex
                                                searchReturnOffset = activeGridState.firstVisibleItemScrollOffset
                                                searchOpen = true
                                            },
                                            onRefreshClick = {
                                                refreshRequested = true
                                                vm.loadHome(force = true)
                                            },
                                            onSettingsClick = {
                                                saveLibraryPosition()
                                                onSettingsClick()
                                            },
                                            onItemClick = handleClick
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
                                                onItemClick = handleClick,
                                                state = continueWatchingRowState
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
                                                onItemClick = handleClick,
                                                state = nextUpRowState
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
                                            onItemClick = handleClick,
                                            state = homeRowState
                                        )
                                    }
                                }

                                if (latestMovies.isEmpty() && latestShows.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyBlock(
                                            "Nothing here yet",
                                            "Add movies or shows on your Jellyfin server, then refresh."
                                        )
                                    }
                                } else {
                                    if (latestMovies.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LatestMediaRow(
                                                title = "Recently Added Movies",
                                                items = latestMovies.take(20),
                                                vm = vm,
                                                onViewAll = { openFullList(LibraryViewMode.MOVIES) },
                                                onItemClick = handleClick,
                                                state = latestMoviesState
                                            )
                                        }
                                    }
                                    if (latestShows.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LatestMediaRow(
                                                title = "Recently Added TV",
                                                items = latestShows.take(20),
                                                vm = vm,
                                                onViewAll = { openFullList(LibraryViewMode.SHOWS) },
                                                onItemClick = handleClick,
                                                state = latestShowsState
                                            )
                                        }
                                    }
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

    if (showSortDialog) {
        SortDialog(
            selectedKey = sortKey,
            ascending = sortAscending,
            onSelectKey = {
                if (sortKey != it) {
                    LibraryNavigationSnapshot.firstVisibleItemIndex = 0
                    LibraryNavigationSnapshot.firstVisibleItemScrollOffset = 0
                    pendingSortScrollToTop = true
                    sortKey = it
                }
            },
            onAscendingChange = {
                if (sortAscending != it) {
                    LibraryNavigationSnapshot.firstVisibleItemIndex = 0
                    LibraryNavigationSnapshot.firstVisibleItemScrollOffset = 0
                    pendingSortScrollToTop = true
                    sortAscending = it
                }
            },
            onDismiss = { showSortDialog = false }
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
private fun SortDialog(
    selectedKey: MediaSortKey,
    ascending: Boolean,
    onSelectKey: (MediaSortKey) -> Unit,
    onAscendingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ascending,
                        onClick = { onAscendingChange(true) },
                        label = { Text("Ascending") },
                        shape = RoundedCornerShape(50)
                    )
                    FilterChip(
                        selected = !ascending,
                        onClick = { onAscendingChange(false) },
                        label = { Text("Descending") },
                        shape = RoundedCornerShape(50)
                    )
                }
                MediaSortKey.values().forEach { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectKey(key) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedKey == key,
                            onClick = { onSelectKey(key) }
                        )
                        Text(
                            key.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FeaturedCarousel(
    items: List<MediaItem>,
    vm: AppViewModel,
    label: String,
    refreshing: Boolean,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onItemClick: (MediaItem) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    var lastTouchAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LaunchedEffect(items.size) {
        while (items.size > 1) {
            kotlinx.coroutines.delay(1000)
            val idleForMs = System.currentTimeMillis() - lastTouchAt
            if (idleForMs >= 15_000 && !pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
                lastTouchAt = System.currentTimeMillis()
            }
        }
    }

    Box(
        modifier = Modifier
            .requiredWidth(screenWidth)
            .offset(x = (-16).dp)
            .pointerInput(items.size) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastTouchAt = System.currentTimeMillis()
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val item = items[page]
            FeaturedBanner(
                item = item,
                vm = vm,
                label = label,
                onClick = { onItemClick(item) }
            )
        }
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = statusTop + 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayIconButton(
                onClick = onSearchClick,
                enabled = true,
                contentDescription = "Search"
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            OverlayIconButton(
                onClick = onRefreshClick,
                enabled = !refreshing,
                contentDescription = "Refresh"
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }
            Spacer(Modifier.width(8.dp))
            OverlayIconButton(
                onClick = onSettingsClick,
                enabled = true,
                contentDescription = "Settings"
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        }
    }
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
    val remainingMinutes = remainingMinutes(item, progress)
    val title = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName
    } else {
        item.name
    }
    val subtitle = remember(item) { featuredSubtitle(item) }
    val meta = remember(item) { featuredMeta(item) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .heightIn(min = 420.dp)
            .aspectRatio(9f / 12.2f)
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
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.06f),
                        0.38f to Color.Black.copy(alpha = 0.16f),
                        0.70f to Color.Black.copy(alpha = 0.72f),
                        0.90f to cs.background.copy(alpha = 0.78f),
                        1f to cs.background
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Transparent
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.76f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.padding(top = 18.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                if (progress > 0f && progress < 0.99f) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.16f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        remainingMinutes != null -> "$remainingMinutes min left"
                        progress > 0f && progress < 0.99f -> "Resume"
                        else -> "Play"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 18.dp)
                )
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
                    .height(4.dp)
            )
        }
    }
}

@Composable
private fun OverlayIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        contentColor = Color.White,
        modifier = Modifier.size(44.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    onClickLabel = contentDescription,
                    onClick = { if (enabled) onClick() }
                ),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

private fun featuredMeta(item: MediaItem): String = buildList {
    add(if (item.type == "Series") "Series" else if (item.type == "Episode") "Episode" else "Movie")
    item.communityRating?.let { add("★ ${"%.1f".format(it)}") }
    item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
    item.productionYear?.let { add(it.toString()) }
}.joinToString(" • ")

private fun remainingMinutes(item: MediaItem, progress: Float): Int? {
    val runtime = item.runtimeMinutes ?: return null
    if (progress <= 0f || progress >= 0.99f) return null
    return (runtime * (1f - progress)).toInt().coerceAtLeast(1)
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

private fun homeHeroItems(
    source: HomeHeroSource,
    continueWatching: List<MediaItem>,
    nextUp: List<MediaItem>,
    libraryItems: List<MediaItem>
): List<MediaItem> {
    val featured = libraryItems.take(1)
    return when (source) {
        HomeHeroSource.RESUME -> continueWatching.ifEmpty { featured }
        HomeHeroSource.NEXT_UP -> nextUp.ifEmpty { featured }
        HomeHeroSource.FEATURED -> featured
    }
}

private fun homeHeroLabel(
    source: HomeHeroSource,
    hasResumeItems: Boolean,
    hasNextUpItems: Boolean
): String = when (source) {
    HomeHeroSource.RESUME -> if (hasResumeItems) "Continue watching" else "Featured"
    HomeHeroSource.NEXT_UP -> if (hasNextUpItems) "Next up" else "Featured"
    HomeHeroSource.FEATURED -> "Featured"
}

private fun MediaItem.latestSortValue(): String =
    if (type == "Series") {
        dateLastMediaAdded ?: dateCreated ?: premiereDate ?: productionYear?.toString().orEmpty()
    } else {
        dateCreated ?: dateLastMediaAdded ?: premiereDate ?: productionYear?.toString().orEmpty()
    }

private fun MediaItem.releaseSortValue(): String? =
    premiereDate ?: productionYear?.let { "%04d".format(it) }

private fun sortMediaItems(
    items: List<MediaItem>,
    key: MediaSortKey,
    ascending: Boolean
): List<MediaItem> = items.sortedWith { left, right ->
    when (key) {
        MediaSortKey.TITLE -> compareNullableStrings(left.name, right.name, ascending)
        MediaSortKey.IMDB_RATING -> compareNullableValues(
            left.communityRating,
            right.communityRating,
            ascending
        )
        MediaSortKey.PARENTAL_RATING -> compareNullableStrings(
            left.officialRating,
            right.officialRating,
            ascending
        )
        MediaSortKey.DATE_ADDED -> compareNullableStrings(
            left.dateCreated ?: left.dateLastMediaAdded,
            right.dateCreated ?: right.dateLastMediaAdded,
            ascending
        )
        MediaSortKey.DATE_PLAYED -> compareNullableStrings(
            left.userData?.lastPlayedDate,
            right.userData?.lastPlayedDate,
            ascending
        )
        MediaSortKey.RELEASE_DATE -> compareNullableStrings(
            left.releaseSortValue(),
            right.releaseSortValue(),
            ascending
        )
    }
}

private fun <T : Comparable<T>> compareNullableValues(
    left: T?,
    right: T?,
    ascending: Boolean
): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    val result = left.compareTo(right)
    return if (ascending) result else -result
}

private fun compareNullableStrings(left: String?, right: String?, ascending: Boolean): Int {
    val cleanLeft = left?.takeIf { it.isNotBlank() }
    val cleanRight = right?.takeIf { it.isNotBlank() }
    if (cleanLeft == null && cleanRight == null) return 0
    if (cleanLeft == null) return 1
    if (cleanRight == null) return -1
    val result = cleanLeft.compareTo(cleanRight, ignoreCase = true)
    return if (ascending) result else -result
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun LatestMediaRow(
    title: String,
    items: List<MediaItem>,
    vm: AppViewModel,
    onViewAll: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState()
) {
    Column(Modifier.padding(top = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onViewAll, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Show all $title")
            }
        }
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 2.dp)
        ) {
            items(items, key = { it.id }) { item ->
                LibraryCard(
                    item = item,
                    vm = vm,
                    onClick = { onItemClick(item) },
                    showTypeBadge = false,
                    modifier = Modifier.width(150.dp)
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
    onItemClick: (MediaItem) -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState()
) {
    LazyRow(
        state = state,
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
private fun LibraryCard(
    item: MediaItem,
    vm: AppViewModel,
    onClick: () -> Unit,
    showTypeBadge: Boolean = true,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier = modifier.clickable { onClick() }) {
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
            if (showTypeBadge) {
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
private fun ServerLibraryCard(
    library: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.clickable { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (library.collectionType == "tvshows") Icons.Default.Tv else Icons.Default.Movie,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            library.name.ifBlank { library.collectionType?.libraryTypeLabel() ?: "Library" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            library.collectionType?.libraryTypeLabel() ?: "Mixed",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun String.libraryTypeLabel(): String = when (this) {
    "movies" -> "Movies"
    "tvshows" -> "TV Shows"
    "boxsets" -> "Collections"
    "homevideos" -> "Home Videos"
    "mixed" -> "Mixed"
    else -> replaceFirstChar { it.uppercase() }
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

private enum class BottomDestination {
    HOME,
    LIBRARIES,
    DOWNLOADS
}

@Composable
private fun FjoraBottomNavigation(
    selected: BottomDestination,
    onHomeClick: () -> Unit,
    onLibrariesClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.background.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
        ) {
            NavigationBarItem(
                selected = selected == BottomDestination.HOME,
                onClick = onHomeClick,
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") },
                colors = fjoraBottomNavigationItemColors()
            )
            NavigationBarItem(
                selected = selected == BottomDestination.LIBRARIES,
                onClick = onLibrariesClick,
                icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                label = { Text("Libraries") },
                colors = fjoraBottomNavigationItemColors()
            )
            NavigationBarItem(
                selected = selected == BottomDestination.DOWNLOADS,
                onClick = onDownloadsClick,
                icon = {
                    Icon(
                        com.example.jellyfinplayer.ui.icons.DownloadIconVector,
                        contentDescription = null
                    )
                },
                label = { Text("Downloads") },
                colors = fjoraBottomNavigationItemColors()
            )
        }
    }
}

@Composable
private fun fjoraBottomNavigationItemColors(): NavigationBarItemColors {
    val cs = MaterialTheme.colorScheme
    return NavigationBarItemDefaults.colors(
        selectedIconColor = cs.onPrimary,
        selectedTextColor = cs.onBackground,
        indicatorColor = cs.primary,
        unselectedIconColor = cs.onSurfaceVariant,
        unselectedTextColor = cs.onSurfaceVariant
    )
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
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onDelete() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelMedium)
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
            title = { Text(if (status.isInFlight) "Cancel download?" else "Delete download?") },
            text = {
                Text(
                    if (status.isInFlight) {
                        "\"${record.title}\" will stop downloading and be removed from this device."
                    } else {
                        "\"${record.title}\" will be removed from your device."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text(if (status.isInFlight) "Cancel" else "Delete", color = cs.error) }
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
