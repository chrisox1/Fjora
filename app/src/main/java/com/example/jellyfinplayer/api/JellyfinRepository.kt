package com.example.jellyfinplayer.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds Retrofit clients lazily for whatever Jellyfin server the user logs into.
 * Holds onto auth state in memory; persistence is handled by AuthStore (DataStore).
 *
 * Header format reference: https://gist.github.com/nielsvanvelzen/ea047d9028f676185832e51ffaf12a6f
 *
 * Key requirements that the previous code violated and which caused HTTP 500s:
 *   1. The Authorization header MUST include a Token field (empty before login),
 *      otherwise the server's parser leaves auth metadata null and the controller
 *      throws ArgumentNullException -> HTTP 500.
 *   2. Field values may not contain stray quotes or commas because the parser is
 *      a naive split. We sanitize the device name to be alphanumeric-and-dash.
 *   3. The DeviceId must be stable across runs; one access token per DeviceId.
 */

/**
 * Thrown by repository calls when the server reports our token is no longer
 * valid. The VM watches for this and triggers an auto-logout so the user lands
 * on the login screen instead of staring at "Failed to load library" forever.
 */
class AuthExpiredException : RuntimeException("Session expired")

/**
 * The end product of a PlaybackInfo negotiation. The url field is what we
 * hand directly to ExoPlayer — already includes auth, already in a format we
 * can decode. The other fields are needed for progress reporting (so the
 * server's "Continue Watching" knows which session it belongs to) and for
 * subtitle attachment.
 */
data class ResolvedStream(
    val url: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val playMethod: String, // "DirectPlay" | "DirectStream" | "Transcode"
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    /**
     * Fully-resolved URL to fetch the selected subtitle from, or null if no
     * subtitle was selected or the subtitle is being delivered embedded (in
     * which case ExoPlayer extracts it from the container/HLS itself).
     */
    val subtitleUrl: String? = null,
    /** "vtt", "srt", "ass", etc. — used to set the right MIME type. */
    val subtitleFormat: String? = null
) {
    val isTranscoding: Boolean get() = playMethod == "Transcode"
}

class JellyfinRepository {

    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""
    private var deviceId: String = ""
    private var api: JellyfinApi? = null

    private val clientName = "Fjora"
    private val clientVersion = "0.1.1"
    private val deviceName: String = sanitize(android.os.Build.MODEL ?: "Android")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Configure the repository for a given server. Pass the persisted deviceId so
     * subsequent app launches reuse the same identity (Jellyfin allows only one
     * active access token per DeviceId).
     */
    fun configure(
        serverUrl: String,
        deviceId: String,
        token: String = "",
        userId: String = ""
    ) {
        this.serverUrl = normalizeServerUrl(serverUrl)
        this.deviceId = deviceId
        this.accessToken = token
        this.userId = userId
        this.api = buildApi(this.serverUrl)
    }

    /**
     * Wipe all auth state in memory. Call after AuthStore.clear() so any
     * in-flight composables hitting `getServerUrl()` etc. don't try to use a
     * stale token.
     */
    fun reset() {
        serverUrl = ""
        accessToken = ""
        userId = ""
        api = null
    }

    fun isAuthenticated(): Boolean = accessToken.isNotBlank() && userId.isNotBlank()
    fun getServerUrl(): String = serverUrl

    suspend fun loadServerName(): String = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getSystemInfo(authHeader()).serverName?.takeIf { it.isNotBlank() }
            ?: serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }

    private fun buildApi(baseUrl: String): JellyfinApi {
        val client = OkHttpClient.Builder()
            .apply {
                // Logging is BASIC in debug builds (URLs only), NONE in release
                // so we never leak api_key= tokens into logcat on production
                // devices. BuildConfig.DEBUG is true for debug variants.
                if (com.example.jellyfinplayer.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(JellyfinApi::class.java)
    }

    /**
     * MediaBrowser auth header. Token is included (possibly empty) on every call,
     * including pre-auth, because Jellyfin 10.11+ rejects values that omit it.
     */
    private fun authHeader(): String =
        """MediaBrowser Client="$clientName", Device="$deviceName", DeviceId="$deviceId", Version="$clientVersion", Token="$accessToken""""

    suspend fun login(username: String, password: String): AuthResponse {
        val a = api ?: error("Call configure() first")
        try {
            val response = a.authenticate(authHeader(), AuthRequest(username, password))
            accessToken = response.accessToken
            userId = response.user.id
            return response
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                401 -> "Wrong username or password."
                500 -> "Server error. If you just upgraded Jellyfin, your client may need an update."
                else -> "Login failed (HTTP ${e.code()})."
            }
            throw IllegalStateException(msg, e)
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException("Couldn't reach that server. Check the URL and that you're on the right network.", e)
        } catch (e: java.net.ConnectException) {
            throw IllegalStateException("Couldn't connect to the server. Is it running on that port?", e)
        } catch (e: javax.net.ssl.SSLException) {
            throw IllegalStateException("HTTPS connection failed. The server's certificate may be invalid.", e)
        } catch (e: java.net.SocketTimeoutException) {
            throw IllegalStateException("Server didn't respond in time. It may be slow or unreachable.", e)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Network error: ${e.message ?: "couldn't reach the server"}", e)
        } catch (e: IllegalArgumentException) {
            // Retrofit throws this for malformed base URLs.
            throw IllegalStateException("Server URL doesn't look right. Try \"http://your-server:8096\".", e)
        }
    }

    suspend fun initiateQuickConnect(): QuickConnectResult {
        val a = api ?: error("Call configure() first")
        try {
            val enabled = a.getQuickConnectEnabled()
            if (!enabled) {
                throw IllegalStateException("Quick Connect is disabled on this Jellyfin server.")
            }
            return a.initiateQuickConnect(authHeader())
        } catch (e: HttpException) {
            if (e.code() == 401) {
                throw IllegalStateException("Quick Connect is disabled on this Jellyfin server.", e)
            }
            throw IllegalStateException("Quick Connect failed (HTTP ${e.code()}).", e)
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException("Couldn't reach that server. Check the URL and that you're on the right network.", e)
        } catch (e: java.net.ConnectException) {
            throw IllegalStateException("Couldn't connect to the server. Is it running on that port?", e)
        } catch (e: javax.net.ssl.SSLException) {
            throw IllegalStateException("HTTPS connection failed. The server's certificate may be invalid.", e)
        } catch (e: java.net.SocketTimeoutException) {
            throw IllegalStateException("Server didn't respond in time. It may be slow or unreachable.", e)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Network error: ${e.message ?: "couldn't reach the server"}", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Server URL doesn't look right. Try \"http://your-server:8096\".", e)
        }
    }

    suspend fun getQuickConnectState(secret: String): QuickConnectResult {
        val a = api ?: error("Call configure() first")
        return a.getQuickConnectState(secret)
    }

    suspend fun authenticateWithQuickConnect(secret: String): AuthResponse {
        val a = api ?: error("Call configure() first")
        val response = a.authenticateWithQuickConnect(
            authHeader(),
            QuickConnectAuthRequest(secret)
        )
        accessToken = response.accessToken
        userId = response.user.id
        return response
    }

    suspend fun loadLibrary(): List<MediaItem> = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getMoviesAndSeries(userId, authHeader()).items
    }

    suspend fun loadFavorites(): List<MediaItem> = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getFavoriteItems(userId, authHeader()).items
    }

    /**
     * Load the user's library views (Movies, TV Shows, etc.). Filtered to
     * the kinds we know how to display — movies / tvshows / boxsets /
     * homevideos. Music and books are excluded since this client doesn't
     * render those.
     */
    suspend fun loadViews(): List<MediaItem> = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getViews(userId, authHeader()).items
    }

    /**
     * Items inside a specific library. The type filter adapts to the
     * collection: movies-only libraries skip the Series filter and so on.
     */
    suspend fun loadLibraryItems(
        parentId: String,
        collectionType: String?,
        limit: Int = 500
    ): List<MediaItem> =
        mapAuthErrors {
            val a = api ?: error("Call configure() first")
            val types = when (collectionType) {
                "movies" -> "Movie"
                "tvshows" -> "Series"
                "boxsets" -> "BoxSet,Movie,Series"
                else -> "Movie,Series"
            }
            a.getItemsInLibrary(
                userId = userId,
                authHeader = authHeader(),
                parentId = parentId,
                includeItemTypes = types,
                limit = limit
            ).items
        }

    suspend fun loadItemsByPerson(person: Person): List<MediaItem> = mapAuthErrors {
        if (person.id.isBlank()) return@mapAuthErrors emptyList()
        val a = api ?: error("Call configure() first")
        a.getItemsByPerson(userId, authHeader(), person.id).items
    }

    suspend fun loadEpisodes(seriesId: String): List<MediaItem> = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getEpisodes(seriesId, authHeader(), userId).items
    }

    suspend fun setFavorite(itemId: String, favorite: Boolean) = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        if (favorite) {
            a.markFavorite(userId, itemId, authHeader())
        } else {
            a.unmarkFavorite(userId, itemId, authHeader())
        }
    }

    suspend fun setPlayed(itemId: String, played: Boolean) = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        if (played) {
            a.markPlayed(userId, itemId, authHeader())
        } else {
            a.unmarkPlayed(userId, itemId, authHeader())
        }
    }

    suspend fun loadItemDetails(itemId: String): MediaItem = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getItemDetails(userId, itemId, authHeader())
    }

    /**
     * Search the user's library. Empty / blank query returns nothing.
     *
     * When `includeEpisodes` is true, individual `Episode` entries are also
     * returned alongside Movies/Series — useful for jumping to a remembered
     * episode by name. The server-side `IncludeItemTypes` filter and the
     * client-side type filter are kept in sync.
     */
    suspend fun search(
        query: String,
        includeEpisodes: Boolean = false
    ): List<MediaItem> = mapAuthErrors {
        if (query.isBlank()) return@mapAuthErrors emptyList()
        val a = api ?: error("Call configure() first")
        val includeTypes = if (includeEpisodes) "Movie,Series,Episode" else "Movie,Series"
        a.search(userId, authHeader(), query.trim(), includeTypes).items.filter {
            it.type == "Movie" || it.type == "Series" ||
                (includeEpisodes && it.type == "Episode")
        }
    }

    /** Items in progress — feeds the "Continue Watching" row. */
    suspend fun loadResumeItems(): List<MediaItem> = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getResumeItems(authHeader(), userId).items
    }

    /** Next unwatched episodes — feeds the "Next Up" row. */
    suspend fun loadNextUp(): List<MediaItem> = mapAuthErrors {
        val a = api ?: error("Call configure() first")
        a.getNextUp(authHeader(), userId).items
    }

    /** Wrap a data-load call to convert 401s into AuthExpiredException. */
    private suspend inline fun <T> mapAuthErrors(block: () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            if (e.code() == 401) throw AuthExpiredException()
            throw e
        }
    }

    // ---- Playback reporting ------------------------------------------------
    // Failures here are silently swallowed: a hiccup reporting progress should
    // never interrupt playback. Without these calls the server's "Continue
    // Watching" row never updates.

    suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionMs: Long,
        playMethod: String
    ) {
        val a = api ?: return
        runCatching {
            a.reportPlaybackStart(
                authHeader(),
                PlaybackStartInfo(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = positionMs * 10_000L,
                    playMethod = playMethod
                )
            )
        }
    }

    suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionMs: Long,
        isPaused: Boolean,
        playMethod: String
    ) {
        val a = api ?: return
        runCatching {
            a.reportPlaybackProgress(
                authHeader(),
                PlaybackProgressInfo(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = positionMs * 10_000L,
                    isPaused = isPaused,
                    playMethod = playMethod
                )
            )
        }
    }

    suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionMs: Long
    ) {
        val a = api ?: return
        runCatching {
            a.reportPlaybackStopped(
                authHeader(),
                PlaybackStopInfo(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = positionMs * 10_000L
                )
            )
        }
    }

    /** Build a poster URL for an item (Primary image). Coil will fetch it. */
    fun posterUrl(item: MediaItem, maxHeight: Int = 480): String? {
        val tag = item.imageTags["Primary"] ?: return null
        return "$serverUrl/Items/${item.id}/Images/Primary?maxHeight=$maxHeight&tag=$tag&quality=90"
    }

    /** Backdrop URL for hero / detail screens. Falls back to the primary image. */
    fun backdropUrl(item: MediaItem, maxWidth: Int = 1280): String? {
        val backdropTag = item.backdropImageTags.firstOrNull()
        return if (backdropTag != null) {
            "$serverUrl/Items/${item.id}/Images/Backdrop/0?maxWidth=$maxWidth&tag=$backdropTag&quality=90"
        } else {
            posterUrl(item, maxHeight = maxWidth)
        }
    }

    /** Backdrop from a parent series/season, useful for episode hero art. */
    fun parentBackdropUrl(item: MediaItem, maxWidth: Int = 1280): String? {
        val parentId = item.parentBackdropItemId ?: item.seriesId ?: return null
        val tag = item.parentBackdropImageTags.firstOrNull() ?: return null
        return "$serverUrl/Items/$parentId/Images/Backdrop/0?maxWidth=$maxWidth&tag=$tag&quality=90"
    }

    /**
     * Build a headshot URL for a cast / crew person. Returns null when the
     * server has no image on file (Coil falls back to its error/placeholder
     * slot — we render a generic avatar in that case).
     *
     * Jellyfin treats people as items internally, so the same /Items/{id}/
     * Images endpoint serves their portraits.
     */
    fun personImageUrl(person: Person, maxHeight: Int = 240): String? {
        val tag = person.primaryImageTag ?: return null
        return "$serverUrl/Items/${person.id}/Images/Primary?maxHeight=$maxHeight&tag=$tag&quality=90"
    }

    suspend fun loadIntroSkipperSegments(itemId: String): List<MediaSegment> {
        val a = api ?: error("Call configure() first")
        val mediaSegmentsPayload = try {
            a.getMediaSegments(itemId, authHeader())
        } catch (_: Throwable) {
            null
        }
        val nativeSegments = mediaSegmentsPayload?.let(::parseNativeMediaSegments).orEmpty()
        if (nativeSegments.isNotEmpty()) return nativeSegments

        val segmentsPayload = try {
            a.getIntroSkipperSegments(itemId, authHeader())
        } catch (_: HttpException) {
            null
        } catch (_: Throwable) {
            null
        }
        val segments = segmentsPayload?.let(::parseIntroSkipperSegments).orEmpty()
        if (segments.isNotEmpty()) return segments

        val timestampsPayload = try {
            a.getIntroSkipperTimestamps(itemId, authHeader())
        } catch (_: Throwable) {
            null
        }
        val timestamps = timestampsPayload?.let(::parseIntroSkipperSegments).orEmpty()
        if (timestamps.isNotEmpty()) return timestamps

        val legacyIntro = try {
            a.getLegacyIntroTimestamps(itemId, authHeader())
        } catch (_: Throwable) {
            null
        }
        return legacyIntro?.let(::parseLegacyIntroTimestamp).orEmpty()
    }

    private fun parseNativeMediaSegments(payload: JsonObject): List<MediaSegment> {
        val items = runCatching { payload["Items"]?.jsonArray }.getOrNull() ?: return emptyList()
        return items.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val type = obj["Type"]?.jsonPrimitive?.contentOrNull
                ?: obj["SegmentType"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val start = obj.segmentTimeMs("StartTicks")
                ?: obj.segmentTimeMs("Start")
                ?: obj.segmentTimeMs("StartTimeTicks")
                ?: return@mapNotNull null
            val end = obj.segmentTimeMs("EndTicks")
                ?: obj.segmentTimeMs("End")
                ?: obj.segmentTimeMs("EndTimeTicks")
                ?: return@mapNotNull null
            MediaSegment(type, start.coerceAtLeast(0L), end.coerceAtLeast(start))
        }
    }

    private fun parseIntroSkipperSegments(payload: JsonObject): List<MediaSegment> {
        return payload.mapNotNull { (type, element) ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val start = obj.segmentTimeMs("Start")
                ?: obj.segmentTimeMs("IntroStart")
                ?: obj.segmentTimeMs("CreditsStart")
                ?: obj.segmentTimeMs("start")
                ?: obj.segmentTimeMs("startTime")
                ?: return@mapNotNull null
            val end = obj.segmentTimeMs("End")
                ?: obj.segmentTimeMs("IntroEnd")
                ?: obj.segmentTimeMs("CreditsEnd")
                ?: obj.segmentTimeMs("end")
                ?: obj.segmentTimeMs("endTime")
                ?: start + 30_000L
            MediaSegment(
                type = type,
                startMs = start.coerceAtLeast(0L),
                endMs = end.coerceAtLeast(start)
            )
        }
    }

    private fun parseLegacyIntroTimestamp(payload: JsonObject): List<MediaSegment> {
        val start = payload.segmentTimeMs("ShowSkipPromptAt")
            ?: payload.segmentTimeMs("IntroStart")
            ?: return emptyList()
        val end = payload.segmentTimeMs("IntroEnd") ?: return emptyList()
        val hideAt = payload.segmentTimeMs("HideSkipPromptAt") ?: end
        return listOf(
            MediaSegment(
                type = "Introduction",
                startMs = start.coerceAtLeast(0L),
                endMs = end.coerceAtLeast(hideAt)
            )
        )
    }

    private fun JsonObject.segmentTimeMs(key: String): Long? {
        val primitive = runCatching { this[key]?.jsonPrimitive }.getOrNull() ?: return null
        val n = primitive.doubleOrNull ?: return parseClockTimeMs(primitive.contentOrNull)
        return when {
            n > 10_000_000 -> (n / 10_000.0).toLong()
            n > 10_000 -> n.toLong()
            else -> (n * 1000.0).toLong()
        }
    }

    private fun parseClockTimeMs(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val parts = text.split(':').mapNotNull { it.toDoubleOrNull() }
        if (parts.size != text.split(':').size || parts.isEmpty()) return null
        val seconds = parts.fold(0.0) { acc, part -> acc * 60.0 + part }
        return (seconds * 1000.0).toLong()
    }

    /**
     * Negotiate a playable stream with the server using PlaybackInfo. This is
     * the same flow Findroid and the official web client use — the server
     * picks DirectPlay / DirectStream / Transcode based on what we tell it we
     * can decode, and returns a fully-resolved URL.
     *
     * @param item the item to play (we use its `mediaStreams` to pick a non-
     *   commentary audio track when possible)
     * @param maxBitrate user-selected quality cap, or null for unlimited
     * @param audioStreamIndex explicit audio track override (from the picker),
     *   or null to let us pick a sensible default
     * @param subtitleStreamIndex explicit subtitle track, or null for none
     */
    suspend fun resolveStream(
        item: MediaItem,
        maxBitrate: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        forceTranscode: Boolean = false,
        directPlayOnly: Boolean = false,
        useMpvProfile: Boolean = false
    ): ResolvedStream = mapAuthErrors {
        val a = api ?: error("Call configure() first")

        val pickedAudioIndex = audioStreamIndex ?: pickPrimaryAudioIndex(item)
        val forceServerTranscode = !directPlayOnly && (forceTranscode || maxBitrate != null)

        val source = item.mediaSources.firstOrNull()
        val request = PlaybackInfoRequest(
            userId = userId,
            maxStreamingBitrate = if (directPlayOnly) null else maxBitrate,
            audioStreamIndex = if (useMpvProfile || directPlayOnly) null else pickedAudioIndex,
            subtitleStreamIndex = if (useMpvProfile || directPlayOnly) null else subtitleStreamIndex,
            mediaSourceId = source?.id,
            // forceTranscode = true → tell server to skip direct paths. This
            // is the fallback when DirectPlay was picked but the player can't
            // actually play the file (e.g. unusual container variants like
            // High 4:4:4 H.264 that MediaCodecList claims to support but
            // crashes on real content).
            //
            // A user-selected bitrate is also treated as a hard transcode
            // request. If the user picks 360p / 1 Mbps, direct play and direct
            // stream must be disabled; otherwise Jellyfin can legally return
            // an original-quality stream while still reporting a session that
            // looks like transcoding server-side.
            enableDirectPlay = !forceServerTranscode,
            enableDirectStream = !forceServerTranscode && !directPlayOnly,
            enableTranscoding = !directPlayOnly,
            allowVideoStreamCopy = !forceServerTranscode && !directPlayOnly,
            allowAudioStreamCopy = !forceServerTranscode && !directPlayOnly,
            deviceProfile = if (useMpvProfile) {
                buildMpvDeviceProfile(maxBitrate ?: 200_000_000)
            } else {
                buildDeviceProfile(maxBitrate ?: 200_000_000)
            }
        )

        val response = a.getPlaybackInfo(item.id, authHeader(), request)
        val ms = response.mediaSources.firstOrNull() ?: source
            ?: error("Server returned no media source for this item.")
        val mediaSourceId = ms.id.ifBlank { source?.id.orEmpty() }

        val (url, method) = when {
            directPlayOnly ->
                "$serverUrl/Videos/${item.id}/stream?Static=true&MediaSourceId=$mediaSourceId&api_key=$accessToken&DeviceId=$deviceId" to "DirectPlay"
            ms.transcodingUrl != null -> {
                val full = if (ms.transcodingUrl.startsWith("http")) {
                    ms.transcodingUrl
                } else {
                    "$serverUrl${ms.transcodingUrl}"
                }
                full to "Transcode"
            }
            ms.supportsDirectStream -> {
                "$serverUrl/Videos/${item.id}/stream?Static=false&MediaSourceId=$mediaSourceId&api_key=$accessToken&DeviceId=$deviceId" to "DirectStream"
            }
            ms.supportsDirectPlay -> {
                "$serverUrl/Videos/${item.id}/stream?Static=true&MediaSourceId=$mediaSourceId&api_key=$accessToken&DeviceId=$deviceId" to "DirectPlay"
            }
            else -> error("Server can't play this item with the current device profile.")
        }

        // Pull the resolved subtitle URL from the response. The server already
        // figured out whether to deliver it as external (text we attach to the
        // player) or embed (no action needed — comes via the container/HLS).
        val (subUrl, subFmt) = resolveSubtitleDelivery(ms, subtitleStreamIndex, item.id)

        ResolvedStream(
            url = url,
            mediaSourceId = mediaSourceId,
            playSessionId = response.playSessionId,
            playMethod = method,
            audioStreamIndex = pickedAudioIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            subtitleUrl = subUrl,
            subtitleFormat = subFmt
        )
    }

    /**
     * Build a subtitle sidecar URL that ExoPlayer can reliably consume. The
     * approach: ALWAYS request the subtitle from the server as VTT, regardless
     * of the source codec. Why:
     *
     *   - ExoPlayer's VTT parser is the most robust of the bunch.
     *   - Jellyfin's `Stream.vtt` endpoint reliably converts SRT, ASS, SSA,
     *     SUBRIP and most embedded subtitle types to VTT on the way out.
     *   - Other extensions (`.srt`, `.subrip`) have known server-side bugs
     *     (see jellyfin-android #717) and are inconsistently supported.
     *   - The previous code matched MIME to source codec (`stream.codec`) but
     *     the URL the server gave us often delivered DIFFERENT content (VTT
     *     bytes labeled as SSA), causing ExoPlayer to silently fail to parse.
     *     That's why subtitles only worked on ~10% of titles before — the 10%
     *     where source codec happened to match what the server actually sent.
     *
     * We sidecar even for "Embed" delivery method, because the server can
     * extract embedded subs to VTT on demand via this endpoint — and our own
     * `TrackSelectionOverride` then bypasses any track-selection ambiguity.
     */
    private fun resolveSubtitleDelivery(
        source: MediaSource,
        selectedIndex: Int?,
        itemId: String
    ): Pair<String?, String?> {
        if (selectedIndex == null) return null to null
        // Confirm the requested subtitle stream actually exists in the source.
        // If it doesn't, no point building a URL the server will 404 on.
        val exists = source.mediaStreams.any {
            it.type == "Subtitle" && it.index == selectedIndex
        }
        if (!exists) return null to null

        val url = "$serverUrl/Videos/$itemId/${source.id}/Subtitles/" +
            "$selectedIndex/0/Stream.vtt?api_key=$accessToken"
        return url to "vtt"
    }

    /** Pick the first non-commentary audio track. Falls back to first audio. */
    private fun pickPrimaryAudioIndex(item: MediaItem): Int? {
        val source = item.mediaSources.firstOrNull() ?: return null
        val audio = source.mediaStreams.filter { it.type == "Audio" }
        if (audio.isEmpty()) return null
        // First, anything explicitly NOT commentary/director.
        val main = audio.firstOrNull { !looksLikeCommentary(it) }
        return (main ?: audio.first()).index
    }

    private fun looksLikeCommentary(stream: MediaStream): Boolean {
        val haystack = listOfNotNull(stream.title, stream.displayTitle).joinToString(" ").lowercase()
        return haystack.contains("comment") || haystack.contains("director")
    }

    /**
     * Build a device profile from the codecs Android actually has decoders for.
     * Direct play is the preferred path — transcoding burns server CPU and
     * loses quality. We only force a transcode when the file's codecs aren't
     * in the device's decoder list. ExoPlayer's `onPlayerError` fallback is
     * still in place as the safety net for the rare cases where MediaCodecList
     * lies (sometimes happens with vendor decoders that crash on real files).
     */
    private fun buildDeviceProfile(maxBitrate: Long): DeviceProfile {
        val caps = com.example.jellyfinplayer.player.DeviceCodecs.get()
        val videoCodecCsv = caps.videoCodecs.joinToString(",")
        // Audio codecs both for inside video containers AND standalone audio.
        // We add common lossless / hi-res formats the platform handles natively
        // even though they don't appear in MediaCodecList (PCM, ALAC).
        val audioForVideo = (caps.audioCodecs + listOf("pcm")).distinct().joinToString(",")
        val audioForAudio = (caps.audioCodecs + listOf("pcm", "alac")).distinct().joinToString(",")

        return DeviceProfile(
            maxStreamingBitrate = maxBitrate,
            directPlayProfiles = listOf(
                DirectPlayProfile(
                    // Containers ExoPlayer's stock extractors parse cleanly.
                    // Both "mkv" and "matroska" appear in the wild depending on
                    // how the server identified the file — list both to avoid
                    // bogus transcodes. Same for "mpegts"/"ts" and "mp4"/"m4v".
                    container = "mp4,m4v,mkv,matroska,webm,mov,3gp,ts,mpegts,avi",
                    type = "Video",
                    videoCodec = videoCodecCsv,
                    audioCodec = audioForVideo
                ),
                DirectPlayProfile(
                    container = "mp3,aac,m4a,flac,opus,ogg,wav,wma,ape",
                    type = "Audio",
                    audioCodec = audioForAudio
                )
            ),
            // Transcode target when direct play isn't possible: HLS with H.264
            // baseline + AAC stereo — universally playable.
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = "Video",
                    videoCodec = "h264",
                    audioCodec = "aac",
                    protocol = "hls",
                    maxAudioChannels = "2"
                )
            ),
            subtitleProfiles = listOf(
                // All text-based formats: ask the server to deliver them as
                // External (text we sideload as VTT). NEVER Encode for text
                // formats — Encode means burn-in, which then renders ON TOP of
                // our sideloaded VTT and produces double subtitles.
                SubtitleProfile(format = "vtt", method = "External"),
                SubtitleProfile(format = "srt", method = "External"),
                SubtitleProfile(format = "subrip", method = "External"),
                SubtitleProfile(format = "ass", method = "External"),
                SubtitleProfile(format = "ssa", method = "External"),
                SubtitleProfile(format = "webvtt", method = "External"),
                // Picture-based subs (Blu-ray PGS, DVD VOBSUB) genuinely
                // CAN'T be converted to text — they're bitmap images. Burn-in
                // is the only way to display them. ExoPlayer can't render
                // these as a separate track.
                SubtitleProfile(format = "pgssub", method = "Encode"),
                SubtitleProfile(format = "dvdsub", method = "Encode"),
                SubtitleProfile(format = "dvbsub", method = "Encode"),
                SubtitleProfile(format = "dvbtxt", method = "Encode"),
                SubtitleProfile(format = "dvb_teletext", method = "Encode")
            )
        )
    }

    private fun buildMpvDeviceProfile(maxBitrate: Long): DeviceProfile {
        val videoCodecs = listOf(
            "h264", "hevc", "mpeg2video", "mpeg4", "msmpeg4v2", "msmpeg4v3",
            "vc1", "vp8", "vp9", "av1", "theora", "prores"
        ).joinToString(",")
        val audioCodecs = listOf(
            "aac", "mp3", "mp2", "ac3", "eac3", "dts", "truehd", "flac",
            "alac", "opus", "vorbis", "pcm", "wma", "wmav2"
        ).joinToString(",")

        return DeviceProfile(
            maxStreamingBitrate = maxBitrate,
            maxStaticBitrate = maxBitrate,
            directPlayProfiles = listOf(
                DirectPlayProfile(
                    container = "mp4,m4v,mkv,matroska,webm,mov,3gp,ts,mpegts,avi,mpg,mpeg,wmv,flv,ogm,ogg",
                    type = "Video",
                    videoCodec = videoCodecs,
                    audioCodec = audioCodecs
                ),
                DirectPlayProfile(
                    container = "mp3,aac,m4a,flac,opus,ogg,wav,wma,ape,alac",
                    type = "Audio",
                    audioCodec = audioCodecs
                )
            ),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = "Video",
                    videoCodec = "h264",
                    audioCodec = "aac",
                    protocol = "hls",
                    maxAudioChannels = "2"
                )
            ),
            subtitleProfiles = listOf(
                SubtitleProfile(format = "vtt", method = "External"),
                SubtitleProfile(format = "srt", method = "External"),
                SubtitleProfile(format = "subrip", method = "External"),
                SubtitleProfile(format = "ass", method = "External"),
                SubtitleProfile(format = "ssa", method = "External"),
                SubtitleProfile(format = "webvtt", method = "External"),
                SubtitleProfile(format = "pgssub", method = "Embed"),
                SubtitleProfile(format = "dvdsub", method = "Embed"),
                SubtitleProfile(format = "dvbsub", method = "Embed"),
                SubtitleProfile(format = "dvbtxt", method = "Embed"),
                SubtitleProfile(format = "dvb_teletext", method = "Embed")
            )
        )
    }

    /** Subtitle stream URL for a given media stream index. Defaults to VTT for ExoPlayer. */
    fun subtitleUrl(
        itemId: String,
        mediaSourceId: String,
        streamIndex: Int,
        format: String = "vtt"
    ): String {
        val safeFormat = format.lowercase().trim().ifBlank { "vtt" }
        return "$serverUrl/Videos/$itemId/$mediaSourceId/Subtitles/$streamIndex/0/Stream.$safeFormat?api_key=$accessToken"
    }

    /**
     * For an episode, builds the URL of its parent series' primary poster.
     * Returns null if the item isn't an episode or doesn't carry a series
     * image tag in its response. Used to render a single grouped series
     * card in the Downloads grid even though the underlying records are
     * individual episodes.
     */
    fun seriesPosterUrl(item: MediaItem, maxHeight: Int = 480): String? {
        val sid = item.seriesId ?: return null
        val tag = item.seriesPrimaryImageTag ?: return null
        return "$serverUrl/Items/$sid/Images/Primary?maxHeight=$maxHeight&tag=$tag&quality=90"
    }

    /**
     * Build a download URL for an item at a chosen quality.
     *
     * @param item the item to download
     * @param maxBitrate null for original-quality direct download (server
     *   streams the file as-is, no transcoding cost), otherwise asks the
     *   server to transcode to MP4 at-or-below this bitrate. Transcoded
     *   downloads use significantly more server CPU and time.
     */
    fun downloadUrl(item: MediaItem, maxBitrate: Long?): String {
        val base = "$serverUrl/Videos/${item.id}/stream"
        return if (maxBitrate == null) {
            // Original — Static=true tells the server to serve the raw file.
            "$base?Static=true&api_key=$accessToken"
        } else {
            val audioBitrate = 128_000L.coerceAtMost(maxBitrate / 4)
            val videoBitrate = (maxBitrate - audioBitrate).coerceAtLeast(250_000L)
            val height = downloadTargetHeight(maxBitrate)
            val width = downloadTargetWidth(height)
            val mediaSourceId = item.mediaSources.firstOrNull()?.id
                ?.takeIf { it.isNotBlank() }
                ?.let { "&MediaSourceId=$it" }
                .orEmpty()
            // Transcode to MP4 (universally compatible playback) at-or-below
            // the chosen bitrate. The server picks an appropriate H.264
            // profile/level based on the bitrate target.
            //
            // Note on forcing: Jellyfin will direct-stream rather than
            // re-encode if the source already fits all our constraints
            // (codec/profile/bitrate). For the transcode option we want
            // an actual re-encode at the target bitrate, so we set:
            //   - Static=false (don't serve the original bytes)
            //   - EnableAutoStreamCopy=false plus Allow*StreamCopy=false
            //   - explicit VideoBitrate/AudioBitrate and MaxWidth/MaxHeight
            //
            // MaxStreamingBitrate alone is not enough on every Jellyfin
            // server; some versions can still remux/copy a file and only
            // change the container. The explicit encoder params make the
            // request much harder for the server to silently treat as
            // original-quality.
            "$base.mp4?api_key=$accessToken" +
                "&DeviceId=$deviceId" +
                mediaSourceId +
                "&Static=false" +
                "&StartTimeTicks=0" +
                "&EnableDirectPlay=false" +
                "&EnableDirectStream=false" +
                "&EnableTranscoding=true" +
                "&VideoCodec=h264" +
                "&AudioCodec=aac" +
                "&Container=mp4" +
                "&MaxStreamingBitrate=$maxBitrate" +
                "&VideoBitrate=$videoBitrate" +
                "&AudioBitrate=$audioBitrate" +
                "&MaxWidth=$width" +
                "&MaxHeight=$height" +
                "&MaxAudioChannels=2" +
                "&TranscodingMaxAudioChannels=2" +
                "&EnableAutoStreamCopy=false" +
                "&AllowVideoStreamCopy=false" +
                "&AllowAudioStreamCopy=false" +
                "&TranscodeReasons=ContainerBitrateExceedsLimit" +
                "&RequireAvc=true"
        }
    }

    private fun downloadTargetHeight(maxBitrate: Long): Int = when {
        maxBitrate <= 1_000_000L -> 360
        maxBitrate <= 3_000_000L -> 480
        maxBitrate <= 8_000_000L -> 720
        else -> 1080
    }

    private fun downloadTargetWidth(height: Int): Int = when (height) {
        360 -> 640
        480 -> 854
        720 -> 1280
        else -> 1920
    }

    // ---- helpers ----------------------------------------------------------

    /**
     * Normalize what the user typed into the server URL field. Trim whitespace,
     * remove a trailing slash, and prepend http:// if no scheme was provided.
     */
    private fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) trimmed else "http://$trimmed"
    }

    /** Strip characters that would break the MediaBrowser header parser. */
    private fun sanitize(value: String): String =
        value.replace(Regex("""[^A-Za-z0-9_\- ]"""), "").ifBlank { "Android" }
}
