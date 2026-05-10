package com.example.jellyfinplayer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Separate DataStore so download tracking survives sign-out (the user may
// have downloaded files for offline use; we don't want to lose those records
// just because they switched accounts) and doesn't pollute the auth or
// settings flows.
private val Context.downloadsDataStore by preferencesDataStore(name = "downloads")

class DownloadsStore(private val context: Context) {
    companion object {
        val RECORDS = stringPreferencesKey("records_json")
    }

    /**
     * One downloaded file's metadata. The JSON file in DataStore is the
     * source of truth — we don't try to enumerate the storage directory,
     * because:
     *   1) the directory is app-private and our own;
     *   2) we want richer metadata than the file system can provide
     *      (poster URL, original Jellyfin item ID, quality preset, etc.);
     *   3) it keeps the UI render fast (no IO on the main thread).
     */
    @Serializable
    data class DownloadRecord(
        /** Jellyfin item ID — for navigation back to detail screen later. */
        val itemId: String,
        /** Android DownloadManager's download id, used to query status. */
        val downloadId: Long,
        /** The display name we show in the Downloads grid. */
        val title: String,
        /** Series name for episodes, null for movies. */
        val seriesName: String? = null,
        /**
         * Series ID for episodes, used to group downloads under the parent
         * series in the My Downloads UI. Null for movies.
         */
        val seriesId: String? = null,
        /** "S2E5" formatting for episodes, null for movies. */
        val seasonEpisodeLabel: String? = null,
        /** Numeric season for sorting — null for movies. */
        val seasonNumber: Int? = null,
        /** Numeric episode within its season — null for movies. */
        val episodeNumber: Int? = null,
        /**
         * Poster image URL on the original server. For episodes, this is
         * the episode's still / preview image. Stored at download time so
         * we can render the card even after sign-out / network loss —
         * Coil caches the image bytes locally and renders from cache.
         */
        val posterUrl: String? = null,
        /**
         * Series poster URL for episodes — the show's vertical artwork,
         * used as the cover image for the grouped series card in the
         * Downloads grid. Null for movies (use posterUrl instead).
         */
        val seriesPosterUrl: String? = null,
        /** Item overview / synopsis — shown on the offline detail screen. */
        val overview: String? = null,
        /** Year of release / first air, for the detail-screen subtitle. */
        val productionYear: Int? = null,
        /** Runtime in minutes, for the detail-screen meta row. */
        val runtimeMinutes: Int? = null,
        /** Local absolute file path, set when the download completes. */
        val filePath: String? = null,
        /** Container/size in bytes for display. -1 if not yet known. */
        val sizeBytes: Long = -1L,
        /** True if downloaded at original quality (Static=true). */
        val isOriginalQuality: Boolean = true,
        /** Max bitrate cap for transcoded downloads (null for original). */
        val maxBitrate: Long? = null,
        /** When the download was initiated (epoch millis). */
        val createdAt: Long = System.currentTimeMillis(),
        /** Absolute paths to downloaded subtitle files, keyed by download order. */
        val subtitlePaths: List<String> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val recordsFlow: Flow<List<DownloadRecord>> = context.downloadsDataStore.data.map { prefs ->
        val raw = prefs[RECORDS] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<DownloadRecord>>(raw) }
            .getOrDefault(emptyList())
    }

    suspend fun add(record: DownloadRecord) {
        context.downloadsDataStore.edit { prefs ->
            val existing = prefs[RECORDS]
                ?.let { runCatching { json.decodeFromString<List<DownloadRecord>>(it) }.getOrNull() }
                ?: emptyList()
            // De-dupe by downloadId — if the user re-initiates a download
            // for the same item, replace the old record in place.
            val merged = existing.filterNot { it.downloadId == record.downloadId } + record
            prefs[RECORDS] = json.encodeToString(merged)
        }
    }

    /**
     * Update an existing record (typically called when a download completes
     * to set filePath and sizeBytes).
     */
    suspend fun update(downloadId: Long, transform: (DownloadRecord) -> DownloadRecord) {
        context.downloadsDataStore.edit { prefs ->
            val existing = prefs[RECORDS]
                ?.let { runCatching { json.decodeFromString<List<DownloadRecord>>(it) }.getOrNull() }
                ?: return@edit
            val updated = existing.map { rec ->
                if (rec.downloadId == downloadId) transform(rec) else rec
            }
            prefs[RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun remove(downloadId: Long) {
        context.downloadsDataStore.edit { prefs ->
            val existing = prefs[RECORDS]
                ?.let { runCatching { json.decodeFromString<List<DownloadRecord>>(it) }.getOrNull() }
                ?: return@edit
            val filtered = existing.filterNot { it.downloadId == downloadId }
            prefs[RECORDS] = json.encodeToString(filtered)
        }
    }
}
