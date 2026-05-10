package com.example.jellyfinplayer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val FORCE_TRANSCODING = booleanPreferencesKey("force_transcoding")
        val USE_MPV = booleanPreferencesKey("use_mpv_for_local")
        val USE_MPV_FOR_ALL = booleanPreferencesKey("use_mpv_for_all")
        val DIRECT_PLAY_ONLY = booleanPreferencesKey("direct_play_only")
        val ALWAYS_PLAY_SUBTITLES = booleanPreferencesKey("always_play_subtitles")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
    }

    data class Settings(
        val defaultMaxBitrate: Long?,
        val autoResume: Boolean,
        val showNextUpRow: Boolean,
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
        val preferredSubtitleLanguage: String?
    )

    val flow: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        val directPlayOnly = prefs[DIRECT_PLAY_ONLY] ?: false
        Settings(
            defaultMaxBitrate = prefs[DEFAULT_BITRATE]?.takeIf { it > 0 },
            autoResume = prefs[AUTO_RESUME] ?: true,
            showNextUpRow = prefs[SHOW_NEXT_UP_ROW] ?: true,
            forceTranscoding = prefs[FORCE_TRANSCODING] ?: false,
            directPlayOnly = directPlayOnly,
            useMpvForLocal = if (directPlayOnly) true else prefs[USE_MPV] ?: false,
            useMpvForAll = directPlayOnly,
            alwaysPlaySubtitles = prefs[ALWAYS_PLAY_SUBTITLES] ?: false,
            preferredSubtitleLanguage = prefs[PREFERRED_SUBTITLE_LANGUAGE]?.takeIf { it.isNotBlank() }
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
}
