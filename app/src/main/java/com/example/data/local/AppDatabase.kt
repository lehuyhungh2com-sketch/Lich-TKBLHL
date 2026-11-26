package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SchoolConfigEntity::class,
        TeacherEntity::class,
        SchoolClassEntity::class,
        SubjectEntity::class,
        AssignmentEntity::class,
        TimetableSlotEntity::class,
        WeekInfoEntity::class,
        DeletedSlotsEntity::class,
        SyncMetadataEntity::class,
        WeekSyncVersionEntity::class,
        WeatherCacheEntity::class,
        LessonNoteEntity::class,
        TimetableSlotSnapshotEntity::class,
        CalendarEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schoolDao(): SchoolDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lesson_notes` (
                        `noteKey` TEXT NOT NULL,
                        `week` INTEGER NOT NULL,
                        `day` TEXT NOT NULL,
                        `period` INTEGER NOT NULL,
                        `session` TEXT NOT NULL,
                        `classId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`noteKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `timetable_slot_snapshots` (
                        `week` INTEGER NOT NULL,
                        `slotsJson` TEXT NOT NULL,
                        `savedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`week`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `isLunar` INTEGER NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `day` INTEGER NOT NULL,
                        `isRecurringYearly` INTEGER NOT NULL,
                        `timeLabel` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lhl_timetable_cache.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
