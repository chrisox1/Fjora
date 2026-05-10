package com.example.jellyfinplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.api.MediaItem
import com.example.jellyfinplayer.api.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    vm: AppViewModel,
    person: Person,
    onBack: () -> Unit,
    onItemClick: (MediaItem) -> Unit
) {
    var personItems by remember(person.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember(person.id) { mutableStateOf(true) }
    var error by remember(person.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(person.id) {
        loading = true
        error = null
        runCatching { vm.loadItemsByPerson(person) }
            .onSuccess { personItems = it }
            .onFailure { error = it.message ?: "Could not load titles for this person." }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 118.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                PersonHeader(vm = vm, person = person)
            }

            when {
                loading -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                personItems.isEmpty() -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No movies or shows with ${person.name} were found in your library.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                else -> items(personItems, key = { it.id }) { item ->
                    PersonTitleCard(vm = vm, item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(vm: AppViewModel, person: Person) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val url = vm.personImageUrl(person, maxHeight = 420)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            person.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        val subtitle = listOfNotNull(
            person.type.takeIf { it.isNotBlank() },
            person.role?.takeIf { it.isNotBlank() }
        ).joinToString(" - ")
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PersonTitleCard(vm: AppViewModel, item: MediaItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val poster = vm.posterUrl(item)
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    item.name.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(item.productionYear?.toString(), item.type).joinToString(" - ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
