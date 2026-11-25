package com.example.domain.model

import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime

object TimetableCalculator {

    const val MIN_WEEK = 1
    const val MAX_WEEK = 35
    val SCHOOL_DAYS = listOf("T2", "T3", "T4", "T5", "T6", "T7")

    /**
     * Exact business rule matching the web version:
     * - Base startDate from schools/thcs_lehuulap (default "2026-09-07").
     * - If today is Sunday, add +1 day (Monday) to automatically preview next week.
     * - Clamped to 1..35.
     */
    fun calculateCurrentWeek(
        today: VnDate = VnDate.today(),
        schoolConfig: SchoolConfig = SchoolConfig(),
        weeks: List<WeekInfo> = emptyList()
    ): Int {
        val effectiveDate = if (today.isSunday) today.plusDays(1) else today

        // First check explicit weeks_config date ranges if available
        for (week in weeks) {
            val start = VnDate.parseIso(week.startDate)
            val end = VnDate.parseIso(week.endDate)
            if (start != null && end != null && effectiveDate >= start && effectiveDate <= end) {
                return week.weekNumber.coerceIn(MIN_WEEK, MAX_WEEK)
            }
        }

        val startDate = VnDate.parseIso(schoolConfig.startDate) ?: VnDate(2026, 9, 7)
        val diffDays = VnDate.daysBetween(startDate, effectiveDate)
        val weekNum = if (diffDays < 0) 1 else (diffDays / 7) + 1
        return weekNum.coerceIn(MIN_WEEK, MAX_WEEK)
    }

    /**
     * Calculate exact week date range (Monday..Sunday) for a given weekNumber.
     */
    fun getWeekDateRange(
        weekNumber: Int,
        schoolConfig: SchoolConfig,
        weeks: List<WeekInfo>
    ): Pair<VnDate, VnDate> {
        val configured = weeks.firstOrNull { it.weekNumber == weekNumber }
        if (configured != null) {
            val s = VnDate.parseIso(configured.startDate)
            val e = VnDate.parseIso(configured.endDate)
            if (s != null && e != null) return s to e
        }
        val baseStart = VnDate.parseIso(schoolConfig.startDate) ?: VnDate(2026, 9, 7)
        val monday = baseStart.plusDays((weekNumber - 1) * 7)
        val sunday = monday.plusDays(6)
        return monday to sunday
    }

    /**
     * Exact period start & end time rules:
     * - Morning Monday (T2): recess after Period 3.
     * - Morning T3..T7 & Afternoon (all days): recess after Period 2.
     */
    fun calculatePeriodTimeRange(
        day: String,
        period: Int,
        session: String,
        config: TimeConfig
    ): Pair<VnTime, VnTime> {
        val isAfternoon = session == "afternoon"
        val baseStart = if (isAfternoon) {
            VnTime.parseHHmm(config.afternoonStart, 14, 0)
        } else {
            VnTime.parseHHmm(config.morningStart, 7, 15)
        }

        val recessAfterPeriod = if (!isAfternoon && day == "T2") 3 else 2
        val safePeriod = period.coerceIn(1, 5)

        var offsetMinutes = 0
        for (p in 1 until safePeriod) {
            offsetMinutes += config.periodDuration
            offsetMinutes += if (p == recessAfterPeriod) {
                config.recessDuration
            } else {
                config.transitionDuration
            }
        }
        val start = baseStart.plusMinutes(offsetMinutes)
        val end = start.plusMinutes(config.periodDuration)
        return start to end
    }

    /**
     * Exact effective teacher resolution matching web:
     * 1. If session == "afternoon" and subject.afternoonTeacherOverride[classId] exists -> use it.
     * 2. Else if slot.teacherId is non-blank -> use slot.teacherId.
     * 3. Else lookup in effective assignments by classId + subjectId (matching session if specified).
     */
    fun resolveEffectiveTeacherId(
        slot: TimetableSlot,
        subjectsById: Map<String, Subject>,
        assignments: List<Assignment>
    ): String {
        val normalizedSession = if (slot.session == "afternoon") "afternoon" else "morning"
        val subject = subjectsById[slot.subjectId]

        if (normalizedSession == "afternoon") {
            val overrideTeacher = subject?.afternoonTeacherOverride?.get(slot.classId)
            if (!overrideTeacher.isNullOrBlank()) {
                return overrideTeacher
            }
        }

        if (slot.teacherId.isNotBlank()) {
            return slot.teacherId
        }

        val exactSessionMatch = assignments.firstOrNull {
            it.classId == slot.classId &&
                it.subjectId == slot.subjectId &&
                (it.session ?: "morning") == normalizedSession
        }
        if (exactSessionMatch != null && exactSessionMatch.teacherId.isNotBlank()) {
            return exactSessionMatch.teacherId
        }

        val anySessionMatch = assignments.firstOrNull {
            it.classId == slot.classId && it.subjectId == slot.subjectId
        }
        return anySessionMatch?.teacherId.orEmpty()
    }

    /**
     * Resolve all teaching periods for a week, respecting:
     * - Locked week snapshots (lockedSubjects, lockedAssignments)
     * - Deleted slots filter (only for unlocked weeks)
     * - Either Teacher mode ("TKB của tôi") or Homeroom Class mode ("Lớp CN")
     */
    fun resolveWeekPeriods(
        weekNumber: Int,
        rawSlots: List<TimetableSlot>,
        schoolConfig: SchoolConfig,
        weekInfo: WeekInfo?,
        deletedSlotKeys: Set<String>,
        teachers: List<Teacher>,
        classes: List<SchoolClass>,
        subjects: List<Subject>,
        assignments: List<Assignment>,
        filterTeacherId: String? = null,
        filterClassId: String? = null,
        todayDate: VnDate? = null,
        nowTime: VnTime? = null
    ): List<ResolvedPeriod> {
        val isLocked = weekInfo?.isLocked == true
        val effectiveSubjects = if (isLocked && !weekInfo?.lockedSubjects.isNullOrEmpty()) {
            weekInfo!!.lockedSubjects!!
        } else {
            subjects
        }
        val effectiveAssignments = if (isLocked && !weekInfo?.lockedAssignments.isNullOrEmpty()) {
            weekInfo!!.lockedAssignments!!
        } else {
            assignments
        }

        val subjectsById = effectiveSubjects.associateBy { it.id }
        val teachersById = teachers.associateBy { it.id }
        val classesById = classes.associateBy { it.id }

        val activeSlots = rawSlots.filter { slot ->
            slot.week == weekNumber && (isLocked || slot.id !in deletedSlotKeys)
        }

        val resolved = activeSlots.mapNotNull { slot ->
            val session = if (slot.session == "afternoon") "afternoon" else "morning"
            val effectiveTeacherId = resolveEffectiveTeacherId(
                slot = slot,
                subjectsById = subjectsById,
                assignments = effectiveAssignments
            )

            if (filterClassId != null) {
                if (!slot.classId.equals(filterClassId, ignoreCase = true)) return@mapNotNull null
            } else if (filterTeacherId != null) {
                if (effectiveTeacherId != filterTeacherId) return@mapNotNull null
            }

            val subject = subjectsById[slot.subjectId]
            val schoolClass = classesById[slot.classId]
            val teacher = teachersById[effectiveTeacherId]
            val (start, end) = calculatePeriodTimeRange(
                day = slot.day,
                period = slot.period,
                session = session,
                config = schoolConfig.timeConfig
            )

            val resolvedSubjectName = slot.customLabel?.takeIf { it.isNotBlank() }
                ?: subject?.name
                ?: slot.subjectId
            val resolvedSubjectShort = slot.customLabel?.takeIf { it.isNotBlank() }
                ?: subject?.shortLabel
                ?: resolvedSubjectName
            val resolvedRoom = slot.room?.takeIf { it.isNotBlank() }
                ?: schoolClass?.room.orEmpty()

            ResolvedPeriod(
                slotId = slot.id,
                week = slot.week,
                day = slot.day,
                period = slot.period,
                session = session,
                classId = slot.classId,
                className = schoolClass?.name?.ifBlank { slot.classId } ?: slot.classId,
                subjectId = slot.subjectId,
                subjectName = resolvedSubjectName,
                subjectShort = resolvedSubjectShort,
                teacherId = effectiveTeacherId,
                teacherShortName = teacher?.displayShortName ?: effectiveTeacherId,
                room = resolvedRoom,
                note = slot.note?.takeIf { it.isNotBlank() },
                customLabel = slot.customLabel?.takeIf { it.isNotBlank() },
                startTime = start,
                endTime = end
            )
        }.sortedWith(
            compareBy<ResolvedPeriod>(
                { SCHOOL_DAYS.indexOf(it.day).let { idx -> if (idx < 0) 99 else idx } },
                { if (it.isAfternoon) 1 else 0 },
                { it.period }
            )
        )

        if (todayDate == null || nowTime == null || todayDate.isSunday) {
            return resolved
        }

        val todayCode = todayDate.shortDayCode
        val todayPeriods = resolved.filter { it.day == todayCode }
        val nextSlotId = todayPeriods.firstOrNull { nowTime < it.startTime }?.slotId

        return resolved.map { item ->
            if (item.day != todayCode) {
                item
            } else {
                val status = when {
                    nowTime >= item.startTime && nowTime < item.endTime -> PeriodLiveStatus.TEACHING_NOW
                    nowTime >= item.endTime -> PeriodLiveStatus.PAST
                    item.slotId == nextSlotId -> PeriodLiveStatus.NEXT_PERIOD
                    else -> PeriodLiveStatus.UPCOMING
                }
                item.copy(liveStatus = status)
            }
        }
    }

    /** Strips redundant leading "Lớp " prefix for compact display, e.g. "Lớp 7A2" -> "7A2" */
    fun compactClassName(className: String): String {
        val trimmed = className.trim()
        return if (trimmed.startsWith("Lớp ", ignoreCase = true)) {
            trimmed.substring(4).trim().ifBlank { trimmed }
        } else {
            trimmed
        }
    }

    /**
     * Compact 1-line summary for Glance Widget & Notification, e.g.:
     * "Sáng: T1 6A1 (Tin) · T3 7A2 (Tin) · T4 9A3 (Tin)"
     */
    fun formatCompactSessionLine(
        sessionLabel: String,
        periods: List<ResolvedPeriod>,
        isHomeroomMode: Boolean = false
    ): String {
        if (periods.isEmpty()) return ""
        val items = periods.joinToString(" · ") { p ->
            if (isHomeroomMode) {
                "T${p.period} ${p.subjectShort} (${p.teacherShortName})"
            } else {
                "T${p.period} ${compactClassName(p.className)} (${p.subjectShort})"
            }
        }
        return "$sessionLabel: $items"
    }

    /**
     * Rich summary for 06:00 AM daily teaching reminder notification.
     */
    fun formatNotificationSummary(
        dayLabel: String,
        morning: List<ResolvedPeriod>,
        afternoon: List<ResolvedPeriod>
    ): String {
        val parts = mutableListOf<String>()
        if (morning.isNotEmpty()) {
            val s = morning.joinToString(", ") {
                "T${it.period} ${it.className} (${it.startTime.formatHHmm()} - ${it.subjectShort})"
            }
            parts.add("Sáng: $s")
        }
        if (afternoon.isNotEmpty()) {
            val c = afternoon.joinToString(", ") {
                "T${it.period} ${it.className} (${it.startTime.formatHHmm()} - ${it.subjectShort})"
            }
            parts.add("Chiều: $c")
        }
        return if (parts.isEmpty()) {
            "Hôm nay ($dayLabel) không có tiết dạy."
        } else {
            parts.joinToString("\n")
        }
    }

    fun resolveHomeroomClassId(teacher: Teacher, classes: List<SchoolClass>): String? {
        if (!teacher.homeroomClass.isNullOrBlank()) return teacher.homeroomClass
        return classes.firstOrNull { it.homeroomTeacherId == teacher.id }?.id
    }
}
