@file:OptIn(ExperimentalMaterial3Api::class)

package com.focusguard.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focusguard.app.domain.AppUsageData
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.service.GuardForegroundService
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.PulsingShield
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.presentation.auth.AuthViewModel
import com.focusguard.app.presentation.focus.FocusSessionViewModel
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.presentation.pyq.PyqViewModel
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.auth.LoginScreen
import com.focusguard.app.ui.components.PremiumBottomNavigation
import com.focusguard.app.ui.components.PremiumDrawerContent
import com.focusguard.app.ui.components.PremiumNavItem
import com.focusguard.app.ui.screens.BlacklistScreen
import com.focusguard.app.ui.screens.FocusScreen
import com.focusguard.app.ui.screens.ProfileSettingsScreen
import com.focusguard.app.ui.screens.PyqSolveScreen
import com.focusguard.app.ui.screens.UsageStatsScreen
import com.focusguard.app.ui.screens.ScheduleScreen
import com.focusguard.app.ui.screens.DashboardScreen
import com.focusguard.app.ui.onboarding.OnboardingScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as FocusGuardApp
            var darkTheme by remember { mutableStateOf(app.prefs.isDarkThemeEnabled) }
            FocusGuardTheme(darkTheme = darkTheme) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.settingsRepository)
                )
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.factory(app.profileRepository)
                )
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModel.factory(app.authRepository)
                )
                val focusSettings by settingsViewModel.settingsFlow.collectAsStateWithLifecycle()
                val profile by profileViewModel.profileFlow.collectAsStateWithLifecycle()
                val authState by authViewModel.state.collectAsStateWithLifecycle()
                val disclosureAccepted = focusSettings.hasAcceptedAccessibilityDisclosure

                if (!profile.isOnboardingComplete) {
                    OnboardingScreen(
                        profileViewModel = profileViewModel,
                        onComplete = {}
                    )
                } else if (authState.isInitializing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FrictionColors.Background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = FrictionColors.Accent)
                    }
                } else if (authState.shouldShowLogin) {
                    LoginScreen(
                        state = authState,
                        onGoogleSignIn = {
                            authViewModel.signIn(this@MainActivity)
                        },
                        onEmailSignIn = { email, password ->
                            authViewModel.signInWithEmail(email, password)
                        },
                        onEmailSignUp = { name, email, password ->
                            authViewModel.signUpWithEmail(email, password, name)
                        },
                        onContinueWithoutAccount = {
                            authViewModel.continueWithoutAccount()
                        },
                        onClearError = {
                            authViewModel.clearError()
                        }
                    )
                } else if (!disclosureAccepted) {
                    AccessibilityDisclosureScreen(
                        onAccept = {
                            settingsViewModel.setDisclosureAccepted(true) {
                                startGuardService()
                            }
                        }
                    )
                } else {
                    val usageStatsViewModel: UsageStatsViewModel = viewModel(
                        factory = UsageStatsViewModel.factory(
                            usageRepository = app.usageRepository,
                            usageStatsReconciler = app.usageStatsReconciler
                        )
                    )
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route ?: BottomDestination.Home.route
                    val startDestination = remember {
                        resolveStartDestination(intent)
                    }
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val drawerScope = rememberCoroutineScope()
                    val drawerDestinations = remember {
                        listOf(
                            BottomDestination.Home,
                            BottomDestination.Focus,
                            BottomDestination.Pyq,
                            BottomDestination.Blocks,
                            BottomDestination.Schedule,
                            BottomDestination.Stats,
                            BottomDestination.Profile
                        ).map { it.toPremiumNavItem() }
                    }
                    val navigateTo: (String) -> Unit = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            PremiumDrawerContent(
                                items = drawerDestinations,
                                currentRoute = currentRoute,
                                onItemClick = { item ->
                                    navigateTo(item.route)
                                    drawerScope.launch { drawerState.close() }
                                },
                                onClose = {
                                    drawerScope.launch { drawerState.close() }
                                }
                            )
                        }
                    ) {
                        Scaffold(
                            containerColor = FrictionColors.Background,
                            bottomBar = {
                                PremiumBottomNavigation(
                                    items = bottomDestinations.map { it.toPremiumNavItem() },
                                    currentRoute = currentRoute,
                                    onItemClick = { item -> navigateTo(item.route) }
                                )
                            }
                        ) { padding ->
                            NavHost(
                                navController = navController,
                                startDestination = startDestination,
                                modifier = Modifier.padding(padding)
                            ) {
                                composable(BottomDestination.Home.route) {
                                    DashboardScreen(
                                        usageStatsViewModel = usageStatsViewModel,
                                        focusSettings = focusSettings,
                                        settingsViewModel = settingsViewModel,
                                        isDarkTheme = darkTheme,
                                        onToggleTheme = {
                                            darkTheme = !darkTheme
                                            app.prefs.isDarkThemeEnabled = darkTheme
                                        },
                                        onOpenMenu = {
                                            drawerScope.launch { drawerState.open() }
                                        },
                                        onNavigateBlacklist = {
                                            navigateTo(BottomDestination.Blocks.route)
                                        },
                                        onNavigateUsage = {
                                            navigateTo(BottomDestination.Stats.route)
                                        },
                                        onNavigateSchedule = {
                                            navigateTo(BottomDestination.Schedule.route)
                                        },
                                        onNavigatePyq = {
                                            navigateTo(BottomDestination.Pyq.route)
                                        },
                                        onNavigateProfile = {
                                            navigateTo(BottomDestination.Profile.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Focus.route) {
                                    val focusSessionViewModel: FocusSessionViewModel = viewModel(
                                        factory = FocusSessionViewModel.factory(app.focusSessionRepository)
                                    )
                                    FocusScreen(
                                        viewModel = focusSessionViewModel,
                                        onOpenPyq = {
                                            navigateTo(BottomDestination.Pyq.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Blocks.route) {
                                    BlacklistScreen(
                                        onBack = {
                                            navigateTo(BottomDestination.Home.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Stats.route) {
                                    UsageStatsScreen(
                                        usageStatsViewModel = usageStatsViewModel,
                                        onBack = {
                                            navigateTo(BottomDestination.Home.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Schedule.route) {
                                    ScheduleScreen(
                                        onBack = {
                                            navigateTo(BottomDestination.Home.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Pyq.route) {
                                    val pyqViewModel: PyqViewModel = viewModel(
                                        factory = PyqViewModel.factory(
                                            pyqRepository = app.pyqRepository,
                                            selectorRepository = app.pyqSelectorRepository,
                                            analyticsRepository = app.analyticsRepository,
                                            behaviorRepository = app.behaviorRepository
                                        )
                                    )
                                    PyqSolveScreen(
                                        viewModel = pyqViewModel,
                                        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE),
                                        onBack = {
                                            navigateTo(BottomDestination.Home.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Profile.route) {
                                    ProfileSettingsScreen(
                                        profileViewModel = profileViewModel,
                                        onBack = {
                                            navigateTo(BottomDestination.Home.route)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (FocusGuardApp.instance.prefs.hasAcceptedAccessibilityDisclosure) {
            startGuardService()
        }
    }

    override fun onPause() {
        FocusGuardApp.instance.trackingManager.flushCurrentSession()
        lifecycleScope.launch {
            FocusGuardApp.instance.authRepository.syncUserSettings()
        }
        super.onPause()
    }

    private fun startGuardService() {
        val intent = Intent(this, GuardForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun resolveStartDestination(intent: Intent?): String {
        val data = intent?.data
        return if (data?.scheme == "focusguard" && data.host == "pyq") {
            BottomDestination.Pyq.route
        } else {
            BottomDestination.Home.route
        }
    }
}

@Composable
fun FocusGuardTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    FrictionColors.useDarkPalette = darkTheme
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = FrictionColors.Accent,
            onPrimary = FrictionColors.TextOnAccent,
            surface = FrictionColors.Surface,
            onSurface = FrictionColors.TextPrimary,
            background = FrictionColors.Background,
            onBackground = FrictionColors.TextPrimary,
        )
    } else {
        lightColorScheme(
            primary = FrictionColors.Accent,
            onPrimary = FrictionColors.TextOnAccent,
            surface = FrictionColors.Surface,
            onSurface = FrictionColors.TextPrimary,
            background = FrictionColors.Background,
            onBackground = FrictionColors.TextPrimary,
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        content()
    }
}

@Composable
fun AccessibilityDisclosureScreen(onAccept: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Focus Guard",
                    color = FrictionColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Distraction kholne se pehle PYQ solve karo.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Accessibility disclosure",
                            color = FrictionColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        DisclosurePoint(
                            title = "Foreground app detection",
                            body = "Focus Guard uses Accessibility to detect when a blocked app or distracting surface is opened during focus time."
                        )
                        DisclosurePoint(
                            title = "Visible screen structure",
                            body = "For focused blocks, such as short-form video feeds, the app may inspect visible text, view IDs, and screen structure. This is processed on device only."
                        )
                        DisclosurePoint(
                            title = "No personal data collection",
                            body = "Focus Guard does not upload your screen content, messages, passwords, or personal data. Local usage and PYQ attempts are stored only for focus analytics."
                        )
                        DisclosurePoint(
                            title = "Your control",
                            body = "You can stop the service by disabling Accessibility. Extra Settings exit protection is only used when you explicitly enable it for Strict Mode."
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent
                    )
                ) {
                    Text(
                        text = "I understand and agree",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = FrictionColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = FrictionColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    companion object {
        val Home = BottomDestination("dashboard", "Home", Icons.Rounded.Home)
        val Focus = BottomDestination("focus", "Focus", Icons.Rounded.Timer)
        val Blocks = BottomDestination("blacklist", "Blocks", Icons.Rounded.Block)
        val Schedule = BottomDestination("schedule", "Schedule", Icons.Rounded.CalendarMonth)
        val Stats = BottomDestination("usage", "Stats", Icons.Rounded.BarChart)
        val Pyq = BottomDestination("pyq", "PYQ", Icons.Rounded.School)
        val Profile = BottomDestination("profile", "Profile", Icons.Rounded.Settings)
    }
}

private val bottomDestinations = listOf(
    BottomDestination.Home,
    BottomDestination.Focus,
    BottomDestination.Pyq,
    BottomDestination.Blocks,
    BottomDestination.Stats
)

private fun BottomDestination.toPremiumNavItem(): PremiumNavItem {
    return PremiumNavItem(route = route, label = label, icon = icon)
}

private const val EXTRA_BLOCKED_PACKAGE = "blocked_package"

@Composable
fun LegacyDashboardScreen(
    focusSettings: FocusSettings,
    settingsViewModel: SettingsViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateBlacklist: () -> Unit = {},
    onNavigateUsage: () -> Unit = {},
    onNavigateSchedule: () -> Unit = {},
    onNavigatePyq: () -> Unit = {},
    onNavigateProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = FocusGuardApp.instance
    val prefs = FocusGuardApp.instance.prefs
    val permissionMonitor = remember { PermissionMonitor(context) }

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(false) }
    var usageStatsEnabled by remember { mutableStateOf(false) }
    var batteryOptimized by remember { mutableStateOf(true) }

    var dailyAttempts by remember { mutableIntStateOf(0) }
    var totalBlocks by remember { mutableIntStateOf(0) }
    var totalGiveUps by remember { mutableIntStateOf(0) }
    var daysUntilExam by remember { mutableIntStateOf(-1) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var selectedExam by remember { mutableStateOf(prefs.targetExam) }
    var questionCount by remember { mutableIntStateOf(0) }
    var pyqsToday by remember { mutableIntStateOf(0) }
    var streakDays by remember { mutableIntStateOf(0) }
    var dailyGoal by remember { mutableIntStateOf(0) }
    var weakSubject by remember { mutableStateOf<String?>(null) }

    // Strict mode
    val isStrictMode = focusSettings.isStrictModeEnabled
    var blockDuration by remember { mutableFloatStateOf(focusSettings.strictModeDurationMinutes.toFloat()) }
    var exitProtectionEnabled by remember {
        mutableStateOf(focusSettings.isStrictModeExitProtectionEnabled)
    }

    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    val adminComponent = android.content.ComponentName(context, com.focusguard.app.antibypass.GuardAdminReceiver::class.java)

    var titleTapCount by remember { mutableIntStateOf(0) }
    var showMathPuzzle by remember { mutableStateOf(false) }
    var showStrictModeConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = permissionMonitor.isAccessibilityEnabled()
            overlayEnabled = permissionMonitor.isOverlayPermitted()
            usageStatsEnabled = permissionMonitor.isUsageStatsPermitted()
            batteryOptimized = !isIgnoringBatteryOptimizations(context)
            dailyAttempts = prefs.dailyAttemptCount
            totalBlocks = prefs.totalBlocksEver
            totalGiveUps = prefs.totalGiveUps
            daysUntilExam = prefs.getDaysUntilExam()
            isServiceRunning = GuardForegroundService.instance != null
            questionCount = QuestionRepository.getQuestionCount()
            pyqsToday = app.analyticsRepository.getTodayAttempts()
            streakDays = app.analyticsRepository.getStreak()
            dailyGoal = app.behaviorRepository.getDailyGoal()
            weakSubject = app.behaviorRepository.getBehaviorState().weakSubjects.firstOrNull()
            delay(2000)
        }
    }

    LaunchedEffect(focusSettings.strictModeDurationMinutes, isStrictMode) {
        if (!isStrictMode) {
            blockDuration = focusSettings.strictModeDurationMinutes.toFloat()
        }
    }

    LaunchedEffect(focusSettings.isStrictModeExitProtectionEnabled, isStrictMode) {
        if (!isStrictMode) {
            exitProtectionEnabled = focusSettings.isStrictModeExitProtectionEnabled
        }
    }

    val allPermissionsGranted = accessibilityEnabled && overlayEnabled && usageStatsEnabled
    val isActive = allPermissionsGranted && isServiceRunning

    fun enableStrictModeAfterConsent() {
        if (exitProtectionEnabled && !dpm.isAdminActive(adminComponent)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Optional Exit Protection for Strict Mode. Focus Guard uses this only after you opt in."
                )
            }
            context.startActivity(intent)
        }
        settingsViewModel.toggleStrictMode(
            enabled = true,
            durationMinutes = blockDuration.toInt(),
            exitProtectionEnabled = exitProtectionEnabled
        )
    }

    if (showMathPuzzle) {
        DeveloperBypassDialog(
            onDismiss = { showMathPuzzle = false },
            onSuccess = {
                showMathPuzzle = false
                settingsViewModel.toggleStrictMode(
                    enabled = false,
                    durationMinutes = blockDuration.toInt(),
                    exitProtectionEnabled = false
                )
                exitProtectionEnabled = false
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.removeActiveAdmin(adminComponent)
                }
            }
        )
    }

    if (showStrictModeConfirmation) {
        StrictModeConfirmationDialog(
            exitProtectionEnabled = exitProtectionEnabled,
            durationMinutes = blockDuration.toInt(),
            onDismiss = { showStrictModeConfirmation = false },
            onConfirm = {
                showStrictModeConfirmation = false
                enableStrictModeAfterConsent()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            item {
                Spacer(modifier = Modifier.height(22.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    backgroundColor = FrictionColors.GlassBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greetingText(),
                                fontSize = 13.sp,
                                color = FrictionColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "Focus Guard",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = FrictionColors.TextPrimary,
                                letterSpacing = 0.sp,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (prefs.isGuardActiveNow()) {
                                        titleTapCount = 0
                                        Toast.makeText(context, "Blocked during focus hours", Toast.LENGTH_SHORT).show()
                                    } else {
                                        titleTapCount++
                                        if (titleTapCount >= 7) {
                                            titleTapCount = 0
                                            showMathPuzzle = true
                                        }
                                    }
                                }
                            )
                            Text(
                                text = "Fast, focused, exam-ready.",
                                fontSize = 14.sp,
                                color = FrictionColors.TextSecondary,
                                letterSpacing = 0.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(FrictionColors.SurfaceElevated)
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                                    tint = FrictionColors.Accent
                                )
                            }
                            IconButton(
                                onClick = onNavigateProfile,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(FrictionColors.SurfaceElevated)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Open settings",
                                    tint = FrictionColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                HomeFocusSummary(
                    streakDays = streakDays,
                    pyqsToday = pyqsToday,
                    dailyGoal = dailyGoal,
                    weakSubject = weakSubject,
                    daysUntilExam = daysUntilExam,
                    onStartSolving = onNavigatePyq
                )
            }

            // ── Status Card ──
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    isActive = isActive,
                    backgroundColor = if (isActive) FrictionColors.SuccessSoft else FrictionColors.ErrorSoft
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PulsingShield(isActive = isActive)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isActive) "Protection Active" else "Protection Offline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) FrictionColors.Success else FrictionColors.Accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isActive) "All systems monitoring. Stay on track."
                                   else "Enable permissions below to activate.",
                            fontSize = 13.sp,
                            color = FrictionColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Today Stats ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = dailyAttempts.toString(),
                        label = "Blocked",
                        icon = Icons.Rounded.Block,
                        color = FrictionColors.Accent
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = totalGiveUps.toString(),
                        label = "Gave Up",
                        icon = Icons.Outlined.TrendingUp,
                        color = FrictionColors.Success
                    )
                    if (daysUntilExam > 0) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = daysUntilExam.toString(),
                            label = selectedExam.uppercase(),
                            icon = Icons.Rounded.CalendarMonth,
                            color = FrictionColors.Warning
                        )
                    }
                }
            }

            // ── Strict Mode Card ──
            item {
                SectionLabel("Focus Mode")
                Spacer(modifier = Modifier.height(4.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(FrictionColors.AccentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = FrictionColors.Accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Strict Mode",
                                    color = FrictionColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    if (isStrictMode) "Active until the timer ends"
                                    else "Locks distracting apps for a set duration",
                                    fontSize = 12.sp,
                                    color = FrictionColors.TextSecondary
                                )
                            }
                            Switch(
                                checked = isStrictMode,
                                onCheckedChange = { turnsOn ->
                                    if (turnsOn) {
                                        showStrictModeConfirmation = true
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = FrictionColors.Accent,
                                    uncheckedThumbColor = FrictionColors.TextMuted,
                                    uncheckedTrackColor = FrictionColors.SurfaceElevated
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = FrictionColors.CardBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Duration: ${blockDuration.toInt()} min",
                            color = FrictionColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = blockDuration,
                            onValueChange = { blockDuration = it },
                            onValueChangeFinished = {
                                settingsViewModel.setStrictModeDurationMinutes(blockDuration.toInt())
                            },
                            valueRange = 10f..180f,
                            steps = 17,
                            enabled = !isStrictMode,
                            colors = SliderDefaults.colors(
                                thumbColor = if (isStrictMode) FrictionColors.TextMuted else FrictionColors.Accent,
                                activeTrackColor = if (isStrictMode) FrictionColors.TextMuted else FrictionColors.Accent,
                                inactiveTrackColor = FrictionColors.SurfaceElevated
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = exitProtectionEnabled,
                                onCheckedChange = { enabled ->
                                    if (!isStrictMode) {
                                        exitProtectionEnabled = enabled
                                        settingsViewModel.setExitProtection(enabled)
                                    }
                                },
                                enabled = !isStrictMode,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = FrictionColors.Accent,
                                    uncheckedColor = FrictionColors.TextMuted,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Exit Protection",
                                    color = FrictionColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Optional. During Strict Mode only, Focus Guard can return you home if you open Settings or app-removal screens before the timer ends.",
                                    color = FrictionColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        if (exitProtectionEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FrictionColors.WarningSoft)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "By enabling Exit Protection, you agree that Settings and app-removal screens may be interrupted only while Strict Mode is active.",
                                    color = FrictionColors.Warning,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── Permissions ──
            item {
                SectionLabel("Permissions")
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.Accessibility,
                    title = "Accessibility",
                    subtitle = "Detects distracting apps",
                    isGranted = accessibilityEnabled
                ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.Layers,
                    title = "Display Overlay",
                    subtitle = "Shows blocking screen",
                    isGranted = overlayEnabled
                ) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.QueryStats,
                    title = "Usage Stats",
                    subtitle = "Tracks app usage time",
                    isGranted = usageStatsEnabled
                ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.BatteryAlert,
                    title = "Keep Alive",
                    subtitle = "Prevent system from killing app",
                    isGranted = !batteryOptimized
                ) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.Autorenew,
                    title = "Auto Start (OnePlus/Oppo/Xiaomi)",
                    subtitle = "Required to start after reboot",
                    isGranted = false // Hard to detect, always show as an action
                ) {
                    try {
                        // ColorOS / Auto Start
                        val intent = Intent().apply {
                            component = android.content.ComponentName(
                                "com.coloros.safecenter",
                                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            // OnePlus / OxygenOS Auto Start
                            val intent = Intent().apply {
                                component = android.content.ComponentName(
                                    "com.oneplus.security",
                                    "com.oneplus.security.chainlaunch.view.AllowAutoDetectActivity"
                                )
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            try {
                                val intent = Intent().apply {
                                    component = android.content.ComponentName(
                                        "com.coloros.safecenter",
                                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e3: Exception) {
                                Toast.makeText(context, "Please find Auto Launch in your Phone Manager", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            // ── Quick Actions ──
            item {
                SectionLabel("Quick Actions")
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                ActionRow(
                    icon = Icons.Outlined.Schedule,
                    title = "Block Schedule",
                    subtitle = if (prefs.isScheduleEnabled) {
                        if (prefs.isWithinSchedule()) "Active now" else "Enabled"
                    } else "Not configured",
                    accentColor = FrictionColors.Warning,
                    onClick = onNavigateSchedule
                )
            }

            item {
                ActionRow(
                    icon = Icons.Outlined.AppBlocking,
                    title = "Manage Blacklist",
                    subtitle = "${prefs.blacklistedApps.size} apps blocked",
                    accentColor = FrictionColors.Accent,
                    onClick = onNavigateBlacklist
                )
            }

            item {
                ActionRow(
                    icon = Icons.Outlined.BarChart,
                    title = "Usage Analytics",
                    subtitle = "See where your time goes",
                    accentColor = FrictionColors.Success,
                    onClick = onNavigateUsage
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ── Reusable Components ──

@Composable
fun SectionLabel(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = FrictionColors.TextMuted,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
fun HomeFocusSummary(
    streakDays: Int,
    pyqsToday: Int,
    dailyGoal: Int,
    weakSubject: String?,
    daysUntilExam: Int,
    onStartSolving: () -> Unit
) {
    val goal = dailyGoal.coerceAtLeast(1)
    val progress = (pyqsToday.toFloat() / goal.toFloat()).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassMetricCard(
                modifier = Modifier.weight(1f),
                label = "Streak",
                value = "${streakDays}d",
                subtitle = if (streakDays > 0) "keep it alive" else "start today",
                color = FrictionColors.Warning
            )
            GlassMetricCard(
                modifier = Modifier.weight(1f),
                label = "Exam",
                value = if (daysUntilExam >= 0) daysUntilExam.toString() else "-",
                subtitle = if (daysUntilExam >= 0) "days left" else "set target",
                color = FrictionColors.Accent
            )
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily goal",
                            color = FrictionColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$pyqsToday / $dailyGoal PYQs",
                            color = FrictionColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = onStartSolving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FrictionColors.Accent,
                            contentColor = FrictionColors.TextOnAccent
                        )
                    ) {
                        Text("Start Solving", fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = FrictionColors.Accent,
                    trackColor = FrictionColors.SurfaceElevated
                )
                Text(
                    text = weakSubject?.let { "$it needs attention." } ?: "No weak subject detected yet.",
                    color = if (weakSubject == null) FrictionColors.TextMuted else FrictionColors.Warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun GlassMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String,
    color: Color
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = color,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = FrictionColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isGranted) FrictionColors.SuccessSoft
                        else FrictionColors.WarningSoft
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isGranted) FrictionColors.Success else FrictionColors.Warning,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = FrictionColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = FrictionColors.TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isGranted) FrictionColors.SuccessSoft
                        else FrictionColors.WarningSoft
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isGranted) "Active" else "Required",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGranted) FrictionColors.Success else FrictionColors.Warning
                )
            }
        }
    }
}

@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = FrictionColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = FrictionColors.TextSecondary
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = FrictionColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = FrictionColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

fun getAppLabel(packageName: String): String {
    return when (packageName) {
        "com.instagram.android" -> "Instagram"
        "com.instagram.lite" -> "Instagram Lite"
        "com.google.android.youtube" -> "YouTube"
        "com.zhiliaoapp.musically" -> "TikTok"
        "com.snapchat.android" -> "Snapchat"
        else -> packageName
    }
}

fun greetingText(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late session"
    }
}

@Composable
fun StrictModeConfirmationDialog(
    exitProtectionEnabled: Boolean,
    durationMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enable Strict Mode?",
                color = FrictionColors.Warning,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Strict Mode will run for $durationMinutes minutes.",
                    color = FrictionColors.TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = buildString {
                        append("- Block distracting apps\n")
                        append("- Show focus challenges before access\n")
                        append(
                            if (exitProtectionEnabled) {
                                "- Lock Settings and interrupt app-removal attempts while active"
                            } else {
                                "- Keep Exit Protection off"
                            }
                        )
                    },
                    color = FrictionColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    text = "Enable only if you are serious about focus. You can use this protection only after accepting the Accessibility disclosure.",
                    color = FrictionColors.Warning,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        containerColor = FrictionColors.SurfaceLight,
        shape = RoundedCornerShape(20.dp),
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("ENABLE", color = FrictionColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = FrictionColors.TextSecondary)
            }
        }
    )
}

@Composable
fun DeveloperBypassDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val randomX = remember { (100..400).random() }
    val randomY = remember { (40..90).random() }
    val correctAnswer = randomX * randomY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Developer Override", color = FrictionColors.Warning, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "This will disable Strict Mode and Exit Delay Protection for this device.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Solve: $randomX × $randomY =",
                    fontWeight = FontWeight.Bold,
                    color = FrictionColors.TextPrimary,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it; error = false },
                    isError = error,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrictionColors.TextPrimary,
                        unfocusedTextColor = FrictionColors.TextPrimary,
                        focusedBorderColor = FrictionColors.Accent,
                        unfocusedBorderColor = FrictionColors.CardBorder
                    )
                )
                if (error) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Incorrect. Try again.", color = FrictionColors.Accent, fontSize = 12.sp)
                }
            }
        },
        containerColor = FrictionColors.SurfaceLight,
        shape = RoundedCornerShape(20.dp),
        confirmButton = {
            TextButton(
                onClick = {
                    if (answer.trim() == correctAnswer.toString()) onSuccess()
                    else error = true
                }
            ) {
                Text("UNLOCK", color = FrictionColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = FrictionColors.TextSecondary)
            }
        }
    )
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
