package com.example.data.remote

import android.content.Context
import com.example.domain.model.Assignment
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.Subject
import com.example.domain.model.SyncMetadata
import com.example.domain.model.Teacher
import com.example.domain.model.TimeConfig
import com.example.domain.model.TimetableSlot
import com.example.domain.model.WeekInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * STRICTLY READ-ONLY Firestore client for project `tkbthcslhl-2026` (database `(default)`).
 * SAFETY INVARIANT: This class ONLY calls `.get()`. It NEVER calls `.set()`, `.update()`,
 * `.delete()`, or `.add()` on any Firestore document or collection.
 */
class FirestoreReadOnlyClient(private val context: Context) {

    companion object {
        const val EXPECTED_PROJECT_ID = "tkbthcslhl-2026"
        const val DEFAULT_ADMIN_PASSWORD = "Admin@123"
        const val DEFAULT_TEACHER_PASSWORD = "Lhl@2026"
    }

    fun isFirebaseConfigured(): Boolean {
        return getFirestoreOrNull() != null
    }

    fun configuredProjectId(): String? {
        return runCatching {
            val apps = FirebaseApp.getApps(context)
            val app = apps.firstOrNull() ?: FirebaseApp.initializeApp(context)
            app?.options?.projectId
        }.getOrNull()
    }

    private fun getFirestoreOrNull(): FirebaseFirestore? {
        return runCatching {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(context)
                ?: return null
            FirebaseFirestore.getInstance(app)
        }.getOrNull()
    }

    private fun requireFirestore(): FirebaseFirestore {
        return getFirestoreOrNull()
            ?: throw IllegalStateException(
                "Chưa cấu hình file google-services.json của dự án $EXPECTED_PROJECT_ID."
            )
    }

    /** Single-read check against `cai_dat_he_thong/sync_metadata` */
    suspend fun fetchSyncMetadata(): SyncMetadata {
        val doc = requireFirestore()
            .collection("cai_dat_he_thong")
            .document("sync_metadata")
            .get(Source.SERVER)
            .await()

        if (!doc.exists()) return SyncMetadata()
        return SyncMetadata(
            slotsVersion = doc.getLong("slotsVersion") ?: 0L,
            assignmentsVersion = doc.getLong("assignmentsVersion") ?: 0L,
            catalogVersion = doc.getLong("catalogVersion") ?: 0L,
            weeksVersion = doc.getLong("weeksVersion") ?: 0L,
            constraintsVersion = doc.getLong("constraintsVersion") ?: 0L,
            deletedSlotsVersion = doc.getLong("deletedSlotsVersion") ?: 0L,
            updatedAt = doc.getString("updatedAt").orEmpty()
        )
    }

    /** Read-only fetch of `cai_dat_he_thong/tai_khoan_admin` password field */
    suspend fun fetchAdminPassword(): String {
        val doc = runCatching {
            requireFirestore()
                .collection("cai_dat_he_thong")
                .document("tai_khoan_admin")
                .get()
                .await()
        }.getOrNull()
        val raw = doc?.getString("password")
        return if (raw.isNullOrEmpty()) DEFAULT_ADMIN_PASSWORD else raw
    }

    /** Read-only fetch of `schools/thcs_lehuulap` */
    suspend fun fetchSchoolConfig(): SchoolConfig {
        val doc = requireFirestore()
            .collection("schools")
            .document("thcs_lehuulap")
            .get()
            .await()

        if (!doc.exists()) return SchoolConfig()

        val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: "Trường THCS Lê Hữu Lập"
        val academicYear = doc.getString("academicYear")?.takeIf { it.isNotBlank() } ?: "2026 - 2027"
        val startDate = doc.getString("startDate")?.takeIf { it.isNotBlank() }
            ?: doc.getString("yearStartDate")?.takeIf { it.isNotBlank() }
            ?: "2026-09-07"

        val tcMap = doc.get("timeConfig") as? Map<*, *>
        val timeConfig = TimeConfig(
            morningStart = (tcMap?.get("morningStart") as? String)?.takeIf { it.isNotBlank() } ?: "07:15",
            afternoonStart = (tcMap?.get("afternoonStart") as? String)?.takeIf { it.isNotBlank() } ?: "14:00",
            periodDuration = (tcMap?.get("periodDuration") as? Number)?.toInt() ?: 45,
            recessDuration = (tcMap?.get("recessDuration") as? Number)?.toInt() ?: 10,
            transitionDuration = (tcMap?.get("transitionDuration") as? Number)?.toInt() ?: 0
        )

        return SchoolConfig(
            name = name,
            academicYear = academicYear,
            startDate = startDate,
            timeConfig = timeConfig
        )
    }

    /** Read-only fetch of `teachers` collection */
    suspend fun fetchTeachers(): List<Teacher> {
        val snap = requireFirestore().collection("teachers").get().await()
        return snap.documents.map { doc ->
            Teacher(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                name = doc.getString("name").orEmpty(),
                shortName = doc.getString("shortName").orEmpty(),
                email = doc.getString("email").orEmpty(),
                department = doc.getString("department").orEmpty(),
                mainSubject = doc.getString("mainSubject").orEmpty(),
                homeroomClass = doc.getString("homeroomClass")?.takeIf { it.isNotBlank() },
                accountRole = doc.getString("accountRole")?.takeIf { it.isNotBlank() },
                isBGH = doc.getBoolean("isBGH") ?: false,
                password = doc.getString("password")
            )
        }
    }

    /** Read-only fetch of `classes` collection */
    suspend fun fetchClasses(): List<SchoolClass> {
        val snap = requireFirestore().collection("classes").get().await()
        return snap.documents.map { doc ->
            SchoolClass(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: doc.id,
                grade = (doc.get("grade") as? Number)?.toInt() ?: 6,
                homeroomTeacherId = doc.getString("homeroomTeacherId").orEmpty(),
                room = doc.getString("room").orEmpty(),
                campusId = doc.getString("campusId").orEmpty()
            )
        }
    }

    /** Read-only fetch of `subjects` collection */
    suspend fun fetchSubjects(): List<Subject> {
        val snap = requireFirestore().collection("subjects").get().await()
        return snap.documents.map { doc ->
            val rawOverride = doc.get("afternoonTeacherOverride") as? Map<*, *>
            val overrideMap = rawOverride?.entries
                ?.mapNotNull { (k, v) ->
                    val key = k as? String
                    val value = v as? String
                    if (!key.isNullOrBlank() && !value.isNullOrBlank()) key to value else null
                }
                ?.toMap()
                .orEmpty()

            Subject(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                name = doc.getString("name").orEmpty(),
                code = doc.getString("code").orEmpty(),
                afternoonTeacherOverride = overrideMap
            )
        }
    }

    /** Read-only fetch of `assignments` collection */
    suspend fun fetchAssignments(): List<Assignment> {
        val snap = requireFirestore().collection("assignments").get().await()
        return snap.documents.map { doc ->
            Assignment(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                teacherId = doc.getString("teacherId").orEmpty(),
                subjectId = doc.getString("subjectId").orEmpty(),
                classId = doc.getString("classId").orEmpty(),
                periodsPerWeek = (doc.get("periodsPerWeek") as? Number)?.toInt() ?: 0,
                session = doc.getString("session")?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** Read-only filtered query of `timetables` for ONE specific week only (`whereEqualTo("week", weekNumber)`) */
    suspend fun fetchTimetablesForWeek(weekNumber: Int): List<TimetableSlot> {
        val snap = requireFirestore()
            .collection("timetables")
            .whereEqualTo("week", weekNumber)
            .get()
            .await()

        return snap.documents.map { doc ->
            val rawSession = doc.getString("session")
            val normalizedSession = if (rawSession == "afternoon") "afternoon" else "morning"
            val rawActual = (doc.get("actualPeriod") as? Number)?.toInt()
                ?: (doc.get("actualPeriod") as? String)?.toIntOrNull()
            TimetableSlot(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                week = (doc.get("week") as? Number)?.toInt() ?: weekNumber,
                classId = doc.getString("classId").orEmpty(),
                day = doc.getString("day").orEmpty(),
                period = (doc.get("period") as? Number)?.toInt() ?: 1,
                subjectId = doc.getString("subjectId").orEmpty(),
                teacherId = doc.getString("teacherId").orEmpty(),
                room = doc.getString("room")?.takeIf { it.isNotBlank() },
                note = doc.getString("note")?.takeIf { it.isNotBlank() },
                customLabel = doc.getString("customLabel")?.takeIf { it.isNotBlank() },
                session = normalizedSession,
                actualPeriod = rawActual?.takeIf { it in 1..5 }
            )
        }
    }

    /** Read-only fetch of `cai_dat_he_thong/weeks_config` including locked week snapshots */
    suspend fun fetchWeeksConfig(): List<WeekInfo> {
        val doc = requireFirestore()
            .collection("cai_dat_he_thong")
            .document("weeks_config")
            .get()
            .await()

        if (!doc.exists()) return emptyList()
        val rawWeeks = doc.get("weeks") as? List<*> ?: return emptyList()

        return rawWeeks.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val weekNumber = (map["weekNumber"] as? Number)?.toInt() ?: return@mapNotNull null
            val startDate = map["startDate"] as? String ?: ""
            val endDate = map["endDate"] as? String ?: ""
            val name = map["name"] as? String ?: "Tuần $weekNumber"
            val isLocked = map["isLocked"] as? Boolean ?: false

            val lockedSubjects = (map["lockedSubjects"] as? List<*>)?.mapNotNull { subItem ->
                val sMap = subItem as? Map<*, *> ?: return@mapNotNull null
                val sId = sMap["id"] as? String ?: return@mapNotNull null
                val rawOverride = sMap["afternoonTeacherOverride"] as? Map<*, *>
                val overrideMap = rawOverride?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String
                        val value = v as? String
                        if (!key.isNullOrBlank() && !value.isNullOrBlank()) key to value else null
                    }
                    ?.toMap()
                    .orEmpty()
                Subject(
                    id = sId,
                    name = sMap["name"] as? String ?: sId,
                    code = sMap["code"] as? String ?: "",
                    afternoonTeacherOverride = overrideMap
                )
            }

            val lockedAssignments = (map["lockedAssignments"] as? List<*>)?.mapNotNull { assItem ->
                val aMap = assItem as? Map<*, *> ?: return@mapNotNull null
                Assignment(
                    id = aMap["id"] as? String ?: "",
                    teacherId = aMap["teacherId"] as? String ?: "",
                    subjectId = aMap["subjectId"] as? String ?: "",
                    classId = aMap["classId"] as? String ?: "",
                    periodsPerWeek = (aMap["periodsPerWeek"] as? Number)?.toInt() ?: 0,
                    session = aMap["session"] as? String
                )
            }

            WeekInfo(
                weekNumber = weekNumber,
                startDate = startDate,
                endDate = endDate,
                name = name,
                isLocked = isLocked,
                lockedSubjects = lockedSubjects,
                lockedAssignments = lockedAssignments
            )
        }.sortedBy { it.weekNumber }
    }

    /** Read-only fetch of `cai_dat_he_thong/deleted_slots` */
    suspend fun fetchDeletedSlotKeys(): List<String> {
        val doc = requireFirestore()
            .collection("cai_dat_he_thong")
            .document("deleted_slots")
            .get()
            .await()

        if (!doc.exists()) return emptyList()
        val rawKeys = doc.get("keys") as? List<*> ?: return emptyList()
        return rawKeys.mapNotNull { it as? String }
    }
}
