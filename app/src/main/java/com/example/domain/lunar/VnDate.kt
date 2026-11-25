package com.example.domain.lunar

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

val VIETNAM_TZ: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

data class VnDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) : Comparable<VnDate> {

    fun toCalendar(): Calendar {
        return Calendar.getInstance(VIETNAM_TZ).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /** 1 = Monday (T2), ..., 6 = Saturday (T7), 7 = Sunday (CN) */
    val isoDayOfWeek: Int
        get() {
            val dow = toCalendar().get(Calendar.DAY_OF_WEEK)
            return if (dow == Calendar.SUNDAY) 7 else dow - 1
        }

    val isSunday: Boolean
        get() = isoDayOfWeek == 7

    /** "T2".."T7", or "CN" for Sunday */
    val shortDayCode: String
        get() = when (isoDayOfWeek) {
            1 -> "T2"
            2 -> "T3"
            3 -> "T4"
            4 -> "T5"
            5 -> "T6"
            6 -> "T7"
            else -> "CN"
        }

    /** Full Vietnamese weekday label, e.g. "Thứ Tư" or "Chủ Nhật" */
    val vietnameseWeekday: String
        get() = when (isoDayOfWeek) {
            1 -> "Thứ Hai"
            2 -> "Thứ Ba"
            3 -> "Thứ Tư"
            4 -> "Thứ Năm"
            5 -> "Thứ Sáu"
            6 -> "Thứ Bảy"
            else -> "Chủ Nhật"
        }

    /** Uppercase compact weekday for forecast cards, e.g. "THỨ 5" */
    val forecastWeekdayLabel: String
        get() = when (isoDayOfWeek) {
            1 -> "THỨ 2"
            2 -> "THỨ 3"
            3 -> "THỨ 4"
            4 -> "THỨ 5"
            5 -> "THỨ 6"
            6 -> "THỨ 7"
            else -> "CHỦ NHẬT"
        }

    /** Formatted as "Thứ Tư, 25 Thg 11" */
    fun formatFullVietnamese(): String = "$vietnameseWeekday, $day Thg $month"

    /** Formatted as "25/11" */
    fun formatDayMonth(): String = String.format(Locale.US, "%02d/%02d", day, month)

    /** Formatted as "YYYY-MM-DD" */
    fun toIsoString(): String = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun plusDays(days: Int): VnDate {
        val cal = toCalendar()
        cal.add(Calendar.DAY_OF_MONTH, days)
        return VnDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Returns Monday..Sunday of the week containing this date */
    fun weekDatesMondayToSunday(): List<VnDate> {
        val monday = plusDays(-(isoDayOfWeek - 1))
        return (0..6).map { monday.plusDays(it) }
    }

    fun daysInMonth(): Int {
        return toCalendar().getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    override fun compareTo(other: VnDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    companion object {
        fun today(): VnDate {
            val cal = Calendar.getInstance(VIETNAM_TZ)
            return VnDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun parseIso(iso: String?): VnDate? {
            if (iso.isNullOrBlank()) return null
            val parts = iso.trim().take(10).split("-")
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..31) return null
            return VnDate(y, m, d)
        }

        fun daysBetween(start: VnDate, end: VnDate): Int {
            val jdStart = LunarCalendar.jdFromDate(start.day, start.month, start.year)
            val jdEnd = LunarCalendar.jdFromDate(end.day, end.month, end.year)
            return jdEnd - jdStart
        }
    }
}

data class VnTime(
    val hour: Int,
    val minute: Int
) : Comparable<VnTime> {
    val totalMinutes: Int get() = hour * 60 + minute

    fun formatHHmm(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun plusMinutes(delta: Int): VnTime {
        val total = ((totalMinutes + delta) % (24 * 60) + (24 * 60)) % (24 * 60)
        return VnTime(total / 60, total % 60)
    }

    fun greetingVietnamese(): String = when (hour) {
        in 5..11 -> "Chào buổi sáng"
        in 12..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }

    override fun compareTo(other: VnTime): Int = totalMinutes.compareTo(other.totalMinutes)

    companion object {
        fun now(): VnTime {
            val cal = Calendar.getInstance(VIETNAM_TZ)
            return VnTime(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        }

        fun parseHHmm(text: String?, fallbackHour: Int, fallbackMinute: Int): VnTime {
            if (text.isNullOrBlank()) return VnTime(fallbackHour, fallbackMinute)
            val parts = text.trim().split(":")
            if (parts.size < 2) return VnTime(fallbackHour, fallbackMinute)
            val h = parts[0].toIntOrNull() ?: return VnTime(fallbackHour, fallbackMinute)
            val m = parts[1].toIntOrNull() ?: return VnTime(fallbackHour, fallbackMinute)
            return VnTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
        }
    }
}
