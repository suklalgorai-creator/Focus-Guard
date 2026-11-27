package com.focusguard.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.presentation.auth.AuthViewModel
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.service.GuardForegroundService
import com.focusguard.app.ui.auth.LoginScreen
import com.focusguard.app.ui.navigation.FocusGuardAppShell
import com.focusguard.app.ui.onboarding.AccessibilityDisclosureScreen
import com.focusguard.app.ui.onboarding.OnboardingScreen
import com.focusguard.app.ui.onboarding.ProtectionSetupScreen
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.ui.theme.FrictionColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        setContent {
            val app = application as FocusGuardApp
            var darkTheme by remember { mutableStateOf(app.prefs.isDarkThemeEnabled) }
            val protectionArmed by app.prefs.protectionArmedFlow.collectAsStateWithLifecycle()

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

                keepSplashOnScreen = authState.isInitializing

                when {
                    !profile.isOnboardingComplete -> {
                        OnboardingScreen(
                            profileViewModel = profileViewModel,
                            isDarkTheme = darkTheme,
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                app.prefs.isDarkThemeEnabled = darkTheme
                            },
                            onComplete = {}
                        )
                    }
                    authState.isInitializing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(FrictionColors.Background)
                        )
                    }
                    authState.shouldShowLogin -> {
                        LoginScreen(
                            state = authState,
                            onGoogleSignIn = { authViewModel.signIn(this@MainActivity) },
                            onEmailSignIn = { email, password ->
                                authViewModel.signInWithEmail(email, password)
                            },
                            onEmailSignUp = { name, email, password ->
                                authViewModel.signUpWithEmail(email, password, name)
                            },
                            onContinueWithoutAccount = {
                                authViewModel.continueWithoutAccount()
                            },
                            onClearError = { authViewModel.clearError() }
                        )
                    }
                    !disclosureAccepted -> {
                        AccessibilityDisclosureScreen(
                            onAccept = { settingsViewModel.setDisclosureAccepted(true) }
                        )
                    }
                    !protectionArmed -> {
                        ProtectionSetupScreen(
                            onArmProtection = {
                                app.prefs.isProtectionArmed = true
                                app.prefs.isServiceEnabled = true
                                startGuardService()
                            }
                        )
                    }
                    else -> {
                        FocusGuardAppShell(
                            app = app,
                            intent = intent,
                            focusSettings = focusSettings,
                            settingsViewModel = settingsViewModel,
                            profileViewModel = profileViewModel,
                            authViewModel = authViewModel,
                            isDarkTheme = darkTheme,
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                app.prefs.isDarkThemeEnabled = darkTheme
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = FocusGuardApp.instance.prefs
        prefs.reconcileDeadlinesAfterReboot()
        PermissionMonitor(this).checkAllPermissions()
        if (prefs.hasAcceptedAccessibilityDisclosure &&
            prefs.isProtectionArmed &&
            prefs.isServiceEnabled &&
            !prefs.isEnforcementPausedForMissingPermission
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
}
