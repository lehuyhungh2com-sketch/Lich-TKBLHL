package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.WeatherCodeMapper
import com.example.domain.lunar.LunarCalendar
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.WeatherSnapshot
import com.example.ui.MainUiState
import com.example.ui.components.HomeroomModeToggle
import com.example.ui.components.LessonNoteEditorSheet
import com.example.ui.components.LivePeriodHeroCard
import com.example.ui.components.PeriodRowCard
import com.example.ui.components.WeatherIcon
import com.example.ui.components.WeekStripBar
import com.example.ui.components.WeeklyStatsRibbon
import com.example.ui.theme.LocalCompactDensity
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SunsetOrange
import com.example.ui.theme.SunsetOrangeBright

@Composable
fun TodayScreen(
    uiState: MainUiState,
    onToggleHomeroom: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenTimetable: () -> Unit,
    onSaveLessonNote: (ResolvedPeriod, String) -> Unit,
    onDeleteLessonNote: (ResolvedPeriod) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val teacher = uiState.currentTeacher
    val isCompact = LocalCompactDensity.current
    val holiday = LunarCalendar.getVietnameseHolidayLabel(uiState.todayDate, uiState.todayLunar)
    val todayAllPeriods = uiState.todayMorningPeriods + uiState.todayAfternoonPeriods
    var editingPeriod by remember { mutableStateOf<ResolvedPeriod?>(null) }
    var expandCalendarWeather by rememberSaveable { mutableStateOf(false) }

    val todayDone = todayAllPeriods.count { it.liveStatus == PeriodLiveStatus.PAST }
    val weekMorning = uiState.currentWeekPeriods.count { !it.isAfternoon }
    val weekAfternoon = uiState.currentWeekPeriods.count { it.isAfternoon }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (isCompact) 12.dp else 16.dp,
                vertical = if (isCompact) 10.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp)
        ) {
            // Greeting & Teacher Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.nowTime.greetingVietnamese()},",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = teacher?.name ?: "Giáo viên THCS Lê Hữu Lập",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tuần ${uiState.currentWeekNumber} • ${uiState.schoolConfig.academicYear}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (teacher != null && teacher.department.isNotBlank()) {
                                Text(
                                    text = "• ${teacher.department}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onSyncNow,
                        enabled = !uiState.isSyncing,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("today_sync_button")
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Đồng bộ thời khóa biểu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Sync / Info Status Banner
            item {
                AnimatedVisibility(visible = !uiState.syncStatusBanner.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.syncStatusBanner.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissBanner) {
                                Text("Đóng", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 1. REAL-TIME TEACHING FOCUS HERO CARD (Live progress bar + countdown)
            if (!uiState.isSundayPreview && todayAllPeriods.isNotEmpty()) {
                item {
                    LivePeriodHeroCard(
                        todayPeriods = todayAllPeriods,
                        nowTime = uiState.nowTime,
                        isSundayPreview = uiState.isSundayPreview,
                        showTeacherName = uiState.effectiveShowHomeroom,
                        personalNoteLookup = uiState::personalNoteFor,
                        onPeriodClick = { editingPeriod = it }
                    )
                }
            }

            // 2. COMPACT COLLAPSIBLE DATE + LUNAR + WEATHER SUMMARY BAR
            item {
                CollapsibleCalendarWeatherCard(
                    uiState = uiState,
                    holiday = holiday,
                    expanded = expandCalendarWeather,
                    onToggleExpand = { expandCalendarWeather = !expandCalendarWeather },
                    onRefreshWeather = onRefreshWeather
                )
            }

            // 3. TODAY'S TIMETABLE CARD (Tap any period to add personal note)
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.isSundayPreview) {
                                        "Xem trước Thứ Hai • Tuần ${uiState.currentWeekNumber}"
                                    } else {
                                        "Lịch dạy hôm nay (${uiState.todayDate.vietnameseWeekday})"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val totalCount = todayAllPeriods.size
                                Text(
                                    text = if (totalCount == 0) {
                                        "Không có tiết dạy"
                                    } else {
                                        "$totalCount tiết (${uiState.todayMorningPeriods.size} sáng, ${uiState.todayAfternoonPeriods.size} chiều) • Chạm tiết để ghi chú"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = onOpenTimetable,
                                modifier = Modifier.testTag("today_open_full_timetable")
                            ) {
                                Text("Cả tuần →")
                            }
                        }

                        if (uiState.canToggleHomeroom) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HomeroomModeToggle(
                                homeroomClassName = uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty(),
                                showHomeroom = uiState.effectiveShowHomeroom,
                                onSelect = onToggleHomeroom
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (todayAllPeriods.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (uiState.isSundayPreview) {
                                            "Thứ Hai tuần ${uiState.currentWeekNumber} chưa có tiết dạy"
                                        } else {
                                            "Hôm nay không có tiết dạy"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Chúc thầy/cô một ngày làm việc nhẹ nhàng và hiệu quả.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            if (uiState.todayMorningPeriods.isNotEmpty()) {
                                SessionHeaderRow(
                                    icon = Icons.Rounded.WbSunny,
                                    title = "Buổi Sáng",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)) {
                                    uiState.todayMorningPeriods.forEach { period ->
                                        PeriodRowCard(
                                            period = period,
                                            showTeacherName = uiState.effectiveShowHomeroom,
                                            personalNote = uiState.personalNoteFor(period),
                                            onClick = { editingPeriod = period }
                                        )
                                    }
                                }
                            }

                            if (uiState.todayAfternoonPeriods.isNotEmpty()) {
                                if (uiState.todayMorningPeriods.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                SessionHeaderRow(
                                    icon = Icons.Rounded.WbTwilight,
                                    title = "Buổi Chiều (HSG / CLB)",
                                    tint = SkyBlue
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)) {
                                    uiState.todayAfternoonPeriods.forEach { period ->
                                        PeriodRowCard(
                                            period = period,
                                            showTeacherName = uiState.effectiveShowHomeroom,
                                            personalNote = uiState.personalNoteFor(period),
                                            onClick = { editingPeriod = period }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. WEEKLY TEACHING STATS RIBBON
            item {
                WeeklyStatsRibbon(
                    todayDone = todayDone,
                    todayTotal = todayAllPeriods.size,
                    weekMorning = weekMorning,
                    weekAfternoon = weekAfternoon,
                    notesCount = uiState.lessonNotes.size
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    editingPeriod?.let { period ->
        LessonNoteEditorSheet(
            period = period,
            existingNote = uiState.personalNoteFor(period).orEmpty(),
            onDismiss = { editingPeriod = null },
            onSave = { content -> onSaveLessonNote(period, content) },
            onDelete = { onDeleteLessonNote(period) }
        )
    }
}

@Composable
private fun CollapsibleCalendarWeatherCard(
    uiState: MainUiState,
    holiday: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefreshWeather: () -> Unit
) {
    val weather = uiState.weather
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onToggleExpand() }
            .testTag("collapsible_calendar_weather_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Compact Always-Visible Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.nowTime.formatHHmm(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${uiState.todayDate.vietnameseWeekday}, ${uiState.todayDate.formatDayMonth()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${uiState.todayLunar.formatBadge()} • Ngày ${uiState.todayLunar.canChiDay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Compact weather pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (weather != null) {
                        WeatherIcon(
                            weatherCode = weather.weatherCode,
                            isDay = weather.isDay,
                            size = 24.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${weather.currentTemp}°C",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "${weather.todayLow}°/${weather.todayHigh}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Thu gọn" else "Mở rộng lịch & thời tiết",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!holiday.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Sự kiện hôm nay: $holiday",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Expandable Full Can-Chi + WeekStrip + 4-Day Forecast
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CanChiMiniCell("Ngày", uiState.todayLunar.canChiDay, Modifier.weight(1f))
                        CanChiMiniCell("Tháng", uiState.todayLunar.canChiMonth, Modifier.weight(1f))
                        CanChiMiniCell(
                            "Giờ",
                            LunarCalendar.getHourCanChi(uiState.todayLunar.julianDay, uiState.nowTime.hour),
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    WeekStripBar(today = uiState.todayDate)

                    if (weather != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        ExpandedWeatherDetails(
                            weather = weather,
                            isRefreshing = uiState.isRefreshingWeather,
                            onRefresh = onRefreshWeather
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedWeatherDetails(
    weather: WeatherSnapshot,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${weather.locationName} • ${WeatherCodeMapper.descriptionVietnamese(weather.weatherCode)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier
                .size(36.dp)
                .testTag("refresh_weather_button")
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Cập nhật thời tiết",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(14.dp))
            Text(
                text = " Mưa ${weather.todayRainProb}% • Ẩm ${weather.humidity}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Air, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(
                text = " Gió ${weather.windSpeedKmh} km/h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (weather.dailyForecasts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            weather.dailyForecasts.take(4).forEachIndexed { idx, f ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (idx == 0) "H.NAY" else f.date.forecastShortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        WeatherIcon(weatherCode = f.weatherCode, size = 22.dp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${f.precipitationProbability}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyBlue,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${f.tempMin}°/${f.tempMax}°",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanChiMiniCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SessionHeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}
