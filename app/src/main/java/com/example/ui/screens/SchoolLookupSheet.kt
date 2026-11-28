package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
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
import com.example.domain.model.Assignment
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolClass
import com.example.domain.model.SubstituteSuggestion
import com.example.domain.model.Teacher
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.components.PeriodRowCard
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import com.example.ui.components.StatusTagPill
import com.example.ui.components.TimetableExportSheet
import com.example.ui.theme.EmeraldLive

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
    onNotify: (String) -> Unit,
    initialTab: Int = 0,
    initialSwapSlotId: String? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

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
    var swapSearchQuery by remember { mutableStateOf("") }
    var selectedSwapSlotId by remember(initialSwapSlotId) { mutableStateOf(initialSwapSlotId) }
    var pendingExportClass by remember { mutableStateOf<Pair<SchoolClass, List<ResolvedPeriod>>?>(null) }
    var pendingExportWholeSchool by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tra cứu & Đổi tiết",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tuần ${uiState.selectedWeekNumber} • Dùng dữ liệu offline đã tải",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Đóng tra cứu")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            SegmentedOptionBar(
                options = listOf(
                    SegmentOption(0, "TKB Lớp", "lookup_tab_class"),
                    SegmentOption(1, "Đổi tiết / Dạy thay", "lookup_tab_swap"),
                    SegmentOption(2, "GV trống tiết", "lookup_tab_teachers")
                ),
                selected = activeTab,
                onSelect = { activeTab = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (activeTab) {
                0 -> ClassTimetableLookupContent(
                    uiState = uiState,
                    selectedClassId = selectedClassId,
                    onSelectClass = { selectedClassId = it },
                    onShareClass = { cls, periods ->
                        val hasCycleAfternoon = uiState.allSelectedCycleAfternoonPeriods.any {
                            it.classId.equals(cls.id, ignoreCase = true)
                        }
                        if (periods.isEmpty() && !hasCycleAfternoon) {
                            onNotify("Lớp ${TimetableCalculator.compactClassName(cls.name)} chưa có tiết nào trong tuần này.")
                        } else {
                            pendingExportClass = cls to periods
                        }
                    },
                    onShareWholeSchool = {
                        val hasSchoolData = uiState.allSelectedWeekClassPeriods.isNotEmpty() ||
                            uiState.allSelectedWeekPeriods.isNotEmpty() ||
                            uiState.allSelectedCycleAfternoonPeriods.isNotEmpty()
                        if (!hasSchoolData) {
                            onNotify("Chưa có dữ liệu TKB toàn trường để xuất ảnh. Vui lòng đồng bộ trước.")
                        } else {
                            pendingExportWholeSchool = true
                        }
                    }
                )

                1 -> SwapSubstituteLookupContent(
                    uiState = uiState,
                    selectedSlotId = selectedSwapSlotId,
                    onSelectSlot = { selectedSwapSlotId = it },
                    searchQuery = swapSearchQuery,
                    onSearchChange = { swapSearchQuery = it }
                )

                else -> TeacherAvailabilityLookupContent(
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

    pendingExportClass?.let { (cls, periods) ->
        val (ws, we) = TimetableCalculator.getWeekDateRange(
            weekNumber = uiState.selectedWeekNumber,
            schoolConfig = uiState.schoolConfig,
            weeks = uiState.weeks
        )
        val classCycleAfternoon = uiState.allSelectedCycleAfternoonPeriods.filter {
            it.classId.equals(cls.id, ignoreCase = true)
        }
        TimetableExportSheet(
            title = "TKB Lớp ${TimetableCalculator.compactClassName(cls.name)}",
            subtitle = uiState.schoolConfig.name,
            weekLabel = "Tuần ${uiState.selectedWeekNumber}",
            dateRange = "${ws.formatDayMonth()} – ${we.formatDayMonth()}/${we.year}",
            periods = periods,
            showTeacher = true,
            selectedWeekNumber = uiState.selectedWeekNumber,
            schoolConfig = uiState.schoolConfig,
            weeks = uiState.weeks,
            afternoonCyclePeriods = classCycleAfternoon,
            schoolClasses = uiState.allClasses,
            allSchoolClassPeriods = uiState.allSelectedWeekClassPeriods.ifEmpty { uiState.allSelectedWeekPeriods },
            allSchoolCycleAfternoonPeriods = uiState.allSelectedCycleAfternoonPeriods,
            initialWholeSchool = false,
            onDismiss = { pendingExportClass = null },
            onNotify = onNotify
        )
    }

    if (pendingExportWholeSchool) {
        val (ws, we) = TimetableCalculator.getWeekDateRange(
            weekNumber = uiState.selectedWeekNumber,
            schoolConfig = uiState.schoolConfig,
            weeks = uiState.weeks
        )
        TimetableExportSheet(
            title = "TKB Toàn trường",
            subtitle = uiState.schoolConfig.name,
            weekLabel = "Tuần ${uiState.selectedWeekNumber}",
            dateRange = "${ws.formatDayMonth()} – ${we.formatDayMonth()}/${we.year}",
            periods = uiState.selectedWeekPeriods,
            showTeacher = true,
            selectedWeekNumber = uiState.selectedWeekNumber,
            schoolConfig = uiState.schoolConfig,
            weeks = uiState.weeks,
            afternoonCyclePeriods = uiState.selectedCycleAfternoonPeriods,
            schoolClasses = uiState.allClasses,
            allSchoolClassPeriods = uiState.allSelectedWeekClassPeriods.ifEmpty { uiState.allSelectedWeekPeriods },
            allSchoolCycleAfternoonPeriods = uiState.allSelectedCycleAfternoonPeriods,
            initialWholeSchool = true,
            onDismiss = { pendingExportWholeSchool = false },
            onNotify = onNotify
        )
    }
}

@Composable
private fun ClassTimetableLookupContent(
    uiState: MainUiState,
    selectedClassId: String,
    onSelectClass: (String) -> Unit,
    onShareClass: (SchoolClass, List<ResolvedPeriod>) -> Unit,
    onShareWholeSchool: () -> Unit
) {
    val classes = uiState.allClasses
    val selectedClass = classes.firstOrNull { it.id == selectedClassId } ?: classes.firstOrNull()
    val sourceClassPeriods = uiState.allSelectedWeekClassPeriods.ifEmpty { uiState.allSelectedWeekPeriods }
    val classPeriods = remember(sourceClassPeriods, selectedClass?.id) {
        if (selectedClass == null) emptyList()
        else sourceClassPeriods.filter { it.classId.equals(selectedClass.id, ignoreCase = true) }
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onShareWholeSchool() }
                        .testTag("lookup_share_school_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Xuất ảnh TKB Toàn trường",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Toàn trường",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SwapSubstituteLookupContent(
    uiState: MainUiState,
    selectedSlotId: String?,
    onSelectSlot: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val myPeriods = remember(uiState.allSelectedWeekPeriods, uiState.selectedWeekPeriods, uiState.currentTeacher?.id) {
        val teacherId = uiState.currentTeacher?.id
        val own = if (teacherId != null) {
            uiState.allSelectedWeekPeriods.filter { it.teacherId == teacherId }
        } else {
            emptyList()
        }
        own.ifEmpty { uiState.selectedWeekPeriods }
    }

    if (myPeriods.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Bạn chưa có tiết dạy nào trong Tuần ${uiState.selectedWeekNumber} để tìm người đổi tiết.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val selectedPeriod = myPeriods.firstOrNull { it.slotId == selectedSlotId } ?: myPeriods.first()
    val targetCls = TimetableCalculator.compactClassName(selectedPeriod.className)
    val targetSessionShort = if (selectedPeriod.isAfternoon) "Chiều" else "Sáng T${selectedPeriod.period}"

    val suggestions = remember(
        selectedPeriod,
        uiState.currentTeacher,
        uiState.allTeachers,
        uiState.allSelectedWeekPeriods,
        uiState.allAssignments,
        searchQuery
    ) {
        val raw = TimetableCalculator.suggestSubstituteTeachers(
            targetPeriod = selectedPeriod,
            requesterTeacher = uiState.currentTeacher,
            allTeachers = uiState.allTeachers,
            allWeekPeriods = uiState.allSelectedWeekPeriods,
            assignments = uiState.allAssignments
        )
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) raw
        else raw.filter {
            it.teacher.name.lowercase().contains(q) ||
                it.teacher.department.lowercase().contains(q) ||
                it.teacher.mainSubject.lowercase().contains(q)
        }
    }

    val priorityCount = suggestions.count { it.priorityScore > 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("swap_unified_list"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "swap_period_selector") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "1. Chọn tiết của bạn cần đổi hoặc nhờ dạy thay:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    myPeriods.forEach { p ->
                        val isSelected = p.slotId == selectedPeriod.slotId
                        val clsShort = TimetableCalculator.compactClassName(p.className)
                        val chipLabel = if (p.isAfternoon) {
                            "${p.day} Chiều · $clsShort"
                        } else {
                            "${p.day} S.T${p.period} · $clsShort"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSlot(p.slotId) },
                            label = { Text(chipLabel) },
                            modifier = Modifier.testTag("swap_period_chip_${p.slotId}")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("swap_selected_summary")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⇄ Thay: ${selectedPeriod.day} $targetSessionShort · $targetCls (${selectedPeriod.subjectName} · ${selectedPeriod.timeRangeText})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        stickyHeader(key = "swap_search_sticky") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = "Lọc tên giáo viên hoặc tổ chuyên môn...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "2. Gợi ý ${suggestions.size} giáo viên trống tiết ($priorityCount ưu tiên cùng lớp/tổ):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(suggestions, key = { "swap_${it.teacher.id}" }) { item ->
            SubstituteSuggestionCard(
                suggestion = item,
                targetClassShort = targetCls
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubstituteSuggestionCard(
    suggestion: SubstituteSuggestion,
    targetClassShort: String
) {
    val isPriority = suggestion.priorityScore > 0
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isPriority) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (suggestion.teachesSameClass) 1.5.dp else 1.dp,
            color = if (suggestion.teachesSameClass) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("swap_suggestion_${suggestion.teacher.id}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.teacher.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(suggestion.teacher.mainSubject, suggestion.teacher.department)
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

            if (isPriority) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (suggestion.swapSlotHint != null) {
                        StatusTagPill(
                            text = suggestion.swapSlotHint,
                            bg = EmeraldLive,
                            fg = Color.White
                        )
                    }
                    if (suggestion.teachesSameClass) {
                        StatusTagPill(
                            text = "Cùng dạy $targetClassShort",
                            bg = MaterialTheme.colorScheme.primary,
                            fg = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    if (suggestion.sameDepartment) {
                        StatusTagPill(
                            text = "Cùng tổ",
                            bg = MaterialTheme.colorScheme.secondary,
                            fg = MaterialTheme.colorScheme.onSecondary
                        )
                    } else if (suggestion.sameSubject) {
                        StatusTagPill(
                            text = "Cùng môn",
                            bg = MaterialTheme.colorScheme.tertiary,
                            fg = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
        }
    }
}
