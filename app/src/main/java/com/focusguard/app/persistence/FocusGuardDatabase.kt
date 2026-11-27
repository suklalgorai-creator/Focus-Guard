package com.focusguard.app.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusguard.app.reelcount.data.ReelCountDailyStatsEntity
import com.focusguard.app.reelcount.data.ReelCountDao
import com.focusguard.app.reelcount.data.ReelCountLimitStateEntity

/**
 * Room database for FocusGuard.
 * Stores attempt history for escalation and analytics.
 */
@Database(
    entities = [
        AttemptEntity::class,
        PyqAttemptEntity::class,
        AppUsageSessionEntity::class,
        DailyStatsEntity::class,
        ReelCountDailyStatsEntity::class,
        ReelCountLimitStateEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FocusGuardDatabase : RoomDatabase() {

    abstract fun attemptDao(): AttemptDao
    abstract fun pyqAttemptDao(): PyqAttemptDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun reelCountDao(): ReelCountDao

    companion object {
        @Volatile
        private var INSTANCE: FocusGuardDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pyq_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `questionId` INTEGER NOT NULL,
                        `subject` TEXT NOT NULL,
                        `isCorrect` INTEGER NOT NULL,
                        `selectedOption` TEXT NOT NULL,
                        `correctOption` TEXT NOT NULL,
                        `timeTakenMs` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `blockedPackage` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pyq_attempts_subject` ON `pyq_attempts` (`subject`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pyq_attempts_questionId` ON `pyq_attempts` (`questionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pyq_attempts_timestamp` ON `pyq_attempts` (`timestamp`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pyq_attempts_subject_timestamp` ON `pyq_attempts` (`subject`, `timestamp`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_usage_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `dateKey` TEXT NOT NULL,
                        `isDistracting` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_usage_sessions_dateKey` ON `app_usage_sessions` (`dateKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_usage_sessions_packageName_startTime` ON `app_usage_sessions` (`packageName`, `startTime`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_usage_sessions_isDistracting_dateKey` ON `app_usage_sessions` (`isDistracting`, `dateKey`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_stats` (
                        `dateKey` TEXT NOT NULL,
                        `totalUsageTimeMs` INTEGER NOT NULL,
                        `distractionTimeMs` INTEGER NOT NULL,
                        `timeSavedMs` INTEGER NOT NULL,
                        `sessionsBlocked` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reel_count_daily_stats` (
                        `dateKey` TEXT NOT NULL,
                        `viewedCount` INTEGER NOT NULL,
                        `blockedAttempts` INTEGER NOT NULL,
                        `limitReached` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reel_count_daily_stats_updatedAt` ON `reel_count_daily_stats` (`updatedAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reel_count_limit_state` (
                        `dateKey` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastPromptedAt` INTEGER NOT NULL,
                        `decisionUpdatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): FocusGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusGuardDatabase::class.java,
                    "focus_guard.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
