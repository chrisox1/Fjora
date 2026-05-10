package com.example.jellyfinplayer.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String
)

@Serializable
data class AuthResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String,
    @SerialName("User") val user: JellyfinUser
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String
)

@Serializable
data class SystemInfo(
    @SerialName("ServerName") val serverName: String? = null
)

data class MediaSegment(
    val type: String,
    val startMs: Long,
    val endMs: Long
)

@Serializable
data class ItemsResponse(
    @SerialName("Items") val items: List<MediaItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
data class MediaItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: String, // "Movie", "Series", "Episode"
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    /**
     * For episodes, the image tag of the parent series' primary poster.
     * Combined with seriesId we can build the URL of the show's cover
     * art — used by the My Downloads grid to render a single card for a
     * series even though the records are individual episodes.
     */
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("IndexNumber") val episodeNumber: Int? = null,
    @SerialName("ParentIndexNumber") val seasonNumber: Int? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("UserData") val userData: UserItemData? = null,
    /**
     * For library views (CollectionFolder items), the type of content it
     * holds: "movies", "tvshows", "music", "boxsets", "homevideos", etc.
     * Null on regular items. Used by the library picker to label tabs.
     */
    @SerialName("CollectionType") val collectionType: String? = null,
    /**
     * Cast and crew. Only populated when the request asked for `Fields=People`
     * — list-style endpoints (library, home rows) skip this to keep responses
     * small, the detail endpoint includes it.
     */
    @SerialName("People") val people: List<Person> = emptyList()
) {
    /** Runtime in minutes; 1 tick = 100 ns. */
    val runtimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }

    /** Resume position in milliseconds, or null if the user hasn't started this. */
    val resumePositionMs: Long? get() = userData?.playbackPositionTicks?.takeIf { it > 0 }
        ?.let { it / 10_000L }

    /** 0..1 progress through the item, or null if unknown / not started. */
    val playedFraction: Float?
        get() {
            val pos = userData?.playbackPositionTicks ?: return null
            val total = runTimeTicks ?: return null
            if (total <= 0) return null
            return (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * One entry in an item's cast/crew list. The Type field distinguishes actors
 * (most users care about these) from directors/writers/producers/etc.
 *
 * `primaryImageTag` is non-null when Jellyfin has a headshot for this person.
 * The image URL is built as: serverUrl/Items/{id}/Images/Primary?api_key=...
 * (Jellyfin treats people as items internally, so the same images endpoint
 * works.) When the tag is null we render a placeholder avatar.
 */
@Serializable
data class Person(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    /** Character name for actors; null for crew roles. */
    @SerialName("Role") val role: String? = null,
    /** "Actor" | "Director" | "Writer" | "Producer" | "GuestStar" etc. */
    @SerialName("Type") val type: String = "",
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null
)

@Serializable
data class UserItemData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null
)

/**
 * MediaSource as returned from /Items/{id}/PlaybackInfo. The server fills the
 * SupportsDirectPlay/Stream flags and TranscodingUrl based on the DeviceProfile
 * we sent, telling us which playback method is appropriate.
 */
@Serializable
data class MediaSource(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList(),
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("TranscodingContainer") val transcodingContainer: String? = null
)

@Serializable
data class MediaStream(
    @SerialName("Index") val index: Int = -1,
    @SerialName("Type") val type: String = "", // "Video", "Audio", "Subtitle"
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("DeliveryMethod") val deliveryMethod: String? = null
)

// ---- PlaybackInfo request DTOs -------------------------------------------
// Sent to /Items/{id}/PlaybackInfo so the server knows our codec capabilities
// and can return a stream URL we can actually play. Anything we leave out of
// DirectPlayProfiles forces transcoding.

@Serializable
data class PlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long = 0,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean = true,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean = true,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean = true,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean = true,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean = true,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean = true,
    /**
     * Cap the audio channels when transcoding. 2 = stereo downmix, which is
     * what phone speakers / headphones can use anyway. Without this, a 7-ch
     * DTS source forces the server's ffmpeg into a much harder pipeline and
     * can fail outright on minimal Jellyfin Docker images.
     */
    @SerialName("MaxAudioChannels") val maxAudioChannels: Int = 2,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfile
)

@Serializable
data class DeviceProfile(
    @SerialName("Name") val name: String = "Fjora",
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long = 120_000_000,
    @SerialName("MaxStaticBitrate") val maxStaticBitrate: Long = 200_000_000,
    @SerialName("MusicStreamingTranscodingBitrate") val musicStreamingTranscodingBitrate: Long = 320_000,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfile>,
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfile>,
    @SerialName("ContainerProfiles") val containerProfiles: List<ContainerProfile> = emptyList(),
    @SerialName("CodecProfiles") val codecProfiles: List<CodecProfile> = emptyList(),
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<SubtitleProfile> = emptyList()
)

@Serializable
data class DirectPlayProfile(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String, // "Video", "Audio"
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null
)

@Serializable
data class TranscodingProfile(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String, // "Video", "Audio"
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String, // "hls", "http"
    @SerialName("Context") val context: String = "Streaming",
    @SerialName("MaxAudioChannels") val maxAudioChannels: String = "2",
    @SerialName("MinSegments") val minSegments: Int = 1,
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean = true
)

@Serializable
data class ContainerProfile(
    @SerialName("Type") val type: String,
    @SerialName("Container") val container: String
)

@Serializable
data class CodecProfile(
    @SerialName("Type") val type: String, // "VideoAudio", "Video", "Audio"
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Container") val container: String? = null
)

@Serializable
data class SubtitleProfile(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String // "External", "Embed", "Hls"
)

@Serializable
data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null
)

// ---- Playback state reporting DTOs ---------------------------------------

@Serializable
data class PlaybackStartInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false
)

@Serializable
data class PlaybackProgressInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false,
    @SerialName("EventName") val eventName: String? = null
)

@Serializable
data class PlaybackStopInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long
)
