package com.example.data.repository

import com.example.data.local.AppPreferences
import com.example.data.local.DeletedSlotsEntity
import com.example.data.local.LessonNoteEntity
import com.example.data.local.LocalJson
import com.example.data.local.SchoolDao
import com.example.data.local.SyncMetadataEntity
import com.example.data.local.TimetableSlotSnapshotEntity
import com.example.data.local.WeekSyncVersionEntity
import com.example.data.local.toEntity
import com.example.data.remote.FirestoreReadOnlyClient
import com.example.domain.lunar.VnDate
import com.example.domain.model.Assignment
import com.example.domain.model.CalendarEvent
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.Subject
import com.example.domain.model.SyncMetadata
import com.example.domain.model.Teacher
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.TimetableSlot
import com.example.domain.model.WeekInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface LoginResult {
    data class Success(val teacher: Teacher) : LoginResult
    data class Error(val message: String) : LoginResult
}

data class SyncOutcome(
    val success: Boolean,
    val offlineFallback: Boolean = false,
    val message: String,
    val updatedSections: List<String> = emptyList()
)

class SchoolRepository(
    private val dao: SchoolDao,
    private val firestore: FirestoreReadOnlyClient,
    private val preferences: AppPreferences
) {

    val schoolConfigFlow: Flow<SchoolConfig> = dao.observeSchoolConfig()
        .map { it?.toDomain() ?: SchoolConfig() }

    val teachersFlow: Flow<List<Teacher>> = dao.observeTeachers()
        .map { list -> list.map { it.toDomain() } }

    val classesFlow: Flow<List<SchoolClass>> = dao.observeClasses()
        .map { list -> list.map { it.toDomain() } }

    val subjectsFlow: Flow<List<Subject>> = dao.observeSubjects()
        .map { list -> list.map { it.toDomain() } }

    val assignmentsFlow: Flow<List<Assignment>> = dao.observeAssignments()
        .map { list -> list.map { it.toDomain() } }

    val weeksFlow: Flow<List<WeekInfo>> = dao.observeWeeks()
        .map { list -> list.map { it.toDomain() } }

    val deletedSlotKeysFlow: Flow<Set<String>> = dao.observeDeletedSlots()
        .map { it?.toKeySet() ?: emptySet() }

    val syncMetadataFlow: Flow<SyncMetadataEntity?> = dao.observeSyncMetadata()

    val lessonNotesFlow: Flow<Map<String, String>> = dao.observeLessonNotes()
        .map { list -> list.associate { it.noteKey to it.content } }

    val allSlotsFlow: Flow<List<TimetableSlot>> = dao.observeAllSlots()
        .map { list -> list.map { it.toDomain() } }

    val calendarEventsFlow: Flow<List<CalendarEvent>> = dao.observeCalendarEvents()
        .map { list -> list.map { it.toDomain() } }

    fun observeSlotsForWeek(week: Int): Flow<List<TimetableSlot>> =
        dao.observeSlotsForWeek(week).map { list -> list.map { it.toDomain() } }

    fun observeSlotSnapshotForWeek(week: Int): Flow<List<TimetableSlot>> =
        dao.observeSlotSnapshotForWeek(week).map { it?.toDomainSlots().orEmpty() }

    suspend fun saveLessonNote(
        week: Int,
        day: String,
        period: Int,
        session: String,
        classId: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        val key = LessonNoteEntity.buildKey(week, day, period, session, classId)
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            dao.deleteLessonNote(key)
        } else {
            dao.upsertLessonNote(
                LessonNoteEntity(
                    noteKey = key,
                    week = week,
                    day = day,
                    period = period,
                    session = session,
                    classId = classId,
                    content = trimmed,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteLessonNote(
        week: Int,
        day: String,
        period: Int,
        session: String,
        classId: String
    ) = withContext(Dispatchers.IO) {
        dao.deleteLessonNote(LessonNoteEntity.buildKey(week, day, period, session, classId))
    }

    suspend fun saveCalendarEvent(event: CalendarEvent) = withContext(Dispatchers.IO) {
        val trimmedTitle = event.title.trim()
        if (trimmedTitle.isEmpty()) return@withContext
        val toSave = event.copy(
            title = trimmedTitle,
            note = event.note.trim(),
            timeLabel = event.timeLabel.trim(),
            createdAtMillis = if (event.createdAtMillis > 0L) event.createdAtMillis else System.currentTimeMillis()
        )
        dao.upsertCalendarEvent(toSave.toEntity())
    }

    suspend fun deleteCalendarEvent(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteCalendarEvent(id)
    }

    fun isFirebaseConfigured(): Boolean = firestore.isFirebaseConfigured()
    fun configuredProjectId(): String? = firestore.configuredProjectId()

    /**
     * Strict teacher login verification:
     * - Full email comparison (case-insensitive, trimmed).
     * - Exact password comparison (strictly case-sensitive!).
     * - Blocks immediately if email is not in `teachers` (or built-in 2 admin accounts) or password fails.
     */
    suspend fun login(rawEmail: String, rawPassword: String): LoginResult = withContext(Dispatchers.IO) {
        val emailTrimmed = rawEmail.trim()
        if (emailTrimmed.isEmpty() || !emailTrimmed.contains("@")) {
            return@withContext LoginResult.Error("Vui lòng nhập đầy đủ địa chỉ Email hợp lệ.")
        }
        if (rawPassword.isEmpty()) {
            return@withContext LoginResult.Error("Vui lòng nhập mật khẩu.")
        }

        if (!firestore.isFirebaseConfigured()) {
            return@withContext LoginResult.Error(
                "Chưa có file google-services.json của dự án tkbthcslhl-2026. Vui lòng thêm file cấu hình vào thư mục app/ và biên dịch lại."
            )
        }

        return@withContext try {
            val remoteTeachers = firestore.fetchTeachers()
            val mergedTeachers = ensureBuiltInAdmins(remoteTeachers)

            val matchedTeacher = mergedTeachers.firstOrNull {
                it.email.trim().equals(emailTrimmed, ignoreCase = true)
            } ?: return@withContext LoginResult.Error(
                "Email \"$emailTrimmed\" không tồn tại trong danh sách giáo viên Trường THCS Lê Hữu Lập."
            )

            val adminDocPassword = if (matchedTeacher.isAdminOrBgh) {
                firestore.fetchAdminPassword()
            } else {
                FirestoreReadOnlyClient.DEFAULT_ADMIN_PASSWORD
            }

            val isValidPassword = verifyPassword(
                teacher = matchedTeacher,
                inputPassword = rawPassword,
                adminDocPassword = adminDocPassword
            )

            if (!isValidPassword) {
                return@withContext LoginResult.Error(
                    "Mật khẩu không chính xác. Lưu ý mật khẩu phân biệt chữ hoa và chữ thường."
                )
            }

            // Cache teachers immediately & persist session
            dao.replaceTeachers(mergedTeachers.map { it.toEntity() })
            preferences.saveLoginSession(matchedTeacher.id, matchedTeacher.email.trim())

            // Run initial incremental sync for current week
            syncIncremental(targetWeek = null, force = false)

            LoginResult.Success(matchedTeacher)
        } catch (e: Exception) {
            LoginResult.Error(
                "Không thể kết nối máy chủ Firestore (${e.localizedMessage ?: "lỗi mạng"}). Vui lòng kiểm tra kết nối Internet."
            )
        }
    }

    private fun verifyPassword(
        teacher: Teacher,
        inputPassword: String,
        adminDocPassword: String
    ): Boolean {
        val personalPassword = teacher.password
        return if (!personalPassword.isNullOrEmpty()) {
            inputPassword == personalPassword ||
                (teacher.isAdminOrBgh && inputPassword == adminDocPassword)
        } else {
            if (teacher.isAdminOrBgh) {
                inputPassword == adminDocPassword ||
                    inputPassword == FirestoreReadOnlyClient.DEFAULT_TEACHER_PASSWORD
            } else {
                inputPassword == FirestoreReadOnlyClient.DEFAULT_TEACHER_PASSWORD
            }
        }
    }

    private fun ensureBuiltInAdmins(teachers: List<Teacher>): List<Teacher> {
        val result = teachers.toMutableList()
        val builtIn = listOf(
            Teacher(
                id = "gv-hung",
                name = "Lê Huy Hùng",
                shortName = "Hùng",
                email = "lehuyhung.h2com@gmail.com",
                department = "Quản trị hệ thống",
                mainSubject = "Tin học",
                accountRole = "admin",
                isBGH = true
            ),
            Teacher(
                id = "gv-ha",
                name = "Quản trị viên (BGH)",
                shortName = "Hà",
                email = "ha1973lhl@gmail.com",
                department = "Ban Giám hiệu",
                mainSubject = "",
                accountRole = "admin",
                isBGH = true
            )
        )
        for (admin in builtIn) {
            val exists = result.any {
                it.email.trim().equals(admin.email, ignoreCase = true) || it.id == admin.id
            }
            if (!exists) {
                result.add(admin)
            }
        }
        return result
    }

    suspend fun logout() {
        preferences.clearLoginSession()
    }

    /**
     * Quota-saving Local-First Incremental Sync:
     * 1. Reads ONLY 1 document: `cai_dat_he_thong/sync_metadata`.
     * 2. Compares version counters against local Room metadata.
     * 3. Fetches ONLY sections whose version changed (or are empty locally).
     * 4. Fetches `timetables` strictly with `whereEqualTo("week", weekToLoad)`.
     * 5. Gracefully falls back to local Room cache if offline or quota exceeded.
     */
    suspend fun syncIncremental(
        targetWeek: Int? = null,
        force: Boolean = false
    ): SyncOutcome = withContext(Dispatchers.IO) {
        if (!firestore.isFirebaseConfigured()) {
            return@withContext SyncOutcome(
                success = false,
                offlineFallback = true,
                message = "Chưa có google-services.json — đang dùng dữ liệu cục bộ."
            )
        }

        return@withContext try {
            val remoteMeta: SyncMetadata = firestore.fetchSyncMetadata()
            val localMeta: SyncMetadataEntity? = dao.getSyncMetadata()
            val updatedSections = mutableListOf<String>()

            val hasLocalCatalog = dao.getSchoolConfig() != null &&
                dao.getTeachers().isNotEmpty() &&
                dao.getSubjects().isNotEmpty() &&
                dao.getClasses().isNotEmpty()

            if (force || localMeta == null || !hasLocalCatalog || remoteMeta.catalogVersion != localMeta.catalogVersion) {
                val schoolConfig = firestore.fetchSchoolConfig()
                val teachers = ensureBuiltInAdmins(firestore.fetchTeachers())
                val classes = firestore.fetchClasses()
                val subjects = firestore.fetchSubjects()

                dao.upsertSchoolConfig(schoolConfig.toEntity())
                dao.replaceTeachers(teachers.map { it.toEntity() })
                dao.replaceClasses(classes.map { it.toEntity() })
                dao.replaceSubjects(subjects.map { it.toEntity() })
                updatedSections.add("Danh mục")
            }

            val hasLocalAssignments = dao.getAssignments().isNotEmpty()
            if (force || localMeta == null || !hasLocalAssignments || remoteMeta.assignmentsVersion != localMeta.assignmentsVersion) {
                val assignments = firestore.fetchAssignments()
                dao.replaceAssignments(assignments.map { it.toEntity() })
                updatedSections.add("Phân công")
            }

            val hasLocalWeeks = dao.getWeeks().isNotEmpty()
            if (force || localMeta == null || !hasLocalWeeks || remoteMeta.weeksVersion != localMeta.weeksVersion) {
                val weeks = firestore.fetchWeeksConfig()
                if (weeks.isNotEmpty()) {
                    dao.replaceWeeks(weeks.map { it.toEntity() })
                }
                updatedSections.add("Cấu hình tuần")
            }

            if (force || localMeta == null || remoteMeta.deletedSlotsVersion != localMeta.deletedSlotsVersion) {
                val deletedKeys = firestore.fetchDeletedSlotKeys()
                dao.upsertDeletedSlots(
                    DeletedSlotsEntity(
                        id = 1,
                        keysJson = LocalJson.stringListToJson(deletedKeys)
                    )
                )
                updatedSections.add("Tiết đã xóa")
            }

            val schoolConfig = dao.getSchoolConfig()?.toDomain() ?: SchoolConfig()
            val weeks = dao.getWeeks().map { it.toDomain() }
            val weekToSync = targetWeek ?: TimetableCalculator.calculateCurrentWeek(
                today = VnDate.today(),
                schoolConfig = schoolConfig,
                weeks = weeks
            )

            val localWeekVersion = dao.getWeekSyncVersion(weekToSync)
            val needSlotsFetch = force ||
                localWeekVersion == null ||
                localWeekVersion.slotsVersion != remoteMeta.slotsVersion

            if (needSlotsFetch) {
                val previousLocalSlots = dao.getSlotsForWeek(weekToSync).map { it.toDomain() }
                val slots = firestore.fetchTimetablesForWeek(weekToSync)
                if (previousLocalSlots.isNotEmpty() && previousLocalSlots.toSet() != slots.toSet()) {
                    dao.upsertSlotSnapshot(
                        TimetableSlotSnapshotEntity(
                            week = weekToSync,
                            slotsJson = LocalJson.slotsToJson(previousLocalSlots),
                            savedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
                dao.replaceSlotsForWeek(weekToSync, slots.map { it.toEntity() })
                dao.upsertWeekSyncVersion(
                    WeekSyncVersionEntity(
                        weekNumber = weekToSync,
                        slotsVersion = remoteMeta.slotsVersion,
                        syncedAtMillis = System.currentTimeMillis()
                    )
                )
                updatedSections.add("TKB Tuần $weekToSync")
            }

            dao.upsertSyncMetadata(
                SyncMetadataEntity(
                    id = 1,
                    slotsVersion = remoteMeta.slotsVersion,
                    assignmentsVersion = remoteMeta.assignmentsVersion,
                    catalogVersion = remoteMeta.catalogVersion,
                    weeksVersion = remoteMeta.weeksVersion,
                    constraintsVersion = remoteMeta.constraintsVersion,
                    deletedSlotsVersion = remoteMeta.deletedSlotsVersion,
                    updatedAt = remoteMeta.updatedAt,
                    lastSyncedAtMillis = System.currentTimeMillis()
                )
            )

            val summary = if (updatedSections.isEmpty()) {
                "Dữ liệu đã mới nhất (chỉ đọc 1 tài liệu kiểm tra phiên bản)."
            } else {
                "Đã đồng bộ: ${updatedSections.joinToString(", ")}."
            }
            SyncOutcome(success = true, message = summary, updatedSections = updatedSections)
        } catch (e: Exception) {
            SyncOutcome(
                success = false,
                offlineFallback = true,
                message = "Không thể kết nối Firestore — đang hiển thị dữ liệu lưu trên máy."
            )
        }
    }

    /**
     * Loads a specific week on-demand if not yet cached for the latest slotsVersion.
     */
    suspend fun ensureWeekLoaded(weekNumber: Int): SyncOutcome = withContext(Dispatchers.IO) {
        val localMeta = dao.getSyncMetadata()
        val weekSync = dao.getWeekSyncVersion(weekNumber)
        if (localMeta != null && weekSync != null && weekSync.slotsVersion == localMeta.slotsVersion) {
            return@withContext SyncOutcome(success = true, message = "Đã có sẵn Tuần $weekNumber trong bộ nhớ máy.")
        }
        return@withContext syncIncremental(targetWeek = weekNumber, force = false)
    }

    /**
     * Synchronous-friendly local Room snapshot for Widgets & Alarm Notification Receivers.
     */
    suspend fun getLocalSnapshotForWeek(weekNumber: Int? = null): LocalSchoolSnapshot = withContext(Dispatchers.IO) {
        val config = dao.getSchoolConfig()?.toDomain() ?: SchoolConfig()
        val weeks = dao.getWeeks().map { it.toDomain() }
        val resolvedWeek = weekNumber ?: TimetableCalculator.calculateCurrentWeek(
            today = VnDate.today(),
            schoolConfig = config,
            weeks = weeks
        )
        val teachers = dao.getTeachers().map { it.toDomain() }
        val classes = dao.getClasses().map { it.toDomain() }
        val subjects = dao.getSubjects().map { it.toDomain() }
        val assignments = dao.getAssignments().map { it.toDomain() }
        val slots = dao.getSlotsForWeek(resolvedWeek).map { it.toDomain() }
        val deletedKeys = dao.getDeletedSlots()?.toKeySet() ?: emptySet()
        val lessonNotes = dao.getAllLessonNotes().associate { it.noteKey to it.content }
        val prefs = preferences.getSnapshot()
        val loggedInTeacher = prefs.loggedInTeacherId?.let { id ->
            teachers.firstOrNull { it.id == id }
        }

        LocalSchoolSnapshot(
            weekNumber = resolvedWeek,
            schoolConfig = config,
            weeks = weeks,
            teachers = teachers,
            classes = classes,
            subjects = subjects,
            assignments = assignments,
            slots = slots,
            deletedSlotKeys = deletedKeys,
            lessonNotes = lessonNotes,
            loggedInTeacher = loggedInTeacher,
            preferences = prefs
        )
    }
}

data class LocalSchoolSnapshot(
    val weekNumber: Int,
    val schoolConfig: SchoolConfig,
    val weeks: List<WeekInfo>,
    val teachers: List<Teacher>,
    val classes: List<SchoolClass>,
    val subjects: List<Subject>,
    val assignments: List<Assignment>,
    val slots: List<TimetableSlot>,
    val deletedSlotKeys: Set<String>,
    val lessonNotes: Map<String, String> = emptyMap(),
    val loggedInTeacher: Teacher?,
    val preferences: com.example.data.local.UserPreferencesState
)
