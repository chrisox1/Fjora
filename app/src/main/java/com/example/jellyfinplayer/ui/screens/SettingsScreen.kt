package com.example.jellyfinplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.data.AuthStore
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSignOutAll: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val settings = vm.settings.collectAsState().value
    val accounts = vm.accounts.collectAsState().value
    val activeId = vm.activeAccountId.collectAsState().value
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSubtitleLanguageDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AuthStore.AccountRecord?>(null) }
    var showSignOutAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .formContentWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionLabel("Accounts")
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    isActive = account.id == activeId,
                    onTap = { if (account.id != activeId) vm.switchAccount(account.id) },
                    onDelete = { pendingDelete = account }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddAccount)
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = cs.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Add account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel("Playback")
            ClickableRow(
                label = "Default quality",
                value = formatBitrate(settings.defaultMaxBitrate),
                onClick = { showQualityDialog = true }
            )
            ToggleRow(
                label = "Resume from last position",
                description = "When off, items always start from the beginning.",
                checked = settings.autoResume,
                onCheckedChange = { vm.setAutoResume(it) }
            )
            ToggleRow(
                label = "Always play subtitles",
                description = "Automatically enable a subtitle track when one is available.",
                checked = settings.alwaysPlaySubtitles,
                onCheckedChange = { vm.setAlwaysPlaySubtitles(it) }
            )
            if (settings.alwaysPlaySubtitles) {
                ClickableRow(
                    label = "Preferred subtitle language",
                    value = subtitleLanguageLabel(settings.preferredSubtitleLanguage),
                    onClick = { showSubtitleLanguageDialog = true }
                )
            }
            ToggleRow(
                label = "Always transcode",
                description = "Use server-side transcoding for every video. Fixes " +
                    "seek/scrub on files that don't seek cleanly via direct play. " +
                    "Costs more server CPU.",
                checked = settings.forceTranscoding && !settings.directPlayOnly,
                enabled = !settings.directPlayOnly,
                onCheckedChange = { if (!settings.directPlayOnly) vm.setForceTranscoding(it) }
            )
            ToggleRow(
                label = "Direct play only",
                description = "Never request direct stream or transcode. This forces " +
                    "mpv for all playback and may fail on files the server cannot " +
                    "serve as-is.",
                checked = settings.directPlayOnly,
                onCheckedChange = { vm.setDirectPlayOnly(it) }
            )
            ToggleRow(
                label = "Show \"Next Up\" as a separate row",
                description = "When off, in-progress and next-up items merge " +
                    "into a single Continue Watching row.",
                checked = settings.showNextUpRow,
                onCheckedChange = { vm.setShowNextUpRow(it) }
            )
            ToggleRow(
                label = "Use mpv for downloads",
                description = "Play downloaded files with the bundled mpv " +
                    "engine instead of the built-in player. mpv handles a " +
                    "wider range of file formats — particularly MKVs that " +
                    "the built-in player can't seek. Streaming still uses " +
                    "the built-in player.",
                checked = settings.useMpvForLocal || settings.directPlayOnly,
                enabled = !settings.directPlayOnly,
                onCheckedChange = { if (!settings.directPlayOnly) vm.setUseMpvForLocal(it) }
            )
            ToggleRow(
                label = "Use mpv for all playback",
                description = "Controlled by Direct play only. When direct play " +
                    "is enabled, streaming and downloads use mpv.",
                checked = settings.directPlayOnly,
                enabled = false,
                onCheckedChange = {}
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { showSignOutAllConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.error.copy(alpha = 0.15f),
                    contentColor = cs.error
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out of all accounts", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showQualityDialog) {
        QualityDialog(
            current = settings.defaultMaxBitrate,
            onSelect = {
                vm.setDefaultBitrate(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showSubtitleLanguageDialog) {
        SubtitleLanguageDialog(
            current = settings.preferredSubtitleLanguage,
            onSelect = {
                vm.setPreferredSubtitleLanguage(it)
                showSubtitleLanguageDialog = false
            },
            onDismiss = { showSubtitleLanguageDialog = false }
        )
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove account?") },
            text = {
                Text(
                    "Remove ${account.userName.ifBlank { "this account" }} from this device? " +
                        "Your watch history on the server is not affected."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAccount(account.id)
                    pendingDelete = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showSignOutAllConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutAllConfirm = false },
            title = { Text("Sign out of all accounts?") },
            text = {
                Text("All ${accounts.size} saved account(s) will be removed from this device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutAllConfirm = false
                    onSignOutAll()
                }) {
                    Text("Sign out all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutAllConfirm = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun AccountRow(
    account: AuthStore.AccountRecord,
    isActive: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val avatarUrl = account.avatarUrl()
                if (avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(avatarUrl)
                            .crossfade(200)
                            .build(),
                        contentDescription = "${account.userName} profile picture",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(
                            cs.surfaceVariant
                        )
                    )
                }
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(avatarUrl)
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (isActive) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(cs.primary)
                        .border(
                            width = 2.dp,
                            color = cs.background,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Active",
                        tint = cs.onPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.userName.ifBlank { "(unnamed user)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                account.server,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove account",
                tint = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun ClickableRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun QualityDialog(current: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    val options: List<Pair<Long?, String>> = listOf(
        null to "Original (no cap, prefers direct play)",
        20_000_000L to "1080p (~20 Mbps)",
        8_000_000L to "720p (~8 Mbps)",
        3_000_000L to "480p (~3 Mbps)",
        1_000_000L to "360p (~1 Mbps)"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default quality", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                Text(
                    "Used as a fallback target when direct play fails. " +
                        "On the first attempt the player always tries direct " +
                        "play at the source's native bitrate, regardless of " +
                        "this setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                options.forEach { (value, label) ->
                    val selected = value == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )
}

private fun formatBitrate(b: Long?): String = when (b) {
    null -> "Original"
    20_000_000L -> "1080p"
    8_000_000L -> "720p"
    3_000_000L -> "480p"
    1_000_000L -> "360p"
    else -> "${b / 1_000_000} Mbps"
}

private val subtitleLanguageOptions = listOf(
    null to "Any available",
    "eng" to "English",
    "dan" to "Danish",
    "nor" to "Norwegian",
    "swe" to "Swedish",
    "fin" to "Finnish",
    "deu" to "German",
    "fra" to "French",
    "spa" to "Spanish",
    "ita" to "Italian",
    "nld" to "Dutch",
    "por" to "Portuguese",
    "pol" to "Polish",
    "tur" to "Turkish",
    "jpn" to "Japanese",
    "kor" to "Korean",
    "zho" to "Chinese"
)

private fun subtitleLanguageLabel(code: String?): String =
    subtitleLanguageOptions.firstOrNull { it.first == code }?.second ?: "Any available"

@Composable
private fun SubtitleLanguageDialog(
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred subtitle language", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                subtitleLanguageOptions.forEach { (value, label) ->
                    val selected = value == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )
}
