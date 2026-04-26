package com.focusguard.app.data.usage

import android.content.Context
import android.util.Log
import com.focusguard.app.domain.AppUsageData
import com.focusguard.app.domain.usage.AppUsageSession
import com.focusguard.app.domain.usage.UsageAnalyticsSummary
import com.focusguard.app.persistence.AppUsageDao
import com.focusguard.app.persistence.AppUsageSessionEntity
import com.focusguard.app.persistence.DailyStatsEntity
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class UsageRepository(
    context: Context,
    private val usageDao: AppUsageDao,
    private val prefs: FocusGuardPrefs
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val writeMutex = Mutex()
    private val appLabelCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var cachedUsageSnapshot: UsageSnapshot? = null

    @Volatile
    private var cachedUsageSnapshotAtMs: Long = 0L

    suspend fun recordSession(session: AppUsageSession) {
        if (session.durationMs < MIN_VALID_SESSION_MS) return

        writeMutex.withLock {
            val dateKey = dateKey(session.startTime)
            val overlaps = usageDao.countOverlappingSessions(
                packageName = session.packageName,
                startTime = session.startTime,
                endTime = session.endTime
            )
            if (overlaps > 0) {
                Log.d(TAG, "Skipping overlapping live usage session: ${session.packageName}")
                return@withLock
            }
            usageDao.insertSession(
                AppUsageSessionEntity(
                    packageName = session.packageName,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    durationMs = session.durationMs,
                    dateKey = dateKey,
                    isDistracting = session.isDistracting
                )
            )
            refreshDailyStats(dateKey)
            invalidateUsageCacheLocked()
        }
    }

    suspend fun recordReconciledSession(session: AppUsageSession) {
        if (session.durationMs < MIN_VALID_SESSION_MS) return

        writeMutex.withLock {
            val overlaps = usageDao.countOverlappingSessions(
                packageName = session.packageName,
                startTime = session.startTime,
                endTime = session.endTime
            )
            if (overlaps == 0) {
                val dateKey = dateKey(session.startTime)
                usageDao.insertSession(
                    AppUsageSessionEntity(
                        packageName = session.packageName,
                        startTime = session.startTime,
                        endTime = session.endTime,
                        durationMs = session.durationMs,
                        dateKey = dateKey,
                        isDistracting = session.isDistracting
                    )
                )
                refreshDailyStats(dateKey)
                invalidateUsageCacheLocked()
            }
        }
    }

    suspend fun recordBlockedSession(packageName: String) {
        if (packageName !in prefs.blacklistedApps) return

        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val dateKey = dateKey(now)
            val average = usageDao.getAverageSessionDuration(
                packageName = packageName,
                sinceMs = now - HISTORY_WINDOW_MS,
                minDurationMs = MIN_VALID_SESSION_MS
            )
            val estimatedSavedMs = AnalyticsCalculator.estimateSavedTimeMs(average)
            val current = usageDao.getDailyStats(dateKey)
            val totals = usageDao.getDailyUsageTotals(dateKey)

            usageDao.upsertDailyStats(
                DailyStatsEntity(
                    dateKey = dateKey,
                    totalUsageTimeMs = totals.totalUsageTimeMs ?: 0L,
                    distractionTimeMs = totals.distractionTimeMs ?: 0L,
                    timeSavedMs = (current?.timeSavedMs ?: 0L) + estimatedSavedMs,
                    sessionsBlocked = (current?.sessionsBlocked ?: 0) + 1,
                    updatedAt = now
                )
            )
            invalidateUsageCacheLocked()
        }
    }

    suspend fun getTodayUsage(): UsageAnalyticsSummary {
        return getUsageSnapshot().today
    }

    suspend fun getUsageForDate(dateKey: String): UsageAnalyticsSummary {
        return buildUsageForDate(dateKey, refreshStats = true)
    }

    suspend fun getDistractionTime(dateKey: String = dateKey(System.currentTimeMillis())): Long {
        return getUsageForDate(dateKey).distractionTimeMs
    }

    suspend fun getTopApps(dateKey: String = dateKey(System.currentTimeMillis())): List<AppUsageData> {
        return usageDao.getTopApps(dateKey, TOP_APP_LIMIT).map(::toAppUsageData)
    }

    suspend fun getTimeSaved(dateKey: String = dateKey(System.currentTimeMillis())): Long {
        return getUsageForDate(dateKey).timeSavedMs
    }

    suspend fun getWeeklyUsage(): Map<String, List<AppUsageData>> {
        return getUsageSnapshot().weeklyUsage
    }

    suspend fun getUsageSnapshot(forceRefresh: Boolean = false): UsageSnapshot {
        val now = System.currentTimeMillis()
        cachedUsageSnapshot?.takeIf {
            !forceRefresh && (now - cachedUsageSnapshotAtMs) < SNAPSHOT_CACHE_TTL_MS
        }?.let { return it }

        return writeMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            cachedUsageSnapshot?.takeIf {
                !forceRefresh && (lockedNow - cachedUsageSnapshotAtMs) < SNAPSHOT_CACHE_TTL_MS
            }?.let { return@withLock it }

            val snapshot = UsageSnapshot(
                today = buildUsageForDate(dateKey(lockedNow), refreshStats = true),
                weeklyUsage = buildWeeklyUsage(lockedNow)
            )
            cachedUsageSnapshot = snapshot
            cachedUsageSnapshotAtMs = lockedNow
            snapshot
        }
    }

    private suspend fun refreshDailyStats(dateKey: String) {
        val current = usageDao.getDailyStats(dateKey)
        val totals = usageDao.getDailyUsageTotals(dateKey)
        usageDao.upsertDailyStats(
            DailyStatsEntity(
                dateKey = dateKey,
                totalUsageTimeMs = totals.totalUsageTimeMs ?: 0L,
                distractionTimeMs = totals.distractionTimeMs ?: 0L,
                timeSavedMs = current?.timeSavedMs ?: 0L,
                sessionsBlocked = current?.sessionsBlocked ?: 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun buildUsageForDate(
        dateKey: String,
        refreshStats: Boolean
    ): UsageAnalyticsSummary {
        if (refreshStats) refreshDailyStats(dateKey)
        val daily = usageDao.getDailyStats(dateKey)
        val allApps = usageDao.getUsageByApp(dateKey).map(::toAppUsageData)
        val topApps = usageDao.getTopApps(dateKey, TOP_APP_LIMIT).map(::toAppUsageData)

        return UsageAnalyticsSummary(
            dateKey = dateKey,
            totalUsageTimeMs = daily?.totalUsageTimeMs ?: 0L,
            distractionTimeMs = daily?.distractionTimeMs ?: 0L,
            timeSavedMs = daily?.timeSavedMs ?: 0L,
            sessionsBlocked = daily?.sessionsBlocked ?: 0,
            topApps = topApps,
            allApps = allApps
        )
    }

    private suspend fun buildWeeklyUsage(nowMs: Long): Map<String, List<AppUsageData>> {
        val result = linkedMapOf<String, List<AppUsageData>>()
        val calendar = Calendar.getInstance()
        for (daysAgo in 6 downTo 0) {
            calendar.timeInMillis = nowMs
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val key = dateKey(calendar.timeInMillis)
            result[key] = usageDao.getUsageByApp(key).map(::toAppUsageData)
        }
        return result
    }

    private fun toAppUsageData(row: com.focusguard.app.persistence.AppUsageAggregateRow): AppUsageData {
        return AppUsageData(
            packageName = row.packageName,
            appName = appLabel(row.packageName),
            usageTimeMs = row.usageTimeMs,
            isBlacklisted = row.distractingSessionCount > 0 || row.packageName in prefs.blacklistedApps,
            sessionCount = row.sessionCount.toInt()
        )
    }

    fun dateKey(timestamp: Long): String = dateFormat.format(Date(timestamp))

    private fun appLabel(packageName: String): String {
        return appLabelCache.getOrPut(packageName) {
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                packageName
            }
        }
    }

    private fun invalidateUsageCacheLocked() {
        cachedUsageSnapshot = null
        cachedUsageSnapshotAtMs = 0L
    }

    companion object {
        private const val TAG = "UsageRepository"
        private const val MIN_VALID_SESSION_MS = 1_000L
        private const val HISTORY_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
        private const val TOP_APP_LIMIT = 3
        private const val SNAPSHOT_CACHE_TTL_MS = 15_000L
    }
}

data class UsageSnapshot(
    val today: UsageAnalyticsSummary,
    val weeklyUsage: Map<String, List<AppUsageData>>
)
