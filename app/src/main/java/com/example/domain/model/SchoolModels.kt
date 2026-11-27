package com.example.domain.model

import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime

data class TimeConfig(
    val morningStart: String = "07:15",
    val afternoonStart: String = "14:00",
    val periodDuration: Int = 45,
    val recessDuration: Int = 10,
    val transitionDuration: Int = 0
)

data class SchoolConfig(
    val name: String = "Trường THCS Lê Hữu Lập",
    val academicYear: String = "2026 - 2027",
    val startDate: String = "2026-09-07",
    val timeConfig: TimeConfig = TimeConfig()
)

data class Teacher(
    val id: String,
    val name: String,
    val shortName: String,
    val email: String,
    val department: String = "",
    val mainSubject: String = "",
    val homeroomClass: String? = null,
    val accountRole: String? = null,
    val isBGH: Boolean = false,
    val password: String? = null
) {
    val isAdminOrBgh: Boolean
        get() = isBGH ||
            accountRole.equals("admin", ignoreCase = true) ||
            accountRole.equals("bgh", ignoreCase = true) ||
            id == "gv-hung" ||
            id == "gv-ha"

    val displayShortName: String
        get() = shortName.ifBlank {
            name.trim().split(" ").takeLast(2).joinToString(" ")
        }

    val roleLabelVietnamese: String
        get() = when {
            accountRole.equals("admin", ignoreCase = true) -> "Quản trị viên"
            isBGH || accountRole.equals("bgh", ignoreCase = true) -> "Ban Giám hiệu"
            accountRole.equals("group_leader", ignoreCase = true) -> "Tổ trưởng chuyên môn"
            else -> "Giáo viên"
        }
}

data class SchoolClass(
    val id: String,
    val name: String,
    val grade: Int = 6,
    val homeroomTeacherId: String = "",
    val room: String = "",
    val campusId: String = ""
)

data class Subject(
    val id: String,
    val name: String,
    val code: String,
    val afternoonTeacherOverride: Map<String, String> = emptyMap()
) {
    val shortLabel: String
        get() = code.ifBlank { name }
}

data class Assignment(
    val id: String,
    val teacherId: String,
    val subjectId: String,
    val classId: String,
    val periodsPerWeek: Int = 0,
    val session: String? = null
)

data class TimetableSlot(
    val id: String,
    val week: Int,
    val classId: String,
    val day: String, // "T2".."T7"
    val period: Int, // 1..5
    val subjectId: String,
    val teacherId: String,
    val room: String? = null,
    val note: String? = null,
    val customLabel: String? = null,
    val session: String = "morning", // "morning" | "afternoon"
    val actualPeriod: Int? = null
) {
    val isAfternoon: Boolean get() = session == "afternoon"
}

data class WeekInfo(
    val weekNumber: Int,
    val startDate: String,
    val endDate: String,
    val name: String,
    val isLocked: Boolean = false,
    val lockedSubjects: List<Subject>? = null,
    val lockedAssignments: List<Assignment>? = null
)

data class SyncMetadata(
    val slotsVersion: Long = 0L,
    val assignmentsVersion: Long = 0L,
    val catalogVersion: Long = 0L,
    val weeksVersion: Long = 0L,
    val constraintsVersion: Long = 0L,
    val deletedSlotsVersion: Long = 0L,
    val updatedAt: String = ""
)

enum class PeriodLiveStatus {
    PAST,
    TEACHING_NOW,
    NEXT_PERIOD,
    UPCOMING
}

data class ResolvedPeriod(
    val slotId: String,
    val week: Int,
    val day: String,
    val period: Int,
    val session: String, // "morning" | "afternoon"
    val classId: String,
    val className: String,
    val subjectId: String,
    val subjectName: String,
    val subjectShort: String,
    val teacherId: String,
    val teacherShortName: String,
    val room: String,
    val note: String?,
    val customLabel: String?,
    val startTime: VnTime,
    val endTime: VnTime,
    val liveStatus: PeriodLiveStatus = PeriodLiveStatus.UPCOMING,
    val isChanged: Boolean = false
) {
    val isAfternoon: Boolean get() = session == "afternoon"
    val timeRangeText: String get() = "${startTime.formatHHmm()} – ${endTime.formatHHmm()}"
}

enum class CalendarEventCategory(val key: String, val label: String) {
    MEETING("meeting", "Họp chuyên môn"),
    EXAM("exam", "Coi thi / Kiểm tra"),
    ANNIVERSARY("anniversary", "Kỷ niệm"),
    OTHER("other", "Công việc khác");

    companion object {
        fun fromKey(key: String?): CalendarEventCategory =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER
    }
}

data class CalendarEvent(
    val id: Long = 0L,
    val title: String,
    val note: String = "",
    val category: CalendarEventCategory = CalendarEventCategory.MEETING,
    val isLunar: Boolean = false,
    val year: Int,
    val month: Int,
    val day: Int,
    val isRecurringYearly: Boolean = false,
    val timeLabel: String = "",
    val createdAtMillis: Long = 0L
)

data class WorkloadBreakdownRow(
    val key: String,
    val label: String,
    val weekMorning: Int,
    val weekAfternoon: Int,
    val assignedPerWeek: Int,
    val yearMorning: Int,
    val yearAfternoon: Int
) {
    val weekTotal: Int get() = weekMorning + weekAfternoon
    val yearTotal: Int get() = yearMorning + yearAfternoon
}

data class WorkloadStatsSummary(
    val selectedWeek: Int = 1,
    val weekMorningCount: Int = 0,
    val weekAfternoonCount: Int = 0,
    val weekAssignedQuota: Int = 0,
    val yearCachedWeeksCount: Int = 0,
    val yearCachedWeeksList: List<Int> = emptyList(),
    val yearMorningCount: Int = 0,
    val yearAfternoonCount: Int = 0,
    val byClass: List<WorkloadBreakdownRow> = emptyList(),
    val bySubject: List<WorkloadBreakdownRow> = emptyList()
) {
    val weekTotalCount: Int get() = weekMorningCount + weekAfternoonCount
    val yearTotalCount: Int get() = yearMorningCount + yearAfternoonCount
}

data class SubstituteSuggestion(
    val teacher: Teacher,
    val teachesSameClass: Boolean,
    val sameDepartment: Boolean,
    val sameSubject: Boolean,
    val swapSlotHint: String? = null
) {
    val priorityScore: Int
        get() = (if (teachesSameClass) 4 else 0) +
            (if (sameDepartment) 2 else 0) +
            (if (sameSubject) 1 else 0) +
            (if (swapSlotHint != null) 2 else 0)
}

data class DailyForecast(
    val date: VnDate,
    val weatherCode: Int,
    val tempMax: Int,
    val tempMin: Int,
    val precipitationProbability: Int
)

data class WeatherSnapshot(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val currentTemp: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windSpeedKmh: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val todayHigh: Int,
    val todayLow: Int,
    val todayRainProb: Int,
    val dailyForecasts: List<DailyForecast>,
    val updatedAtMillis: Long
)
