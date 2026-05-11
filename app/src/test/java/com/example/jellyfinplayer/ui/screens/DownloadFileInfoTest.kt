package com.example.jellyfinplayer.ui.screens

import com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileInfoTest {
    @Test
    fun originalDownloadIsLabeledOriginal() {
        assertEquals("Original", downloadQualityLabel(record(isOriginal = true)))
    }

    @Test
    fun cappedDownloadShowsRequestedBitrate() {
        assertEquals(
            "Requested 1 Mbps",
            downloadQualityLabel(record(isOriginal = false, maxBitrate = 1_000_000L))
        )
    }

    @Test
    fun downloadInfoIncludesFormatSizeAndQuality() {
        val info = downloadInfoLine(
            record(
                isOriginal = false,
                maxBitrate = 3_000_000L,
                filePath = "movie.mp4",
                sizeBytes = 420L * 1024L * 1024L
            )
        )

        assertTrue(info.contains("MP4"))
        assertTrue(info.contains("420 MB"))
        assertTrue(info.contains("Requested 3 Mbps"))
    }

    private fun record(
        isOriginal: Boolean,
        maxBitrate: Long? = null,
        filePath: String? = null,
        sizeBytes: Long = -1L
    ): DownloadRecord =
        DownloadRecord(
            itemId = "item",
            downloadId = 1L,
            title = "Movie",
            filePath = filePath,
            sizeBytes = sizeBytes,
            isOriginalQuality = isOriginal,
            maxBitrate = maxBitrate
        )
}
