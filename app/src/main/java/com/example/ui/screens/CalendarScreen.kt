package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.VnDate
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.theme.SundayCoral
import com.example.ui.theme.SunsetOrange
import com.example.ui.theme.SunsetOrangeBright

@Composable
fun CalendarScreen(
    uiState: MainUiState,
    onJumpToWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = uiState.todayDate
    var displayYear by rememberSaveable { mutableIntStateOf(today.year) }
    var displayMonth by rememberSaveable { mutableIntStateOf(today.month) }
    var selectedYear by rememberSaveable { mutableIntStateOf(today.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(today.month) }
    var selectedDay by rememberSaveable { mutableIntStateOf(today.day) }

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

                                        val bg = when {
                                            isToday -> SunsetOrangeBright
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                        val solarColor = when {
                                            isToday -> Color.White
                                            cell.isSunday -> SundayCoral
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
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(bg)
                                                .clickable {
                                                    selectedYear = cell.year
                                                    selectedMonth = cell.month
                                                    selectedDay = cell.day
                                                }
                                                .padding(vertical = 6.dp)
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
                                color = SunsetOrange.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = selectedHoliday,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SunsetOrange,
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
        }
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
