package com.focusguard.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.presentation.auth.AuthViewModel
import com.focusguard.app.presentation.focus.FocusSessionViewModel
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.presentation.pyq.PyqViewModel
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.components.FocusGuardIconId
import com.focusguard.app.ui.components.PremiumBottomNavigation
import com.focusguard.app.ui.components.PremiumDrawerContent
import com.focusguard.app.ui.components.PremiumNavItem
import com.focusguard.app.ui.screens.BlacklistScreen
import com.focusguard.app.ui.screens.FocusScreen
import com.focusguard.app.ui.screens.MultiScheduleScreen
import com.focusguard.app.ui.screens.PermissionsScreen
import com.focusguard.app.ui.screens.ProfileSettingsScreen
import com.focusguard.app.ui.screens.ProgressHubScreen
import com.focusguard.app.ui.screens.PyqSolveScreen
import com.focusguard.app.ui.screens.ShieldHubScreen
import com.focusguard.app.ui.screens.TodayHubScreen
import com.focusguard.app.ui.screens.UsageStatsScreen
import com.focusguard.app.ui.screens.YouHubScreen
import com.focusguard.app.ui.theme.FrictionColors
import kotlinx.coroutines.launch

@Composable
fun FocusGuardAppShell(
    app: FocusGuardApp,
    intent: Intent?,
    focusSettings: FocusSettings,
    settingsViewModel: SettingsViewModel,
    profileViewModel: ProfileViewModel,
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val usageStatsViewModel: UsageStatsViewModel = viewModel(
        factory = UsageStatsViewModel.factory(
            usageRepository = app.usageRepository,
            usageStatsReconciler = app.usageStatsReconciler,
            attemptDao = app.database.attemptDao()
        )
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BottomDestination.Today.route
    val startDestination = remember(intent) { resolveStartDestination(intent) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val topLevelRoutes = remember { bottomDestinations.map { it.route }.toSet() }

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
                        onOpenMenu = { drawerScope.launch { drawerState.open() } },
                        onOpenShield = { navigateToPrimary(BottomDestination.Shield.route) },
                        onOpenFocus = { navigateToPrimary(BottomDestination.StartFocus.route) },
                        onOpenProgress = { navigateToPrimary(BottomDestination.Progress.route) },
                        onOpenPyq = { openDetail(BottomDestination.Pyq.route) },
                        onOpenYou = { navigateToPrimary(BottomDestination.You.route) }
                    )
                }
                composable(BottomDestination.Shield.route) {
                    ShieldHubScreen(
                        focusSettings = focusSettings,
                        settingsViewModel = settingsViewModel,
                        onOpenMenu = { drawerScope.launch { drawerState.open() } },
                        onOpenApps = { openDetail(BottomDestination.Apps.route) },
                        onOpenSchedule = { openDetail(BottomDestination.Schedule.route) },
                        onOpenPermissions = { openDetail(BottomDestination.Permissions.route) },
                        onOpenFocus = { navigateToPrimary(BottomDestination.StartFocus.route) }
                    )
                }
                composable(BottomDestination.StartFocus.route) {
                    val focusSessionViewModel: FocusSessionViewModel = viewModel(
                        factory = FocusSessionViewModel.factory(app.focusSessionRepository)
                    )
                    FocusScreen(
                        viewModel = focusSessionViewModel,
                        onOpenPyq = { openDetail(BottomDestination.Pyq.route) }
                    )
                }
                composable(BottomDestination.Progress.route) {
                    ProgressHubScreen(
                        usageStatsViewModel = usageStatsViewModel,
                        onOpenMenu = { drawerScope.launch { drawerState.open() } },
                        onOpenUsageDetail = { openDetail(BottomDestination.UsageDetail.route) },
                        onOpenPyq = { openDetail(BottomDestination.Pyq.route) },
                        onOpenFocus = { navigateToPrimary(BottomDestination.StartFocus.route) }
                    )
                }
                composable(BottomDestination.You.route) {
                    YouHubScreen(
                        profileViewModel = profileViewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onOpenMenu = { drawerScope.launch { drawerState.open() } },
                        onOpenProfileDetails = { openDetail(BottomDestination.ProfileSettings.route) },
                        onOpenPermissionHealth = { openDetail(BottomDestination.Permissions.route) }
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
                        blockedPackage = sanitizeBlockedPackageExtra(
                            intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)
                        ),
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

private fun resolveStartDestination(intent: Intent?): String {
    val data = intent?.data
    return if (data?.scheme == "focusguard" && data.host == "pyq") {
        BottomDestination.Pyq.route
    } else {
        BottomDestination.Today.route
    }
}

private fun sanitizeBlockedPackageExtra(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return trimmed.takeIf { VALID_BLOCKED_PACKAGE_REGEX.matches(it) }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconId: FocusGuardIconId,
    val isPrimaryCta: Boolean = false
) {
    companion object {
        val Today = BottomDestination("dashboard", "Today", Icons.Rounded.Home, FocusGuardIconId.Today)
        val Shield = BottomDestination("shield_home", "Shield", Icons.Rounded.Security, FocusGuardIconId.Shield)
        val StartFocus = BottomDestination("focus", "Start", Icons.Rounded.Timer, FocusGuardIconId.FocusSession, isPrimaryCta = true)
        val Progress = BottomDestination("progress_home", "Progress", Icons.Rounded.BarChart, FocusGuardIconId.Progress)
        val You = BottomDestination("you_home", "You", Icons.Rounded.Person, FocusGuardIconId.You)

        val Apps = BottomDestination("blacklist", "Apps to Block", Icons.Rounded.Block, FocusGuardIconId.Blocklist)
        val Permissions = BottomDestination("permissions", "Permission Health", Icons.Rounded.AdminPanelSettings, FocusGuardIconId.Permissions)
        val Schedule = BottomDestination("schedule", "Schedules & Limits", Icons.Rounded.CalendarMonth, FocusGuardIconId.Schedule)
        val UsageDetail = BottomDestination("usage", "Analytics Detail", Icons.Rounded.BarChart, FocusGuardIconId.Usage)
        val Pyq = BottomDestination("pyq", "PYQ Lab", Icons.Rounded.School, FocusGuardIconId.Pyq)
        val ProfileSettings = BottomDestination("profile", "Profile Details", Icons.Rounded.Settings, FocusGuardIconId.Settings)
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
    return PremiumNavItem(
        route = route,
        label = label,
        icon = icon,
        isPrimaryCta = isPrimaryCta,
        iconId = iconId
    )
}

private const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
private val VALID_BLOCKED_PACKAGE_REGEX = Regex("^[a-zA-Z0-9._:]{1,128}$")
