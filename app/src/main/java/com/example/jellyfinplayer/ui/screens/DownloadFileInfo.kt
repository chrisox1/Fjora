package com.example.jellyfinplayer.ui.screens

import com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord
import com.example.jellyfinplayer.ui.components.DownloadStatus
import java.io.File

internal fun downloadInfoLine(
    record: DownloadRecord,
    status: DownloadStatus? = null,
    includeQuality: Boolean = true
): String {
    val parts = buildList {
        add(detectDownloadFormat(record))
        val size = downloadSizeBytes(record, status)
        if (size > 0L) add(formatBytes(size))
        if (includeQuality) add(downloadQualityLabel(record))
    }
    return parts.joinToString(" · ")
}

internal fun downloadQualityLabel(record: DownloadRecord): String {
    return when {
        record.isOriginalQuality -> "Original"
        record.maxBitrate != null -> "Requested ${record.maxBitrate / 1_000_000} Mbps"
        else -> "Transcoded"
    }
}

private fun downloadSizeBytes(record: DownloadRecord, status: DownloadStatus?): Long {
    val fileSize = record.filePath
        ?.let { runCatching { File(it).length() }.getOrNull() }
        ?.takeIf { it > 0L }
    if (fileSize != null) return fileSize
    if (record.sizeBytes > 0L) return record.sizeBytes
    if (status != null && status.totalBytes > 0L) return status.totalBytes
    return -1L
}

private fun detectDownloadFormat(record: DownloadRecord): String {
    val path = record.filePath
    if (path != null) {
        val file = File(path)
        val detected = runCatching {
            if (file.exists() && file.length() > 0L) detectContainerFromFile(file) else null
        }.getOrNull()
        if (detected != null) return detected
    }
    return when {
        path?.endsWith(".mp4", ignoreCase = true) == true -> "MP4"
        path?.endsWith(".mkv", ignoreCase = true) == true -> "MKV"
        path?.endsWith(".ts", ignoreCase = true) == true -> "TS"
        path?.endsWith(".avi", ignoreCase = true) == true -> "AVI"
        record.isOriginalQuality -> "Original file"
        else -> "MP4 requested"
    }
}

private fun detectContainerFromFile(file: File): String? {
    val header = ByteArray(512)
    val read = file.inputStream().use { it.read(header) }
    if (read < 4) return null

    if (header[0] == 0x1A.toByte() &&
        header[1] == 0x45.toByte() &&
        header[2] == 0xDF.toByte() &&
        header[3] == 0xA3.toByte()
    ) {
        return "MKV"
    }

    if (read >= 12 &&
        header[4] == 'f'.code.toByte() &&
        header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() &&
        header[7] == 'p'.code.toByte()
    ) {
        return "MP4"
    }

    if (read >= 12 &&
        header[0] == 'R'.code.toByte() &&
        header[1] == 'I'.code.toByte() &&
        header[2] == 'F'.code.toByte() &&
        header[3] == 'F'.code.toByte() &&
        header[8] == 'A'.code.toByte() &&
        header[9] == 'V'.code.toByte() &&
        header[10] == 'I'.code.toByte()
    ) {
        return "AVI"
    }

    val looksLikeTs = header[0] == 0x47.toByte() &&
        (read <= 188 || header[188] == 0x47.toByte()) &&
        (read <= 376 || header[376] == 0x47.toByte())
    if (looksLikeTs) return "TS"

    return null
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.0f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
