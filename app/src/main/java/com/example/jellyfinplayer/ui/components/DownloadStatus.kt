package com.example.jellyfinplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Snapshot of a DownloadManager download's progress. Polled every 2 seconds
 * while the download is in flight; polling stops as soon as the download
 * reaches a terminal state (success or failure) so battery isn't burned on
 * long-completed records.
 */
data class DownloadStatus(
    val state: State,
    /** 0..1, or null if total size isn't known yet (server hasn't sent
     *  Content-Length, e.g. during the early bytes of a transcode). */
    val progress: Float?,
    val bytesDownloaded: Long,
    val totalBytes: Long
) {
    enum class State { Pending, Running, Paused, Successful, Failed, Unknown }

    val isComplete: Boolean get() = state == State.Successful
    val isInFlight: Boolean get() =
        state == State.Pending || state == State.Running || state == State.Paused
}

/**
 * Composable that polls DownloadManager for a given download id and returns
 * a live [DownloadStatus] state. Safe to call from anywhere — handles
 * exceptions internally (cleared download records, missing permissions,
 * etc.) and reports Unknown state in the worst case.
 */
@Composable
fun rememberDownloadStatus(downloadId: Long): DownloadStatus {
    val ctx = LocalContext.current
    var status by remember(downloadId) {
        mutableStateOf(DownloadStatus(DownloadStatus.State.Unknown, null, 0L, 0L))
    }
    LaunchedEffect(downloadId) {
        val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
            as? android.app.DownloadManager ?: return@LaunchedEffect
        while (true) {
            val q = android.app.DownloadManager.Query().setFilterById(downloadId)
            val s = runCatching {
                dm.query(q).use { c ->
                    if (!c.moveToFirst()) return@runCatching null
                    val statusCol = c.getInt(
                        c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)
                    )
                    val downloaded = c.getLong(
                        c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = c.getLong(
                        c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val state = when (statusCol) {
                        android.app.DownloadManager.STATUS_PENDING -> DownloadStatus.State.Pending
                        android.app.DownloadManager.STATUS_RUNNING -> DownloadStatus.State.Running
                        android.app.DownloadManager.STATUS_PAUSED -> DownloadStatus.State.Paused
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.State.Successful
                        android.app.DownloadManager.STATUS_FAILED -> DownloadStatus.State.Failed
                        else -> DownloadStatus.State.Unknown
                    }
                    val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else null
                    DownloadStatus(state, progress, downloaded, total)
                }
            }.getOrNull()
            if (s != null) status = s
            if (s == null || !s.isInFlight) break
            kotlinx.coroutines.delay(2_000)
        }
    }
    return status
}
