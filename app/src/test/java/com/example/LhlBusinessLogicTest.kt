package com.example

import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.Assignment
import com.example.domain.model.PeriodLiveStatus
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
}
