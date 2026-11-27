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
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.persistence.FocusGuardDatabase
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.reelcount.data.ReelCountRepository
import com.focusguard.app.reelcount.integration.ReelCountCoordinator
import com.focusguard.app.service.ServiceHealthWorker
import com.focusguard.app.service.SmartNotificationWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for Focus Guard.
 * Keeps main-thread cold start minimal by lazily constructing repositories and
 * deferring JSON asset loading and WorkManager scheduling to [Dispatchers.IO].
 */
class FocusGuardApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: FocusGuardDatabase by lazy { FocusGuardDatabase.getInstance(this) }
    lateinit var prefs: FocusGuardPrefs
        private set
    lateinit var antiBypassManager: AntiBypassManager
        private set
    lateinit var blockingManager: BlockingManager
        private set

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            settingsDataStore = SettingsDataStore(this),
            legacyPrefs = prefs
        )
    }
    val profileRepository: ProfileRepository by lazy {
        ProfileRepository.default(
            profileDataStore = ProfileDataStore(this),
            legacyPrefs = prefs,
            questionRepositoryContext = this
        )
    }
    val usageRepository: UsageRepository by lazy {
        UsageRepository(
            context = this,
            usageDao = database.appUsageDao(),
            prefs = prefs
        )
    }
    val usageStatsReconciler: UsageStatsReconciler by lazy {
        UsageStatsReconciler(
            context = this,
            prefs = prefs,
            usageRepository = usageRepository
        )
    }
    val trackingManager: TrackingManager by lazy {
        TrackingManager(
            context = this,
            prefs = prefs,
            usageRepository = usageRepository
        )
    }
    val pyqRepository: PyqRepository by lazy {
        PyqRepository(
            attemptDao = database.pyqAttemptDao(),
            questionSource = JsonPyqQuestionSource()
        )
    }
    val behaviorRepository: BehaviorRepository by lazy {
        BehaviorRepository(
            prefs = prefs,
            attemptDao = database.pyqAttemptDao(),
            pyqRepository = pyqRepository
        )
    }
    val pyqSelectorRepository: PyqSelectorRepository by lazy {
        PyqSelectorRepository(
            attemptDao = database.pyqAttemptDao(),
            pyqRepository = pyqRepository,
            behaviorRepository = behaviorRepository,
            questionSource = JsonPyqQuestionSource()
        )
    }
    val analyticsRepository: AnalyticsRepository by lazy {
        AnalyticsRepository(
            attemptDao = database.pyqAttemptDao(),
            pyqRepository = pyqRepository,
            behaviorRepository = behaviorRepository
        )
    }
    val focusSessionRepository: FocusSessionRepository by lazy {
        FocusSessionRepository(
            prefs = prefs,
            settingsRepository = settingsRepository,
            analyticsRepository = analyticsRepository
        )
    }
    val authRepository: AuthRepository by lazy {
        val authConfig = AuthConfig.fromBuildConfig()
        AuthRepository(
            config = authConfig,
            sessionStore = AuthSessionStore(this),
            googleAuthManager = GoogleAuthManager(authConfig),
            supabaseAuthApi = SupabaseAuthApi(authConfig),
            prefs = prefs,
            analyticsRepository = analyticsRepository
        )
    }
    val smartNotificationRepository: SmartNotificationRepository by lazy {
        SmartNotificationRepository(
            context = this,
            pyqRepository = pyqRepository,
            behaviorRepository = behaviorRepository
        )
    }
    val reelCountRepository: ReelCountRepository by lazy {
        ReelCountRepository(
            dao = database.reelCountDao(),
            prefs = prefs
        )
    }
    val reelCountCoordinator: ReelCountCoordinator by lazy {
        ReelCountCoordinator(
            repository = reelCountRepository,
            prefs = prefs
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        prefs = FocusGuardPrefs(this)
        antiBypassManager = AntiBypassManager(this, prefs)
        blockingManager = BlockingManager(this, prefs)
        createNotificationChannels()

        // Defer JSON asset parsing, preference migrations, and WorkManager IPC off the main thread.
        appScope.launch {
            prefs.migrateFocusedSurfaceDefaultsIfNeeded()
            QuestionRepository.initialize(this@FocusGuardApp, prefs.targetExam)
            scheduleSmartNotifications()
            scheduleServiceHealthWorker()
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Focus Guard Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for Focus Guard background service"
            setShowBadge(false)
        }

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
