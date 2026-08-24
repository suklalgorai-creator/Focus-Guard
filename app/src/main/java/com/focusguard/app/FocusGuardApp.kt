package com.focusguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusguard.app.antibypass.AntiBypassManager
import com.focusguard.app.blocking.BlockingManager
import com.focusguard.app.data.analytics.AnalyticsRepository
import com.focusguard.app.data.auth.AuthConfig
import com.focusguard.app.data.auth.AuthRepository
import com.focusguard.app.data.auth.AuthSessionStore
import com.focusguard.app.data.auth.GoogleAuthManager
import com.focusguard.app.data.auth.SupabaseAuthApi
import com.focusguard.app.data.behavior.BehaviorRepository
import com.focusguard.app.data.focus.FocusSessionRepository
import com.focusguard.app.data.notification.SmartNotificationRepository
import com.focusguard.app.data.profile.ProfileDataStore
import com.focusguard.app.data.profile.ProfileRepository
import com.focusguard.app.data.pyq.JsonPyqQuestionSource
import com.focusguard.app.data.pyq.PyqRepository
import com.focusguard.app.data.pyq.PyqSelectorRepository
import com.focusguard.app.data.settings.SettingsDataStore
import com.focusguard.app.data.settings.SettingsRepository
import com.focusguard.app.data.usage.TrackingManager
import com.focusguard.app.data.usage.UsageRepository
import com.focusguard.app.data.usage.UsageStatsReconciler
import com.focusguard.app.persistence.FocusGuardDatabase
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.service.ServiceHealthWorker
import com.focusguard.app.service.SmartNotificationWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for Focus Guard.
 * Initializes persistence, notification channels, and pre-warms overlay resources.
 */
class FocusGuardApp : Application() {

    lateinit var database: FocusGuardDatabase
        private set
    lateinit var prefs: FocusGuardPrefs
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var pyqRepository: PyqRepository
        private set
    lateinit var behaviorRepository: BehaviorRepository
        private set
    lateinit var pyqSelectorRepository: PyqSelectorRepository
        private set
    lateinit var smartNotificationRepository: SmartNotificationRepository
        private set
    lateinit var analyticsRepository: AnalyticsRepository
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var focusSessionRepository: FocusSessionRepository
        private set
    lateinit var blockingManager: BlockingManager
        private set
    lateinit var usageRepository: UsageRepository
        private set
    lateinit var usageStatsReconciler: UsageStatsReconciler
        private set
    lateinit var trackingManager: TrackingManager
        private set
    lateinit var antiBypassManager: AntiBypassManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize persistence
        database = FocusGuardDatabase.getInstance(this)
        prefs = FocusGuardPrefs(this)
        antiBypassManager = AntiBypassManager(this, prefs)
        prefs.migrateFocusedSurfaceDefaultsIfNeeded()
        blockingManager = BlockingManager(this, prefs)
        usageRepository = UsageRepository(
            context = this,
            usageDao = database.appUsageDao(),
            prefs = prefs
        )
        usageStatsReconciler = UsageStatsReconciler(
            context = this,
            prefs = prefs,
            usageRepository = usageRepository
        )
        trackingManager = TrackingManager(
            context = this,
            prefs = prefs,
            usageRepository = usageRepository
        )
        settingsRepository = SettingsRepository(
            settingsDataStore = SettingsDataStore(this),
            legacyPrefs = prefs
        )
        profileRepository = ProfileRepository.default(
            profileDataStore = ProfileDataStore(this),
            legacyPrefs = prefs,
            questionRepositoryContext = this
        )

        // Initialize question database
        QuestionRepository.initialize(this, prefs.targetExam)

        pyqRepository = PyqRepository(
            attemptDao = database.pyqAttemptDao(),
            questionSource = JsonPyqQuestionSource()
        )
        behaviorRepository = BehaviorRepository(
            prefs = prefs,
            attemptDao = database.pyqAttemptDao(),
            pyqRepository = pyqRepository
        )
        pyqSelectorRepository = PyqSelectorRepository(
            attemptDao = database.pyqAttemptDao(),
            pyqRepository = pyqRepository,
            behaviorRepository = behaviorRepository,
            questionSource = JsonPyqQuestionSource()
        )
        analyticsRepository = AnalyticsRepository(
            attemptDao = database.pyqAttemptDao(),
            pyqRepository = pyqRepository,
            behaviorRepository = behaviorRepository
        )
        focusSessionRepository = FocusSessionRepository(
            prefs = prefs,
            settingsRepository = settingsRepository,
            analyticsRepository = analyticsRepository
        )
        val authConfig = AuthConfig.fromBuildConfig()
        authRepository = AuthRepository(
            config = authConfig,
            sessionStore = AuthSessionStore(this),
            googleAuthManager = GoogleAuthManager(authConfig),
            supabaseAuthApi = SupabaseAuthApi(authConfig),
            prefs = prefs,
            analyticsRepository = analyticsRepository
        )
        smartNotificationRepository = SmartNotificationRepository(
            context = this,
            pyqRepository = pyqRepository,
            behaviorRepository = behaviorRepository
        )

        // Create notification channels
        createNotificationChannels()
        scheduleSmartNotifications()
        scheduleServiceHealthWorker()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Foreground service channel
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Focus Guard Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for Focus Guard background service"
            setShowBadge(false)
        }

        // Alert channel (permission warnings, bypass attempts)
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Guard Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when permissions are disabled or bypass attempted"
        }

        val smartStudyChannel = NotificationChannel(
            CHANNEL_SMART,
            "Smart Study Nudges",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Personalized study reminders based on PYQ progress"
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(smartStudyChannel)
    }

    private fun scheduleSmartNotifications() {
        val request = PeriodicWorkRequestBuilder<SmartNotificationWorker>(
            6,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SmartNotificationWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleServiceHealthWorker() {
        val request = PeriodicWorkRequestBuilder<ServiceHealthWorker>(
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ServiceHealthWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "focus_guard_service"
        const val CHANNEL_ALERTS = "focus_guard_alerts"
        const val CHANNEL_SMART = "focus_guard_smart_study"

        lateinit var instance: FocusGuardApp
            private set
    }
}
