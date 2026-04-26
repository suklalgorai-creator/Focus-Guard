package com.focusguard.app.ui.screens

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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.domain.InstalledAppInfo
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors

private data class FocusedSurfaceOption(
    val surfaceId: String,
    val title: String,
    val subtitle: String,
    val packageName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = FocusGuardApp.instance.prefs
    val packageManager = context.packageManager

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var blacklistedSet by remember { mutableStateOf(prefs.blacklistedApps.toMutableSet()) }
    var blockedSurfaces by remember { mutableStateOf(prefs.blockedContentSurfaces.toMutableSet()) }

    val isLocked = prefs.isGuardActiveNow()

    val installedApps = remember {
        packageManager.getInstalledApplications(0)
            .filter { app ->
                packageManager.getLaunchIntentForPackage(app.packageName) != null &&
                    app.packageName != "com.focusguard.app"
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
    }

    val installedPackageNames = remember(installedApps) {
        installedApps.map { it.packageName }.toSet()
    }

    val suggestedPackages = remember {
        setOf(
            "com.instagram.android",
            "com.instagram.lite",
            "com.google.android.youtube",
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

        if (instagramPackage == null) {
            emptyList()
        } else {
            listOf(
                FocusedSurfaceOption(
                    surfaceId = FocusGuardPrefs.SURFACE_INSTAGRAM_REELS,
                    title = "Instagram Reels",
                    subtitle = "Keep Instagram usable, but kick users out of Reels during focus hours.",
                    packageName = instagramPackage
                )
            )
        }
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
        filteredApps.filter { it.packageName !in suggestedPackages }
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
                        title = "Focused Blocks",
                        subtitle = "Target one distracting surface without blocking the whole app."
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
                        Text("Search apps", color = FrictionColors.TextMuted)
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
                        title = "Quick Picks",
                        subtitle = "Common distraction apps you may want to lock first."
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
                    title = if (searchQuery.isBlank()) "All Apps" else "Results",
                    subtitle = if (searchQuery.isBlank()) {
                        "Blocked apps stay pinned at the top so you can audit them quickly."
                    } else {
                        "Matching apps for \"$searchQuery\"."
                    }
                )
            }

            if (otherApps.isEmpty()) {
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
                text = "$blockedAppCount apps blocked  |  $focusedBlockCount focused blocks active",
                color = FrictionColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isLocked) {
                    "Focus hours are active. You can add more blockers, but you cannot remove the ones already on."
                } else {
                    "Use full app blocks for the worst apps and focused blocks when only one tab needs a wall."
                },
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
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
                    text = option.subtitle,
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (wholeAppBlocked) {
                    Text(
                        text = "Whole app is already blacklisted, so this acts as a backup.",
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
                    text = app.packageName,
                    color = FrictionColors.TextMuted,
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

    val question = currentQuestion!!

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
