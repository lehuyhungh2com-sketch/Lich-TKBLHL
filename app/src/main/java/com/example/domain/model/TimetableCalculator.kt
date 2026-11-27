package com.example.domain.model

import com.example.domain.lunar.LunarDate
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime

object TimetableCalculator {

    const val MIN_WEEK = 1
    const val MAX_WEEK = 35
    val SCHOOL_DAYS = listOf("T2", "T3", "T4", "T5", "T6", "T7")

    val AFTERNOON_MONTH_CYCLES: List<AfternoonMonthCycle> = listOf(
        AfternoonMonthCycle(month = 9, weeks = listOf(1, 2, 3, 4)),
        AfternoonMonthCycle(month = 10, weeks = listOf(5, 6, 7, 8)),
        AfternoonMonthCycle(month = 11, weeks = listOf(9, 10, 11, 12)),
        AfternoonMonthCycle(month = 12, weeks = listOf(13, 14, 15, 16)),
        AfternoonMonthCycle(month = 1, weeks = listOf(17, 18, 19, 20))
    )

    fun getAfternoonMonthCycle(weekNumber: Int): AfternoonMonthCycle {
        val safeWeek = weekNumber.coerceAtLeast(MIN_WEEK)
        AFTERNOON_MONTH_CYCLES.firstOrNull { safeWeek in it.weeks }?.let { return it }
        val groupIndex = (safeWeek - 1) / 4
        val startWeek = groupIndex * 4 + 1
        val month = ((8 + groupIndex) % 12) + 1
        return AfternoonMonthCycle(
            month = month,
            weeks = listOf(startWeek, startWeek + 1, startWeek + 2, startWeek + 3)
        )
    }

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
     * Exact actual teaching period resolution matching the Web timetable logic:
     * 1. Explicit actualPeriod (1..5) takes highest priority.
     * 2. Monday morning (T2): assembly/HĐTN is period 1; row periods 1..4 shift down +1 (1->2..4->5).
     * 3. Saturday morning (T7): SHL/HĐTN at period 5 stays period 5.
     * 4. Other days (T3..T6) or afternoon sessions keep slot.period.
     */
    fun getSlotActualPeriod(slot: TimetableSlot): Int {
        val explicit = slot.actualPeriod
        if (explicit != null && explicit in 1..5) {
            return explicit
        }

        val isAfternoon = slot.session == "afternoon"
        val subIdLower = slot.subjectId.lowercase()

        if (slot.day == "T2" && !isAfternoon) {
            val isAssembly = subIdLower.contains("hdtn") ||
                subIdLower.contains("chao-co") ||
                subIdLower.contains("shdc") ||
                subIdLower.contains("trai-nghiem")
            if (isAssembly) return 1
            if (slot.period in 1..4) return slot.period + 1
        }

        if (slot.day == "T7" && !isAfternoon) {
            val isClassMeeting = subIdLower.contains("shl") ||
                subIdLower.contains("sinh-hoat") ||
                subIdLower.contains("hdtn") ||
                subIdLower.contains("trai-nghiem")
            if (isClassMeeting && slot.period == 5) return 5
        }

        return slot.period
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

            val effectivePeriod = getSlotActualPeriod(slot)
            val subject = subjectsById[slot.subjectId]
            val schoolClass = classesById[slot.classId]
            val teacher = teachersById[effectiveTeacherId]
            val (start, end) = calculatePeriodTimeRange(
                day = slot.day,
                period = effectivePeriod,
                session = session,
                config = schoolConfig.timeConfig
            )

            val resolvedSubjectName = readableSubjectName(
                subjectId = slot.subjectId,
                subjectName = subject?.name,
                subjectCode = subject?.code,
                customLabel = slot.customLabel,
                session = session
            )
            val resolvedSubjectShort = resolvedSubjectName
            val resolvedRoom = slot.room?.takeIf { it.isNotBlank() }
                ?: schoolClass?.room.orEmpty()
            val hasAfternoonOverride = session == "afternoon" &&
                !subject?.afternoonTeacherOverride?.get(slot.classId).isNullOrBlank()
            val hideAfternoonTeacher = session == "afternoon" &&
                isClubOrHsgActivity(
                    subjectId = slot.subjectId,
                    subjectCode = subject?.code,
                    subjectName = subject?.name,
                    resolvedName = resolvedSubjectName
                ) &&
                !hasAfternoonOverride

            ResolvedPeriod(
                slotId = slot.id,
                week = slot.week,
                day = slot.day,
                period = effectivePeriod,
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
                endTime = end,
                hideAfternoonTeacher = hideAfternoonTeacher
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

    fun isClubOrHsgActivity(
        subjectId: String,
        subjectCode: String? = null,
        subjectName: String? = null,
        resolvedName: String? = null
    ): Boolean {
        val idLower = subjectId.trim().lowercase()
        val codeLower = subjectCode?.trim()?.lowercase().orEmpty()
        val nameLower = subjectName?.trim()?.lowercase().orEmpty()
        val resolvedLower = resolvedName?.trim()?.lowercase().orEmpty()
        return idLower in setOf("sub-clb", "sub-club", "clb", "shclb", "sub-bdhsg", "bdhsg") ||
            idLower.startsWith("clb-") ||
            idLower.startsWith("sub-clb-") ||
            idLower.contains("clb") ||
            idLower.contains("club") ||
            idLower.contains("bdhsg") ||
            codeLower.contains("clb") ||
            codeLower.contains("shclb") ||
            codeLower.contains("bdhsg") ||
            nameLower.contains("clb") ||
            nameLower.contains("shclb") ||
            nameLower.contains("câu lạc bộ") ||
            nameLower.contains("bồi dưỡng hsg") ||
            resolvedLower.contains("clb") ||
            resolvedLower.contains("bồi dưỡng hsg")
    }

    /**
     * Style 1 clean Vietnamese subject names (replaces technical codes like "LS&ĐL-SU", "KHTN-LY"),
     * and enforces afternoon club/HSG naming (never "Sinh hoạt lớp" in the afternoon).
     */
    fun readableSubjectName(
        subjectId: String,
        subjectName: String?,
        subjectCode: String?,
        customLabel: String? = null,
        session: String = "morning"
    ): String {
        if (!customLabel.isNullOrBlank()) return customLabel.trim()

        val idTrimmed = subjectId.trim()
        val idLower = idTrimmed.lowercase()
        val codeLower = subjectCode?.trim()?.lowercase().orEmpty()
        val nameLower = subjectName?.trim()?.lowercase().orEmpty()
        val isAfternoon = session == "afternoon"

        val rawCombined = listOfNotNull(subjectId, subjectCode, subjectName)
            .joinToString(" ")
            .lowercase()
            .trim()

        val codeOrId = listOfNotNull(subjectCode, subjectId)
            .joinToString(" ")
            .lowercase()

        // Specific prefix: CLB-... or sub-clb-... -> "SH CLB <SUFFIX>"
        if (idLower.startsWith("sub-clb-")) {
            val suffix = idTrimmed.substring(8).trim().uppercase()
            return if (suffix.isNotEmpty()) "SH CLB $suffix" else "Sinh hoạt CLB"
        }
        if (idLower.startsWith("clb-")) {
            val suffix = idTrimmed.substring(4).trim().uppercase()
            return if (suffix.isNotEmpty()) "SH CLB $suffix" else "Sinh hoạt CLB"
        }

        // Generic CLB / SHCLB (check BEFORE Sinh hoạt lớp so "Sinh hoạt CLB" is never misclassified)
        if (idLower in setOf("sub-clb", "sub-club", "clb", "shclb") ||
            idLower.contains("clb") ||
            idLower.contains("club") ||
            codeLower.contains("clb") ||
            codeLower.contains("shclb") ||
            nameLower.contains("clb") ||
            nameLower.contains("shclb") ||
            nameLower.contains("câu lạc bộ")
        ) {
            return "Sinh hoạt CLB"
        }

        // Bồi dưỡng HSG
        if (idLower == "sub-bdhsg" ||
            idLower == "bdhsg" ||
            idLower.contains("bdhsg") ||
            codeLower.contains("bdhsg") ||
            rawCombined.contains("bồi dưỡng hsg")
        ) {
            return "Bồi dưỡng HSG"
        }

        return when {
            codeOrId.contains("chao-co") || rawCombined.contains("chào cờ") -> "Chào cờ"
            !isAfternoon && (
                idLower == "sub-shl" ||
                    codeLower == "shl" ||
                    codeOrId.contains("shl") ||
                    rawCombined.contains("sinh hoạt lớp") ||
                    rawCombined.contains("sinh hoạt")
                ) -> "Sinh hoạt lớp"
            isAfternoon && (
                idLower == "sub-shl" ||
                    codeLower == "shl" ||
                    rawCombined.contains("sinh hoạt")
                ) -> "Sinh hoạt CLB"
            codeOrId.contains("ls&đl-su") || codeOrId.contains("ls&dl-su") || codeOrId.contains("lsdl-su") ||
                codeOrId.contains("sub-su") || codeOrId.endsWith("-su") ||
                (rawCombined.contains("lịch sử") && !rawCombined.contains("địa")) ||
                rawCombined.contains("(sử)") || rawCombined.contains("- sử") -> "Lịch sử"
            codeOrId.contains("ls&đl-dia") || codeOrId.contains("ls&dl-dia") || codeOrId.contains("lsdl-dia") ||
                codeOrId.contains("sub-dia") || codeOrId.endsWith("-dia") ||
                (rawCombined.contains("địa lí") && !rawCombined.contains("lịch sử")) ||
                (rawCombined.contains("địa lý") && !rawCombined.contains("lịch sử")) ||
                rawCombined.contains("(địa)") || rawCombined.contains("- địa") -> "Địa lí"
            codeOrId.contains("khtn-ly") || codeOrId.contains("khtn-lý") || codeOrId.contains("sub-ly") ||
                rawCombined.contains("vật lí") || rawCombined.contains("vật lý") ||
                rawCombined.contains("(lý)") || rawCombined.contains("(lí)") -> "Vật lí"
            codeOrId.contains("khtn-hoa") || codeOrId.contains("khtn-hóa") || codeOrId.contains("sub-hoa") ||
                rawCombined.contains("hóa học") || rawCombined.contains("hoá học") ||
                rawCombined.contains("(hóa)") || rawCombined.contains("(hoá)") -> "Hóa học"
            codeOrId.contains("khtn-sinh") || codeOrId.contains("sub-sinh") ||
                rawCombined.contains("sinh học") || rawCombined.contains("(sinh)") -> "Sinh học"
            codeOrId.contains("khtn") || rawCombined.contains("khoa học tự nhiên") -> "KHTN"
            codeOrId.contains("ls&đl") || codeOrId.contains("ls&dl") || codeOrId.contains("lsdl") ||
                (rawCombined.contains("lịch sử") && rawCombined.contains("địa")) -> "Lịch sử & Địa lí"
            codeOrId.contains("toan") || rawCombined.contains("toán") -> "Toán"
            codeOrId.contains("van") || rawCombined.contains("ngữ văn") -> "Ngữ văn"
            codeOrId.contains("anh") || rawCombined.contains("tiếng anh") || rawCombined.contains("ngoại ngữ") -> "Tiếng Anh"
            codeOrId.contains("tin") || rawCombined.contains("tin học") -> "Tin học"
            codeOrId.contains("gdcd") || rawCombined.contains("giáo dục công dân") -> "GDCD"
            codeOrId.contains("cong-nghe") || codeOrId.contains("công nghệ") || subjectCode.equals("CN", ignoreCase = true) -> "Công nghệ"
            codeOrId.contains("gdtc") || rawCombined.contains("thể chất") || rawCombined.contains("thể dục") -> "GDTC"
            codeOrId.contains("am-nhac") || rawCombined.contains("âm nhạc") || subjectCode.equals("AN", ignoreCase = true) -> "Âm nhạc"
            codeOrId.contains("my-thuat") || rawCombined.contains("mỹ thuật") || rawCombined.contains("mĩ thuật") || subjectCode.equals("MT", ignoreCase = true) -> "Mỹ thuật"
            codeOrId.contains("hdtn") || rawCombined.contains("hđtn") || rawCombined.contains("trải nghiệm") -> "HĐTN"
            codeOrId.contains("gddp") || rawCombined.contains("gdđp") || rawCombined.contains("địa phương") -> "GDĐP"
            !subjectName.isNullOrBlank() -> subjectName.trim()
            !subjectCode.isNullOrBlank() -> subjectCode.trim()
            else -> subjectId
        }
    }

    /**
     * Compact 1-line summary for Glance Widget & Notification, e.g.:
     * "Sáng: T1 6A1 (Tin) · T3 7A2 (Tin)" or "Chiều: 9A3 (Tin học)"
     */
    fun formatCompactSessionLine(
        sessionLabel: String,
        periods: List<ResolvedPeriod>,
        isHomeroomMode: Boolean = false
    ): String {
        if (periods.isEmpty()) return ""
        val items = periods.joinToString(" · ") { p ->
            if (p.isAfternoon) {
                if (isHomeroomMode) {
                    if (p.hideAfternoonTeacher || p.teacherShortName.isBlank()) p.subjectShort
                    else "${p.subjectShort} (${p.teacherShortName})"
                } else {
                    "${compactClassName(p.className)} (${p.subjectShort})"
                }
            } else {
                if (isHomeroomMode) {
                    "T${p.period} ${p.subjectShort} (${p.teacherShortName})"
                } else {
                    "T${p.period} ${compactClassName(p.className)} (${p.subjectShort})"
                }
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
                "${it.className} (${it.startTime.formatHHmm()} - ${it.subjectShort})"
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

    /**
     * Compares current resolved periods against a baseline (either a previous sync snapshot
     * of the same week or the previous week's cached periods) and returns slotIds that are new or modified.
     */
    fun detectChangedSlotIds(
        currentPeriods: List<ResolvedPeriod>,
        baselinePeriods: List<ResolvedPeriod>
    ): Set<String> {
        if (currentPeriods.isEmpty() || baselinePeriods.isEmpty()) return emptySet()

        val baselineByPos = baselinePeriods.groupBy { "${it.day}_${it.session}_${it.period}" }
        val changed = mutableSetOf<String>()

        for (period in currentPeriods) {
            val posKey = "${period.day}_${period.session}_${period.period}"
            val baselineAtPos = baselineByPos[posKey].orEmpty()
            val matchesBaseline = baselineAtPos.any { base ->
                base.classId.equals(period.classId, ignoreCase = true) &&
                    base.subjectId.equals(period.subjectId, ignoreCase = true) &&
                    base.teacherId.equals(period.teacherId, ignoreCase = true) &&
                    base.room.equals(period.room, ignoreCase = true)
            }
            if (!matchesBaseline) {
                changed.add(period.slotId)
            }
        }
        return changed
    }

    /**
     * Offline workload & quota ("Định mức") calculator for the selected week and cumulative
     * from Week 1 up to selectedWeek using already-cached Room slots (0 Firestore reads).
     */
    fun calculateWorkloadStats(
        selectedWeek: Int,
        selectedWeekPeriods: List<ResolvedPeriod>,
        allCachedSlots: List<TimetableSlot>,
        schoolConfig: SchoolConfig,
        weeks: List<WeekInfo>,
        deletedSlotKeys: Set<String>,
        teachers: List<Teacher>,
        classes: List<SchoolClass>,
        subjects: List<Subject>,
        assignments: List<Assignment>,
        filterTeacherId: String?,
        filterClassId: String?
    ): WorkloadStatsSummary {
        val selectedWeekInfo = weeks.firstOrNull { it.weekNumber == selectedWeek }
        val effectiveAssignments = if (selectedWeekInfo?.isLocked == true && !selectedWeekInfo.lockedAssignments.isNullOrEmpty()) {
            selectedWeekInfo.lockedAssignments
        } else {
            assignments
        }.filter { a ->
            when {
                filterClassId != null -> a.classId.equals(filterClassId, ignoreCase = true)
                filterTeacherId != null -> a.teacherId == filterTeacherId
                else -> false
            }
        }

        val weekMorning = selectedWeekPeriods.count { !it.isAfternoon }
        val weekAfternoon = selectedWeekPeriods.count { it.isAfternoon }
        val weekQuota = effectiveAssignments.sumOf { it.periodsPerWeek.coerceAtLeast(0) }

        val slotsUpToSelectedByWeek = allCachedSlots
            .filter { it.week in MIN_WEEK..selectedWeek }
            .groupBy { it.week }

        val cachedWeekNumbers = slotsUpToSelectedByWeek.keys.sorted()
        val yearResolved = mutableListOf<ResolvedPeriod>()
        for (wk in cachedWeekNumbers) {
            val wkInfo = weeks.firstOrNull { it.weekNumber == wk }
            val wkRaw = slotsUpToSelectedByWeek[wk].orEmpty()
            yearResolved += resolveWeekPeriods(
                weekNumber = wk,
                rawSlots = wkRaw,
                schoolConfig = schoolConfig,
                weekInfo = wkInfo,
                deletedSlotKeys = deletedSlotKeys,
                teachers = teachers,
                classes = classes,
                subjects = subjects,
                assignments = assignments,
                filterTeacherId = filterTeacherId,
                filterClassId = filterClassId
            )
        }

        val yearMorning = yearResolved.count { !it.isAfternoon }
        val yearAfternoon = yearResolved.count { it.isAfternoon }

        // Breakdown by Class
        val classKeys = buildSet {
            selectedWeekPeriods.forEach { add(it.classId) }
            yearResolved.forEach { add(it.classId) }
            effectiveAssignments.forEach { add(it.classId) }
        }
        val classesById = classes.associateBy { it.id }
        val byClass = classKeys.map { clsId ->
            val wkList = selectedWeekPeriods.filter { it.classId.equals(clsId, ignoreCase = true) }
            val yrList = yearResolved.filter { it.classId.equals(clsId, ignoreCase = true) }
            val assigned = effectiveAssignments
                .filter { it.classId.equals(clsId, ignoreCase = true) }
                .sumOf { it.periodsPerWeek.coerceAtLeast(0) }
            val label = classesById[clsId]?.name?.let { compactClassName(it) }
                ?: wkList.firstOrNull()?.className?.let { compactClassName(it) }
                ?: clsId
            WorkloadBreakdownRow(
                key = clsId,
                label = "Lớp $label",
                weekMorning = wkList.count { !it.isAfternoon },
                weekAfternoon = wkList.count { it.isAfternoon },
                assignedPerWeek = assigned,
                yearMorning = yrList.count { !it.isAfternoon },
                yearAfternoon = yrList.count { it.isAfternoon }
            )
        }.sortedWith(compareByDescending<WorkloadBreakdownRow> { it.weekTotal }.thenBy { it.label })

        // Breakdown by Subject
        val subjectsById = subjects.associateBy { it.id }
        val weekBySubject = selectedWeekPeriods.groupBy { it.subjectName }
        val yearBySubject = yearResolved.groupBy { it.subjectName }
        val assignedBySubjectLabel = mutableMapOf<String, Int>()
        for (a in effectiveAssignments) {
            val sub = subjectsById[a.subjectId]
            val label = readableSubjectName(a.subjectId, sub?.name, sub?.code, null, session = a.session ?: "morning")
            assignedBySubjectLabel[label] = (assignedBySubjectLabel[label] ?: 0) + a.periodsPerWeek.coerceAtLeast(0)
        }
        val subjectLabels = (weekBySubject.keys + yearBySubject.keys + assignedBySubjectLabel.keys).toSet()
        val bySubject = subjectLabels.map { label ->
            val wkList = weekBySubject[label].orEmpty()
            val yrList = yearBySubject[label].orEmpty()
            WorkloadBreakdownRow(
                key = label,
                label = label,
                weekMorning = wkList.count { !it.isAfternoon },
                weekAfternoon = wkList.count { it.isAfternoon },
                assignedPerWeek = assignedBySubjectLabel[label] ?: 0,
                yearMorning = yrList.count { !it.isAfternoon },
                yearAfternoon = yrList.count { it.isAfternoon }
            )
        }.sortedWith(compareByDescending<WorkloadBreakdownRow> { it.weekTotal }.thenBy { it.label })

        return WorkloadStatsSummary(
            selectedWeek = selectedWeek,
            weekMorningCount = weekMorning,
            weekAfternoonCount = weekAfternoon,
            weekAssignedQuota = weekQuota,
            yearCachedWeeksCount = cachedWeekNumbers.size,
            yearCachedWeeksList = cachedWeekNumbers,
            yearMorningCount = yearMorning,
            yearAfternoonCount = yearAfternoon,
            byClass = byClass,
            bySubject = bySubject
        )
    }

    /**
     * Suggests free teachers who can cover or swap a selected period, prioritizing:
     * 1) Teachers who also teach the same class (and can swap directly when the requester is free)
     * 2) Teachers in the same department or main subject
     * 3) Other free teachers at that exact slot
     */
    fun suggestSubstituteTeachers(
        targetPeriod: ResolvedPeriod,
        requesterTeacher: Teacher?,
        allTeachers: List<Teacher>,
        allWeekPeriods: List<ResolvedPeriod>,
        assignments: List<Assignment>
    ): List<SubstituteSuggestion> {
        val targetPos = "${targetPeriod.day}_${targetPeriod.session}_${targetPeriod.period}"
        val busyAtTarget = allWeekPeriods
            .filter { "${it.day}_${it.session}_${it.period}" == targetPos }
            .map { it.teacherId }
            .toSet()

        val requesterId = requesterTeacher?.id ?: targetPeriod.teacherId
        val requesterBusyPositions = allWeekPeriods
            .filter { it.teacherId == requesterId }
            .map { "${it.day}_${it.session}_${it.period}" }
            .toSet()

        val classTeacherIdsFromAssignments = assignments
            .filter { it.classId.equals(targetPeriod.classId, ignoreCase = true) }
            .map { it.teacherId }
            .toSet()

        return allTeachers
            .filter { teacher ->
                teacher.id != requesterId &&
                    teacher.id != targetPeriod.teacherId &&
                    teacher.id !in busyAtTarget
            }
            .map { candidate ->
                val candidateSlotsInTargetClass = allWeekPeriods.filter {
                    it.teacherId == candidate.id &&
                        it.classId.equals(targetPeriod.classId, ignoreCase = true)
                }
                val teachesSameClass = candidateSlotsInTargetClass.isNotEmpty() ||
                    candidate.id in classTeacherIdsFromAssignments

                val sameDepartment = requesterTeacher != null &&
                    requesterTeacher.department.isNotBlank() &&
                    candidate.department.equals(requesterTeacher.department, ignoreCase = true)

                val sameSubject = candidate.mainSubject.isNotBlank() && (
                    (requesterTeacher?.mainSubject?.isNotBlank() == true &&
                        candidate.mainSubject.equals(requesterTeacher.mainSubject, ignoreCase = true)) ||
                        targetPeriod.subjectName.contains(candidate.mainSubject, ignoreCase = true) ||
                        candidate.mainSubject.contains(targetPeriod.subjectName, ignoreCase = true)
                    )

                val directSwapSlot = candidateSlotsInTargetClass.firstOrNull { slot ->
                    val pos = "${slot.day}_${slot.session}_${slot.period}"
                    pos != targetPos && pos !in requesterBusyPositions
                }
                val swapHint = directSwapSlot?.let { slot ->
                    if (slot.isAfternoon) {
                        "Đổi chéo: ${slot.day} Chiều (${slot.subjectShort})"
                    } else {
                        "Đổi chéo: ${slot.day} Sáng T${slot.period} (${slot.subjectShort})"
                    }
                }

                SubstituteSuggestion(
                    teacher = candidate,
                    teachesSameClass = teachesSameClass,
                    sameDepartment = sameDepartment,
                    sameSubject = sameSubject,
                    swapSlotHint = swapHint
                )
            }
            .sortedWith(
                compareByDescending<SubstituteSuggestion> { it.priorityScore }
                    .thenBy { it.teacher.name }
            )
    }

    fun eventMatchesDate(
        event: CalendarEvent,
        solarDate: VnDate,
        lunarDate: LunarDate
    ): Boolean {
        return if (event.isLunar) {
            event.month == lunarDate.month &&
                event.day == lunarDate.day &&
                (event.isRecurringYearly || event.year == lunarDate.year)
        } else {
            event.month == solarDate.month &&
                event.day == solarDate.day &&
                (event.isRecurringYearly || event.year == solarDate.year)
        }
    }
}
