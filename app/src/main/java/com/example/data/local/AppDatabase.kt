package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        WeatherCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schoolDao(): SchoolDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lhl_timetable_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
