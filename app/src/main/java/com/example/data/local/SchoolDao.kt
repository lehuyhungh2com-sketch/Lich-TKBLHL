package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolDao {
    // School Config
    @Query("SELECT * FROM school_config WHERE id = 'thcs_lehuulap' LIMIT 1")
    fun observeSchoolConfig(): Flow<SchoolConfigEntity?>

    @Query("SELECT * FROM school_config WHERE id = 'thcs_lehuulap' LIMIT 1")
    suspend fun getSchoolConfig(): SchoolConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchoolConfig(config: SchoolConfigEntity)

    // Teachers
    @Query("SELECT * FROM teachers")
    fun observeTeachers(): Flow<List<TeacherEntity>>

    @Query("SELECT * FROM teachers")
    suspend fun getTeachers(): List<TeacherEntity>

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun getTeacherById(id: String): TeacherEntity?

    @Query("DELETE FROM teachers")
    suspend fun clearTeachers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeachers(teachers: List<TeacherEntity>)

    @Transaction
    suspend fun replaceTeachers(teachers: List<TeacherEntity>) {
        clearTeachers()
        insertTeachers(teachers)
    }

    // Classes
    @Query("SELECT * FROM school_classes")
    fun observeClasses(): Flow<List<SchoolClassEntity>>

    @Query("SELECT * FROM school_classes")
    suspend fun getClasses(): List<SchoolClassEntity>

    @Query("DELETE FROM school_classes")
    suspend fun clearClasses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<SchoolClassEntity>)

    @Transaction
    suspend fun replaceClasses(classes: List<SchoolClassEntity>) {
        clearClasses()
        insertClasses(classes)
    }

    // Subjects
    @Query("SELECT * FROM subjects")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects")
    suspend fun getSubjects(): List<SubjectEntity>

    @Query("DELETE FROM subjects")
    suspend fun clearSubjects()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Transaction
    suspend fun replaceSubjects(subjects: List<SubjectEntity>) {
        clearSubjects()
        insertSubjects(subjects)
    }

    // Assignments
    @Query("SELECT * FROM assignments")
    fun observeAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments")
    suspend fun getAssignments(): List<AssignmentEntity>

    @Query("DELETE FROM assignments")
    suspend fun clearAssignments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<AssignmentEntity>)

    @Transaction
    suspend fun replaceAssignments(assignments: List<AssignmentEntity>) {
        clearAssignments()
        insertAssignments(assignments)
    }

    // Timetable Slots (per week & cached cumulative)
    @Query("SELECT * FROM timetable_slots")
    fun observeAllSlots(): Flow<List<TimetableSlotEntity>>

    @Query("SELECT * FROM timetable_slots WHERE week = :week")
    fun observeSlotsForWeek(week: Int): Flow<List<TimetableSlotEntity>>

    @Query("SELECT * FROM timetable_slots WHERE week IN (:weeks)")
    fun observeSlotsForWeeks(weeks: List<Int>): Flow<List<TimetableSlotEntity>>

    @Query("SELECT * FROM timetable_slots WHERE week = :week")
    suspend fun getSlotsForWeek(week: Int): List<TimetableSlotEntity>

    @Query("DELETE FROM timetable_slots WHERE week = :week")
    suspend fun deleteSlotsForWeek(week: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<TimetableSlotEntity>)

    @Transaction
    suspend fun replaceSlotsForWeek(week: Int, slots: List<TimetableSlotEntity>) {
        deleteSlotsForWeek(week)
        if (slots.isNotEmpty()) {
            insertSlots(slots)
        }
    }

    // Weeks Info
    @Query("SELECT * FROM weeks_info ORDER BY weekNumber ASC")
    fun observeWeeks(): Flow<List<WeekInfoEntity>>

    @Query("SELECT * FROM weeks_info ORDER BY weekNumber ASC")
    suspend fun getWeeks(): List<WeekInfoEntity>

    @Query("DELETE FROM weeks_info")
    suspend fun clearWeeks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeks(weeks: List<WeekInfoEntity>)

    @Transaction
    suspend fun replaceWeeks(weeks: List<WeekInfoEntity>) {
        clearWeeks()
        insertWeeks(weeks)
    }

    // Deleted Slots
    @Query("SELECT * FROM deleted_slots WHERE id = 1 LIMIT 1")
    fun observeDeletedSlots(): Flow<DeletedSlotsEntity?>

    @Query("SELECT * FROM deleted_slots WHERE id = 1 LIMIT 1")
    suspend fun getDeletedSlots(): DeletedSlotsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeletedSlots(entity: DeletedSlotsEntity)

    // Sync Metadata
    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun observeSyncMetadata(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun getSyncMetadata(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

    // Week Sync Versions (tracks which weeks were downloaded at which slotsVersion)
    @Query("SELECT * FROM week_sync_versions WHERE weekNumber = :week LIMIT 1")
    suspend fun getWeekSyncVersion(week: Int): WeekSyncVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekSyncVersion(entity: WeekSyncVersionEntity)

    // Weather Cache
    @Query("SELECT * FROM weather_cache WHERE id = 1 LIMIT 1")
    fun observeWeatherCache(): Flow<WeatherCacheEntity?>

    @Query("SELECT * FROM weather_cache WHERE id = 1 LIMIT 1")
    suspend fun getWeatherCache(): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeatherCache(cache: WeatherCacheEntity)

    // Personal Teacher Lesson Notes (100% local to device)
    @Query("SELECT * FROM lesson_notes ORDER BY updatedAtMillis DESC")
    fun observeLessonNotes(): Flow<List<LessonNoteEntity>>

    @Query("SELECT * FROM lesson_notes")
    suspend fun getAllLessonNotes(): List<LessonNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonNote(note: LessonNoteEntity)

    @Query("DELETE FROM lesson_notes WHERE noteKey = :noteKey")
    suspend fun deleteLessonNote(noteKey: String)

    // Previous Sync Slot Snapshots (100% local comparison baseline)
    @Query("SELECT * FROM timetable_slot_snapshots WHERE week = :week LIMIT 1")
    fun observeSlotSnapshotForWeek(week: Int): Flow<TimetableSlotSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlotSnapshot(snapshot: TimetableSlotSnapshotEntity)

    // Personal Calendar Events (100% local to device)
    @Query("SELECT * FROM calendar_events ORDER BY month ASC, day ASC, createdAtMillis DESC")
    fun observeCalendarEvents(): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalendarEvent(event: CalendarEventEntity): Long

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteCalendarEvent(id: Long)
}
