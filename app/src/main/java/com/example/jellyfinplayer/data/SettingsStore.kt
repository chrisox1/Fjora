package com.example.jellyfinplayer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Separate DataStore from auth so settings survive a sign-out cleanly and
// don't pollute the auth flow with extra keys.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    companion object {
        val DEFAULT_BITRATE = longPreferencesKey("default_max_bitrate")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val SHOW_NEXT_UP_ROW = booleanPreferencesKey("show_next_up_row")
        val HOME_HERO_SOURCE = stringPreferencesKey("home_hero_source")
        val FORCE_TRANSCODING = booleanPreferencesKey("force_transcoding")
        val USE_MPV = booleanPreferencesKey("use_mpv_for_local")
        val USE_MPV_FOR_ALL = booleanPreferencesKey("use_mpv_for_all")
        val DIRECT_PLAY_ONLY = booleanPreferencesKey("direct_play_only")
        val ALWAYS_PLAY_SUBTITLES = booleanPreferencesKey("always_play_subtitles")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
        val SUBTITLE_TEXT_SCALE = floatPreferencesKey("subtitle_text_scale")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
        val SUBTITLE_DELAY_MS = longPreferencesKey("subtitle_delay_ms")
        val DOWNLOAD_STORAGE_LIMIT_BYTES = longPreferencesKey("download_storage_limit_bytes")
    }

    data class Settings(
        val defaultMaxBitrate: Long?,
        val autoResume: Boolean,
        val showNextUpRow: Boolean,
        val homeHeroSource: HomeHeroSource,
        val forceTranscoding: Boolean,
        val directPlayOnly: Boolean,
        /**
         * When true, downloaded files always play with the bundled mpv
         * player instead of the built-in ExoPlayer. mpv handles a wider
         * range of file formats correctly — notably MKVs without proper
         * cues that ExoPlayer can't seek. Costs ~30 MB APK size (the libmpv
         * native library) but the user explicitly opts in.
         */
        val useMpvForLocal: Boolean,
        val useMpvForAll: Boolean,
        val alwaysPlaySubtitles: Boolean,
        val preferredSubtitleLanguage: String?,
        val subtitleTextScale: Float,
        val subtitleColor: String,
        val subtitleBackground: Boolean,
        val subtitleDelayMs: Long,
        val downloadStorageLimitBytes: Long?
    )

    val flow: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        val directPlayOnly = prefs[DIRECT_PLAY_ONLY] ?: false
        Settings(
            defaultMaxBitrate = prefs[DEFAULT_BITRATE]?.takeIf { it > 0 },
            autoResume = prefs[AUTO_RESUME] ?: true,
            showNextUpRow = prefs[SHOW_NEXT_UP_ROW] ?: true,
            homeHeroSource = HomeHeroSource.fromStorageValue(prefs[HOME_HERO_SOURCE]),
            forceTranscoding = prefs[FORCE_TRANSCODING] ?: false,
            directPlayOnly = directPlayOnly,
            useMpvForLocal = effectiveUseMpvForLocal(
                directPlayOnly = directPlayOnly,
                storedUseMpvForLocal = prefs[USE_MPV] ?: false
            ),
            useMpvForAll = effectiveUseMpvForAll(directPlayOnly),
            alwaysPlaySubtitles = prefs[ALWAYS_PLAY_SUBTITLES] ?: false,
            preferredSubtitleLanguage = prefs[PREFERRED_SUBTITLE_LANGUAGE]?.takeIf { it.isNotBlank() },
            subtitleTextScale = (prefs[SUBTITLE_TEXT_SCALE] ?: 1.0f).coerceIn(0.75f, 1.4f),
            subtitleColor = prefs[SUBTITLE_COLOR]?.takeIf { it.isNotBlank() } ?: "white",
            subtitleBackground = prefs[SUBTITLE_BACKGROUND] ?: false,
            subtitleDelayMs = (prefs[SUBTITLE_DELAY_MS] ?: 0L).coerceIn(-5_000L, 5_000L),
            downloadStorageLimitBytes = prefs[DOWNLOAD_STORAGE_LIMIT_BYTES]?.takeIf { it > 0L }
        )
    }

    suspend fun setUseMpvForLocal(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[USE_MPV] = enabled
        }
    }

    suspend fun setUseMpvForAll(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            if (enabled && prefs[DIRECT_PLAY_ONLY] == true) {
                prefs[USE_MPV_FOR_ALL] = true
            }
        }
    }

    suspend fun setDirectPlayOnly(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[DIRECT_PLAY_ONLY] = enabled
            prefs[USE_MPV_FOR_ALL] = enabled
            prefs[USE_MPV] = enabled
            if (enabled) prefs[FORCE_TRANSCODING] = false
        }
    }

    suspend fun setDefaultBitrate(bitrate: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (bitrate == null) prefs.remove(DEFAULT_BITRATE)
            else prefs[DEFAULT_BITRATE] = bitrate
        }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[AUTO_RESUME] = enabled
        }
    }

    suspend fun setShowNextUpRow(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SHOW_NEXT_UP_ROW] = enabled
        }
    }

    suspend fun setHomeHeroSource(source: HomeHeroSource) {
        context.settingsDataStore.edit { prefs ->
            prefs[HOME_HERO_SOURCE] = source.storageValue
        }
    }

    suspend fun setForceTranscoding(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[FORCE_TRANSCODING] = enabled
        }
    }

    suspend fun setAlwaysPlaySubtitles(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ALWAYS_PLAY_SUBTITLES] = enabled
        }
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        context.settingsDataStore.edit { prefs ->
            if (language.isNullOrBlank()) prefs.remove(PREFERRED_SUBTITLE_LANGUAGE)
            else prefs[PREFERRED_SUBTITLE_LANGUAGE] = language
        }
    }

    suspend fun setSubtitleTextScale(scale: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[SUBTITLE_TEXT_SCALE] = scale.coerceIn(0.75f, 1.4f)
        }
    }

    suspend fun setSubtitleColor(color: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SUBTITLE_COLOR] = color
        }
    }

    suspend fun setSubtitleBackground(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SUBTITLE_BACKGROUND] = enabled
        }
    }

    suspend fun setSubtitleDelayMs(delayMs: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[SUBTITLE_DELAY_MS] = delayMs.coerceIn(-5_000L, 5_000L)
        }
    }

    suspend fun setDownloadStorageLimitBytes(bytes: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (bytes == null || bytes <= 0L) prefs.remove(DOWNLOAD_STORAGE_LIMIT_BYTES)
            else prefs[DOWNLOAD_STORAGE_LIMIT_BYTES] = bytes
        }
    }
}

enum class HomeHeroSource(val storageValue: String, val label: String) {
    RESUME("resume", "Resume"),
    FEATURED("featured", "Featured"),
    NEXT_UP("next_up", "Next up");

    companion object {
        fun fromStorageValue(value: String?): HomeHeroSource =
            entries.firstOrNull { it.storageValue == value } ?: RESUME
    }
}

internal fun effectiveUseMpvForLocal(
    directPlayOnly: Boolean,
    storedUseMpvForLocal: Boolean
): Boolean = directPlayOnly || storedUseMpvForLocal

internal fun effectiveUseMpvForAll(directPlayOnly: Boolean): Boolean = directPlayOnly
