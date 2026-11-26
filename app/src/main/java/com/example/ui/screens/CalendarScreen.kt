package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.LunarDate
import com.example.domain.lunar.VnDate
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarEventCategory
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.SundayCoral
import com.example.ui.theme.SunsetOrange

@Composable
fun CalendarScreen(
    uiState: MainUiState,
    onJumpToWeek: (Int) -> Unit,
    onSaveEvent: (CalendarEvent) -> Unit = {},
    onDeleteEvent: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = uiState.todayDate
    var displayYear by rememberSaveable { mutableIntStateOf(today.year) }
    var displayMonth by rememberSaveable { mutableIntStateOf(today.month) }
    var selectedYear by rememberSaveable { mutableIntStateOf(today.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(today.month) }
    var selectedDay by rememberSaveable { mutableIntStateOf(today.day) }

    var showEventEditor by rememberSaveable { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    val selectedDate = remember(selectedYear, selectedMonth, selectedDay) {
        VnDate(selectedYear, selectedMonth, selectedDay)
    }
    val selectedLunar = remember(selectedDate) {
        LunarCalendar.convertSolar2Lunar(selectedDate)
    }
    val selectedHoliday = remember(selectedDate, selectedLunar) {
        LunarCalendar.getVietnameseHolidayLabel(selectedDate, selectedLunar)
    }
    val selectedSchoolWeek = remember(selectedDate, uiState.schoolConfig, uiState.weeks) {
        TimetableCalculator.calculateCurrentWeek(selectedDate, uiState.schoolConfig, uiState.weeks)
    }

    val monthCells = remember(displayYear, displayMonth) {
        buildMonthGrid(displayYear, displayMonth)
    }

    val eventsByDateIso = remember(monthCells, uiState.calendarEvents) {
        monthCells.filterNotNull().associate { cell ->
            val lunar = LunarCalendar.convertSolar2Lunar(cell)
            val matched = uiState.calendarEvents.filter {
                TimetableCalculator.eventMatchesDate(it, cell, lunar)
            }
            cell.toIsoString() to matched
        }
    }

    val selectedDateEvents = remember(selectedDate, selectedLunar, uiState.calendarEvents) {
        uiState.calendarEvents.filter {
            TimetableCalculator.eventMatchesDate(it, selectedDate, selectedLunar)
        }
    }

    val monthEventsList = remember(monthCells, eventsByDateIso) {
        monthCells.filterNotNull().flatMap { cell ->
            eventsByDateIso[cell.toIsoString()].orEmpty().map { ev -> cell to ev }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lịch Âm – Dương",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    TextButton(
                        onClick = {
                            displayYear = today.year
                            displayMonth = today.month
                            selectedYear = today.year
                            selectedMonth = today.month
                            selectedDay = today.day
                        },
                        modifier = Modifier.testTag("calendar_jump_today")
                    ) {
                        Text("Hôm nay")
                    }
                }
            }

            // Month Navigation & Grid Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (displayMonth == 1) {
                                        displayMonth = 12
                                        displayYear -= 1
                                    } else {
                                        displayMonth -= 1
                                    }
                                },
                                modifier = Modifier.testTag("calendar_prev_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Tháng trước"
                                )
                            }

                            val midMonthLunar = LunarCalendar.convertSolar2Lunar(VnDate(displayYear, displayMonth, 15))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Tháng $displayMonth, $displayYear",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Năm ${midMonthLunar.canChiYear}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (displayMonth == 12) {
                                        displayMonth = 1
                                        displayYear += 1
                                    } else {
                                        displayMonth += 1
                                    }
                                },
                                modifier = Modifier.testTag("calendar_next_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Tháng sau"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Weekday headers T2..CN
                        val headers = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            headers.forEachIndexed { idx, label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = if (idx == 6) SundayCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 6 rows x 7 columns
                        monthCells.chunked(7).forEach { weekRow ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                weekRow.forEach { cell ->
                                    if (cell == null) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else {
                                        val isToday = cell == today
                                        val isSelected = cell == selectedDate
                                        val lunar = LunarCalendar.convertSolar2Lunar(cell)
                                        val dayEvents = eventsByDateIso[cell.toIsoString()].orEmpty()

                                        val bg = when {
                                            isToday -> MaterialTheme.colorScheme.primary
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                        val solarColor = when {
                                            isToday -> MaterialTheme.colorScheme.onPrimary
                                            cell.isSunday -> SundayCoral
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        val lunarColor = when {
                                            isToday -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                            lunar.isSpecialLunarDay -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(bg)
                                                .clickable {
                                                    selectedYear = cell.year
                                                    selectedMonth = cell.month
                                                    selectedDay = cell.day
                                                }
                                                .padding(vertical = 5.dp)
                                                .testTag("calendar_cell_${cell.year}_${cell.month}_${cell.day}")
                                        ) {
                                            Text(
                                                text = cell.day.toString(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = solarColor
                                            )
                                            Text(
                                                text = lunar.formatCellLabel(),
                                                fontSize = 10.sp,
                                                fontWeight = if (lunar.isSpecialLunarDay) FontWeight.Bold else FontWeight.Normal,
                                                color = lunarColor
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            if (dayEvents.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.testTag("calendar_event_dot_${cell.year}_${cell.month}_${cell.day}")
                                                ) {
                                                    dayEvents.take(3).forEach { ev ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isToday) Color.White
                                                                    else categoryColor(ev.category)
                                                                )
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.height(5.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Details Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedDate.formatFullVietnamese()}, ${selectedDate.year}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Âm lịch: Ngày ${selectedLunar.day} tháng ${selectedLunar.month}" +
                                        (if (selectedLunar.isLeap) " (nhuận)" else "") +
                                        " năm ${selectedLunar.canChiYear}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Tuần $selectedSchoolWeek",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (!selectedHoliday.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = selectedHoliday,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CanChiColumn(label = "Can Chi Ngày", value = selectedLunar.canChiDay)
                            CanChiColumn(label = "Can Chi Tháng", value = selectedLunar.canChiMonth)
                            CanChiColumn(label = "Can Chi Năm", value = selectedLunar.canChiYear)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { onJumpToWeek(selectedSchoolWeek) },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("calendar_open_week_timetable")
                        ) {
                            Text("Xem TKB Tuần $selectedSchoolWeek →")
                        }
                    }
                }
            }

            // Personal Events & Work Schedule for Selected Date
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_events_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Lịch công tác & Sự kiện",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ngày ${selectedDate.formatDayMonth()}/${selectedDate.year} • Lưu trên máy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    editingEvent = null
                                    showEventEditor = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("calendar_add_event_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thêm")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedDateEvents.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Chưa có sự kiện nào trong ngày này. Nhấn \"Thêm\" để ghi chú họp chuyên môn, coi thi hoặc ngày kỷ niệm âm/dương.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedDateEvents.forEach { ev ->
                                    CalendarEventRowCard(
                                        event = ev,
                                        onClick = {
                                            editingEvent = ev
                                            showEventEditor = true
                                        },
                                        onDelete = { onDeleteEvent(ev.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Month overview list when the displayed month has events
            if (monthEventsList.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Sự kiện trong tháng $displayMonth/${displayYear} (${monthEventsList.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                monthEventsList.forEach { (date, ev) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedYear = date.year
                                                selectedMonth = date.month
                                                selectedDay = date.day
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(categoryColor(ev.category))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = date.formatDayMonth(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ev.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (ev.isLunar) {
                                            Text(
                                                text = "Âm ${ev.day}/${ev.month}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }

    if (showEventEditor) {
        CalendarEventEditorSheet(
            initialEvent = editingEvent,
            solarDate = selectedDate,
            lunarDate = selectedLunar,
            onDismiss = {
                showEventEditor = false
                editingEvent = null
            },
            onSave = { ev ->
                onSaveEvent(ev)
                showEventEditor = false
                editingEvent = null
            },
            onDelete = editingEvent?.let { ev ->
                {
                    onDeleteEvent(ev.id)
                    showEventEditor = false
                    editingEvent = null
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarEventRowCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = categoryColor(event.category)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("calendar_event_item_${event.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = event.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Text(
                        text = if (event.isLunar) "Âm lịch ${event.day}/${event.month}" else "Dương lịch ${event.day}/${event.month}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.isRecurringYearly) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Hằng năm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (event.timeLabel.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = event.timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (event.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("calendar_event_delete_${event.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Xóa sự kiện",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CalendarEventEditorSheet(
    initialEvent: CalendarEvent?,
    solarDate: VnDate,
    lunarDate: LunarDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var note by remember(initialEvent) { mutableStateOf(initialEvent?.note.orEmpty()) }
    var timeLabel by remember(initialEvent) { mutableStateOf(initialEvent?.timeLabel.orEmpty()) }
    var category by remember(initialEvent) {
        mutableStateOf(initialEvent?.category ?: CalendarEventCategory.MEETING)
    }
    var isLunar by remember(initialEvent) { mutableStateOf(initialEvent?.isLunar ?: false) }
    var isRecurringYearly by remember(initialEvent) {
        mutableStateOf(initialEvent?.isRecurringYearly ?: false)
    }

    val templates = listOf(
        "Họp chuyên môn" to CalendarEventCategory.MEETING,
        "Coi thi" to CalendarEventCategory.EXAM,
        "Sinh hoạt tổ" to CalendarEventCategory.MEETING,
        "Kỷ niệm gia đình" to CalendarEventCategory.ANNIVERSARY
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (initialEvent == null) "Thêm sự kiện / Lịch công tác" else "Chỉnh sửa sự kiện",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dương ${solarDate.formatDayMonth()}/${solarDate.year} • Âm ${lunarDate.day}/${lunarDate.month}/${lunarDate.year}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                templates.forEach { (label, cat) ->
                    AssistChip(
                        onClick = {
                            title = label
                            category = cat
                            if (cat == CalendarEventCategory.ANNIVERSARY) {
                                isRecurringYearly = true
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Tên sự kiện *") },
                placeholder = { Text("Ví dụ: Họp chuyên môn, Coi thi giữa kỳ...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calendar_event_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Loại sự kiện:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalendarEventCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = {
                            category = cat
                            if (cat == CalendarEventCategory.ANNIVERSARY && initialEvent == null) {
                                isRecurringYearly = true
                            }
                        },
                        label = { Text(cat.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tính theo lịch:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            SegmentedOptionBar(
                options = listOf(
                    SegmentOption(false, "Dương (${solarDate.day}/${solarDate.month})", "event_type_solar"),
                    SegmentOption(true, "Âm (${lunarDate.day}/${lunarDate.month})", "event_type_lunar")
                ),
                selected = isLunar,
                onSelect = { isLunar = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lặp lại hằng năm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isLunar) "Tự nhắc đúng ngày ${lunarDate.day}/${lunarDate.month} Âm lịch mỗi năm"
                        else "Tự nhắc đúng ngày ${solarDate.day}/${solarDate.month} Dương lịch mỗi năm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isRecurringYearly,
                    onCheckedChange = { isRecurringYearly = it },
                    modifier = Modifier.testTag("calendar_event_recurring_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it },
                    label = { Text("Giờ (tùy chọn)") },
                    placeholder = { Text("14:00") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(0.9f)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú / Địa điểm") },
                    placeholder = { Text("Phòng HĐSP...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.4f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa")
                    }
                }
                Button(
                    onClick = {
                        val targetYear = if (initialEvent != null && initialEvent.isLunar == isLunar) {
                            initialEvent.year
                        } else if (isLunar) {
                            lunarDate.year
                        } else {
                            solarDate.year
                        }
                        val targetMonth = if (initialEvent != null && initialEvent.isLunar == isLunar) {
                            initialEvent.month
                        } else if (isLunar) {
                            lunarDate.month
                        } else {
                            solarDate.month
                        }
                        val targetDay = if (initialEvent != null && initialEvent.isLunar == isLunar) {
                            initialEvent.day
                        } else if (isLunar) {
                            lunarDate.day
                        } else {
                            solarDate.day
                        }
                        onSave(
                            CalendarEvent(
                                id = initialEvent?.id ?: 0L,
                                title = title.trim(),
                                note = note.trim(),
                                category = category,
                                isLunar = isLunar,
                                year = targetYear,
                                month = targetMonth,
                                day = targetDay,
                                isRecurringYearly = isRecurringYearly,
                                timeLabel = timeLabel.trim(),
                                createdAtMillis = initialEvent?.createdAtMillis ?: System.currentTimeMillis()
                            )
                        )
                    },
                    enabled = title.trim().isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calendar_event_save_button")
                ) {
                    Text(if (initialEvent == null) "Lưu sự kiện" else "Cập nhật")
                }
            }
        }
    }
}

@Composable
private fun categoryColor(category: CalendarEventCategory): Color {
    return when (category) {
        CalendarEventCategory.MEETING -> MaterialTheme.colorScheme.primary
        CalendarEventCategory.EXAM -> SundayCoral
        CalendarEventCategory.ANNIVERSARY -> SunsetOrange
        CalendarEventCategory.OTHER -> EmeraldLive
    }
}

@Composable
private fun CanChiColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun buildMonthGrid(year: Int, month: Int): List<VnDate?> {
    val firstDay = VnDate(year, month, 1)
    val leadingBlanks = firstDay.isoDayOfWeek - 1 // Monday = 0 blanks, Sunday = 6 blanks
    val daysCount = firstDay.daysInMonth()
    val result = mutableListOf<VnDate?>()
    repeat(leadingBlanks) { result.add(null) }
    for (d in 1..daysCount) {
        result.add(VnDate(year, month, d))
    }
    while (result.size % 7 != 0) {
        result.add(null)
    }
    return result
}
