package com.example.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.LhlTimetableApp
import com.example.data.local.LessonNoteEntity
import com.example.data.local.WidgetCornerStyle
import com.example.data.local.WidgetTextTheme
import com.example.data.remote.WeatherCodeMapper
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.LunarDate
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.DailyForecast
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.WeatherSnapshot

data class WeekStripDay(
    val solar: VnDate,
    val lunar: LunarDate,
    val isToday: Boolean
)

data class WidgetPalette(
    val useLightText: Boolean,
    val isFullyTransparent: Boolean,
    val backgroundColor: Color,
    val cardTileBackground: Color,
    val liveNowBackground: Color,
    val liveNowAccent: Color,
    val nextPeriodBackground: Color,
    val nextPeriodAccent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedPastText: Color,
    val pillBackground: Color,
    val pillText: Color,
    val scheduleContainer: Color,
    val dividerColor: Color,
    val accentOrange: Color,
    val sundayColor: Color,
    val rainBlue: Color
)

data class WidgetRenderData(
    val today: VnDate,
    val now: VnTime,
    val greeting: String,
    val solarDateText: String,
    val lunarBadgeText: String,
    val lunarShortText: String,
    val weekStrip: List<WeekStripDay>,
    val weather: WeatherSnapshot?,
    val weatherDesc: String,
    val weatherIconRes: Int,
    val forecasts3Days: List<DailyForecast>,
    val forecasts4Days: List<DailyForecast>,
    val isLoggedIn: Boolean,
    val teacherName: String,
    val weekNumber: Int,
    val isSundayPreview: Boolean,
    val targetDayLabel: String,
    val morningPeriods: List<ResolvedPeriod>,
    val afternoonPeriods: List<ResolvedPeriod>,
    val morningCompactLine: String,
    val afternoonCompactLine: String,
    val lessonNotes: Map<String, String>,
    val cornerRadiusDp: Int,
    val fontScale: Float,
    val fontDeltaSp: Float,
    val showSubjectOnWidget: Boolean,
    val palette: WidgetPalette
) {
    fun sp(baseSp: Float, minSp: Float = 8.5f, maxSp: Float = 38f) =
        (baseSp * fontScale).coerceIn(minSp, maxSp).sp

    fun noteFor(period: ResolvedPeriod): String? {
        val key = LessonNoteEntity.buildKey(
            week = period.week,
            day = period.day,
            period = period.period,
            session = period.session,
            classId = period.classId
        )
        return lessonNotes[key]?.takeIf { it.isNotBlank() } ?: period.note?.takeIf { it.isNotBlank() }
    }

    fun formatPeriodTitle(period: ResolvedPeriod): String {
        val cls = TimetableCalculator.compactClassName(period.className)
        val hasNote = !noteFor(period).isNullOrBlank()
        val noteMark = if (hasNote) " ✎" else ""
        return if (showSubjectOnWidget) {
            "$cls · ${period.subjectName}$noteMark"
        } else {
            "Lớp $cls$noteMark"
        }
    }

    fun liveStatusBadgeText(): Pair<String, Color>? {
        val all = morningPeriods + afternoonPeriods
        if (!isLoggedIn || isSundayPreview || all.isEmpty()) return null
        val teachingNow = all.firstOrNull { it.liveStatus == PeriodLiveStatus.TEACHING_NOW }
        val nextPeriod = all.firstOrNull { it.liveStatus == PeriodLiveStatus.NEXT_PERIOD }
        return when {
            teachingNow != null -> {
                val tag = if (teachingNow.isAfternoon) "Chiều" else "T${teachingNow.period}"
                val cls = TimetableCalculator.compactClassName(teachingNow.className)
                val left = (teachingNow.endTime.totalMinutes - now.totalMinutes).coerceAtLeast(1)
                "● Đang dạy $tag ($cls) · còn ${left}p" to palette.liveNowAccent
            }
            nextPeriod != null -> {
                val tag = if (nextPeriod.isAfternoon) "Chiều" else "T${nextPeriod.period}"
                val until = (nextPeriod.startTime.totalMinutes - now.totalMinutes).coerceAtLeast(1)
                val countdown = if (until < 60) "còn ${until}p" else nextPeriod.startTime.formatHHmm()
                "Tiếp: $tag · $countdown" to palette.nextPeriodAccent
            }
            else -> "✓ Đã xong ${all.size}/${all.size} tiết" to palette.mutedPastText
        }
    }
}

object WidgetDataBuilder {

    suspend fun load(context: Context): WidgetRenderData {
        val app = context.applicationContext as LhlTimetableApp
        val snapshot = app.schoolRepository.getLocalSnapshotForWeek()
        val weather = app.weatherRepository.getCachedWeather()

        val today = VnDate.today()
        val now = VnTime.now()
        val todayLunar = LunarCalendar.convertSolar2Lunar(today)

        val strip = today.weekDatesMondayToSunday().map { d ->
            WeekStripDay(
                solar = d,
                lunar = LunarCalendar.convertSolar2Lunar(d),
                isToday = d == today
            )
        }

        val isSundayPreview = today.isSunday
        val targetDayCode = if (isSundayPreview) "T2" else today.shortDayCode
        val targetDayLabel = if (isSundayPreview) "Thứ Hai (Tuần ${snapshot.weekNumber})" else today.vietnameseWeekday

        val teacher = snapshot.loggedInTeacher
        val weekInfo = snapshot.weeks.firstOrNull { it.weekNumber == snapshot.weekNumber }

        val periods = if (teacher != null) {
            TimetableCalculator.resolveWeekPeriods(
                weekNumber = snapshot.weekNumber,
                rawSlots = snapshot.slots,
                schoolConfig = snapshot.schoolConfig,
                weekInfo = weekInfo,
                deletedSlotKeys = snapshot.deletedSlotKeys,
                teachers = snapshot.teachers,
                classes = snapshot.classes,
                subjects = snapshot.subjects,
                assignments = snapshot.assignments,
                filterTeacherId = teacher.id,
                todayDate = if (isSundayPreview) null else today,
                nowTime = if (isSundayPreview) null else now
            ).filter { it.day == targetDayCode }
        } else {
            emptyList()
        }

        val morning = periods.filter { !it.isAfternoon }
        val afternoon = periods.filter { it.isAfternoon }
        val prefs = snapshot.preferences

        val palette = resolvePalette(
            context = context,
            transparentMode = prefs.widgetTransparentMode,
            opacity = prefs.widgetOpacity,
            textTheme = prefs.widgetTextTheme
        )

        val weatherCode = weather?.weatherCode ?: 2
        val weatherDesc = weather?.let { WeatherCodeMapper.descriptionVietnamese(it.weatherCode) }
            ?: "Chạm để cập nhật"
        val weatherIconRes = WeatherCodeMapper.widgetIconRes(weatherCode)
        val lunarShort = "${todayLunar.day}/${todayLunar.month}${if (todayLunar.isLeap) "N" else ""} ÂL"

        return WidgetRenderData(
            today = today,
            now = now,
            greeting = now.greetingVietnamese(),
            solarDateText = today.formatFullVietnamese(),
            lunarBadgeText = todayLunar.formatBadge(),
            lunarShortText = lunarShort,
            weekStrip = strip,
            weather = weather,
            weatherDesc = weatherDesc,
            weatherIconRes = weatherIconRes,
            forecasts3Days = weather?.dailyForecasts?.take(3).orEmpty(),
            forecasts4Days = weather?.dailyForecasts?.take(4).orEmpty(),
            isLoggedIn = teacher != null,
            teacherName = teacher?.displayShortName.orEmpty(),
            weekNumber = snapshot.weekNumber,
            isSundayPreview = isSundayPreview,
            targetDayLabel = targetDayLabel,
            morningPeriods = morning,
            afternoonPeriods = afternoon,
            morningCompactLine = TimetableCalculator.formatCompactSessionLine("Sáng", morning),
            afternoonCompactLine = TimetableCalculator.formatCompactSessionLine("Chiều", afternoon),
            lessonNotes = snapshot.lessonNotes,
            cornerRadiusDp = prefs.widgetCornerStyle.radiusDp,
            fontScale = prefs.widgetFontScale,
            fontDeltaSp = prefs.widgetFontDeltaSp,
            showSubjectOnWidget = prefs.widgetShowSubject,
            palette = palette
        )
    }

    fun detectUseLightText(
        context: Context,
        isFullyTransparent: Boolean,
        textTheme: WidgetTextTheme
    ): Boolean {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        return when (textTheme) {
            WidgetTextTheme.LIGHT -> true
            WidgetTextTheme.DARK -> false
            WidgetTextTheme.AUTO -> {
                if (isFullyTransparent) {
                    val supportsDarkText = runCatching {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                            val wm = android.app.WallpaperManager.getInstance(context)
                            val colors = wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
                            if (colors != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                (colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                            } else if (colors != null) {
                                val primary = colors.primaryColor
                                val luminance = 0.299f * primary.red() + 0.587f * primary.green() + 0.114f * primary.blue()
                                luminance > 0.68f
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                    !supportsDarkText
                } else {
                    isNight
                }
            }
        }
    }

    fun resolvePalette(
        context: Context,
        transparentMode: Boolean,
        opacity: Float,
        textTheme: WidgetTextTheme
    ): WidgetPalette {
        val clampedAlpha = opacity.coerceIn(0f, 1f)
        val isFullyTransparent = transparentMode || clampedAlpha <= 0.02f
        val useLightText = detectUseLightText(context, isFullyTransparent, textTheme)
        val bgBase = if (useLightText) Color(0xFF151A24) else Color(0xFFFFFCF7)
        val tileBase = if (useLightText) Color(0xFF232B3A) else Color(0xFFF3EFE8)
        val scheduleBase = if (useLightText) Color(0xFF202736) else Color(0xFFE7E1D6)
        val tileAlpha = (clampedAlpha * 0.90f).coerceIn(0f, 1f)
        val liveTileAlpha = (clampedAlpha * 0.36f).coerceIn(0.20f, 0.36f)
        val nextTileAlpha = (clampedAlpha * 0.28f).coerceIn(0.14f, 0.28f)

        return WidgetPalette(
            useLightText = useLightText,
            isFullyTransparent = isFullyTransparent,
            backgroundColor = if (isFullyTransparent) Color.Transparent else bgBase.copy(alpha = clampedAlpha),
            cardTileBackground = if (isFullyTransparent) Color.Transparent else tileBase.copy(alpha = tileAlpha),
            liveNowBackground = if (isFullyTransparent) {
                if (useLightText) Color(0x3310B981) else Color(0x28059669)
            } else {
                if (useLightText) Color(0xFF10B981).copy(alpha = liveTileAlpha)
                else Color(0xFF059669).copy(alpha = liveTileAlpha * 0.65f)
            },
            liveNowAccent = if (useLightText) Color(0xFF34D399) else Color(0xFF047857),
            nextPeriodBackground = if (isFullyTransparent) {
                if (useLightText) Color(0x26FB923C) else Color(0x22EA580C)
            } else {
                if (useLightText) Color(0xFFFB923C).copy(alpha = nextTileAlpha)
                else Color(0xFFEA580C).copy(alpha = nextTileAlpha * 0.6f)
            },
            nextPeriodAccent = if (useLightText) Color(0xFFFB923C) else Color(0xFFEA580C),
            primaryText = if (useLightText) Color(0xFFFFFFFF) else Color(0xFF111827),
            secondaryText = if (useLightText) Color(0xFFDDE5F0) else Color(0xFF334155),
            mutedPastText = if (useLightText) Color(0x88CBD5E1) else Color(0x7A475569),
            pillBackground = if (isFullyTransparent) {
                Color.Transparent
            } else {
                if (useLightText) Color(0xCC334155) else Color(0xFF272D38)
            },
            pillText = if (isFullyTransparent) {
                if (useLightText) Color(0xFFFDBA74) else Color(0xFFC2410C)
            } else {
                Color(0xFFF8FAFC)
            },
            scheduleContainer = if (isFullyTransparent) Color.Transparent else scheduleBase.copy(alpha = clampedAlpha * 0.85f),
            dividerColor = if (useLightText) Color(0x34FFFFFF) else Color(0x22000000),
            accentOrange = if (useLightText) Color(0xFFFB923C) else Color(0xFFEA580C),
            sundayColor = if (useLightText) Color(0xFFF87171) else Color(0xFFDC2626),
            rainBlue = if (useLightText) Color(0xFF38BDF8) else Color(0xFF0284C7)
        )
    }
}
