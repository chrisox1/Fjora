package com.example.jellyfinplayer.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.Person
import com.example.jellyfinplayer.ui.components.CastRow
import com.example.jellyfinplayer.ui.components.rememberDownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    vm: AppViewModel,
    item: MediaItem,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onPersonClick: (Person) -> Unit = {}
) {
    var details by remember { mutableStateOf(item) }
    var loadingDetails by remember { mutableStateOf(true) }
    LaunchedEffect(item.id) {
        loadingDetails = true
        runCatching { vm.loadItemDetails(item.id) }
            .onSuccess { details = it }
        loadingDetails = false
    }

    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Hero(vm, details, topPadding = padding.calculateTopPadding())

            AnimatedVisibility(
                visible = loadingDetails,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            val started = (details.userData?.playbackPositionTicks ?: 0L) > 0L
            var showDownloadDialog by remember { mutableStateOf(false) }

            val downloads = vm.downloads.collectAsState().value
            val existingDownload = remember(downloads, details.id) {
                downloads.firstOrNull { it.itemId == details.id }
            }
            val isDownloadable = details.type == "Movie" || details.type == "Episode"
            val downloadStatus = if (isDownloadable && existingDownload != null) {
                rememberDownloadStatus(existingDownload.downloadId)
            } else null

            Row(
                modifier = Modifier
                    .tabletContentWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlay(details) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (started) "Resume" else "Play",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                    )
                }
                if (isDownloadable) {
                    val isComplete = downloadStatus?.isComplete == true
                    val isInFlight = downloadStatus?.isInFlight == true
                    OutlinedButton(
                        onClick = {
                            if (!isInFlight && !isComplete) showDownloadDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.Check
                                else com.example.jellyfinplayer.ui.icons.DownloadIconVector,
                            contentDescription = if (isComplete) "Downloaded" else "Download",
                            tint = if (isComplete) cs.primary else LocalContentColor.current
                        )
                    }
                }
            }

            if (downloadStatus?.isInFlight == true) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    if (downloadStatus.progress != null) {
                        LinearProgressIndicator(
                            progress = downloadStatus.progress,
                            color = cs.primary,
                            trackColor = cs.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = cs.primary,
                            trackColor = cs.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val statusLine = when {
                        downloadStatus.progress == null -> "Preparing download…"
                        downloadStatus.totalBytes <= 0L ->
                            "${(downloadStatus.progress * 100).toInt()}%"
                        else -> "${(downloadStatus.progress * 100).toInt()}% - " +
                            "${formatBytes(downloadStatus.bytesDownloaded)} of " +
                            formatBytes(downloadStatus.totalBytes)
                    }
                    Text(
                        statusLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            if (showDownloadDialog) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                DownloadQualityDialog(
                    onDismiss = { showDownloadDialog = false },
                    onConfirm = { maxBitrate ->
                        showDownloadDialog = false
                        startDownload(
                            ctx = ctx,
                            vm = vm,
                            item = details,
                            url = vm.downloadUrl(details, maxBitrate),
                            isOriginal = maxBitrate == null,
                            maxBitrate = maxBitrate
                        )
                    }
                )
            }

            if (!details.overview.isNullOrBlank()) {
                Text(
                    details.overview!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier
                        .tabletContentWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .animateContentSize()
                )
            } else {
                Spacer(Modifier.height(20.dp))
            }

            if (details.genres.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .tabletContentWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                        .animateContentSize()
                ) {
                    details.genres.take(4).forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = { Text(genre, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            DetailGrid(details)

            CastRow(vm = vm, people = details.people, onPersonClick = onPersonClick)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun Hero(vm: AppViewModel, item: MediaItem, topPadding: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(cs.surfaceVariant)
    ) {
        val backdrop = vm.backdropUrl(item, maxWidth = 1280)
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(cs.surfaceVariant))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.4f to Color.Transparent,
                        0.85f to cs.background.copy(alpha = 0.6f),
                        1f to cs.background
                    )
                )
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val posterUrl = vm.posterUrl(item, maxHeight = 480)
            if (posterUrl != null) {
                Box(
                    Modifier
                        .width(96.dp)
                        .height(144.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surfaceVariant)
                ) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                val isEpisode = item.type == "Episode"
                if (isEpisode) {
                    val context = buildList {
                        item.seriesName?.takeIf { it.isNotBlank() }?.let { add(it) }
                        item.seasonNumber?.let { s ->
                            item.episodeNumber?.let { e -> add("S$s · E$e") }
                                ?: add("Season $s")
                        }
                    }.joinToString(" - ")
                    if (context.isNotEmpty()) {
                        Text(
                            context,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    item.productionYear?.let { add(it.toString()) }
                    item.runtimeMinutes?.let { add("${it} min") }
                    item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                    item.communityRating?.let { add("Rating ${"%.1f".format(it)}") }
                }.joinToString(" - ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailGrid(item: MediaItem) {
    val cs = MaterialTheme.colorScheme
    val source = item.mediaSources.maxByOrNull { it.mediaStreams.size }
    val videoStream = source?.mediaStreams?.firstOrNull { it.type == "Video" }
    val audioStreams = source?.mediaStreams?.filter { it.type == "Audio" } ?: emptyList()
    val subCount = source?.mediaStreams?.count { it.type == "Subtitle" } ?: 0

    val w = videoStream?.width ?: 0
    val h = videoStream?.height ?: 0
    val resolution = when {
        w >= 3840 || h >= 2160 -> "4K"
        w >= 2560 || h >= 1440 -> "1440p"
        w >= 1920 || h >= 1080 -> "1080p"
        w >= 1280 || h >= 720 -> "720p"
        w >= 854 || h >= 480 -> "480p"
        h > 0 -> "${h}p"
        else -> null
    }
    val videoCodec = videoStream?.codec?.let {
        when (it.lowercase()) {
            "hevc", "h265" -> "HEVC"
            "h264", "avc" -> "H.264"
            "av1" -> "AV1"
            "vp9" -> "VP9"
            "mpeg4" -> "MPEG-4"
            else -> it.uppercase()
        }
    }
    val container = source?.container?.uppercase()

    val primaryAudioLabel = audioStreams.firstOrNull()?.codec?.let {
        when (it.lowercase()) {
            "ac3" -> "Dolby Digital"
            "eac3" -> "Dolby Digital+"
            "truehd" -> "Dolby TrueHD"
            "dts", "dca" -> "DTS"
            "aac" -> "AAC"
            "flac" -> "FLAC"
            "mp3" -> "MP3"
            "opus" -> "Opus"
            else -> it.uppercase()
        }
    }
    val audioLabel = buildString {
        if (primaryAudioLabel != null) append(primaryAudioLabel)
        if (audioStreams.size > 1) {
            if (isNotEmpty()) append(" · ")
            append("${audioStreams.size} tracks")
        }
    }

    val director = item.people.firstOrNull { it.type == "Director" }?.name

    val badges = listOfNotNull(resolution, videoCodec, container)
    val infoRows = buildList {
        if (audioLabel.isNotEmpty()) add("Audio" to audioLabel)
        if (subCount > 0) add("Subtitles" to "$subCount ${if (subCount == 1) "track" else "tracks"}")
        director?.let { add("Director" to it) }
    }

    if (badges.isEmpty() && infoRows.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .tabletContentWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cs.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (badges.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = if (infoRows.isEmpty()) 0.dp else 14.dp)
                    ) {
                        badges.forEach { badge ->
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = cs.outline.copy(alpha = 0.35f),
                            ) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                infoRows.forEachIndexed { index, (label, value) ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = cs.outline.copy(alpha = 0.15f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.width(78.dp)
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DownloadQualityDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val options: List<Pair<Long?, String>> = listOf(
        null to "Original quality (recommended)",
        20_000_000L to "1080p (~20 Mbps)",
        8_000_000L to "720p (~8 Mbps)",
        3_000_000L to "480p (~3 Mbps)",
        1_000_000L to "360p (~1 Mbps)"
    )
    var selected by remember { mutableStateOf<Long?>(null) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val destinationPath = remember(ctx) {
        val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
        dir?.absolutePath
            ?.replace("/storage/emulated/0", "Internal storage")
            ?: "App's private storage"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download", fontWeight = FontWeight.SemiBold) },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Download", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column {
                Text(
                    "Original downloads stream the file as-is. Other " +
                        "qualities require the server to transcode, which " +
                        "uses significant server CPU and may take a long " +
                        "time on large files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { selected = value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Saves to: $destinationPath",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Files are removed if you uninstall the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

internal fun startDownload(
    ctx: android.content.Context,
    vm: AppViewModel,
    item: MediaItem,
    url: String,
    isOriginal: Boolean,
    maxBitrate: Long?
) {
    try {
        val safeName = item.name.replace(Regex("""[^A-Za-z0-9._\- ]"""), "_")
            .ifBlank { "download" }
        val ext = if (isOriginal) ".mkv" else ".mp4"
        val fileName = "${safeName}_${item.id.takeLast(6)}$ext"

        val safeSeries = item.seriesName?.replace(Regex("""[^A-Za-z0-9._\- ]"""), "_")
            ?.trim()
            ?.ifBlank { null }
        val subPath = if (item.type == "Episode" && safeSeries != null) {
            "${safeSeries}/${fileName}"
        } else {
            fileName
        }

        val moviesDir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
        if (subPath != fileName && moviesDir != null) {
            java.io.File(moviesDir, safeSeries!!).mkdirs()
        }

        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle(item.name)
            .setDescription(
                if (isOriginal) "Downloading original quality"
                else "Downloading transcoded copy (server is re-encoding)"
            )
            .setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                ctx,
                android.os.Environment.DIRECTORY_MOVIES,
                subPath
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
            as android.app.DownloadManager
        val downloadId = dm.enqueue(request)

        // Download subtitle tracks alongside the video. Skip image-based
        // codecs (PGS/VOBSUB) — Jellyfin can't serve those as text.
        val subtitlePaths = mutableListOf<String>()
        val mediaSource = item.mediaSources.firstOrNull()
        if (mediaSource != null) {
            val imageCodecs = setOf(
                "pgssub", "pgs", "hdmv_pgs_subtitle",
                "dvdsub", "dvd_subtitle", "xsub", "dvbsub"
            )
            val subtitleStreams = mediaSource.mediaStreams.filter { stream ->
                stream.type == "Subtitle" &&
                    stream.codec?.lowercase()?.trim() !in imageCodecs
            }
            subtitleStreams.forEach { stream ->
                val langTag = stream.language?.take(3)?.ifBlank { null }
                    ?: "sub${stream.index}"
                val subFileName = "${safeName}_${item.id.takeLast(6)}_$langTag.srt"
                val subSubPath = if (item.type == "Episode" && safeSeries != null) {
                    "${safeSeries}/${subFileName}"
                } else {
                    subFileName
                }
                val subUrl = vm.subtitleUrl(
                    itemId = item.id,
                    mediaSourceId = mediaSource.id,
                    streamIndex = stream.index,
                    format = "srt"
                )
                val subRequest = android.app.DownloadManager.Request(
                    android.net.Uri.parse(subUrl)
                )
                    .setTitle("${item.name} — subtitles ($langTag)")
                    .setDescription("Subtitle track")
                    .setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(
                        ctx,
                        android.os.Environment.DIRECTORY_MOVIES,
                        subSubPath
                    )
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                dm.enqueue(subRequest)
                moviesDir?.let { subtitlePaths.add(java.io.File(it, subSubPath).absolutePath) }
            }
        }

        // Build the absolute path the file will land at — DownloadManager
        // doesn't tell us up front, but we know the dir we asked for and
        // the file name we set.
        val targetPath = moviesDir?.let { java.io.File(it, subPath).absolutePath }
        val expectedSize = runCatching {
            dm.query(android.app.DownloadManager.Query().setFilterById(downloadId)).use { c ->
                if (c.moveToFirst()) {
                    c.getLong(
                        c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                } else {
                    -1L
                }
            }
        }.getOrDefault(-1L)

        // Compose a friendly subtitle for episodes: "Series · S2E5"
        val seasonEpisodeLabel = if (item.type == "Episode") {
            val s = item.seasonNumber
            val e = item.episodeNumber
            if (s != null && e != null) "S${s}E${"%02d".format(e)}" else null
        } else null

        vm.registerDownload(
            com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord(
                itemId = item.id,
                downloadId = downloadId,
                title = item.name,
                seriesName = item.seriesName,
                seriesId = item.seriesId,
                seasonEpisodeLabel = seasonEpisodeLabel,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                posterUrl = vm.posterUrl(item, maxHeight = 480),
                seriesPosterUrl = if (item.type == "Episode")
                    vm.seriesPosterUrl(item, maxHeight = 480) else null,
                overview = item.overview,
                productionYear = item.productionYear,
                runtimeMinutes = item.runtimeMinutes,
                filePath = targetPath,
                sizeBytes = expectedSize,
                isOriginalQuality = isOriginal,
                maxBitrate = maxBitrate,
                subtitlePaths = subtitlePaths
            )
        )

        android.widget.Toast.makeText(
            ctx,
            if (isOriginal) "Downloading ${item.name}"
            else "Downloading ${item.name} (transcoding may take a while)",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (t: Throwable) {
        android.widget.Toast.makeText(
            ctx,
            "Couldn't start download: ${t.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Format a byte count for human display: "1.2 GB", "145 MB", "23 KB", "456 B".
 * Uses 1024-based units for consistency with Android system displays.
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.0f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
