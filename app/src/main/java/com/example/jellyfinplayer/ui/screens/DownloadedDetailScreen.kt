package com.example.jellyfinplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.Person
import com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord
import com.example.jellyfinplayer.ui.components.CastRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedDetailScreen(
    vm: AppViewModel,
    downloadId: Long,
    onBack: () -> Unit,
    onPlay: (DownloadRecord) -> Unit,
    onDelete: (DownloadRecord) -> Unit,
    onPersonClick: (Person) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val downloads = vm.downloads.collectAsState().value
    val record = remember(downloads, downloadId) {
        downloads.firstOrNull { it.downloadId == downloadId }
    }

    if (record == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var serverItem by remember { mutableStateOf<MediaItem?>(null) }
    var loadingDetails by remember { mutableStateOf(true) }
    LaunchedEffect(record.itemId) {
        loadingDetails = true
        runCatching { vm.loadItemDetails(record.itemId) }
            .onSuccess { serverItem = it }
        loadingDetails = false
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete download",
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val heroItem = serverItem ?: buildFallbackItem(record)
            Hero(vm = vm, item = heroItem, topPadding = padding.calculateTopPadding())

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

            Row(
                modifier = Modifier
                    .tabletContentWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlay(record) },
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
                        "Play",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                    )
                }
                OutlinedButton(
                    onClick = { openWithExternalApp(context, record) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        "Open with…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val overview = serverItem?.overview ?: record.overview
            if (!overview.isNullOrBlank()) {
                Text(
                    overview,
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

            val genres = serverItem?.genres ?: emptyList()
            if (genres.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .tabletContentWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                        .animateContentSize()
                ) {
                    genres.take(4).forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = { Text(genre, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            DetailGrid(serverItem ?: buildFallbackItem(record))

            val people = serverItem?.people ?: emptyList()
            if (people.isNotEmpty()) {
                CastRow(vm = vm, people = people, onPersonClick = onPersonClick)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete download?") },
            text = {
                Text(
                    "${record.title} will be removed from your device. " +
                        "You can re-download it any time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(record)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Build a minimal MediaItem from a DownloadRecord so the Hero and DetailGrid
 * composables have something to render when the server is unreachable.
 * The image tags and mediaSources will be empty, so the hero falls back to the
 * surface-tinted placeholder and the detail grid shows nothing — which is
 * fine for a purely offline context.
 */
private fun buildFallbackItem(record: DownloadRecord): MediaItem {
    val type = if (record.seriesName != null) "Episode" else "Movie"
    val ticks = record.runtimeMinutes?.let { it.toLong() * 600_000_000L }
    return MediaItem(
        id = record.itemId,
        name = record.title,
        type = type,
        productionYear = record.productionYear,
        overview = record.overview,
        runTimeTicks = ticks,
        seriesName = record.seriesName,
        seriesId = record.seriesId,
        episodeNumber = record.episodeNumber,
        seasonNumber = record.seasonNumber
    )
}

/**
 * Hand the downloaded file off to whatever video app the user has installed.
 */
private fun openWithExternalApp(
    ctx: android.content.Context,
    record: DownloadRecord
) {
    val path = record.filePath
    if (path == null) {
        android.widget.Toast.makeText(
            ctx,
            "This download isn't ready yet",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        return
    }
    val file = java.io.File(path)
    if (!file.exists()) {
        android.widget.Toast.makeText(
            ctx,
            "Downloaded file is missing",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        return
    }
    try {
        val authority = "${ctx.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, authority, file)
        val mime = when {
            path.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            path.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            else -> "video/*"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "Open with…")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    } catch (t: Throwable) {
        android.widget.Toast.makeText(
            ctx,
            "Couldn't open the file: ${t.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
