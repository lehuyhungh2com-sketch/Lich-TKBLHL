package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.components.HomeroomModeToggle
import com.example.ui.components.PeriodRowCard
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SunsetOrange
import com.example.ui.theme.SunsetOrangeBright

@Composable
fun TimetableScreen(
    uiState: MainUiState,
    onSelectWeek: (Int) -> Unit,
    onToggleHomeroom: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMatrixView by rememberSaveable { mutableStateOf(false) }
    var selectedSessionFilter by rememberSaveable { mutableStateOf("all") } // "all" | "morning" | "afternoon"

    val (weekStart, weekEnd) = TimetableCalculator.getWeekDateRange(
        weekNumber = uiState.selectedWeekNumber,
        schoolConfig = uiState.schoolConfig,
        weeks = uiState.weeks
    )
    val isLockedWeek = uiState.selectedWeekInfo?.isLocked == true

    val filteredPeriods = when (selectedSessionFilter) {
        "morning" -> uiState.selectedWeekPeriods.filter { !it.isAfternoon }
        "afternoon" -> uiState.selectedWeekPeriods.filter { it.isAfternoon }
        else -> uiState.selectedWeekPeriods
    }

    val periodsByDay = TimetableCalculator.SCHOOL_DAYS.associateWith { day ->
        filteredPeriods.filter { it.day == day }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + View Mode Switch
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thời khóa biểu",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (uiState.effectiveShowHomeroom) {
                                "Lớp chủ nhiệm ${uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty()}"
                            } else {
                                uiState.currentTeacher?.name ?: "Giáo viên"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { isMatrixView = !isMatrixView },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("timetable_toggle_matrix")
                        ) {
                            Icon(
                                imageVector = if (isMatrixView) Icons.Rounded.ViewAgenda else Icons.Rounded.GridOn,
                                contentDescription = if (isMatrixView) "Xem theo danh sách" else "Xem bảng ma trận",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onSyncNow,
                            enabled = !uiState.isSyncing,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("timetable_sync_button")
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = "Đồng bộ tuần này",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Week Selector Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { onSelectWeek(uiState.selectedWeekNumber - 1) },
                            enabled = uiState.selectedWeekNumber > TimetableCalculator.MIN_WEEK,
                            modifier = Modifier.testTag("week_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Tuần trước"
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectWeek(uiState.currentWeekNumber) }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = uiState.selectedWeekInfo?.name?.takeIf { it.isNotBlank() }
                                        ?: "Tuần ${uiState.selectedWeekNumber}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isLockedWeek) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = "Tuần đã khóa",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Đã khóa",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "${weekStart.formatDayMonth()} – ${weekEnd.formatDayMonth()}/${weekEnd.year}" +
                                    if (uiState.selectedWeekNumber == uiState.currentWeekNumber) " • Tuần hiện tại" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onSelectWeek(uiState.selectedWeekNumber + 1) },
                            enabled = uiState.selectedWeekNumber < TimetableCalculator.MAX_WEEK,
                            modifier = Modifier.testTag("week_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Tuần sau"
                            )
                        }
                    }
                }
            }

            // Homeroom Toggle (if GVCN)
            if (uiState.canToggleHomeroom) {
                item {
                    HomeroomModeToggle(
                        homeroomClassName = uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty(),
                        showHomeroom = uiState.effectiveShowHomeroom,
                        onSelect = onToggleHomeroom
                    )
                }
            }

            // Session Filter Segmented Bar (All / Morning / Afternoon — never wraps vertically)
            item {
                val morningCount = uiState.selectedWeekPeriods.count { !it.isAfternoon }
                val afternoonCount = uiState.selectedWeekPeriods.count { it.isAfternoon }
                SegmentedOptionBar(
                    options = listOf(
                        SegmentOption("all", "Tất cả (${morningCount + afternoonCount})", "filter_all_sessions"),
                        SegmentOption("morning", "Sáng ($morningCount)", "filter_morning_session"),
                        SegmentOption("afternoon", "Chiều ($afternoonCount)", "filter_afternoon_session")
                    ),
                    selected = selectedSessionFilter,
                    onSelect = { selectedSessionFilter = it }
                )
            }

            // Main Content: Matrix Grid or Day-by-Day List
            if (isMatrixView) {
                item {
                    TimetableMatrixSection(
                        periods = uiState.selectedWeekPeriods,
                        showTeacher = uiState.effectiveShowHomeroom,
                        todayCode = if (uiState.selectedWeekNumber == uiState.currentWeekNumber && !uiState.isSundayPreview) {
                            uiState.todayDate.shortDayCode
                        } else null,
                        showMorning = selectedSessionFilter != "afternoon",
                        showAfternoon = selectedSessionFilter != "morning"
                    )
                }
            } else {
                if (filteredPeriods.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarViewWeek,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Chưa có tiết dạy trong Tuần ${uiState.selectedWeekNumber}",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Nhấn biểu tượng Đồng bộ góc phải để tải lịch mới nhất từ hệ thống nhà trường.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(TimetableCalculator.SCHOOL_DAYS) { dayCode ->
                        val dayPeriods = periodsByDay[dayCode].orEmpty()
                        val isToday = uiState.selectedWeekNumber == uiState.currentWeekNumber &&
                            !uiState.isSundayPreview &&
                            uiState.todayDate.shortDayCode == dayCode

                        DayScheduleCard(
                            dayCode = dayCode,
                            dayIndex = TimetableCalculator.SCHOOL_DAYS.indexOf(dayCode),
                            weekStartDate = weekStart,
                            isToday = isToday,
                            periods = dayPeriods,
                            showTeacherName = uiState.effectiveShowHomeroom
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun DayScheduleCard(
    dayCode: String,
    dayIndex: Int,
    weekStartDate: com.example.domain.lunar.VnDate,
    isToday: Boolean,
    periods: List<ResolvedPeriod>,
    showTeacherName: Boolean
) {
    val date = weekStartDate.plusDays(dayIndex)
    val morning = periods.filter { !it.isAfternoon }
    val afternoon = periods.filter { it.isAfternoon }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isToday) 1.5.dp else 1.dp,
            color = if (isToday) SunsetOrangeBright else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isToday) SunsetOrangeBright else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = dayCode,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${date.vietnameseWeekday}, ${date.formatDayMonth()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isToday) {
                            Text(
                                text = "Hôm nay",
                                style = MaterialTheme.typography.labelSmall,
                                color = SunsetOrange
                            )
                        }
                    }
                }

                Text(
                    text = if (periods.isEmpty()) "Nghỉ" else "${periods.size} tiết",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (periods.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (periods.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                if (morning.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WbSunny,
                            contentDescription = null,
                            tint = SunsetOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Buổi Sáng",
                            style = MaterialTheme.typography.labelMedium,
                            color = SunsetOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        morning.forEach { p ->
                            PeriodRowCard(period = p, showTeacherName = showTeacherName)
                        }
                    }
                }

                if (afternoon.isNotEmpty()) {
                    if (morning.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WbTwilight,
                            contentDescription = null,
                            tint = SkyBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Buổi Chiều (HSG / CLB)",
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        afternoon.forEach { p ->
                            PeriodRowCard(period = p, showTeacherName = showTeacherName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableMatrixSection(
    periods: List<ResolvedPeriod>,
    showTeacher: Boolean,
    todayCode: String?,
    showMorning: Boolean,
    showAfternoon: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showMorning) {
            MatrixCard(
                title = "Bảng TKB Buổi Sáng",
                accentColor = SunsetOrange,
                periods = periods.filter { !it.isAfternoon },
                showTeacher = showTeacher,
                todayCode = todayCode
            )
        }
        if (showAfternoon) {
            val afternoonPeriods = periods.filter { it.isAfternoon }
            if (afternoonPeriods.isNotEmpty() || !showMorning) {
                MatrixCard(
                    title = "Bảng TKB Buổi Chiều (HSG / CLB)",
                    accentColor = SkyBlue,
                    periods = afternoonPeriods,
                    showTeacher = showTeacher,
                    todayCode = todayCode
                )
            }
        }
    }
}

@Composable
private fun MatrixCard(
    title: String,
    accentColor: Color,
    periods: List<ResolvedPeriod>,
    showTeacher: Boolean,
    todayCode: String?
) {
    val days = TimetableCalculator.SCHOOL_DAYS
    val scrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.horizontalScroll(scrollState)) {
                // Header Row
                Row {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(42.dp)
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "Tiết",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    days.forEach { d ->
                        val isToday = d == todayCode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isToday) SunsetOrangeBright else Color.Transparent)
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Period rows 1..5 (recess automatically hidden!)
                for (p in 1..5) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.width(42.dp)
                        ) {
                            Text(
                                text = "T$p",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        days.forEach { d ->
                            val match = periods.firstOrNull { it.day == d && it.period == p }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(76.dp)
                                    .padding(horizontal = 2.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (match != null) accentColor.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .padding(4.dp)
                            ) {
                                if (match != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (showTeacher) match.subjectName else TimetableCalculator.compactClassName(match.className),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (showTeacher) match.teacherShortName else match.subjectName,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "–",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
