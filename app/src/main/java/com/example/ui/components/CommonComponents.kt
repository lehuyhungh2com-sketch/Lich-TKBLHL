package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Room
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WidgetTextTheme
import com.example.data.remote.WeatherCodeMapper
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.WeatherSnapshot
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SundayCoral
import com.example.ui.theme.SunsetOrange
import com.example.ui.theme.SunsetOrangeBright
import com.example.widget.WidgetDataBuilder

data class SegmentOption<T>(
    val value: T,
    val label: String,
    val testTag: String = ""
)

@Composable
fun <T> SegmentedOptionBar(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onSelect(option.value) }
                        .then(if (option.testTag.isNotBlank()) Modifier.testTag(option.testTag) else Modifier)
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherIcon(
    weatherCode: Int,
    isDay: Boolean = true,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (weatherCode) {
        0 -> if (isDay) Icons.Rounded.WbSunny to Color(0xFFF59E0B) else Icons.Rounded.NightsStay to Color(0xFF93C5FD)
        1, 2 -> Icons.Rounded.FilterDrama to Color(0xFFF59E0B)
        3, 45, 48 -> Icons.Rounded.WbCloudy to Color(0xFF94A3B8)
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.Grain to SkyBlue
        71, 73, 75, 77 -> Icons.Rounded.AcUnit to Color(0xFF38BDF8)
        95, 96, 99 -> Icons.Rounded.Thunderstorm to Color(0xFFEAB308)
        else -> Icons.Rounded.Cloud to Color(0xFF94A3B8)
    }
    Icon(
        imageVector = icon,
        contentDescription = WeatherCodeMapper.descriptionVietnamese(weatherCode),
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun HomeroomModeToggle(
    homeroomClassName: String,
    showHomeroom: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortClass = TimetableCalculator.compactClassName(homeroomClassName)
    SegmentedOptionBar(
        options = listOf(
            SegmentOption(false, "TKB của tôi", "toggle_my_timetable"),
            SegmentOption(true, "Lớp CN ($shortClass)", "toggle_homeroom_timetable")
        ),
        selected = showHomeroom,
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
fun PeriodRowCard(
    period: ResolvedPeriod,
    showTeacherName: Boolean,
    modifier: Modifier = Modifier
) {
    val isNow = period.liveStatus == PeriodLiveStatus.TEACHING_NOW
    val isNext = period.liveStatus == PeriodLiveStatus.NEXT_PERIOD
    val isPast = period.liveStatus == PeriodLiveStatus.PAST

    val containerColor = when {
        isNow -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        isNext -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isNow -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
        isNext -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    val badgeBg = when {
        isNow -> MaterialTheme.colorScheme.tertiary
        isNext -> MaterialTheme.colorScheme.primary
        period.isAfternoon -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val badgeText = when {
        isNow -> MaterialTheme.colorScheme.onTertiary
        isNext -> MaterialTheme.colorScheme.onPrimary
        period.isAfternoon -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val shortClass = TimetableCalculator.compactClassName(period.className)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "T${period.period}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                    Text(
                        text = if (period.isAfternoon) "Chiều" else "Sáng",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = badgeText.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (showTeacherName) period.subjectName else "$shortClass • ${period.subjectName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isNow) {
                        StatusTagPill(
                            text = "ĐANG DẠY",
                            bg = EmeraldLive,
                            fg = Color.White
                        )
                    } else if (isNext) {
                        StatusTagPill(
                            text = "TIẾP THEO",
                            bg = SunsetOrange,
                            fg = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = period.timeRangeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showTeacherName && period.teacherShortName.isNotBlank()) {
                        Text(
                            text = "GV: ${period.teacherShortName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (period.room.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Room,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = period.room,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (!period.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = period.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusTagPill(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WeekStripBar(
    today: VnDate,
    selectedDate: VnDate = today,
    onDateClick: ((VnDate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val weekDates = today.weekDatesMondayToSunday()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weekDates.forEach { date ->
            val lunar = LunarCalendar.convertSolar2Lunar(date)
            val isToday = date == today
            val isSelected = date == selectedDate && !isToday
            val bg = when {
                isToday -> SunsetOrangeBright
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val dowColor = when {
                isToday -> Color.White
                date.isSunday -> SundayCoral
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val solarColor = when {
                isToday -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            }
            val lunarColor = when {
                isToday -> Color(0xFFFFEDD5)
                lunar.isSpecialLunarDay -> SunsetOrange
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .then(
                        if (onDateClick != null) Modifier.clickable { onDateClick(date) }
                        else Modifier
                    )
                    .padding(vertical = 8.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = date.shortDayCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = dowColor,
                    fontWeight = if (isToday || date.isSunday) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = solarColor
                )
                Text(
                    text = lunar.formatCellLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = lunarColor
                )
            }
        }
    }
}

@Composable
fun LiveWidgetPreviewCard(
    today: VnDate,
    now: VnTime,
    weather: WeatherSnapshot?,
    todayPeriods: List<ResolvedPeriod>,
    weekNumber: Int,
    transparentMode: Boolean,
    opacity: Float,
    textTheme: WidgetTextTheme,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTransparent = transparentMode || opacity <= 0.02f
    val useLightText = WidgetDataBuilder.detectUseLightText(
        context = context,
        isFullyTransparent = isTransparent,
        textTheme = textTheme
    )
    val bgBase = if (useLightText) Color(0xFF161A22) else Color(0xFFF3EFE8)
    val primaryText = if (useLightText) Color.White else SlateInk
    val secondaryText = if (useLightText) Color(0xFFDDE5F0) else Color(0xFF334155)
    val accentOrange = if (useLightText) Color(0xFFFB923C) else SunsetOrange
    val lunarText = if (isTransparent) {
        if (useLightText) Color(0xFFFDBA74) else Color(0xFFC2410C)
    } else {
        Color.White
    }
    val rootBg = if (isTransparent) Color.Transparent else bgBase.copy(alpha = opacity.coerceIn(0f, 1f))
    val dividerColor = if (useLightText) Color(0x38FFFFFF) else Color(0x28000000)

    val lunar = LunarCalendar.convertSolar2Lunar(today)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = if (useLightText) {
                        listOf(Color(0xFF1A1C24), Color(0xFF2B2832), Color(0xFF111319))
                    } else {
                        listOf(Color(0xFFBFD7EA), Color(0xFFE6DDD0), Color(0xFF9BB7A7))
                    }
                )
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (isTransparent) 0.dp else 18.dp))
                .background(rootBg)
                .padding(if (isTransparent) 4.dp else 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Clock + Solar/Lunar + Weather
            Column(modifier = Modifier.width(106.dp)) {
                Text(
                    text = now.formatHHmm(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Text(
                    text = "${today.vietnameseWeekday}, ${today.formatDayMonth()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isTransparent) {
                    Text(
                        text = lunar.formatBadge(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = lunarText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (useLightText) Color(0xCC334155) else Color(0xFF272D38)
                    ) {
                        Text(
                            text = lunar.formatBadge(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(
                        weatherCode = weather?.weatherCode ?: 2,
                        isDay = weather?.isDay ?: true,
                        size = 22.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = weather?.let { "${it.currentTemp}°" } ?: "16°",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }
                Text(
                    text = weather?.let {
                        val desc = WeatherCodeMapper.descriptionVietnamese(it.weatherCode)
                        if (it.todayRainProb > 0) "$desc · ${it.todayRainProb}%" else desc
                    } ?: "Ít mây",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = secondaryText,
                    maxLines = 1
                )
                Text(
                    text = "${weather?.locationName ?: "Hậu Lộc"} · ${weather?.todayLow ?: 15}°/${weather?.todayHigh ?: 23}°",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Column: Aligned Timetable Rows with Hairline Dividers
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lịch dạy · Tuần $weekNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentOrange
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (todayPeriods.isEmpty()) {
                    Text(
                        text = "Hôm nay không có tiết dạy",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText
                    )
                } else {
                    val visible = todayPeriods.take(5)
                    visible.forEachIndexed { idx, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (p.isAfternoon) "C${p.period}" else "T${p.period}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (p.isAfternoon) SkyBlue else accentOrange,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = "${TimetableCalculator.compactClassName(p.className)} · ${p.subjectName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = p.startTime.formatHHmm(),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryText
                            )
                        }
                        if (idx < visible.lastIndex) {
                            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveWeatherTimetablePreviewCard(
    today: VnDate,
    weather: WeatherSnapshot?,
    todayPeriods: List<ResolvedPeriod>,
    weekNumber: Int,
    transparentMode: Boolean,
    opacity: Float,
    textTheme: WidgetTextTheme,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTransparent = transparentMode || opacity <= 0.02f
    val clampedAlpha = opacity.coerceIn(0f, 1f)
    val useLightText = WidgetDataBuilder.detectUseLightText(
        context = context,
        isFullyTransparent = isTransparent,
        textTheme = textTheme
    )

    val bgBase = if (useLightText) Color(0xFF161A22) else Color(0xFFFFFCF7)
    val tileBase = if (useLightText) Color(0xFF252C3A) else Color(0xFFF3EFE8)
    val primaryText = if (useLightText) Color.White else SlateInk
    val secondaryText = if (useLightText) Color(0xFFDDE5F0) else Color(0xFF334155)
    val mutedPastText = if (useLightText) Color(0x99CBD5E1) else Color(0x88475569)
    val accentOrange = if (useLightText) Color(0xFFFB923C) else SunsetOrange
    val liveAccent = if (useLightText) Color(0xFF34D399) else Color(0xFF059669)
    val dividerColor = if (useLightText) Color(0x38FFFFFF) else Color(0x26000000)

    val rootBg = if (isTransparent) Color.Transparent else bgBase.copy(alpha = clampedAlpha)
    val tileBg = if (isTransparent) Color.Transparent else tileBase.copy(alpha = (clampedAlpha * 0.92f).coerceIn(0f, 1f))
    val liveBg = if (isTransparent) {
        Color.Transparent
    } else {
        if (useLightText) Color(0xFF10B981).copy(alpha = (clampedAlpha * 0.34f).coerceIn(0.18f, 0.34f))
        else Color(0xFF059669).copy(alpha = (clampedAlpha * 0.24f).coerceIn(0.12f, 0.24f))
    }

    val lunar = LunarCalendar.convertSolar2Lunar(today)
    val lunarShort = "${lunar.day}/${lunar.month}${if (lunar.isLeap) "N" else ""} ÂL"
    val locName = weather?.locationName ?: "Hậu Lộc"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = if (useLightText) {
                        listOf(Color(0xFF1A1C24), Color(0xFF2B2832), Color(0xFF111319))
                    } else {
                        listOf(Color(0xFFBFD7EA), Color(0xFFE6DDD0), Color(0xFF9BB7A7))
                    }
                )
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (isTransparent) 0.dp else 20.dp))
                .background(rootBg)
                .padding(if (isTransparent) 4.dp else 12.dp)
        ) {
            // Tier 1: Location + Date + Refresh + Weather
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = SunsetOrangeBright,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$locName · ${today.shortDayCode}, ${today.formatDayMonth()} ($lunarShort)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = secondaryText,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    WeatherIcon(
                        weatherCode = weather?.weatherCode ?: 2,
                        isDay = weather?.isDay ?: true,
                        size = 32.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${weather?.currentTemp ?: 16}°C",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = primaryText,
                            maxLines = 1
                        )
                        Text(
                            text = weather?.let { WeatherCodeMapper.descriptionVietnamese(it.weatherCode) } ?: "Ít mây",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "↑ ${weather?.todayHigh ?: 23}°  ↓ ${weather?.todayLow ?: 15}°",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = SkyBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = " Mưa ${weather?.todayRainProb ?: 0}% · Ẩm ${weather?.humidity ?: 71}%",
                            fontSize = 10.sp,
                            color = secondaryText,
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Air,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = " Gió ${weather?.windSpeedKmh ?: 12} km/h",
                            fontSize = 10.sp,
                            color = secondaryText,
                            maxLines = 1
                        )
                    }
                }
            }

            // Tier 2: 4-Day Forecast
            val forecasts = weather?.dailyForecasts?.take(4).orEmpty()
            if (forecasts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    forecasts.forEachIndexed { idx, f ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(if (isTransparent) 0.dp else 12.dp))
                                .background(tileBg)
                                .padding(vertical = if (isTransparent) 2.dp else 6.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = if (idx == 0) "H.NAY" else f.date.forecastShortLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryText,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            WeatherIcon(weatherCode = f.weatherCode, size = 18.dp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${f.precipitationProbability}%",
                                fontSize = 9.sp,
                                color = SkyBlue,
                                maxLines = 1
                            )
                            Text(
                                text = "${f.tempMin}°/${f.tempMax}°",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Tier 3: 2-Column Live Timetable
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            val teachingNow = todayPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.TEACHING_NOW }
            val nextPeriod = todayPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.NEXT_PERIOD }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch dạy · Tuần $weekNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentOrange,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                val statusLabel = when {
                    teachingNow != null -> "● Đang dạy: T${teachingNow.period} (${TimetableCalculator.compactClassName(teachingNow.className)})"
                    nextPeriod != null -> "● Tiếp: T${nextPeriod.period} (${nextPeriod.startTime.formatHHmm()})"
                    else -> null
                }
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (teachingNow != null) liveAccent else accentOrange,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (todayPeriods.isEmpty()) {
                Text(
                    text = "Hôm nay không có tiết dạy",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )
            } else {
                val pairs = todayPeriods.take(4).chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    pairs.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            pair.forEach { p ->
                                val isNow = p.liveStatus == PeriodLiveStatus.TEACHING_NOW
                                val isPast = p.liveStatus == PeriodLiveStatus.PAST
                                val tag = if (p.isAfternoon) "C${p.period}" else "T${p.period}"
                                val tagColor = when {
                                    isNow -> liveAccent
                                    isPast -> mutedPastText
                                    p.isAfternoon -> SkyBlue
                                    else -> accentOrange
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(if (isTransparent) 0.dp else 10.dp))
                                        .background(if (isNow) liveBg else tileBg)
                                        .padding(
                                            horizontal = if (isTransparent) 2.dp else 6.dp,
                                            vertical = if (isTransparent) 2.dp else 4.dp
                                        )
                                ) {
                                    Text(
                                        text = if (isNow) "● $tag" else tag,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tagColor,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${TimetableCalculator.compactClassName(p.className)} · ${p.subjectName}",
                                        fontSize = 10.sp,
                                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isPast) mutedPastText else primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
