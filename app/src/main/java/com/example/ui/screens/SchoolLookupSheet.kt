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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolClass
import com.example.domain.model.Teacher
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.components.PeriodRowCard
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import com.example.util.TimetableShareHelper

/**
 * Offline school-wide lookup tool powered by already-synced Room data (zero extra Firestore reads):
 * - Tab 0: View any Class timetable for the selected week (+ share PNG to Zalo).
 * - Tab 1: Look up which teachers are Teaching vs Free at any Day / Session / Period.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolLookupSheet(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableIntStateOf(0) }

    val defaultClassId = uiState.homeroomClassId
        ?: uiState.allClasses.firstOrNull()?.id.orEmpty()
    var selectedClassId by remember(defaultClassId) { mutableStateOf(defaultClassId) }

    val activePeriodNow = (uiState.todayMorningPeriods + uiState.todayAfternoonPeriods)
        .firstOrNull { it.liveStatus == PeriodLiveStatus.TEACHING_NOW || it.liveStatus == PeriodLiveStatus.NEXT_PERIOD }

    var lookupDay by remember {
        mutableStateOf(
            if (uiState.todayDate.isSunday) "T2" else uiState.todayDate.shortDayCode
        )
    }
    var lookupSession by remember {
        mutableStateOf(
            activePeriodNow?.session ?: if (uiState.nowTime.hour >= 13) "afternoon" else "morning"
        )
    }
    var lookupPeriod by remember {
        mutableIntStateOf(activePeriodNow?.period ?: 1)
    }
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tra cứu TKB nhà trường",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tuần ${uiState.selectedWeekNumber} • Dùng dữ liệu offline đã tải",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Đóng tra cứu")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SegmentedOptionBar(
                options = listOf(
                    SegmentOption(0, "TKB theo Lớp", "lookup_tab_class"),
                    SegmentOption(1, "GV dạy / Trống tiết", "lookup_tab_teachers")
                ),
                selected = activeTab,
                onSelect = { activeTab = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTab == 0) {
                ClassTimetableLookupContent(
                    uiState = uiState,
                    selectedClassId = selectedClassId,
                    onSelectClass = { selectedClassId = it },
                    onShareClass = { cls, periods ->
                        val (ws, we) = TimetableCalculator.getWeekDateRange(
                            weekNumber = uiState.selectedWeekNumber,
                            schoolConfig = uiState.schoolConfig,
                            weeks = uiState.weeks
                        )
                        val ok = TimetableShareHelper.shareWeeklyTimetableImage(
                            context = context,
                            title = "TKB Lớp ${TimetableCalculator.compactClassName(cls.name)}",
                            subtitle = uiState.schoolConfig.name,
                            weekLabel = "Tuần ${uiState.selectedWeekNumber}",
                            dateRange = "${ws.formatDayMonth()} – ${we.formatDayMonth()}/${we.year}",
                            periods = periods,
                            showTeacher = true
                        )
                        if (!ok) onNotify("Không thể tạo ảnh chia sẻ lúc này.")
                    }
                )
            } else {
                TeacherAvailabilityLookupContent(
                    allTeachers = uiState.allTeachers,
                    weekPeriods = uiState.allSelectedWeekPeriods,
                    selectedDay = lookupDay,
                    onSelectDay = { lookupDay = it },
                    selectedSession = lookupSession,
                    onSelectSession = { lookupSession = it },
                    selectedPeriod = lookupPeriod,
                    onSelectPeriod = { lookupPeriod = it },
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it }
                )
            }
        }
    }
}

@Composable
private fun ClassTimetableLookupContent(
    uiState: MainUiState,
    selectedClassId: String,
    onSelectClass: (String) -> Unit,
    onShareClass: (SchoolClass, List<ResolvedPeriod>) -> Unit
) {
    val classes = uiState.allClasses
    val selectedClass = classes.firstOrNull { it.id == selectedClassId } ?: classes.firstOrNull()
    val classPeriods = remember(uiState.allSelectedWeekPeriods, selectedClass?.id) {
        if (selectedClass == null) emptyList()
        else uiState.allSelectedWeekPeriods.filter { it.classId.equals(selectedClass.id, ignoreCase = true) }
    }

    if (classes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Chưa có dữ liệu danh sách lớp. Vui lòng đồng bộ TKB trước.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Class horizontal selector chips
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        classes.forEach { cls ->
            val isSelected = cls.id == selectedClass?.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelectClass(cls.id) },
                label = { Text(TimetableCalculator.compactClassName(cls.name)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Class, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (selectedClass != null) {
        val homeroomTeacher = uiState.allTeachers.firstOrNull { it.id == selectedClass.homeroomTeacherId }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lớp ${TimetableCalculator.compactClassName(selectedClass.name)} • ${classPeriods.size} tiết",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (homeroomTeacher != null) {
                    Text(
                        text = "GVCN: ${homeroomTeacher.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { onShareClass(selectedClass, classPeriods) },
                modifier = Modifier.testTag("lookup_share_class_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Chia sẻ ảnh TKB lớp",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val byDay = TimetableCalculator.SCHOOL_DAYS.associateWith { d ->
        classPeriods.filter { it.day == d }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(TimetableCalculator.SCHOOL_DAYS) { dayCode ->
            val list = byDay[dayCode].orEmpty()
            if (list.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "$dayCode • ${list.size} tiết",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.forEach { p ->
                                PeriodRowCard(period = p, showTeacherName = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherAvailabilityLookupContent(
    allTeachers: List<Teacher>,
    weekPeriods: List<ResolvedPeriod>,
    selectedDay: String,
    onSelectDay: (String) -> Unit,
    selectedSession: String,
    onSelectSession: (String) -> Unit,
    selectedPeriod: Int,
    onSelectPeriod: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    // Day selector row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TimetableCalculator.SCHOOL_DAYS.forEach { d ->
            FilterChip(
                selected = d == selectedDay,
                onClick = { onSelectDay(d) },
                label = { Text(d) }
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Session + Period selectors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentedOptionBar(
            options = listOf(
                SegmentOption("morning", "Sáng"),
                SegmentOption("afternoon", "Chiều")
            ),
            selected = selectedSession,
            onSelect = onSelectSession,
            modifier = Modifier.width(136.dp)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (p in 1..5) {
                FilterChip(
                    selected = p == selectedPeriod,
                    onClick = { onSelectPeriod(p) },
                    label = { Text("T$p") }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Tìm tên giáo viên, tổ bộ môn...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    val matchingSlots = remember(weekPeriods, selectedDay, selectedSession, selectedPeriod) {
        weekPeriods.filter {
            it.day == selectedDay &&
                it.session == selectedSession &&
                it.period == selectedPeriod
        }
    }
    val busyByTeacherId = remember(matchingSlots) {
        matchingSlots.associateBy { it.teacherId }
    }

    val filteredTeachers = remember(allTeachers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allTeachers
        else allTeachers.filter {
            it.name.lowercase().contains(q) ||
                it.department.lowercase().contains(q) ||
                it.mainSubject.lowercase().contains(q)
        }
    }

    val busyTeachers = filteredTeachers.filter { busyByTeacherId.containsKey(it.id) }
    val freeTeachers = filteredTeachers.filter { !busyByTeacherId.containsKey(it.id) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Đang có tiết: ${busyTeachers.size} GV",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Trống tiết: ${freeTeachers.size} GV",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (busyTeachers.isNotEmpty()) {
            item {
                Text(
                    text = "Giáo viên đang lên lớp ($selectedDay · ${if (selectedSession == "afternoon") "Chiều" else "Sáng"} T$selectedPeriod)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(busyTeachers, key = { "busy_${it.id}" }) { teacher ->
                val period = busyByTeacherId[teacher.id]
                val cls = period?.let { TimetableCalculator.compactClassName(it.className) }.orEmpty()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = teacher.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = teacher.department.ifBlank { teacher.mainSubject },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (period != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "$cls · ${period.subjectName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (freeTeachers.isNotEmpty()) {
            item {
                Text(
                    text = "Giáo viên đang trống tiết",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(freeTeachers, key = { "free_${it.id}" }) { teacher ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = teacher.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = listOf(teacher.mainSubject, teacher.department)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Trống tiết",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}
