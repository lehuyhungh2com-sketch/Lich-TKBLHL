package com.example

import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.Assignment
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarEventCategory
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.Subject
import com.example.domain.model.Teacher
import com.example.domain.model.TimeConfig
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.TimetableSlot
import com.example.domain.model.WeekInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LhlBusinessLogicTest {

    @Test
    fun lunarCalendar_matchesReferenceDate_25Nov2026() {
        val date = VnDate(2026, 11, 25)
        val lunar = LunarCalendar.convertSolar2Lunar(date)
        assertEquals(17, lunar.day)
        assertEquals(10, lunar.month)
        assertEquals(2026, lunar.year)
        assertFalse(lunar.isLeap)
        assertEquals("Bính Ngọ", lunar.canChiYear)
        assertEquals("17/10 ÂL • Bính Ngọ", lunar.formatBadge())
    }

    @Test
    fun periodTimes_followExactRecessRules() {
        val config = TimeConfig(
            morningStart = "07:15",
            afternoonStart = "14:00",
            periodDuration = 45,
            recessDuration = 10,
            transitionDuration = 0
        )

        // Monday morning (T2): recess AFTER Period 3
        val (t2p3Start, t2p3End) = TimetableCalculator.calculatePeriodTimeRange("T2", 3, "morning", config)
        val (t2p4Start, _) = TimetableCalculator.calculatePeriodTimeRange("T2", 4, "morning", config)
        assertEquals("08:45", t2p3Start.formatHHmm())
        assertEquals("09:30", t2p3End.formatHHmm())
        assertEquals("09:40", t2p4Start.formatHHmm())

        // Wednesday morning (T4): recess AFTER Period 2
        val (t4p1Start, _) = TimetableCalculator.calculatePeriodTimeRange("T4", 1, "morning", config)
        val (t4p3Start, _) = TimetableCalculator.calculatePeriodTimeRange("T4", 3, "morning", config)
        assertEquals("07:15", t4p1Start.formatHHmm())
        assertEquals("08:55", t4p3Start.formatHHmm())

        // Afternoon: recess AFTER Period 2
        val (af3Start, _) = TimetableCalculator.calculatePeriodTimeRange("T2", 3, "afternoon", config)
        assertEquals("15:40", af3Start.formatHHmm())
    }

    @Test
    fun currentWeek_advancesOnSunday() {
        val config = SchoolConfig(startDate = "2026-09-07")
        // Monday 2026-09-07 is Week 1
        assertEquals(1, TimetableCalculator.calculateCurrentWeek(VnDate(2026, 9, 7), config))
        // Saturday 2026-09-12 is still Week 1
        assertEquals(1, TimetableCalculator.calculateCurrentWeek(VnDate(2026, 9, 12), config))
        // Sunday 2026-09-13 advances +1 day -> Week 2!
        assertEquals(2, TimetableCalculator.calculateCurrentWeek(VnDate(2026, 9, 13), config))
    }

    @Test
    fun resolveWeekPeriods_respectsAfternoonOverride_deletedSlots_andLiveStatus() {
        val subjects = listOf(
            Subject(
                id = "sub-tin",
                name = "Tin học",
                code = "Tin",
                afternoonTeacherOverride = mapOf("9A3" to "gv-hung")
            )
        )
        val teachers = listOf(
            Teacher(id = "gv-hung", name = "Lê Huy Hùng", shortName = "Hùng", email = "lehuyhung.h2com@gmail.com"),
            Teacher(id = "gv-other", name = "Nguyễn Văn A", shortName = "A", email = "a@gmail.com")
        )
        val classes = listOf(
            SchoolClass(id = "6A1", name = "6A1", grade = 6, room = "P101"),
            SchoolClass(id = "9A3", name = "9A3", grade = 9, room = "P301")
        )
        val assignments = listOf(
            Assignment(id = "a1", teacherId = "gv-hung", subjectId = "sub-tin", classId = "6A1", periodsPerWeek = 1)
        )
        val slots = listOf(
            // Empty teacherId -> fallback to assignments -> gv-hung
            TimetableSlot(id = "s1", week = 12, classId = "6A1", day = "T4", period = 1, subjectId = "sub-tin", teacherId = "", session = "morning"),
            // Deleted slot on unlocked week -> should be filtered out
            TimetableSlot(id = "s-del", week = 12, classId = "6A1", day = "T4", period = 2, subjectId = "sub-tin", teacherId = "gv-hung", session = "morning"),
            // Afternoon slot with slot.teacherId = gv-other, overridden to gv-hung by afternoonTeacherOverride["9A3"]
            TimetableSlot(id = "s2", week = 12, classId = "9A3", day = "T4", period = 1, subjectId = "sub-tin", teacherId = "gv-other", session = "afternoon")
        )

        val resolved = TimetableCalculator.resolveWeekPeriods(
            weekNumber = 12,
            rawSlots = slots,
            schoolConfig = SchoolConfig(),
            weekInfo = WeekInfo(12, "2026-11-23", "2026-11-29", "Tuần 12", isLocked = false),
            deletedSlotKeys = setOf("s-del"),
            teachers = teachers,
            classes = classes,
            subjects = subjects,
            assignments = assignments,
            filterTeacherId = "gv-hung",
            todayDate = VnDate(2026, 11, 25), // Wednesday T4
            nowTime = VnTime(7, 30)           // During Period 1 (07:15 - 08:00)
        )

        assertEquals(2, resolved.size)
        assertEquals("s1", resolved[0].slotId)
        assertEquals(PeriodLiveStatus.TEACHING_NOW, resolved[0].liveStatus)
        assertEquals("s2", resolved[1].slotId)
        assertTrue(resolved[1].isAfternoon)
        assertEquals(PeriodLiveStatus.NEXT_PERIOD, resolved[1].liveStatus)
    }

    @Test
    fun readableSubjectName_formatsCleanVietnameseNames() {
        assertEquals("Lịch sử", TimetableCalculator.readableSubjectName("sub-su", "LS&ĐL-SU", "LS&ĐL-SU"))
        assertEquals("Địa lí", TimetableCalculator.readableSubjectName("sub-dia", "LS&ĐL-DIA", "LS&ĐL-DIA"))
        assertEquals("Vật lí", TimetableCalculator.readableSubjectName("sub-khtn-ly", "KHTN-LY", "KHTN-LY"))
        assertEquals("Hóa học", TimetableCalculator.readableSubjectName("sub-khtn-hoa", "KHTN-HOA", "KHTN-HOA"))
        assertEquals("Sinh học", TimetableCalculator.readableSubjectName("sub-khtn-sinh", "KHTN-SINH", "KHTN-SINH"))
        assertEquals("Tin học", TimetableCalculator.readableSubjectName("sub-tin", "Tin học", "Tin"))
        assertEquals("7A2", TimetableCalculator.compactClassName("Lớp 7A2"))
    }

    @Test
    fun detectChangedSlotIds_flagsModifiedAndNewSlotsOnly() {
        val base1 = ResolvedPeriod(
            slotId = "b1", week = 11, day = "T2", period = 1, session = "morning",
            classId = "6A1", className = "6A1", subjectId = "sub-tin", subjectName = "Tin học",
            subjectShort = "Tin", teacherId = "gv1", teacherShortName = "Hùng", room = "P101",
            note = null, customLabel = null, startTime = VnTime(7, 15), endTime = VnTime(8, 0)
        )
        val base2 = base1.copy(slotId = "b2", day = "T3", period = 2, classId = "7A1", className = "7A1")

        val curSame = base1.copy(slotId = "c1", week = 12)
        val curModifiedClass = base2.copy(slotId = "c2", week = 12, classId = "8A2", className = "8A2")
        val curAdded = base1.copy(slotId = "c3", week = 12, day = "T5", period = 4)

        val changed = TimetableCalculator.detectChangedSlotIds(
            currentPeriods = listOf(curSame, curModifiedClass, curAdded),
            baselinePeriods = listOf(base1, base2)
        )
        assertEquals(setOf("c2", "c3"), changed)
    }

    @Test
    fun calculateWorkloadStats_aggregatesWeekAndCumulativeCachedWeeks() {
        val teachers = listOf(Teacher(id = "gv1", name = "Lê Huy Hùng", shortName = "Hùng", email = "h@lhl.vn"))
        val classes = listOf(
            SchoolClass(id = "6A1", name = "6A1", grade = 6),
            SchoolClass(id = "9A3", name = "9A3", grade = 9)
        )
        val subjects = listOf(Subject(id = "sub-tin", name = "Tin học", code = "Tin"))
        val assignments = listOf(
            Assignment(id = "a1", teacherId = "gv1", subjectId = "sub-tin", classId = "6A1", periodsPerWeek = 2),
            Assignment(id = "a2", teacherId = "gv1", subjectId = "sub-tin", classId = "9A3", periodsPerWeek = 1)
        )
        val w1Slots = listOf(
            TimetableSlot("w1_1", 1, "6A1", "T2", 1, "sub-tin", "gv1", session = "morning"),
            TimetableSlot("w1_2", 1, "9A3", "T4", 1, "sub-tin", "gv1", session = "afternoon")
        )
        val w2Slots = listOf(
            TimetableSlot("w2_1", 2, "6A1", "T2", 1, "sub-tin", "gv1", session = "morning"),
            TimetableSlot("w2_2", 2, "6A1", "T3", 2, "sub-tin", "gv1", session = "morning")
        )
        val w2Resolved = TimetableCalculator.resolveWeekPeriods(
            weekNumber = 2, rawSlots = w2Slots, schoolConfig = SchoolConfig(), weekInfo = null,
            deletedSlotKeys = emptySet(), teachers = teachers, classes = classes, subjects = subjects,
            assignments = assignments, filterTeacherId = "gv1"
        )

        val stats = TimetableCalculator.calculateWorkloadStats(
            selectedWeek = 2,
            selectedWeekPeriods = w2Resolved,
            allCachedSlots = w1Slots + w2Slots,
            schoolConfig = SchoolConfig(),
            weeks = emptyList(),
            deletedSlotKeys = emptySet(),
            teachers = teachers,
            classes = classes,
            subjects = subjects,
            assignments = assignments,
            filterTeacherId = "gv1",
            filterClassId = null
        )

        assertEquals(2, stats.weekMorningCount)
        assertEquals(0, stats.weekAfternoonCount)
        assertEquals(3, stats.weekAssignedQuota)
        assertEquals(2, stats.yearCachedWeeksCount)
        assertEquals(3, stats.yearMorningCount)
        assertEquals(1, stats.yearAfternoonCount)
        assertEquals(4, stats.yearTotalCount)
        assertEquals(2, stats.byClass.size)
        assertEquals(1, stats.bySubject.size)
    }

    @Test
    fun suggestSubstituteTeachers_prioritizesSameClassAndDirectSwap() {
        val me = Teacher(id = "gv1", name = "GV 1", shortName = "1", email = "1@lhl.vn", department = "Toán-Tin", mainSubject = "Tin học")
        val sameClassTeacher = Teacher(id = "gv2", name = "GV 2", shortName = "2", email = "2@lhl.vn", department = "KHXH", mainSubject = "Ngữ văn")
        val sameDeptTeacher = Teacher(id = "gv3", name = "GV 3", shortName = "3", email = "3@lhl.vn", department = "Toán-Tin", mainSubject = "Toán")
        val busyTeacher = Teacher(id = "gv4", name = "GV 4", shortName = "4", email = "4@lhl.vn", department = "Toán-Tin", mainSubject = "Toán")

        val myPeriod = ResolvedPeriod(
            slotId = "s1", week = 12, day = "T2", period = 1, session = "morning",
            classId = "7A1", className = "7A1", subjectId = "sub-tin", subjectName = "Tin học",
            subjectShort = "Tin", teacherId = "gv1", teacherShortName = "1", room = "P1",
            note = null, customLabel = null, startTime = VnTime(7, 15), endTime = VnTime(8, 0)
        )
        val sameClassOtherSlot = myPeriod.copy(
            slotId = "s2", day = "T4", period = 3, subjectId = "sub-van", subjectName = "Ngữ văn",
            subjectShort = "Văn", teacherId = "gv2", teacherShortName = "2"
        )
        val busyAtTargetSlot = myPeriod.copy(
            slotId = "s3", classId = "8A1", className = "8A1", subjectId = "sub-toan",
            subjectName = "Toán", subjectShort = "Toán", teacherId = "gv4", teacherShortName = "4"
        )

        val suggestions = TimetableCalculator.suggestSubstituteTeachers(
            targetPeriod = myPeriod,
            requesterTeacher = me,
            allTeachers = listOf(me, sameClassTeacher, sameDeptTeacher, busyTeacher),
            allWeekPeriods = listOf(myPeriod, sameClassOtherSlot, busyAtTargetSlot),
            assignments = emptyList()
        )

        // busyTeacher (gv4) must be excluded; sameClassTeacher (gv2) ranks first with swap hint!
        assertEquals(2, suggestions.size)
        assertEquals("gv2", suggestions[0].teacher.id)
        assertTrue(suggestions[0].teachesSameClass)
        assertTrue(suggestions[0].swapSlotHint?.contains("T4 Sáng T3") == true)
        assertEquals("gv3", suggestions[1].teacher.id)
        assertTrue(suggestions[1].sameDepartment)
    }

    @Test
    fun eventMatchesDate_supportsSolarLunarAndRecurringYearly() {
        val solar = VnDate(2026, 11, 25)
        val lunar = LunarCalendar.convertSolar2Lunar(solar) // 17/10/2026 AL

        val solarOneOff = CalendarEvent(
            id = 1, title = "Họp", category = CalendarEventCategory.MEETING,
            isLunar = false, year = 2026, month = 11, day = 25, isRecurringYearly = false
        )
        val lunarRecurring = CalendarEvent(
            id = 2, title = "Giỗ / Kỷ niệm", category = CalendarEventCategory.ANNIVERSARY,
            isLunar = true, year = 2020, month = 10, day = 17, isRecurringYearly = true
        )
        val lunarOtherDay = lunarRecurring.copy(day = 18)

        assertTrue(TimetableCalculator.eventMatchesDate(solarOneOff, solar, lunar))
        assertTrue(TimetableCalculator.eventMatchesDate(lunarRecurring, solar, lunar))
        assertFalse(TimetableCalculator.eventMatchesDate(lunarOtherDay, solar, lunar))
    }

    @Test
    fun getSlotActualPeriod_matchesWebRulesForExplicitMondayAndSaturday() {
        // 1. Explicit actualPeriod (1..5) overrides row period
        assertEquals(
            3,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s1", 12, "9A1", "T2", 5, "sub-tin", "gv-hung", session = "morning", actualPeriod = 3)
            )
        )
        // 2. Monday morning without actualPeriod: Chào cờ / HĐTN -> Period 1; other rows 1..4 -> +1
        assertEquals(
            1,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s2", 12, "9A1", "T2", 1, "sub-chao-co", "gv1", session = "morning", actualPeriod = null)
            )
        )
        assertEquals(
            1,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s3", 12, "9A1", "T2", 2, "sub-hdtn", "gv1", session = "morning", actualPeriod = null)
            )
        )
        assertEquals(
            4,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s4", 12, "9A1", "T2", 3, "sub-toan", "gv1", session = "morning", actualPeriod = null)
            )
        )
        assertEquals(
            5,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s5", 12, "8A1", "T2", 5, "sub-tin", "gv-hung", session = "morning", actualPeriod = null)
            )
        )
        // 3. Saturday morning SHL/HĐTN at period 5 -> Period 5
        assertEquals(
            5,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s6", 12, "7A1", "T7", 5, "sub-shl", "gv1", session = "morning", actualPeriod = null)
            )
        )
        // 4. Other days (T3..T6) or afternoon sessions keep raw period when actualPeriod is null
        assertEquals(
            2,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s7", 12, "6A1", "T3", 2, "sub-tin", "gv1", session = "morning", actualPeriod = null)
            )
        )
        assertEquals(
            1,
            TimetableCalculator.getSlotActualPeriod(
                TimetableSlot("s8", 12, "6A1", "T2", 1, "sub-tin", "gv1", session = "afternoon", actualPeriod = null)
            )
        )
    }

    @Test
    fun resolveWeekPeriods_mondayActualPeriod_resolves9A1AtT3Before8A1AtT5() {
        val teachers = listOf(Teacher(id = "gv-hung", name = "Lê Huy Hùng", shortName = "Hùng", email = "h@lhl.vn"))
        val classes = listOf(
            SchoolClass(id = "8A1", name = "8A1", grade = 8, room = "P201"),
            SchoolClass(id = "9A1", name = "9A1", grade = 9, room = "P301")
        )
        val subjects = listOf(Subject(id = "sub-tin", name = "Tin học", code = "Tin"))
        val slots = listOf(
            TimetableSlot("slot-8a1", 12, "8A1", "T2", 5, "sub-tin", "gv-hung", session = "morning", actualPeriod = null),
            TimetableSlot("slot-9a1", 12, "9A1", "T2", 5, "sub-tin", "gv-hung", session = "morning", actualPeriod = 3)
        )

        val resolved = TimetableCalculator.resolveWeekPeriods(
            weekNumber = 12,
            rawSlots = slots,
            schoolConfig = SchoolConfig(),
            weekInfo = WeekInfo(12, "2026-11-23", "2026-11-29", "Tuần 12", isLocked = false),
            deletedSlotKeys = emptySet(),
            teachers = teachers,
            classes = classes,
            subjects = subjects,
            assignments = emptyList(),
            filterTeacherId = "gv-hung",
            todayDate = VnDate(2026, 11, 23), // Monday (T2)
            nowTime = VnTime(9, 0)            // During Monday T3 (08:45 - 09:30)
        )

        assertEquals(2, resolved.size)
        // First period must be 9A1 at T3 (effectivePeriod = 3)
        assertEquals("9A1", resolved[0].classId)
        assertEquals(3, resolved[0].period)
        assertEquals("08:45 – 09:30", resolved[0].timeRangeText)
        assertEquals(PeriodLiveStatus.TEACHING_NOW, resolved[0].liveStatus)

        // Second period must be 8A1 at T5 (effectivePeriod = 5)
        assertEquals("8A1", resolved[1].classId)
        assertEquals(5, resolved[1].period)
        assertEquals("10:25 – 11:10", resolved[1].timeRangeText)
        assertEquals(PeriodLiveStatus.NEXT_PERIOD, resolved[1].liveStatus)

        val compactLine = TimetableCalculator.formatCompactSessionLine("Sáng", resolved)
        assertEquals("Sáng: T3 9A1 (Tin học) · T5 8A1 (Tin học)", compactLine)

        val notifSummary = TimetableCalculator.formatNotificationSummary("Thứ Hai", resolved, emptyList())
        assertTrue(notifSummary.contains("T3 9A1 (08:45 - Tin học), T5 8A1 (10:25 - Tin học)"))
    }
}
