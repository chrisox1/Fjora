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
import com.example.jellyfinplayer.BuildConfig
import com.example.jellyfinplayer.data.AppBackgroundColor
import com.example.jellyfinplayer.data.AppThemeColor
import com.example.jellyfinplayer.data.AuthStore
import com.example.jellyfinplayer.data.DiagnosticLog
import com.example.jellyfinplayer.data.HomeHeroSource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var showQualityDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showBackgroundColorDialog by remember { mutableStateOf(false) }
    var showSubtitleLanguageDialog by remember { mutableStateOf(false) }
    var showSubtitleColorDialog by remember { mutableStateOf(false) }
    var showHeroSourceDialog by remember { mutableStateOf(false) }
    var showDownloadLimitDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
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
            // Accounts section — each saved account is a row. Active one has
            // a check on the leading avatar; tap a non-active row to switch;
            // trash icon opens the delete confirmation sheet.
            SectionLabel("Accounts")
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    isActive = account.id == activeId,
                    onTap = { if (account.id != activeId) vm.switchAccount(account.id) },
                    onDelete = { pendingDelete = account }
                )
            }
            // "Add account" entry — looks like a row but with a + icon.
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

            SectionLabel("Appearance")
            ClickableRow(
                label = "Theme color",
                value = settings.appThemeColor.label,
                onClick = { showThemeColorDialog = true }
            )
            ClickableRow(
                label = "Background color",
                value = settings.appBackgroundColor.label,
                onClick = { showBackgroundColorDialog = true }
            )
            ClickableRow(
                label = "Home card",
                value = settings.homeHeroSource.label,
                onClick = { showHeroSourceDialog = true }
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel("Playback")
            ToggleRow(
                label = "Resume from last position",
                description = "When off, items always start from the beginning.",
                checked = settings.autoResume,
                onCheckedChange = { vm.setAutoResume(it) }
            )
            ToggleRow(
                label = "Show \"Next Up\" as a separate row",
                description = "When off, in-progress and next-up items merge " +
                    "into a single Continue Watching row.",
                checked = settings.showNextUpRow,
                onCheckedChange = { vm.setShowNextUpRow(it) }
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel("Subtitles")
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
            ClickableRow(
                label = "Subtitle color",
                value = subtitleColorLabel(settings.subtitleColor),
                onClick = { showSubtitleColorDialog = true }
            )
            SliderRow(
                label = "Subtitle size",
                valueLabel = "${(settings.subtitleTextScale * 100).roundToInt()}%",
                value = settings.subtitleTextScale,
                valueRange = 0.75f..1.4f,
                onValueChange = { vm.setSubtitleTextScale(it) }
            )
            SliderRow(
                label = "Subtitle sync",
                valueLabel = subtitleDelayLabel(settings.subtitleDelayMs),
                value = settings.subtitleDelayMs.toFloat(),
                valueRange = -5_000f..5_000f,
                onValueChange = { vm.setSubtitleDelayMs((it / 250f).roundToInt() * 250L) }
            )
            ToggleRow(
                label = "Subtitle background",
                description = "Add a dark backing behind subtitle text for bright scenes.",
                checked = settings.subtitleBackground,
                onCheckedChange = { vm.setSubtitleBackground(it) }
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel("Streaming")
            ClickableRow(
                label = "Default quality",
                value = formatBitrate(settings.defaultMaxBitrate),
                onClick = { showQualityDialog = true }
            )
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

            SectionLabel("Downloads")
            ClickableRow(
                label = "Storage limit",
                value = downloadLimitLabel(settings.downloadStorageLimitBytes),
                onClick = { showDownloadLimitDialog = true }
            )
            Text(
                "When the limit is reached, new downloads are blocked until space is freed.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(32.dp))

            SectionLabel("Diagnostics")
            ClickableRow(
                label = "Export diagnostic log",
                value = "Share",
                onClick = { exportDiagnostics(context) }
            )
            Text(
                "Includes device and playback diagnostics, not passwords or access tokens.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(32.dp))

            SectionLabel("About")
            ClickableRow(
                label = "Fjora",
                value = "Version ${BuildConfig.VERSION_NAME}",
                onClick = { showAboutDialog = true }
            )

            Spacer(Modifier.height(32.dp))

            // Destructive: sign out of every account at once. The single-
            // account "delete this account" lives on each account row above.
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

    if (showThemeColorDialog) {
        ThemeColorDialog(
            current = settings.appThemeColor,
            onSelect = {
                vm.setAppThemeColor(it)
                showThemeColorDialog = false
            },
            onDismiss = { showThemeColorDialog = false }
        )
    }

    if (showBackgroundColorDialog) {
        BackgroundColorDialog(
            current = settings.appBackgroundColor,
            onSelect = {
                vm.setAppBackgroundColor(it)
                showBackgroundColorDialog = false
            },
            onDismiss = { showBackgroundColorDialog = false }
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

    if (showSubtitleColorDialog) {
        SubtitleColorDialog(
            current = settings.subtitleColor,
            onSelect = {
                vm.setSubtitleColor(it)
                showSubtitleColorDialog = false
            },
            onDismiss = { showSubtitleColorDialog = false }
        )
    }

    if (showHeroSourceDialog) {
        HomeHeroSourceDialog(
            current = settings.homeHeroSource,
            onSelect = {
                vm.setHomeHeroSource(it)
                showHeroSourceDialog = false
            },
            onDismiss = { showHeroSourceDialog = false }
        )
    }

    if (showDownloadLimitDialog) {
        DownloadLimitDialog(
            current = settings.downloadStorageLimitBytes,
            onSelect = {
                vm.setDownloadStorageLimitBytes(it)
                showDownloadLimitDialog = false
            },
            onDismiss = { showDownloadLimitDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onOpenGithub = { openGithub(context) },
            onDismiss = { showAboutDialog = false }
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
        // Avatar block — fixed-size circle with two layered visuals:
        //   1. The user's actual Jellyfin profile picture (AsyncImage). On
        //      404 (most users haven't set one), the fallback slot renders
        //      a Person icon on a tinted background.
        //   2. A small Check badge overlaid in the bottom-right when this is
        //      the active account, so the user can still see at a glance
        //      which account they're signed in as without losing the avatar.
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
                        // Show the Person icon while loading and on error
                        // (404 = user hasn't set an avatar — common case).
                        error = androidx.compose.ui.graphics.painter.ColorPainter(
                            cs.surfaceVariant
                        )
                    )
                    // The Person icon is layered UNDER the AsyncImage so it
                    // shows during load and on error when the image fails.
                    // We only show it when the AsyncImage hasn't drawn over
                    // it; the simplest way is to just always render it and
                    // let the image cover it when present.
                }
                // Always render the fallback icon — gets covered by the image
                // when the image succeeds, visible during load and on 404.
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                // Re-render the AsyncImage on top so a successful load covers
                // the Person icon. (Coil 2.x doesn't cleanly support a
                // "loading-or-error" composable slot in a single AsyncImage,
                // so we stack: Person icon first, AsyncImage second.)
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
            // Active-account badge — small primary-tinted check in the
            // bottom-right corner of the avatar circle.
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

@Composable
private fun ThemeColorDialog(
    current: AppThemeColor,
    onSelect: (AppThemeColor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme color", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                AppThemeColor.entries.forEach { color ->
                    val selected = color == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(color) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(themeColorSwatch(color))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            color.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(selected = selected, onClick = { onSelect(color) })
                    }
                }
            }
        }
    )
}

private fun themeColorSwatch(color: AppThemeColor): androidx.compose.ui.graphics.Color =
    when (color) {
        AppThemeColor.FJORA -> androidx.compose.ui.graphics.Color(0xFFBF5820)
        AppThemeColor.PURPLE -> androidx.compose.ui.graphics.Color(0xFF7A5AC8)
        AppThemeColor.TEAL -> androidx.compose.ui.graphics.Color(0xFF3D9A93)
        AppThemeColor.BLUE -> androidx.compose.ui.graphics.Color(0xFF587DAE)
        AppThemeColor.ROSE -> androidx.compose.ui.graphics.Color(0xFFA84F66)
        AppThemeColor.GREEN -> androidx.compose.ui.graphics.Color(0xFF7C8F56)
    }

@Composable
private fun BackgroundColorDialog(
    current: AppBackgroundColor,
    onSelect: (AppBackgroundColor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Background color", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                AppBackgroundColor.entries.forEach { color ->
                    val selected = color == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(color) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(backgroundColorSwatch(color))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            color.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(selected = selected, onClick = { onSelect(color) })
                    }
                }
            }
        }
    )
}

private fun backgroundColorSwatch(color: AppBackgroundColor): androidx.compose.ui.graphics.Color =
    when (color) {
        AppBackgroundColor.FJORA -> androidx.compose.ui.graphics.Color(0xFF05060D)
        AppBackgroundColor.CHARCOAL -> androidx.compose.ui.graphics.Color(0xFF070809)
        AppBackgroundColor.MIDNIGHT -> androidx.compose.ui.graphics.Color(0xFF030812)
        AppBackgroundColor.AUBERGINE -> androidx.compose.ui.graphics.Color(0xFF0C0610)
        AppBackgroundColor.FOREST -> androidx.compose.ui.graphics.Color(0xFF050A07)
        AppBackgroundColor.ESPRESSO -> androidx.compose.ui.graphics.Color(0xFF0B0705)
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

private fun subtitleDelayLabel(delayMs: Long): String = when {
    delayMs == 0L -> "0 ms"
    delayMs > 0L -> "+${delayMs} ms"
    else -> "${delayMs} ms"
}

@Composable
private fun HomeHeroSourceDialog(
    current: HomeHeroSource,
    onSelect: (HomeHeroSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home card", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                Text(
                    "If the selected source has nothing to show, Fjora uses a featured item.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HomeHeroSource.entries.forEach { source ->
                    val selected = source == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(source) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(source) })
                        Spacer(Modifier.width(8.dp))
                        Text(source.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )
}

private fun exportDiagnostics(ctx: android.content.Context) {
    runCatching {
        val file = DiagnosticLog.exportFile(ctx)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(android.content.Intent.createChooser(intent, "Export diagnostics"))
    }.onFailure {
        android.widget.Toast.makeText(
            ctx,
            "Couldn't export diagnostics: ${it.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun AboutDialog(
    onOpenGithub: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Fjora", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "A third-party Jellyfin player. Fjora is not affiliated with Jellyfin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onOpenGithub,
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text("github.com/chrisox1/Fjora", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

private fun openGithub(ctx: android.content.Context) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://github.com/chrisox1/Fjora")
    )
    runCatching { ctx.startActivity(intent) }
}

private val downloadLimitOptions = listOf(
    null to "No limit",
    2L * 1024 * 1024 * 1024 to "2 GB",
    5L * 1024 * 1024 * 1024 to "5 GB",
    10L * 1024 * 1024 * 1024 to "10 GB",
    25L * 1024 * 1024 * 1024 to "25 GB",
    50L * 1024 * 1024 * 1024 to "50 GB"
)

private fun downloadLimitLabel(bytes: Long?): String =
    downloadLimitOptions.firstOrNull { it.first == bytes }?.second ?: "Custom"

@Composable
private fun DownloadLimitDialog(
    current: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download storage limit", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                downloadLimitOptions.forEach { (value, label) ->
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

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SubtitleColorDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle color", fontWeight = FontWeight.SemiBold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                subtitleColorOptions.forEach { (value, label) ->
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
