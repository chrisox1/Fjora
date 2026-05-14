package com.example.jellyfinplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinplayer.api.AuthExpiredException
import com.example.jellyfinplayer.api.JellyfinRepository
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.Person
import com.example.jellyfinplayer.data.AppBackgroundColor
import com.example.jellyfinplayer.data.AppThemeColor
import com.example.jellyfinplayer.data.AuthStore
import com.example.jellyfinplayer.data.DownloadsStore
import com.example.jellyfinplayer.data.HomeHeroSource
import com.example.jellyfinplayer.data.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val SUBSCRIBED_TIMEOUT_MS = 5_000L

sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Error(val message: String) : UiState()
}

sealed class QuickConnectState {
    data object Idle : QuickConnectState()
    data object Starting : QuickConnectState()
    data class Waiting(val code: String) : QuickConnectState()
    data object Completing : QuickConnectState()
    data class Error(val message: String) : QuickConnectState()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = JellyfinRepository()
    private val store = AuthStore(app)
    private val settingsStore = SettingsStore(app)
    private val downloadsStore = DownloadsStore(app)

    /**
     * All download records the app has registered, newest first. Powers
     * the My Downloads tab and the in-app player when the user picks a
     * downloaded item.
     */
    val downloads: StateFlow<List<DownloadsStore.DownloadRecord>> =
        downloadsStore.recordsFlow
            .map { it.sortedByDescending { rec -> rec.createdAt } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIBED_TIMEOUT_MS),
                emptyList()
            )

    /** Register a new download; called by the UI right after enqueueing with DownloadManager. */
    fun registerDownload(record: DownloadsStore.DownloadRecord) {
        viewModelScope.launch { downloadsStore.add(record) }
    }

    /** Update a download's file path / size when it completes. */
    fun updateDownload(
        downloadId: Long,
        transform: (DownloadsStore.DownloadRecord) -> DownloadsStore.DownloadRecord
    ) {
        viewModelScope.launch { downloadsStore.update(downloadId, transform) }
    }

    /** Remove a download record. The file deletion happens at the call site. */
    fun removeDownload(downloadId: Long) {
        viewModelScope.launch { downloadsStore.remove(downloadId) }
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState
    private val _quickConnectState = MutableStateFlow<QuickConnectState>(QuickConnectState.Idle)
    val quickConnectState: StateFlow<QuickConnectState> = _quickConnectState
    private var quickConnectJob: Job? = null
    private val _homeLoadInFlight = MutableStateFlow(false)
    val homeLoadInFlight: StateFlow<Boolean> = _homeLoadInFlight

    private val _serverName = MutableStateFlow("")
    val serverName: StateFlow<String> = _serverName

    /** User-set defaults (bitrate cap, auto-resume on/off). */
    val settings: StateFlow<SettingsStore.Settings> = settingsStore.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBED_TIMEOUT_MS),
        initialValue = SettingsStore.Settings(
            defaultMaxBitrate = null,
            appThemeColor = AppThemeColor.FJORA,
            appBackgroundColor = AppBackgroundColor.FJORA,
            autoResume = true,
            showNextUpRow = true,
            homeHeroSource = HomeHeroSource.RESUME,
            forceTranscoding = false,
            directPlayOnly = false,
            useMpvForLocal = false,
            useMpvForAll = false,
            alwaysPlaySubtitles = false,
            preferredSubtitleLanguage = null,
            subtitleTextScale = 1.0f,
            subtitleColor = "white",
            subtitleBackground = false,
            subtitleDelayMs = 0L,
            downloadStorageLimitBytes = null,
            includeEpisodesInSearch = false,
            imageCacheLimitBytes = 50L * 1024 * 1024,
            subtitlePositionFraction = 0.08f
        )
    )

    fun setDefaultBitrate(bitrate: Long?) {
        viewModelScope.launch { settingsStore.setDefaultBitrate(bitrate) }
    }

    fun setAppThemeColor(color: AppThemeColor) {
        viewModelScope.launch { settingsStore.setAppThemeColor(color) }
    }

    fun setAppBackgroundColor(color: AppBackgroundColor) {
        viewModelScope.launch { settingsStore.setAppBackgroundColor(color) }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoResume(enabled) }
    }

    fun setShowNextUpRow(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowNextUpRow(enabled) }
    }

    fun setHomeHeroSource(source: HomeHeroSource) {
        viewModelScope.launch { settingsStore.setHomeHeroSource(source) }
    }

    fun setForceTranscoding(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setForceTranscoding(enabled) }
    }

    fun setUseMpvForLocal(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseMpvForLocal(enabled) }
    }

    fun setUseMpvForAll(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseMpvForAll(enabled) }
    }

    fun setDirectPlayOnly(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDirectPlayOnly(enabled) }
    }

    fun setAlwaysPlaySubtitles(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAlwaysPlaySubtitles(enabled) }
    }

    fun setPreferredSubtitleLanguage(language: String?) {
        viewModelScope.launch { settingsStore.setPreferredSubtitleLanguage(language) }
    }

    fun setSubtitleTextScale(scale: Float) {
        viewModelScope.launch { settingsStore.setSubtitleTextScale(scale) }
    }

    fun setSubtitleColor(color: String) {
        viewModelScope.launch { settingsStore.setSubtitleColor(color) }
    }

    fun setSubtitleBackground(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSubtitleBackground(enabled) }
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        viewModelScope.launch { settingsStore.setSubtitleDelayMs(delayMs) }
    }

    fun setDownloadStorageLimitBytes(bytes: Long?) {
        viewModelScope.launch { settingsStore.setDownloadStorageLimitBytes(bytes) }
    }

    fun setIncludeEpisodesInSearch(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setIncludeEpisodesInSearch(enabled)
            val q = _searchQuery.value
            if (q.isNotBlank() && repo.isAuthenticated()) {
                _searching.value = true
                runCatching { repo.search(q, includeEpisodes = enabled) }
                    .onSuccess { _searchResults.value = it }
                    .onFailure { t ->
                        if (t is AuthExpiredException) handleAuthExpired()
                        _searchResults.value = emptyList()
                    }
                _searching.value = false
            }
        }
    }

    fun setImageCacheLimitBytes(bytes: Long?) {
        viewModelScope.launch { settingsStore.setImageCacheLimitBytes(bytes) }
    }

    fun setSubtitlePositionFraction(fraction: Float) {
        viewModelScope.launch { settingsStore.setSubtitlePositionFraction(fraction) }
    }

    /** Read-only server URL for display in settings. */
    fun serverUrl(): String = repo.getServerUrl()

    private fun serverDisplayName(url: String): String =
        url.removePrefix("https://").removePrefix("http://").trimEnd('/')

    private val _library = MutableStateFlow<List<MediaItem>>(emptyList())
    val library: StateFlow<List<MediaItem>> = _library

    /**
     * The user's library views (Movies, TV Shows, etc.). Populated on first
     * loadHome() and kept in sync with the active account.
     */
    private val _libraries = MutableStateFlow<List<MediaItem>>(emptyList())
    val libraries: StateFlow<List<MediaItem>> = _libraries

    /**
     * Currently selected library, or null for "All" (everything across all
     * libraries — the default). When non-null, the Library screen filters
     * its grid to items inside this library.
     */
    private val _selectedLibraryId = MutableStateFlow<String?>(null)
    val selectedLibraryId: StateFlow<String?> = _selectedLibraryId

    private val _continueWatching = MutableStateFlow<List<MediaItem>>(emptyList())
    val continueWatching: StateFlow<List<MediaItem>> = _continueWatching

    private val _nextUp = MutableStateFlow<List<MediaItem>>(emptyList())
    val nextUp: StateFlow<List<MediaItem>> = _nextUp

    /**
     * Merged "Continue Watching" row combining in-progress items and next-up
     * episodes. Netflix-style: one row, deduped by series so we don't show
     * "S2E5 in progress" + "S2E6 next up" for the same show.
     *
     * Order: continue-watching items first (they're more specific intent),
     * then next-up entries whose series isn't already represented.
     */
    val homeRow: StateFlow<List<MediaItem>> = combine(
        _continueWatching, _nextUp
    ) { cw, nu ->
        val seenSeries = mutableSetOf<String>()
        val out = mutableListOf<MediaItem>()
        for (it in cw) {
            out += it
            // Track series ID if this is an episode so we don't duplicate
            // it with a Next Up entry for the same show.
            it.seriesId?.let(seenSeries::add)
        }
        for (it in nu) {
            val sid = it.seriesId
            if (sid == null || sid !in seenSeries) {
                out += it
                sid?.let(seenSeries::add)
            }
        }
        out
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults

    /**
     * Whether a search request is currently in flight. The library UI uses
     * this to suppress the "No matches" message during the debounce + fetch
     * window — without it, users see "No matches" pop up briefly before
     * results land, which feels broken.
     */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    // Null = auth not yet checked (we're still reading DataStore on init).
    // The UI shows nothing while null so the LoginScreen doesn't flash for
    // already-signed-in users on cold app start.
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName

    /** Live list of saved accounts for Settings UI. */
    val accounts: StateFlow<List<AuthStore.AccountRecord>> = store.accountsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBED_TIMEOUT_MS),
        initialValue = emptyList()
    )

    /** ID of the active account (matches one entry in `accounts`). */
    val activeAccountId: StateFlow<String?> = store.activeAccountIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBED_TIMEOUT_MS),
        initialValue = null
    )

    init {
        viewModelScope.launch {
            // Read auth FIRST so a fresh install (which calls
            // getOrCreateDeviceId() and writes to DataStore) doesn't race with
            // the auth read.
            store.migrateTokensToEncryptedStorage()
            val auth = store.authFlow.first()
            if (auth.isLoggedIn) {
                val deviceId = store.getOrCreateDeviceId()
                repo.configure(auth.server, deviceId, auth.token, auth.userId)
                _userName.value = auth.userName
                _serverName.value = serverDisplayName(auth.server)
                _isLoggedIn.value = true
                loadHome()
            } else {
                // Resolve the unknown→logged-out transition so the UI can show
                // the LoginScreen. Without this, isLoggedIn would stay null.
                _isLoggedIn.value = false
            }
        }
        viewModelScope.launch { observeSearchQuery() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeSearchQuery() {
        _searchQuery
            .debounce(350)
            .onEach { q ->
                if (q.isBlank()) {
                    _searchResults.value = emptyList()
                    _searching.value = false
                    return@onEach
                }
                if (!repo.isAuthenticated()) {
                    _searching.value = false
                    return@onEach
                }
                _searching.value = true
                val includeEpisodes = settings.value.includeEpisodesInSearch
                runCatching { repo.search(q, includeEpisodes = includeEpisodes) }
                    .onSuccess { _searchResults.value = it }
                    .onFailure { t ->
                        if (t is AuthExpiredException) handleAuthExpired()
                        _searchResults.value = emptyList()
                    }
                _searching.value = false
            }
            .collect {}
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        // Flip the searching flag the instant the user types something —
        // before the 350ms debounce fires. Otherwise there's a window where
        // the new query is set but no request has run yet, and the UI would
        // show the previous (stale) result set or a misleading "No matches"
        // for the new query.
        if (q.isNotBlank()) _searching.value = true
    }
    fun clearSearch() {
        _searchQuery.value = ""
        _searching.value = false
    }

    fun login(server: String, username: String, password: String) {
        if (server.isBlank() || username.isBlank()) return
        cancelQuickConnect(resetState = true)
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val deviceId = store.getOrCreateDeviceId()
                repo.configure(server, deviceId)
                val resp = repo.login(username, password)
                // Add as a new account (or refresh if same server+user). This
                // automatically becomes the active account.
                store.addAccount(
                    server = repo.getServerUrl(),
                    token = resp.accessToken,
                    userId = resp.user.id,
                    userName = resp.user.name
                )
                _userName.value = resp.user.name
                _serverName.value = serverDisplayName(repo.getServerUrl())
                // Reset state so the home rows from the previous account
                // don't briefly show through during the switch.
                _library.value = emptyList()
                _continueWatching.value = emptyList()
                _nextUp.value = emptyList()
                _searchResults.value = emptyList()
                _isLoggedIn.value = true
                _uiState.value = UiState.Idle
                loadHome()
            } catch (t: Throwable) {
                _uiState.value = UiState.Error(t.message ?: "Login failed")
            }
        }
    }

    fun startQuickConnect(server: String) {
        if (server.isBlank()) return
        cancelQuickConnect(resetState = false)
        quickConnectJob = viewModelScope.launch {
            try {
                _quickConnectState.value = QuickConnectState.Starting
                val deviceId = store.getOrCreateDeviceId()
                repo.configure(server, deviceId)
                val started = repo.initiateQuickConnect()
                if (started.code.isBlank() || started.secret.isBlank()) {
                    throw IllegalStateException("Server did not return a Quick Connect code.")
                }
                _quickConnectState.value = QuickConnectState.Waiting(started.code)

                repeat(36) {
                    delay(5_000)
                    val state = repo.getQuickConnectState(started.secret)
                    if (state.authenticated) {
                        _quickConnectState.value = QuickConnectState.Completing
                        _uiState.value = UiState.Loading
                        val resp = repo.authenticateWithQuickConnect(started.secret)
                        store.addAccount(
                            server = repo.getServerUrl(),
                            token = resp.accessToken,
                            userId = resp.user.id,
                            userName = resp.user.name
                        )
                        _userName.value = resp.user.name
                        _serverName.value = serverDisplayName(repo.getServerUrl())
                        _library.value = emptyList()
                        _continueWatching.value = emptyList()
                        _nextUp.value = emptyList()
                        _searchResults.value = emptyList()
                        _isLoggedIn.value = true
                        _quickConnectState.value = QuickConnectState.Idle
                        _uiState.value = UiState.Idle
                        loadHome()
                        return@launch
                    }
                }
                _quickConnectState.value = QuickConnectState.Error(
                    "Quick Connect code expired. Start a new code and try again."
                )
            } catch (_: CancellationException) {
                // User started a new Quick Connect attempt or closed the panel.
            } catch (t: Throwable) {
                _uiState.value = UiState.Idle
                _quickConnectState.value = QuickConnectState.Error(
                    t.message ?: "Quick Connect failed"
                )
            }
        }
    }

    fun cancelQuickConnect(resetState: Boolean = true) {
        quickConnectJob?.cancel()
        quickConnectJob = null
        if (resetState) _quickConnectState.value = QuickConnectState.Idle
    }

    /** Switch to a previously-saved account by id. */
    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            store.switchTo(accountId)
            val auth = store.authFlow.first()
            val deviceId = store.getOrCreateDeviceId()
            if (auth.isLoggedIn) {
                repo.configure(auth.server, deviceId, auth.token, auth.userId)
                _userName.value = auth.userName
                _serverName.value = serverDisplayName(auth.server)
                _library.value = emptyList()
                _continueWatching.value = emptyList()
                _nextUp.value = emptyList()
                _searchResults.value = emptyList()
                _searchQuery.value = ""
                _isLoggedIn.value = true
                loadHome()
            }
        }
    }

    /** Remove a saved account. Falls back to another account if the active one was removed. */
    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            store.remove(accountId)
            val auth = store.authFlow.first()
            val deviceId = store.getOrCreateDeviceId()
            if (auth.isLoggedIn) {
                // Another account remained — make it the active one.
                repo.configure(auth.server, deviceId, auth.token, auth.userId)
                _userName.value = auth.userName
                _serverName.value = serverDisplayName(auth.server)
                _library.value = emptyList()
                _continueWatching.value = emptyList()
                _nextUp.value = emptyList()
                _searchResults.value = emptyList()
                _isLoggedIn.value = true
                loadHome()
            } else {
                // No accounts left — go back to the login screen.
                repo.reset()
                _library.value = emptyList()
                _continueWatching.value = emptyList()
                _nextUp.value = emptyList()
                _searchResults.value = emptyList()
                _searchQuery.value = ""
                _userName.value = ""
                _serverName.value = ""
                _isLoggedIn.value = false
            }
        }
    }

    /** Sign out the currently active account (without wiping the others). */
    fun signOutActive() {
        val active = activeAccountId.value ?: return
        removeAccount(active)
    }

    /** Sign out and wipe ALL saved accounts (the destructive option). */
    fun logout() {
        viewModelScope.launch {
            store.clear()
            repo.reset()
            _library.value = emptyList()
            _continueWatching.value = emptyList()
            _nextUp.value = emptyList()
            _searchResults.value = emptyList()
            _searchQuery.value = ""
            _userName.value = ""
            _serverName.value = ""
            _uiState.value = UiState.Idle
            _isLoggedIn.value = false
        }
    }

    /** Called when an API call returns 401 — token's no good, force re-login. */
    private fun handleAuthExpired() {
        // Don't recursively call logout()'s coroutine; this might be called
        // from inside one. Just do the state flips inline.
        viewModelScope.launch {
            store.clear()
            repo.reset()
            _library.value = emptyList()
            _continueWatching.value = emptyList()
            _nextUp.value = emptyList()
            _searchResults.value = emptyList()
            _userName.value = ""
            _serverName.value = ""
            _uiState.value = UiState.Error("Your session expired. Please sign in again.")
            _isLoggedIn.value = false
        }
    }

    /**
     * Refresh everything the home screen needs: library + continue-watching +
     * next-up. Three parallel calls. Loading state is only shown when there's
     * nothing currently visible — refresh-while-populated is silent.
     */
    fun loadHome(force: Boolean = false) {
        if (!repo.isAuthenticated()) return
        if (_homeLoadInFlight.value && !force) return
        _homeLoadInFlight.value = true
        if (_library.value.isEmpty()) _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val libraryJob = launch {
                    val libraryId = _selectedLibraryId.value
                    // Downloads is a synthetic tab — no server fetch. The UI
                    // reads `vm.downloads` directly when this tab is active.
                    if (libraryId == DOWNLOADS_TAB_SENTINEL) {
                        _library.value = emptyList()
                        _uiState.value = UiState.Idle
                        return@launch
                    }
                    val libraryType = _libraries.value.firstOrNull { it.id == libraryId }
                        ?.collectionType
                    val result = if (libraryId != null) {
                        runCatching { repo.loadLibraryItems(libraryId, libraryType) }
                    } else {
                        runCatching { repo.loadLibrary() }
                    }
                    result
                        .onSuccess {
                            _library.value = it
                            _uiState.value = UiState.Idle
                        }
                        .onFailure { t ->
                            if (t is AuthExpiredException) {
                                handleAuthExpired()
                            } else if (_library.value.isEmpty()) {
                                _uiState.value = UiState.Error(t.message ?: "Failed to load library")
                            }
                        }
                }
                val viewsJob = launch {
                    // Fetch libraries list. Don't block library-grid loading on
                    // it — if it fails, the user just sees no library tabs.
                    runCatching { repo.loadViews() }
                        .onSuccess { _libraries.value = it }
                        .onFailure { if (it is AuthExpiredException) handleAuthExpired() }
                }
                val serverNameJob = launch {
                    runCatching { repo.loadServerName() }
                        .onSuccess { _serverName.value = it }
                        .onFailure {
                            if (it is AuthExpiredException) handleAuthExpired()
                            else if (_serverName.value.isBlank()) {
                                _serverName.value = serverDisplayName(repo.getServerUrl())
                            }
                        }
                }
                val resumeJob = launch {
                    runCatching { repo.loadResumeItems() }
                        .onSuccess { _continueWatching.value = it }
                        .onFailure { if (it is AuthExpiredException) handleAuthExpired() }
                }
                val nextJob = launch {
                    runCatching { repo.loadNextUp() }
                        .onSuccess { _nextUp.value = it }
                        .onFailure { if (it is AuthExpiredException) handleAuthExpired() }
                }
                libraryJob.join()
                viewsJob.join()
                serverNameJob.join()
                resumeJob.join()
                nextJob.join()
            } finally {
                _homeLoadInFlight.value = false
            }
        }
    }

    companion object {
        /**
         * Sentinel ID for the Downloads tab. Used by selectLibrary() to
         * trigger the synthetic local-data view in the Library screen
         * instead of hitting the server. Must match DOWNLOADS_TAB_ID in
         * LibraryScreen.kt.
         */
        const val DOWNLOADS_TAB_SENTINEL = "__downloads__"
    }

    /**
     * Switch the active library filter. null = "All" (all movies + series
     * across every library). The library grid reloads against the new
     * scope on the next loadHome() call.
     */
    fun selectLibrary(libraryId: String?) {
        if (_selectedLibraryId.value == libraryId) return
        _selectedLibraryId.value = libraryId
        // Force a reload of the grid since the scope changed. Clear first
        // so the user sees a loading state instead of stale items from the
        // previous library.
        _library.value = emptyList()
        loadHome(force = true)
    }

    /** Refresh just the home rows after a playback session ends. */
    fun refreshHomeRows() {
        if (!repo.isAuthenticated()) return
        viewModelScope.launch {
            runCatching { repo.loadResumeItems() }.onSuccess { _continueWatching.value = it }
            runCatching { repo.loadNextUp() }.onSuccess { _nextUp.value = it }
        }
    }

    suspend fun loadItemDetails(itemId: String): MediaItem = repo.loadItemDetails(itemId)
    suspend fun loadEpisodes(seriesId: String): List<MediaItem> = repo.loadEpisodes(seriesId)
    suspend fun loadItemsByPerson(person: Person): List<MediaItem> =
        repo.loadItemsByPerson(person)

    /** Negotiate a playable stream URL via PlaybackInfo. */
    suspend fun resolveStream(
        item: MediaItem,
        maxBitrate: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        forceTranscode: Boolean = false,
        directPlayOnly: Boolean = false,
        useMpvProfile: Boolean = false
    ) = repo.resolveStream(
        item,
        maxBitrate,
        audioStreamIndex,
        subtitleStreamIndex,
        forceTranscode,
        directPlayOnly,
        useMpvProfile
    )

    fun posterUrl(item: MediaItem, maxHeight: Int = 480) = repo.posterUrl(item, maxHeight)
    fun backdropUrl(item: MediaItem, maxWidth: Int = 1280) = repo.backdropUrl(item, maxWidth)
    fun personImageUrl(person: Person, maxHeight: Int = 240) =
        repo.personImageUrl(person, maxHeight)
    suspend fun loadIntroSkipperSegments(itemId: String) =
        repo.loadIntroSkipperSegments(itemId)
    fun subtitleUrl(
        itemId: String,
        mediaSourceId: String,
        streamIndex: Int,
        format: String = "vtt"
    ) = repo.subtitleUrl(itemId, mediaSourceId, streamIndex, format)

    fun seriesPosterUrl(item: MediaItem, maxHeight: Int = 480) =
        repo.seriesPosterUrl(item, maxHeight)

    fun downloadUrl(item: MediaItem, maxBitrate: Long?) =
        repo.downloadUrl(item, maxBitrate)

    // ---- Playback reporting ------------------------------------------------

    fun reportStart(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionMs: Long,
        playMethod: String
    ) {
        viewModelScope.launch {
            repo.reportPlaybackStart(itemId, mediaSourceId, playSessionId, positionMs, playMethod)
        }
    }

    fun reportProgress(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionMs: Long,
        isPaused: Boolean,
        playMethod: String
    ) {
        viewModelScope.launch {
            repo.reportPlaybackProgress(itemId, mediaSourceId, playSessionId, positionMs, isPaused, playMethod)
        }
    }

    fun reportStop(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionMs: Long
    ) {
        viewModelScope.launch {
            repo.reportPlaybackStopped(itemId, mediaSourceId, playSessionId, positionMs)
        }
    }
}
