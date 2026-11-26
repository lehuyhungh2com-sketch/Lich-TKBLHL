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
        LessonNoteEntity::class
    ],
    version = 2,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lhl_timetable_cache.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
