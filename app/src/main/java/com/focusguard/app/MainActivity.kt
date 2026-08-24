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
import com.focusguard.app.data.settings.SettingsRepository
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
import com.focusguard.app.ui.components.StableLinearProgress
import com.focusguard.app.ui.screens.BlacklistScreen
import com.focusguard.app.ui.screens.FocusScreen
import com.focusguard.app.ui.screens.ProfileSettingsScreen
import com.focusguard.app.ui.screens.PyqSolveScreen
import com.focusguard.app.ui.screens.MultiScheduleScreen
import com.focusguard.app.ui.screens.PermissionsScreen
import com.focusguard.app.ui.screens.UsageStatsScreen
import com.focusguard.app.ui.screens.ProgressHubScreen
import com.focusguard.app.ui.screens.ShieldHubScreen
import com.focusguard.app.ui.screens.TodayHubScreen
import com.focusguard.app.ui.screens.YouHubScreen
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
            var protectionArmed by remember { mutableStateOf(app.prefs.isProtectionArmed) }
            LaunchedEffect(Unit) {
                while (true) {
                    protectionArmed = app.prefs.isProtectionArmed
                    delay(750)
                }
            }
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(FrictionColors.GlassBackground)
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Preparing Focus Guard...",
                                color = FrictionColors.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                            settingsViewModel.setDisclosureAccepted(true)
                        }
                    )
                } else if (!protectionArmed) {
                    ProtectionSetupScreen(
                        onArmProtection = {
                            app.prefs.isProtectionArmed = true
                            app.prefs.isServiceEnabled = true
                            protectionArmed = true
                            startGuardService()
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
                    val currentRoute = backStackEntry?.destination?.route ?: BottomDestination.Today.route
                    val startDestination = remember {
                        resolveStartDestination(intent)
                    }
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val drawerScope = rememberCoroutineScope()
                    val topLevelRoutes = remember {
                        bottomDestinations.map { it.route }.toSet()
                    }
                    val selectedBottomRoute = remember(currentRoute) {
                        when (currentRoute) {
                            BottomDestination.Apps.route,
                            BottomDestination.Schedule.route,
                            BottomDestination.Permissions.route -> BottomDestination.Shield.route
                            BottomDestination.UsageDetail.route,
                            BottomDestination.Pyq.route -> BottomDestination.Progress.route
                            BottomDestination.ProfileSettings.route -> BottomDestination.You.route
                            else -> currentRoute
                        }
                    }
                    val drawerDestinations = remember {
                        listOf(
                            BottomDestination.Today,
                            BottomDestination.Shield,
                            BottomDestination.StartFocus,
                            BottomDestination.Progress,
                            BottomDestination.You,
                            BottomDestination.Apps,
                            BottomDestination.Schedule,
                            BottomDestination.UsageDetail,
                            BottomDestination.Permissions,
                            BottomDestination.Pyq,
                            BottomDestination.ProfileSettings
                        ).map { it.toPremiumNavItem() }
                    }
                    val navigateToPrimary: (String) -> Unit = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    val openDetail: (String) -> Unit = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            PremiumDrawerContent(
                                items = drawerDestinations,
                                currentRoute = currentRoute,
                                onItemClick = { item ->
                                    if (item.route in topLevelRoutes) {
                                        navigateToPrimary(item.route)
                                    } else {
                                        openDetail(item.route)
                                    }
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
                                    currentRoute = selectedBottomRoute,
                                    onItemClick = { item -> navigateToPrimary(item.route) }
                                )
                            }
                        ) { padding ->
                            NavHost(
                                navController = navController,
                                startDestination = startDestination,
                                modifier = Modifier.padding(padding)
                            ) {
                                composable(BottomDestination.Today.route) {
                                    TodayHubScreen(
                                        usageStatsViewModel = usageStatsViewModel,
                                        focusSettings = focusSettings,
                                        onOpenMenu = {
                                            drawerScope.launch { drawerState.open() }
                                        },
                                        onOpenShield = {
                                            navigateToPrimary(BottomDestination.Shield.route)
                                        },
                                        onOpenFocus = {
                                            navigateToPrimary(BottomDestination.StartFocus.route)
                                        },
                                        onOpenProgress = {
                                            navigateToPrimary(BottomDestination.Progress.route)
                                        },
                                        onOpenPyq = {
                                            openDetail(BottomDestination.Pyq.route)
                                        },
                                        onOpenYou = {
                                            navigateToPrimary(BottomDestination.You.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Shield.route) {
                                    ShieldHubScreen(
                                        focusSettings = focusSettings,
                                        settingsViewModel = settingsViewModel,
                                        onOpenMenu = {
                                            drawerScope.launch { drawerState.open() }
                                        },
                                        onOpenApps = {
                                            openDetail(BottomDestination.Apps.route)
                                        },
                                        onOpenSchedule = {
                                            openDetail(BottomDestination.Schedule.route)
                                        },
                                        onOpenPermissions = {
                                            openDetail(BottomDestination.Permissions.route)
                                        },
                                        onOpenFocus = {
                                            navigateToPrimary(BottomDestination.StartFocus.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.StartFocus.route) {
                                    val focusSessionViewModel: FocusSessionViewModel = viewModel(
                                        factory = FocusSessionViewModel.factory(app.focusSessionRepository)
                                    )
                                    FocusScreen(
                                        viewModel = focusSessionViewModel,
                                        onOpenPyq = {
                                            openDetail(BottomDestination.Pyq.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Progress.route) {
                                    ProgressHubScreen(
                                        usageStatsViewModel = usageStatsViewModel,
                                        onOpenMenu = {
                                            drawerScope.launch { drawerState.open() }
                                        },
                                        onOpenUsageDetail = {
                                            openDetail(BottomDestination.UsageDetail.route)
                                        },
                                        onOpenPyq = {
                                            openDetail(BottomDestination.Pyq.route)
                                        },
                                        onOpenFocus = {
                                            navigateToPrimary(BottomDestination.StartFocus.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.You.route) {
                                    YouHubScreen(
                                        profileViewModel = profileViewModel,
                                        isDarkTheme = darkTheme,
                                        onToggleTheme = {
                                            darkTheme = !darkTheme
                                            app.prefs.isDarkThemeEnabled = darkTheme
                                        },
                                        onOpenMenu = {
                                            drawerScope.launch { drawerState.open() }
                                        },
                                        onOpenProfileDetails = {
                                            openDetail(BottomDestination.ProfileSettings.route)
                                        },
                                        onOpenPermissionHealth = {
                                            openDetail(BottomDestination.Permissions.route)
                                        }
                                    )
                                }
                                composable(BottomDestination.Apps.route) {
                                    BlacklistScreen(
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToPrimary(BottomDestination.Shield.route)
                                            }
                                        }
                                    )
                                }
                                composable(BottomDestination.UsageDetail.route) {
                                    UsageStatsScreen(
                                        usageStatsViewModel = usageStatsViewModel,
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToPrimary(BottomDestination.Progress.route)
                                            }
                                        }
                                    )
                                }
                                composable(BottomDestination.Schedule.route) {
                                    MultiScheduleScreen(
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToPrimary(BottomDestination.Shield.route)
                                            }
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
                                            if (!navController.popBackStack()) {
                                                navigateToPrimary(BottomDestination.Progress.route)
                                            }
                                        }
                                    )
                                }
                                composable(BottomDestination.Permissions.route) {
                                    PermissionsScreen(
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToPrimary(BottomDestination.You.route)
                                            }
                                        }
                                    )
                                }
                                composable(BottomDestination.ProfileSettings.route) {
                                    ProfileSettingsScreen(
                                        profileViewModel = profileViewModel,
                                        authViewModel = authViewModel,
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToPrimary(BottomDestination.You.route)
                                            }
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
        val prefs = FocusGuardApp.instance.prefs
        if (prefs.hasAcceptedAccessibilityDisclosure &&
            prefs.isProtectionArmed &&
            prefs.isServiceEnabled
        ) {
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
            BottomDestination.Today.route
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
                    text = "Block distractions during focus time.",
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
                            body = "Detects blocked apps during focus time."
                        )
                        DisclosurePoint(
                            title = "Visible screen structure",
                            body = "For Reels/Shorts rules, visible screen text and structure are checked on device."
                        )
                        DisclosurePoint(
                            title = "No personal data collection",
                            body = "Screen content, messages, and passwords are not uploaded."
                        )
                        DisclosurePoint(
                            title = "Your control",
                            body = "Disable Accessibility to stop the service."
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
private fun ProtectionSetupScreen(onArmProtection: () -> Unit) {
    val context = LocalContext.current
    val permissionMonitor = remember { PermissionMonitor(context) }
    var accessibilityReady by remember { mutableStateOf(false) }
    var overlayReady by remember { mutableStateOf(false) }
    var usageReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityReady = permissionMonitor.isAccessibilityEnabled()
            overlayReady = permissionMonitor.isOverlayPermitted()
            usageReady = permissionMonitor.isUsageStatsPermitted()
            delay(750)
        }
    }

    val allReady = accessibilityReady && overlayReady && usageReady
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = FrictionColors.Accent,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "Finish protection setup",
                    color = FrictionColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Focus Guard remains off until all three permissions are enabled. This prevents an active-looking session without a visible block screen.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                SetupPermissionButton(
                    label = "Accessibility",
                    ready = accessibilityReady,
                    onClick = {
                        FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                SetupPermissionButton(
                    label = "Display over other apps",
                    ready = overlayReady,
                    onClick = {
                        FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
                SetupPermissionButton(
                    label = "Usage access",
                    ready = usageReady,
                    onClick = {
                        FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                Button(
                    onClick = onArmProtection,
                    enabled = allReady,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        disabledContainerColor = FrictionColors.SurfaceElevated
                    )
                ) {
                    Text(if (allReady) "Arm Focus Guard" else "Complete all permissions")
                }
            }
        }
    }
}

@Composable
private fun SetupPermissionButton(label: String, ready: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label · ${if (ready) "Ready" else "Required"}",
            color = if (ready) FrictionColors.Success else FrictionColors.TextPrimary
        )
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
    val icon: ImageVector,
    val isPrimaryCta: Boolean = false
) {
    companion object {
        val Today = BottomDestination("dashboard", "Today", Icons.Rounded.Home)
        val Shield = BottomDestination("shield_home", "Shield", Icons.Rounded.Security)
        val StartFocus = BottomDestination("focus", "Start", Icons.Rounded.Timer, isPrimaryCta = true)
        val Progress = BottomDestination("progress_home", "Progress", Icons.Rounded.BarChart)
        val You = BottomDestination("you_home", "You", Icons.Rounded.Person)

        val Apps = BottomDestination("blacklist", "Apps to Block", Icons.Rounded.Block)
        val Permissions = BottomDestination("permissions", "Permission Health", Icons.Rounded.AdminPanelSettings)
        val Schedule = BottomDestination("schedule", "Schedules & Limits", Icons.Rounded.CalendarMonth)
        val UsageDetail = BottomDestination("usage", "Analytics Detail", Icons.Rounded.BarChart)
        val Pyq = BottomDestination("pyq", "PYQ Lab", Icons.Rounded.School)
        val ProfileSettings = BottomDestination("profile", "Profile Details", Icons.Rounded.Settings)
    }
}

private val bottomDestinations = listOf(
    BottomDestination.Today,
    BottomDestination.Shield,
    BottomDestination.StartFocus,
    BottomDestination.Progress,
    BottomDestination.You
)

private fun BottomDestination.toPremiumNavItem(): PremiumNavItem {
    return PremiumNavItem(route = route, label = label, icon = icon, isPrimaryCta = isPrimaryCta)
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

    val coreBlockingReady = accessibilityEnabled
    val guardWindowActive = prefs.isGuardActiveNow()
    val fallbackReady = usageStatsEnabled && isServiceRunning
    val isActive = coreBlockingReady && guardWindowActive
    val statusTitle = when {
        isActive -> "Protection Armed"
        coreBlockingReady -> "Protection Ready"
        else -> "Protection Incomplete"
    }
    val statusMessage = when {
        isActive && fallbackReady ->
            "Blocked apps can be interrupted now."
        isActive ->
            "Blocking is armed. Usage Access adds backup detection."
        coreBlockingReady ->
            "Start Focus or set a schedule."
        else ->
            "Turn on Accessibility first."
    }
    val statusAccentColor = when {
        isActive -> FrictionColors.Success
        coreBlockingReady -> FrictionColors.Warning
        else -> FrictionColors.Accent
    }
    val statusBackground = when {
        isActive -> FrictionColors.SuccessSoft
        coreBlockingReady -> FrictionColors.WarningSoft
        else -> FrictionColors.ErrorSoft
    }

    fun enableStrictModeAfterConsent() {
        if (exitProtectionEnabled && !dpm.isAdminActive(adminComponent)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Optional Strict Mode exit protection."
                )
            }
            context.startActivity(intent)
        }
        settingsViewModel.toggleStrictMode(
            enabled = true,
            durationMinutes = if (exitProtectionEnabled) {
                SettingsRepository.STRICT_HARDLOCK_MINUTES
            } else {
                blockDuration.toInt()
            },
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
            durationMinutes = if (exitProtectionEnabled) {
                SettingsRepository.STRICT_HARDLOCK_MINUTES
            } else {
                blockDuration.toInt()
            },
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
                                text = "Focus, block, study.",
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
                    backgroundColor = statusBackground
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
                            text = statusTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusAccentColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
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
                                    else "Blocks distractions for a set duration",
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

                        val effectiveDurationMinutes = if (exitProtectionEnabled) {
                            SettingsRepository.STRICT_HARDLOCK_MINUTES
                        } else {
                            blockDuration.toInt()
                        }
                        Text(
                            "Duration: ${formatStrictModeDuration(effectiveDurationMinutes)}",
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
                            enabled = !isStrictMode && !exitProtectionEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = if (isStrictMode || exitProtectionEnabled) FrictionColors.TextMuted else FrictionColors.Accent,
                                activeTrackColor = if (isStrictMode || exitProtectionEnabled) FrictionColors.TextMuted else FrictionColors.Accent,
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
                                    text = "Optional. During Strict Mode, removal screens return to Home.",
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
                                    text = "Hardlock duration is 30 days while Exit Protection is enabled.",
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
                    isGranted = accessibilityEnabled,
                    isRequired = true
                ) {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.Layers,
                    title = "Display Overlay",
                    subtitle = "Shows blocking screen",
                    isGranted = overlayEnabled
                ) {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.QueryStats,
                    title = "Usage Stats",
                    subtitle = "Backup detection",
                    isGranted = usageStatsEnabled
                ) {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.BatteryAlert,
                    title = "Keep Alive",
                    subtitle = "Keep background guard alive",
                    isGranted = !batteryOptimized
                ) {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            }

            item {
                PermissionRow(
                    icon = Icons.Outlined.Autorenew,
                    title = "Auto Start (OnePlus/Oppo/Xiaomi)",
                    subtitle = "Restart after reboot",
                    isGranted = false // Hard to detect, always show as an action
                ) {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
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
                        prefs.getActiveStudyBlock()?.let { "Active: ${it.title}" }
                            ?: "${prefs.studyBlocks.count { it.enabled }} study block(s)"
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
                StableLinearProgress(
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
    isRequired: Boolean = false,
    onClick: () -> Unit
) {
    val statusText = when {
        isGranted -> "Active"
        isRequired -> "Required"
        else -> "Optional"
    }
    val statusColor = when {
        isGranted -> FrictionColors.Success
        isRequired -> FrictionColors.Warning
        else -> FrictionColors.TextMuted
    }
    val statusBackground = when {
        isGranted -> FrictionColors.SuccessSoft
        isRequired -> FrictionColors.WarningSoft
        else -> FrictionColors.SurfaceElevated
    }

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
                    .background(statusBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = statusColor,
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
                    .background(statusBackground)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
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

private fun formatStrictModeDuration(minutes: Int): String {
    val days = minutes / (24 * 60)
    val remainingMinutes = minutes % (24 * 60)
    val hours = remainingMinutes / 60
    val mins = remainingMinutes % 60

    return when {
        days > 0 && hours == 0 && mins == 0 -> "$days days"
        days > 0 && mins == 0 -> "$days days ${hours}h"
        days > 0 -> "$days days ${hours}h ${mins}m"
        hours > 0 && mins == 0 -> "${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "$mins min"
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
                    text = "Strict Mode will run for ${formatStrictModeDuration(durationMinutes)}.",
                    color = FrictionColors.TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = buildString {
                        append(
                            if (exitProtectionEnabled) {
                                "Exit protection is on."
                            } else {
                                "Exit protection is off."
                            }
                        )
                    },
                    color = FrictionColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    text = "You can stop Strict Mode after the timer ends.",
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
