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
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NightsStay
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
    opacity: Float,
    textTheme: WidgetTextTheme,
    modifier: Modifier = Modifier
) {
    val useLightText = when (textTheme) {
        WidgetTextTheme.LIGHT -> true
        WidgetTextTheme.DARK -> false
        WidgetTextTheme.AUTO -> true
    }
    val isTransparent = opacity <= 0.02f
    val bgBase = if (useLightText) Color(0xFF161A22) else Color(0xFFF3EFE8)
    val primaryText = if (useLightText) Color.White else SlateInk
    val secondaryText = if (useLightText) Color(0xFFE2E8F0) else Color(0xFF334155)
    val pillBg = if (isTransparent) {
        if (useLightText) Color(0x661E293B) else Color(0xCC272D38)
    } else {
        if (useLightText) Color(0xCC334155) else Color(0xFF272D38)
    }
    val rootBg = if (isTransparent) Color.Transparent else bgBase.copy(alpha = opacity.coerceIn(0f, 1f))
    val scheduleBg = if (isTransparent) {
        Color.Transparent
    } else {
        (if (useLightText) Color(0xFF232936) else Color(0xFFE5DFD3)).copy(alpha = opacity.coerceIn(0f, 1f) * 0.85f)
    }

    val lunar = LunarCalendar.convertSolar2Lunar(today)

    // Simulated wallpaper backdrop behind the translucent widget preview
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = if (useLightText) {
                        listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF0F172A))
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
                .clip(RoundedCornerShape(18.dp))
                .background(rootBg)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = now.greetingVietnamese(),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText
                    )
                    Text(
                        text = now.formatHHmm(),
                        style = MaterialTheme.typography.displayMedium,
                        color = primaryText
                    )
                    Text(
                        text = today.formatFullVietnamese(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = primaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = pillBg
                    ) {
                        Text(
                            text = lunar.formatBadge(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(
                            weatherCode = weather?.weatherCode ?: 2,
                            isDay = weather?.isDay ?: true,
                            size = 24.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = weather?.let { "${it.currentTemp}°" } ?: "20°",
                            style = MaterialTheme.typography.headlineMedium,
                            color = primaryText
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = weather?.let { WeatherCodeMapper.descriptionVietnamese(it.weatherCode) }
                                ?: "Ít mây",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = SkyBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${weather?.todayRainProb ?: 15}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyBlue
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = SunsetOrangeBright,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${weather?.locationName ?: "Hậu Lộc"} • ↑${weather?.todayHigh ?: 24}° ↓${weather?.todayLow ?: 17}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = if (useLightText) Color(0x44FFFFFF) else Color(0x33000000))
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheduleBg)
                    .padding(
                        horizontal = if (isTransparent) 2.dp else 10.dp,
                        vertical = if (isTransparent) 2.dp else 7.dp
                    )
            ) {
                Text(
                    text = "Lịch dạy hôm nay • Tuần $weekNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SunsetOrangeBright
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (todayPeriods.isEmpty()) {
                    Text(
                        text = "Hôm nay không có tiết dạy",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText
                    )
                } else {
                    todayPeriods.take(4).forEach { p ->
                        val tag = if (p.isAfternoon) "C" else "S"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = "${p.startTime.formatHHmm()} ${tag}T${p.period}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (p.isAfternoon) SkyBlue else SunsetOrangeBright
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${TimetableCalculator.compactClassName(p.className)} • ${p.subjectShort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
