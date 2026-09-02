package com.focusguard.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.domain.InstalledAppInfo
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FocusedSurfaceOption(
    val surfaceId: String,
    val title: String,
    val subtitle: String,
    val packageName: String,
    val wholeAppBlockedMessage: String = "Whole app is already blacklisted, so this acts as a backup."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = FocusGuardApp.instance.prefs
    val lifecycleOwner = LocalLifecycleOwner.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var blacklistedSet by remember { mutableStateOf(prefs.blacklistedApps.toMutableSet()) }
    var blockedSurfaces by remember { mutableStateOf(prefs.blockedContentSurfaces.toMutableSet()) }
    var productiveChannels by remember { mutableStateOf(prefs.youtubeProductiveChannels.toMutableSet()) }
    var distractingChannels by remember { mutableStateOf(prefs.youtubeDistractingChannels.toMutableSet()) }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var appRefreshSignal by remember { mutableStateOf(0) }

    val isLocked = prefs.isGuardActiveNow()

    LaunchedEffect(appContext, appRefreshSignal) {
        isLoadingApps = true
        installedApps = try {
            withContext(Dispatchers.IO) {
                loadInstalledApps(appContext)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        isLoadingApps = false
    }

    DisposableEffect(lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appRefreshSignal += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val installedPackageNames = remember(installedApps) {
        installedApps.map { it.packageName }.toSet()
    }

    val suggestedPackages = remember {
        setOf(
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.twitter.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca",
            "com.reddit.frontpage",
            "in.mohalla.sharechat",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.discord",
            "com.pinterest",
            "tv.twitch.android.app",
            "com.spotify.music",
            "com.netflix.mediaclient"
        )
    }

    val focusedSurfaceOptions = remember(installedPackageNames) {
        val instagramPackage = when {
            "com.instagram.android" in installedPackageNames -> "com.instagram.android"
            "com.instagram.lite" in installedPackageNames -> "com.instagram.lite"
            else -> null
        }

        buildList {
            if (instagramPackage != null) {
                add(
                    FocusedSurfaceOption(
                        surfaceId = FocusGuardPrefs.SURFACE_INSTAGRAM_REELS,
                        title = "Instagram Reels",
                        subtitle = "Instagram opens. Reels are blocked.",
                        packageName = instagramPackage,
                        wholeAppBlockedMessage = "Instagram is fully blocked. Turn this on for Reels-only mode."
                    )
                )
            }

            if ("com.google.android.youtube" in installedPackageNames) {
                add(
                    FocusedSurfaceOption(
                        surfaceId = FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS,
                        title = "YouTube Shorts",
                        subtitle = "YouTube opens. Shorts are blocked.",
                        packageName = "com.google.android.youtube",
                        wholeAppBlockedMessage = "YouTube is fully blocked. Unblock it to use channel rules."
                    )
                )
            }
        }
    }

    val surfaceManagedPackages = remember(focusedSurfaceOptions) {
        focusedSurfaceOptions.map { it.packageName }.toSet()
    }

    val filteredApps = remember(searchQuery, installedApps, blacklistedSet) {
        installedApps
            .filter { app ->
                searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { blacklistedSet.contains(it.packageName) }
                    .thenBy { it.appName.lowercase() }
            )
    }

    val suggestedApps = if (searchQuery.isBlank()) {
        filteredApps.filter { it.packageName in suggestedPackages }
    } else {
        emptyList()
    }

    val otherApps = if (searchQuery.isBlank()) {
        filteredApps.filter { app ->
            app.packageName !in suggestedPackages &&
                app.packageName !in surfaceManagedPackages
        }
    } else {
        filteredApps
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Distraction Blocks", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = FrictionColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FrictionColors.Background,
                    titleContentColor = FrictionColors.TextPrimary,
                    navigationIconContentColor = FrictionColors.TextPrimary
                )
            )
        },
        containerColor = FrictionColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BlacklistOverviewCard(
                    blockedAppCount = blacklistedSet.size,
                    focusedBlockCount = blockedSurfaces.size,
                    isLocked = isLocked
                )
            }

            if (focusedSurfaceOptions.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Surface Blocks",
                        subtitle = "Block only Reels or Shorts."
                    )
                }

                items(focusedSurfaceOptions, key = { "${it.packageName}:${it.surfaceId}" }) { option ->
                    val isEnabled = blockedSurfaces.contains(option.surfaceId)
                    FocusedSurfaceToggleItem(
                        option = option,
                        isEnabled = isEnabled,
                        wholeAppBlocked = blacklistedSet.contains(option.packageName),
                        isLocked = isLocked,
                        onToggle = { enabled ->
                            if (isLocked && !enabled) return@FocusedSurfaceToggleItem

                            blockedSurfaces = blockedSurfaces.toMutableSet().apply {
                                if (enabled) add(option.surfaceId) else remove(option.surfaceId)
                            }
                            prefs.blockedContentSurfaces = blockedSurfaces

                            if (enabled && option.surfaceId == FocusGuardPrefs.SURFACE_INSTAGRAM_REELS) {
                                blacklistedSet = blacklistedSet.toMutableSet().apply {
                                    remove(FocusGuardPrefs.INSTAGRAM_PACKAGE)
                                    remove(FocusGuardPrefs.INSTAGRAM_LITE_PACKAGE)
                                }
                                prefs.blacklistedApps = blacklistedSet
                            }
                        }
                    )
                }
            }

            if ("com.google.android.youtube" in installedPackageNames) {
                item {
                    StudyYoutubeModeCard(
                        productiveChannels = productiveChannels,
                        distractingChannels = distractingChannels,
                        shortsShieldEnabled = blockedSurfaces.contains(FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS),
                        wholeAppBlocked = blacklistedSet.contains("com.google.android.youtube"),
                        isLocked = isLocked,
                        onUpdateProductive = { updated ->
                            productiveChannels = updated.toMutableSet()
                            prefs.youtubeProductiveChannels = productiveChannels
                        },
                        onUpdateDistracting = { updated ->
                            distractingChannels = updated.toMutableSet()
                            prefs.youtubeDistractingChannels = distractingChannels
                        }
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Search apps for full block", color = FrictionColors.TextMuted)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = FrictionColors.TextMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrictionColors.TextPrimary,
                        unfocusedTextColor = FrictionColors.TextPrimary,
                        focusedBorderColor = FrictionColors.Accent,
                        unfocusedBorderColor = FrictionColors.CardBorder,
                        focusedContainerColor = FrictionColors.CardBackground,
                        unfocusedContainerColor = FrictionColors.CardBackground
                    )
                )
            }

            if (isLocked) {
                item {
                    PYQLockedCard()
                }
            }

            if (suggestedApps.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Full App Quick Picks",
                        subtitle = "These apps are blocked as soon as they open."
                    )
                }

                items(suggestedApps, key = { it.packageName }) { app ->
                    AppToggleItem(
                        app = app,
                        isBlacklisted = blacklistedSet.contains(app.packageName),
                        highlightColor = FrictionColors.Warning,
                        isLocked = isLocked,
                        onToggle = { enabled ->
                            if (isLocked && !enabled) return@AppToggleItem

                            blacklistedSet = blacklistedSet.toMutableSet().apply {
                                if (enabled) add(app.packageName) else remove(app.packageName)
                            }
                            prefs.blacklistedApps = blacklistedSet
                        }
                    )
                }
            }

            item {
                SectionTitle(
                    title = if (searchQuery.isBlank()) "All Full App Blocks" else "Full-Block Results",
                    subtitle = if (searchQuery.isBlank()) "Pick any app to block." else "Results for \"$searchQuery\"."
                )
            }

            if (isLoadingApps && installedApps.isEmpty()) {
                item {
                    AppsLoadingState()
                }
            } else if (otherApps.isEmpty()) {
                item {
                    EmptySearchState(query = searchQuery)
                }
            } else {
                items(otherApps, key = { it.packageName }) { app ->
                    AppToggleItem(
                        app = app,
                        isBlacklisted = blacklistedSet.contains(app.packageName),
                        highlightColor = FrictionColors.Accent,
                        isLocked = isLocked,
                        onToggle = { enabled ->
                            if (isLocked && !enabled) return@AppToggleItem

                            blacklistedSet = blacklistedSet.toMutableSet().apply {
                                if (enabled) add(app.packageName) else remove(app.packageName)
                            }
                            prefs.blacklistedApps = blacklistedSet
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlacklistOverviewCard(
    blockedAppCount: Int,
    focusedBlockCount: Int,
    isLocked: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Distractions stay behind friction.",
                color = FrictionColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$blockedAppCount full apps  |  $focusedBlockCount surface blocks",
                color = FrictionColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isLocked) {
                    "Focus is active. You can add blockers, but not remove them."
                } else {
                    "Use surface blocks for Reels or Shorts. Use full app blocks for everything else."
                },
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun BlockModeGuideCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BlockModeRow(
                title = "Surface-only",
                subtitle = "Instagram opens, Reels tab is blocked.",
                accentColor = FrictionColors.Success
            )
            Divider(color = FrictionColors.CardBorder)
            BlockModeRow(
                title = "Full app",
                subtitle = "The selected app is blocked on launch.",
                accentColor = FrictionColors.Accent
            )
            Divider(color = FrictionColors.CardBorder)
            BlockModeRow(
                title = "Daily limit",
                subtitle = "Time budgets can plug in here later.",
                accentColor = FrictionColors.Warning
            )
        }
    }
}

@Composable
private fun BlockModeRow(
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 34.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accentColor)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = FrictionColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Text(
            text = subtitle,
            color = FrictionColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun FocusedSurfaceToggleItem(
    option: FocusedSurfaceOption,
    isEnabled: Boolean,
    wholeAppBlocked: Boolean,
    isLocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val rowEnabled = !isLocked || !isEnabled

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = if (isEnabled) FrictionColors.AccentMuted else FrictionColors.GlassBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = rowEnabled) { onToggle(!isEnabled) }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = option.title,
                    color = if (isEnabled) FrictionColors.Accent else FrictionColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (isEnabled) {
                        "${option.subtitle} Active now."
                    } else {
                        option.subtitle
                    },
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (wholeAppBlocked) {
                    Text(
                        text = option.wholeAppBlockedMessage,
                        color = FrictionColors.Warning,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = { if (rowEnabled || it) onToggle(it) },
                enabled = rowEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FrictionColors.Accent,
                    uncheckedThumbColor = FrictionColors.TextMuted,
                    uncheckedTrackColor = FrictionColors.SurfaceElevated,
                    disabledCheckedThumbColor = FrictionColors.TextSecondary,
                    disabledCheckedTrackColor = FrictionColors.Accent.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun StudyYoutubeModeCard(
    productiveChannels: Set<String>,
    distractingChannels: Set<String>,
    shortsShieldEnabled: Boolean,
    wholeAppBlocked: Boolean,
    isLocked: Boolean,
    onUpdateProductive: (Set<String>) -> Unit,
    onUpdateDistracting: (Set<String>) -> Unit
) {
    var productiveDraft by rememberSaveable { mutableStateOf("") }
    var distractingDraft by rememberSaveable { mutableStateOf("") }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Study YouTube Mode",
                    color = FrictionColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Block Shorts. Allow selected study channels.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            if (!shortsShieldEnabled) {
                Text(
                    text = "Turn on YouTube Shorts above to activate these channel rules.",
                    color = FrictionColors.Warning,
                    fontSize = 12.sp
                )
            }

            if (wholeAppBlocked) {
                Text(
                    text = "YouTube is fully blocked. Switch to Shorts-only to use channel rules.",
                    color = FrictionColors.Warning,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            ChannelRuleSection(
                title = "Productive Channels",
                subtitle = "Allowed channels for study videos.",
                channels = productiveChannels,
                accentColor = FrictionColors.Success,
                draftValue = productiveDraft,
                addLabel = "Add productive",
                isLocked = isLocked,
                onDraftChange = { productiveDraft = it },
                onAdd = {
                    val updated = productiveChannels
                        .plus(productiveDraft.trim())
                        .filter { it.isNotBlank() }
                        .toCollection(linkedSetOf())
                    onUpdateProductive(updated)
                    productiveDraft = ""
                },
                onRemove = { channel ->
                    onUpdateProductive(productiveChannels.filterNot { it == channel }.toCollection(linkedSetOf()))
                }
            )

            ChannelRuleSection(
                title = "Distracting Channels",
                subtitle = "Channels to block even when YouTube is allowed.",
                channels = distractingChannels,
                accentColor = FrictionColors.Accent,
                draftValue = distractingDraft,
                addLabel = "Add distracting",
                isLocked = isLocked,
                onDraftChange = { distractingDraft = it },
                onAdd = {
                    val updated = distractingChannels
                        .plus(distractingDraft.trim())
                        .filter { it.isNotBlank() }
                        .toCollection(linkedSetOf())
                    onUpdateDistracting(updated)
                    distractingDraft = ""
                },
                onRemove = { channel ->
                    onUpdateDistracting(distractingChannels.filterNot { it == channel }.toCollection(linkedSetOf()))
                }
            )
        }
    }
}

@Composable
private fun ChannelRuleSection(
    title: String,
    subtitle: String,
    channels: Set<String>,
    accentColor: Color,
    draftValue: String,
    addLabel: String,
    isLocked: Boolean,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        if (channels.isEmpty()) {
            Text(
                text = "No channels added yet.",
                color = FrictionColors.TextMuted,
                fontSize = 12.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                channels.sortedBy { it.lowercase() }.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FrictionColors.SurfaceElevated.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel,
                            color = FrictionColors.TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onRemove(channel) },
                            enabled = !isLocked
                        ) {
                            Text(
                                text = "Remove",
                                color = if (isLocked) FrictionColors.TextMuted else accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draftValue,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = !isLocked,
                placeholder = {
                    Text("Channel name or @handle", color = FrictionColors.TextMuted)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FrictionColors.TextPrimary,
                    unfocusedTextColor = FrictionColors.TextPrimary,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = FrictionColors.CardBorder,
                    focusedContainerColor = FrictionColors.CardBackground,
                    unfocusedContainerColor = FrictionColors.CardBackground,
                    disabledTextColor = FrictionColors.TextMuted,
                    disabledBorderColor = FrictionColors.CardBorder,
                    disabledContainerColor = FrictionColors.CardBackground
                )
            )
            Button(
                onClick = onAdd,
                enabled = !isLocked && draftValue.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(text = addLabel)
            }
        }
    }
}

@Composable
private fun AppToggleItem(
    app: InstalledAppInfo,
    isBlacklisted: Boolean,
    highlightColor: Color,
    isLocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val rowEnabled = !isLocked || !isBlacklisted

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = if (isBlacklisted) FrictionColors.AccentMuted else FrictionColors.GlassBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = rowEnabled) { onToggle(!isBlacklisted) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } ?: Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FrictionColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.appName.take(1),
                    color = FrictionColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.appName,
                    color = if (isBlacklisted) highlightColor else FrictionColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isBlacklisted) {
                        "Full app block active  |  ${app.packageName}"
                    } else {
                        "Full app block  |  ${app.packageName}"
                    },
                    color = if (isBlacklisted) highlightColor else FrictionColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = isBlacklisted,
                onCheckedChange = { if (rowEnabled || it) onToggle(it) },
                enabled = rowEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FrictionColors.Accent,
                    uncheckedThumbColor = FrictionColors.TextMuted,
                    uncheckedTrackColor = FrictionColors.SurfaceElevated,
                    disabledCheckedThumbColor = FrictionColors.TextSecondary,
                    disabledCheckedTrackColor = FrictionColors.Accent.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "No matching apps",
                color = FrictionColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = if (query.isBlank()) {
                    "Install a few apps and they will show up here."
                } else {
                    "Try a different app name or package."
                },
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AppsLoadingState() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Loading installed apps",
                color = FrictionColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = "Scanning apps in the background so this screen stays responsive.",
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun PYQLockedCard() {
    var currentQuestion by remember { mutableStateOf(QuestionRepository.getRandomQuestion()) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var showExplanation by remember { mutableStateOf(false) }

    if (currentQuestion == null) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = FrictionColors.WarningSoft)
        ) {
            Text(
                text = "Blacklist changes are locked during focus hours.",
                color = FrictionColors.Warning,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    currentQuestion?.let { question ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = FrictionColors.CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Locked right now",
                    color = FrictionColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Focus hours are active. Here is a question instead.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp
                )

            Divider(color = FrictionColors.CardBorder)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(FrictionColors.SecondarySoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${question.subject} | ${question.year}",
                    color = FrictionColors.Secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = question.question,
                color = FrictionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp
            )

            question.options.forEach { (key, value) ->
                val isSelected = selectedOption == key
                val isCorrectAnswer = showExplanation && key == question.answer
                val isWrongAnswer = showExplanation && isSelected && key != question.answer

                val backgroundColor = when {
                    isCorrectAnswer -> FrictionColors.SuccessSoft
                    isWrongAnswer -> FrictionColors.ErrorSoft
                    isSelected -> FrictionColors.SurfaceElevated
                    else -> FrictionColors.Surface
                }
                val textColor = when {
                    isCorrectAnswer -> FrictionColors.Success
                    isWrongAnswer -> FrictionColors.Accent
                    else -> FrictionColors.TextPrimary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable(enabled = !showExplanation) {
                            selectedOption = key
                            showExplanation = true
                        }
                        .padding(12.dp)
                ) {
                    Text(
                        text = "$key.",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = value,
                        color = textColor,
                        fontSize = 14.sp
                    )
                }
            }

            if (showExplanation) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = FrictionColors.Surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (selectedOption == question.answer) "Correct" else "Incorrect",
                            color = if (selectedOption == question.answer) {
                                FrictionColors.Success
                            } else {
                                FrictionColors.Accent
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = question.explanation,
                            color = FrictionColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        currentQuestion = QuestionRepository.getRandomQuestion()
                        selectedOption = null
                        showExplanation = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.SurfaceElevated
                    )
                ) {
                    Text(
                        text = "Next Question",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
}

private fun loadInstalledApps(context: android.content.Context): List<InstalledAppInfo> {
    val packageManager = context.packageManager

    val installedApplications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
    }

    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launcherPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    }.mapTo(linkedSetOf()) { it.activityInfo.packageName }

    return installedApplications
        .asSequence()
        .filter { app -> app.packageName != context.packageName }
        .filter { app ->
            launcherPackages.contains(app.packageName) ||
                packageManager.getLaunchIntentForPackage(app.packageName) != null
        }
        .map { app ->
            InstalledAppInfo(
                packageName = app.packageName,
                appName = packageManager.getApplicationLabel(app).toString(),
                icon = try {
                    packageManager.getApplicationIcon(app.packageName)
                } catch (_: Exception) {
                    null
                }
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.appName.lowercase() }
        .toList()
}
