package com.example.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import com.example.LhlTimetableApp
import com.example.data.local.WidgetTextTheme
import com.example.data.remote.WeatherCodeMapper
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.LunarDate
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.DailyForecast
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
    val primaryText: Color,
    val secondaryText: Color,
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
    val weekStrip: List<WeekStripDay>,
    val weather: WeatherSnapshot?,
    val weatherDesc: String,
    val weatherIconRes: Int,
    val forecasts3Days: List<DailyForecast>,
    val isLoggedIn: Boolean,
    val teacherName: String,
    val weekNumber: Int,
    val isSundayPreview: Boolean,
    val targetDayLabel: String,
    val morningPeriods: List<ResolvedPeriod>,
    val afternoonPeriods: List<ResolvedPeriod>,
    val morningCompactLine: String,
    val afternoonCompactLine: String,
    val palette: WidgetPalette
)

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

        val palette = resolvePalette(
            context = context,
            opacity = snapshot.preferences.widgetOpacity,
            textTheme = snapshot.preferences.widgetTextTheme
        )

        val weatherCode = weather?.weatherCode ?: 2
        val weatherDesc = weather?.let { WeatherCodeMapper.descriptionVietnamese(it.weatherCode) }
            ?: "Chạm để cập nhật"
        val weatherIconRes = WeatherCodeMapper.widgetIconRes(weatherCode)

        return WidgetRenderData(
            today = today,
            now = now,
            greeting = now.greetingVietnamese(),
            solarDateText = today.formatFullVietnamese(),
            lunarBadgeText = todayLunar.formatBadge(),
            weekStrip = strip,
            weather = weather,
            weatherDesc = weatherDesc,
            weatherIconRes = weatherIconRes,
            forecasts3Days = weather?.dailyForecasts?.take(3).orEmpty(),
            isLoggedIn = teacher != null,
            teacherName = teacher?.displayShortName.orEmpty(),
            weekNumber = snapshot.weekNumber,
            isSundayPreview = isSundayPreview,
            targetDayLabel = targetDayLabel,
            morningPeriods = morning,
            afternoonPeriods = afternoon,
            morningCompactLine = TimetableCalculator.formatCompactSessionLine("Sáng", morning),
            afternoonCompactLine = TimetableCalculator.formatCompactSessionLine("Chiều", afternoon),
            palette = palette
        )
    }

    fun resolvePalette(
        context: Context,
        opacity: Float,
        textTheme: WidgetTextTheme
    ): WidgetPalette {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val useLightText = when (textTheme) {
            WidgetTextTheme.LIGHT -> true
            WidgetTextTheme.DARK -> false
            WidgetTextTheme.AUTO -> isNight
        }

        val clampedAlpha = opacity.coerceIn(0f, 1f)
        val isFullyTransparent = clampedAlpha <= 0.02f
        val bgBase = if (useLightText) Color(0xFF161A22) else Color(0xFFF3EFE8)
        val scheduleBase = if (useLightText) Color(0xFF232936) else Color(0xFFE7E1D6)

        return WidgetPalette(
            useLightText = useLightText,
            isFullyTransparent = isFullyTransparent,
            backgroundColor = if (isFullyTransparent) Color.Transparent else bgBase.copy(alpha = clampedAlpha),
            primaryText = if (useLightText) Color(0xFFF8FAFC) else Color(0xFF141923),
            secondaryText = if (useLightText) Color(0xFFE2E8F0) else Color(0xFF334155),
            pillBackground = if (isFullyTransparent) {
                if (useLightText) Color(0x661E293B) else Color(0xCC272D38)
            } else {
                if (useLightText) Color(0xCC334155) else Color(0xFF272D38)
            },
            pillText = Color(0xFFF8FAFC),
            scheduleContainer = if (isFullyTransparent) Color.Transparent else scheduleBase.copy(alpha = clampedAlpha * 0.85f),
            dividerColor = if (useLightText) Color(0x44FFFFFF) else Color(0x33000000),
            accentOrange = Color(0xFFF97316),
            sundayColor = if (useLightText) Color(0xFFF87171) else Color(0xFFDC2626),
            rainBlue = if (useLightText) Color(0xFF38BDF8) else Color(0xFF0284C7)
        )
    }
}
