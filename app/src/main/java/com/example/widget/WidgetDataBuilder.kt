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
    val cardTileBackground: Color,
    val liveNowBackground: Color,
    val liveNowAccent: Color,
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
            transparentMode = snapshot.preferences.widgetTransparentMode,
            opacity = snapshot.preferences.widgetOpacity,
            textTheme = snapshot.preferences.widgetTextTheme
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
                                // Estimate luminance from primary wallpaper color on API 27-30
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
        val bgBase = if (useLightText) Color(0xFF161A22) else Color(0xFFFFFCF7)
        val tileBase = if (useLightText) Color(0xFF252C3A) else Color(0xFFF3EFE8)
        val scheduleBase = if (useLightText) Color(0xFF232936) else Color(0xFFE7E1D6)
        val tileAlpha = (clampedAlpha * 0.92f).coerceIn(0f, 1f)
        val liveTileAlpha = (clampedAlpha * 0.34f).coerceIn(0.18f, 0.34f)

        return WidgetPalette(
            useLightText = useLightText,
            isFullyTransparent = isFullyTransparent,
            backgroundColor = if (isFullyTransparent) Color.Transparent else bgBase.copy(alpha = clampedAlpha),
            cardTileBackground = if (isFullyTransparent) Color.Transparent else tileBase.copy(alpha = tileAlpha),
            liveNowBackground = if (isFullyTransparent) {
                Color.Transparent
            } else {
                if (useLightText) Color(0xFF10B981).copy(alpha = liveTileAlpha)
                else Color(0xFF059669).copy(alpha = liveTileAlpha * 0.7f)
            },
            liveNowAccent = if (useLightText) Color(0xFF34D399) else Color(0xFF059669),
            primaryText = if (useLightText) Color(0xFFFFFFFF) else Color(0xFF111827),
            secondaryText = if (useLightText) Color(0xFFDDE5F0) else Color(0xFF334155),
            mutedPastText = if (useLightText) Color(0x99CBD5E1) else Color(0x88475569),
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
            dividerColor = if (useLightText) Color(0x38FFFFFF) else Color(0x26000000),
            accentOrange = if (useLightText) Color(0xFFFB923C) else Color(0xFFEA580C),
            sundayColor = if (useLightText) Color(0xFFF87171) else Color(0xFFDC2626),
            rainBlue = if (useLightText) Color(0xFF38BDF8) else Color(0xFF0284C7)
        )
    }
}
