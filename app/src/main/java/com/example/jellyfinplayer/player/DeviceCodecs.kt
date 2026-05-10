package com.example.jellyfinplayer.player

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Probes the Android platform decoders at runtime to figure out which audio
 * and video codecs this specific device can decode. The result feeds into
 * the Jellyfin DeviceProfile we send to the server, so the server only
 * forces a transcode when the file actually contains something we can't play.
 *
 * Why not just hard-code "AC3, EAC3, etc."? Some lower-end Android devices
 * (and some cheap TV boxes) genuinely lack those decoders. Probing is the
 * only way to be both broad on capable devices AND honest on weak ones.
 *
 * Note: ExoPlayer also has its own software fallback decoders (FFmpeg
 * extension etc.) but we don't include those in the build, so we report
 * platform-only capabilities. That matches what actually plays.
 */
object DeviceCodecs {

    /**
     * MIME → Jellyfin codec name. Jellyfin uses lowercase short names
     * ("h264", not "video/avc"). Anything not in this map is ignored.
     */
    private val VIDEO_CODECS = mapOf(
        MediaFormat.MIMETYPE_VIDEO_AVC to "h264",
        MediaFormat.MIMETYPE_VIDEO_HEVC to "hevc",
        MediaFormat.MIMETYPE_VIDEO_VP8 to "vp8",
        MediaFormat.MIMETYPE_VIDEO_VP9 to "vp9",
        MediaFormat.MIMETYPE_VIDEO_AV1 to "av1",
        MediaFormat.MIMETYPE_VIDEO_MPEG2 to "mpeg2video",
        MediaFormat.MIMETYPE_VIDEO_MPEG4 to "mpeg4"
    )

    private val AUDIO_CODECS = mapOf(
        MediaFormat.MIMETYPE_AUDIO_AAC to "aac",
        MediaFormat.MIMETYPE_AUDIO_MPEG to "mp3",
        MediaFormat.MIMETYPE_AUDIO_AC3 to "ac3",
        MediaFormat.MIMETYPE_AUDIO_EAC3 to "eac3",
        MediaFormat.MIMETYPE_AUDIO_OPUS to "opus",
        MediaFormat.MIMETYPE_AUDIO_VORBIS to "vorbis",
        MediaFormat.MIMETYPE_AUDIO_FLAC to "flac",
        MediaFormat.MIMETYPE_AUDIO_RAW to "pcm"
    )

    // Cached so we don't re-query MediaCodecList on every PlaybackInfo call.
    private val cache: Capabilities by lazy { probe() }

    data class Capabilities(
        val videoCodecs: List<String>,
        val audioCodecs: List<String>
    )

    fun get(): Capabilities = cache

    private fun probe(): Capabilities {
        val video = mutableSetOf<String>()
        val audio = mutableSetOf<String>()

        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                for (mime in info.supportedTypes) {
                    val key = mime.lowercase()
                    VIDEO_CODECS[key]?.let { video += it }
                    AUDIO_CODECS[key]?.let { audio += it }
                }
            }
        } catch (_: Throwable) {
            // Should never happen on a real device, but if MediaCodecList
            // explodes for some reason fall back to a safe minimum so we
            // don't end up sending an empty profile (which would force the
            // server to transcode literally everything).
        }

        // Guarantee a sensible floor — every Android device since API 16 has
        // these. Without them the server has nothing to direct-play to and
        // every single file gets transcoded.
        if (video.isEmpty()) video += "h264"
        if (audio.isEmpty()) audio += listOf("aac", "mp3")

        return Capabilities(
            videoCodecs = video.sorted(),
            audioCodecs = audio.sorted()
        )
    }
}
