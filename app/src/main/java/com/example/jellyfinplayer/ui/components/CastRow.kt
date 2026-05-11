package com.example.jellyfinplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.jellyfinplayer.api.Person

/**
 * Horizontally-scrollable cast & crew strip. Used on movie / episode detail
 * screens AND on the series overview (Episodes screen).
 *
 * Returns immediately with no UI when the people list is empty — callers can
 * just drop this in unconditionally and the surrounding layout adapts.
 *
 * Actors lead the list (most users care about who's IN it). Directors and
 * writers follow. Capped at 20 entries so a film with 50 minor production
 * roles doesn't drown out the headline cast.
 */
@Composable
fun CastRow(vm: AppViewModel, people: List<Person>) {
    CastRow(vm = vm, people = people, onPersonClick = {})
}

@Composable
fun CastRow(
    vm: AppViewModel,
    people: List<Person>,
    onPersonClick: (Person) -> Unit
) {
    if (people.isEmpty()) return
    val ordered = remember(people) {
        // Defense in depth — small failures here would crash the whole
        // surrounding screen, so we apply every guard we can think of:
        //   1) Drop entries with blank id or name (some Jellyfin servers
        //      return placeholder rows for missing metadata).
        //   2) DEDUPE BY ID. Jellyfin lists the same person multiple times
        //      when they fit multiple roles ("Actor" AND "Producer", etc.).
        //      Compose's keyed `items` throws IllegalArgumentException on
        //      duplicate keys — that was the real cause of "less-known
        //      series crash on open" reports for series like Anthony
        //      Bourdain or Jul på Vesterbro: more obscure metadata catalogs
        //      have more duplicate-role entries.
        //   3) Strict bucket ordering with the deduped set. If a person is
        //      listed BOTH as Actor AND Director, the deduped first
        //      occurrence wins, which is fine.
        //   4) Cap to 20. Wide casts are a frequent OOM/perf footgun in
        //      Compose — and visually we don't want the row to scroll for
        //      a minute.
        val valid = people.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        val deduped = valid.distinctBy { it.id }
        val actors = deduped.filter { it.type == "Actor" || it.type == "GuestStar" }
        val crew = deduped.filter { it.type == "Director" || it.type == "Writer" }
        (actors + crew).distinctBy { it.id }.take(20)
    }
    if (ordered.isEmpty()) return
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Cast & crew",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${ordered.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Use a synthetic key (id + index) as a final defensive layer
            // so even if dedupe somehow misses an edge case, the LazyRow
            // can never crash on duplicate keys. The index suffix makes
            // every key unique by construction.
            itemsIndexed(ordered, key = { i, p -> "${p.id}#$i" }) { _, person ->
                PersonCard(vm = vm, person = person, onClick = { onPersonClick(person) })
            }
        }
    }
}

@Composable
private fun PersonCard(vm: AppViewModel, person: Person, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val url = vm.personImageUrl(person)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            person.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        val secondary = when {
            person.type == "Actor" && !person.role.isNullOrBlank() -> person.role
            person.type != "Actor" -> person.type
            else -> null
        }
        if (secondary != null) {
            Text(
                secondary,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
