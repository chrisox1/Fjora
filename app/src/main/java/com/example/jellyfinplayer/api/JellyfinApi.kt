package com.example.jellyfinplayer.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.json.JsonObject

interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun authenticate(
        @Header("Authorization") authHeader: String,
        @Body body: AuthRequest
    ): AuthResponse

    @GET("QuickConnect/Enabled")
    suspend fun getQuickConnectEnabled(): Boolean

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(
        @Header("Authorization") authHeader: String
    ): QuickConnectResult

    @GET("QuickConnect/Connect")
    suspend fun getQuickConnectState(
        @Query("secret") secret: String
    ): QuickConnectResult

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(
        @Header("Authorization") authHeader: String,
        @Body body: QuickConnectAuthRequest
    ): AuthResponse

    @GET("System/Info")
    suspend fun getSystemInfo(
        @Header("Authorization") authHeader: String
    ): SystemInfo

    /**
     * Get movies and TV shows for a user. We filter strictly to "Movie,Series"
     * because that's the requirement: only show shows and movies.
     * UserData is requested so the library knows which items are watched/in-progress.
     */
    @GET("Users/{userId}/Items")
    suspend fun getMoviesAndSeries(
        @Path("userId") userId: String,
        @Header("Authorization") authHeader: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Recursive") recursive: Boolean = true,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate",
        @Query("Limit") limit: Int = 200
    ): ItemsResponse

    /**
     * List the user's "views" (libraries). Each entry is a CollectionFolder
     * with a CollectionType field ("movies", "tvshows", "music", etc.) that
     * the UI uses to label and filter.
     */
    @GET("Users/{userId}/Views")
    suspend fun getViews(
        @Path("userId") userId: String,
        @Header("Authorization") authHeader: String
    ): ItemsResponse

    /**
     * Items under a specific library (parent). Used by the My Media filter
     * so users can scope the grid to e.g. just their movies library or
     * just one of their TV libraries.
     */
    @GET("Users/{userId}/Items")
    suspend fun getItemsInLibrary(
        @Path("userId") userId: String,
        @Header("Authorization") authHeader: String,
        @Query("ParentId") parentId: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Recursive") recursive: Boolean = true,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate",
        @Query("Limit") limit: Int = 500
    ): ItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getItemsByPerson(
        @Path("userId") userId: String,
        @Header("Authorization") authHeader: String,
        @Query("PersonIds") personIds: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Recursive") recursive: Boolean = true,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate",
        @Query("Limit") limit: Int = 200
    ): ItemsResponse

    /** For a Series, list its episodes (flat). */
    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Header("Authorization") authHeader: String,
        @Query("UserId") userId: String,
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate"
    ): ItemsResponse

    /** Full item details, including MediaSources / streams (subtitles, qualities). */
    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItemDetails(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("Authorization") authHeader: String
    ): MediaItem

    @GET("Episode/{itemId}/IntroSkipperSegments")
    suspend fun getIntroSkipperSegments(
        @Path("itemId") itemId: String,
        @Header("Authorization") authHeader: String
    ): JsonObject

    @GET("MediaSegments/{itemId}")
    suspend fun getMediaSegments(
        @Path("itemId") itemId: String,
        @Header("Authorization") authHeader: String
    ): JsonObject

    @GET("Episode/{itemId}/Timestamps")
    suspend fun getIntroSkipperTimestamps(
        @Path("itemId") itemId: String,
        @Header("Authorization") authHeader: String
    ): JsonObject

    @GET("Episode/{itemId}/IntroTimestamps/v1")
    suspend fun getLegacyIntroTimestamps(
        @Path("itemId") itemId: String,
        @Header("Authorization") authHeader: String
    ): JsonObject

    /**
     * Negotiate a playable stream URL. The server takes our DeviceProfile and
     * decides whether to direct-play, container-remux, or transcode, returning
     * a fully-resolved URL we just hand to ExoPlayer.
     *
     * This is the only correct way to play media — building stream URLs by
     * hand misses required params and is why audio breaks on some titles.
     */
    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Header("Authorization") authHeader: String,
        @Body body: PlaybackInfoRequest
    ): PlaybackInfoResponse

    /**
     * Search across the user's library. The global search returns top-level
     * movies and shows only; individual episodes stay discoverable from their
     * show page so the main search does not get noisy.
     */
    @GET("Users/{userId}/Items")
    suspend fun search(
        @Path("userId") userId: String,
        @Header("Authorization") authHeader: String,
        @Query("SearchTerm") query: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate",
        @Query("Limit") limit: Int = 50
    ): ItemsResponse

    /** Items the user has started but not finished. The "Continue Watching" row. */
    @GET("UserItems/Resume")
    suspend fun getResumeItems(
        @Header("Authorization") authHeader: String,
        @Query("UserId") userId: String,
        @Query("MediaTypes") mediaTypes: String = "Video",
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate",
        @Query("Limit") limit: Int = 20
    ): ItemsResponse

    /** Next unwatched episode for series the user is currently watching. */
    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @Header("Authorization") authHeader: String,
        @Query("UserId") userId: String,
        @Query("Fields") fields: String = "Overview,MediaSources,UserData,DateCreated,DateLastMediaAdded,PremiereDate",
        @Query("Limit") limit: Int = 20
    ): ItemsResponse

    // ---- Playback state reporting -----------------------------------------
    // These three endpoints feed the server's "currently playing" + resume +
    // next-up bookkeeping. Without them, Continue Watching never populates.

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Header("Authorization") authHeader: String,
        @Body body: PlaybackStartInfo
    )

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Header("Authorization") authHeader: String,
        @Body body: PlaybackProgressInfo
    )

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Header("Authorization") authHeader: String,
        @Body body: PlaybackStopInfo
    )
}
