package com.example.jellyfinplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.data.DownloadsStore.DownloadRecord

/**
 * Series-level view of downloaded content. Looks and feels like the regular
 * EpisodesScreen but uses DownloadRecord data exclusively — works fully
 * offline. Lists downloaded episodes grouped by season.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedSeriesScreen(
    vm: AppViewModel,
    seriesId: String,
    onBack: () -> Unit,
    onEpisodeClick: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val downloads = vm.downloads.collectAsState().value

    // Filter to this series only. If the user deletes the last episode of
    // a series, this list goes empty and we show an empty state — but the
    // grouped grid in My Downloads would also have removed the series card,
    // so this state is mostly transient.
    val episodes = remember(downloads, seriesId) {
        downloads.filter { it.seriesId == seriesId || it.seriesName == seriesId }
            // Only show records with usable file paths (in flight or done).
            .sortedWith(
                compareBy(
                    { it.seasonNumber ?: Int.MAX_VALUE },
                    { it.episodeNumber ?: Int.MAX_VALUE }
                )
            )
    }
    val seriesName = episodes.firstOrNull()?.seriesName ?: "Series"
    val seriesPoster = episodes.firstOrNull { it.seriesPosterUrl != null }?.seriesPosterUrl
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = remember(context) { coil.Coil.imageLoader(context) }

    LaunchedEffect(seriesPoster, episodes) {
        val urls = buildList {
            seriesPoster?.let(::add)
            episodes.take(12).forEach { it.posterUrl?.let(::add) }
        }.filter { it.isNotBlank() }.distinct()
        urls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(480, 720)
                    .crossfade(false)
                    .build()
            )
        }
    }

    val seriesPosterRequest = remember(seriesPoster, context) {
        seriesPoster?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(480, 720)
                .crossfade(false)
                .build()
        }
    }

    val seasonGroups = remember(episodes) {
        episodes.groupBy { it.seasonNumber ?: 0 }
            .toSortedMap()
    }

    Scaffold(
        containerColor = cs.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        seriesName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero — large series poster centered. Simpler than the
            // streaming version since we don't have backdrop art for
            // downloads, just the vertical poster.
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .aspectRatio(0.66f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.surfaceVariant)
                    ) {
                        if (seriesPosterRequest != null) {
                            AsyncImage(
                                model = seriesPosterRequest,
                                contentDescription = seriesName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            // Title + episode count summary.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        seriesName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${episodes.size} episode${if (episodes.size == 1) "" else "s"} downloaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            seasonGroups.forEach { (seasonNumber, eps) ->
                item(key = "season-$seasonNumber") {
                    Text(
                        if (seasonNumber == 0) "Specials"
                        else "Season $seasonNumber",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onBackground,
                        modifier = Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 8.dp
                        )
                    )
                }
                items(eps, key = { it.downloadId }) { ep ->
                    DownloadedEpisodeRow(
                        record = ep,
                        onClick = {
                            ep.posterUrl?.let { url ->
                                imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .size(720, 1080)
                                        .crossfade(false)
                                        .build()
                                )
                            }
                            onEpisodeClick(ep.downloadId)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun DownloadedEpisodeRow(
    record: DownloadRecord,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val posterRequest = remember(record.posterUrl, context) {
        record.posterUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(320, 180)
                .crossfade(false)
                .build()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(cs.surfaceVariant)
        ) {
            if (posterRequest != null) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Episode number prefix when available.
            val titleText = if (record.episodeNumber != null) {
                "${record.episodeNumber}. ${record.title}"
            } else {
                record.title
            }
            Text(
                titleText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!record.overview.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    record.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                downloadInfoLine(record),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
