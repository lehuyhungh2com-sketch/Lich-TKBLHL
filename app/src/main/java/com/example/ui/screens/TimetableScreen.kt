package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Room
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ViewAgenda
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.lunar.VnDate
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimeConfig
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.components.HomeroomModeToggle
import com.example.ui.components.LessonNoteEditorSheet
import com.example.ui.components.PeriodRowCard
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import com.example.ui.components.StatusTagPill
import com.example.ui.components.TimetableExportSheet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalCompactDensity
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SunsetOrange
import com.example.util.TimetableShareScope
import kotlinx.coroutines.launch

private enum class TimetableViewMode(
    val key: String,
    val label: String,
    val testTag: String,
    val icon: ImageVector
) {
    SINGLE_DAY("day", "1 Ngày", "timetable_mode_day", Icons.Rounded.CalendarToday),
    WEEK_CARDS("cards", "Thẻ tuần", "timetable_mode_cards", Icons.Rounded.ViewAgenda),
    MATRIX("matrix", "Ma trận", "timetable_mode_matrix", Icons.Rounded.GridOn)
}

private data class DayBannerPalette(
    val bannerBg: Color,
    val bannerText: Color,
    val bannerSubText: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val borderColor: Color
)

@Composable
private fun rememberDayBannerPalette(
    dayCode: String,
    isToday: Boolean,
    isSelected: Boolean
): DayBannerPalette {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    if (isToday) {
        return DayBannerPalette(
            bannerBg = MaterialTheme.colorScheme.primary,
            bannerText = MaterialTheme.colorScheme.onPrimary,
            bannerSubText = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
            badgeBg = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
            badgeText = MaterialTheme.colorScheme.onPrimary,
            borderColor = MaterialTheme.colorScheme.primary
        )
    }

    val (accent, lightBg, darkBg) = when (dayCode) {
        "T2" -> Triple(Color(0xFF2563EB), Color(0xFFEFF6FF), Color(0xFF172554))
        "T3" -> Triple(Color(0xFF0D9488), Color(0xFFF0FDFA), Color(0xFF134E4A))
        "T4" -> Triple(Color(0xFFD97706), Color(0xFFFFFBEB), Color(0xFF451A03))
        "T5" -> Triple(Color(0xFF4F46E5), Color(0xFFEEF2FF), Color(0xFF1E1B4B))
        "T6" -> Triple(Color(0xFF0284C7), Color(0xFFF0F9FF), Color(0xFF0C4A6E))
        else -> Triple(Color(0xFFE11D48), Color(0xFFFFF1F2), Color(0xFF4C0519)) // T7
    }

    return DayBannerPalette(
        bannerBg = if (isDarkSurface) darkBg.copy(alpha = 0.72f) else lightBg,
        bannerText = if (isDarkSurface) Color.White else Color(0xFF0F172A),
        bannerSubText = if (isDarkSurface) Color(0xFFCBD5E1) else Color(0xFF475569),
        badgeBg = accent,
        badgeText = Color.White,
        borderColor = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun TimetableScreen(
    uiState: MainUiState,
    onSelectWeek: (Int) -> Unit,
    onToggleHomeroom: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSaveLessonNote: (ResolvedPeriod, String) -> Unit,
    onDeleteLessonNote: (ResolvedPeriod) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompact = LocalCompactDensity.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val defaultTodayCode = if (uiState.isSundayPreview || uiState.todayDate.isSunday) {
        "T2"
    } else {
        uiState.todayDate.shortDayCode.takeIf { it in TimetableCalculator.SCHOOL_DAYS } ?: "T2"
    }

    var viewModeKey by rememberSaveable { mutableStateOf(TimetableViewMode.WEEK_CARDS.key) }
    val viewMode = TimetableViewMode.entries.firstOrNull { it.key == viewModeKey } ?: TimetableViewMode.WEEK_CARDS
    var selectedDayCode by rememberSaveable { mutableStateOf(defaultTodayCode) }
    var selectedSessionFilter by rememberSaveable { mutableStateOf("all") } // "all" | "morning" | "afternoon"
    var showLookupSheet by rememberSaveable { mutableStateOf(false) }
    var lookupInitialTab by rememberSaveable { mutableStateOf(0) }
    var lookupInitialSwapSlotId by rememberSaveable { mutableStateOf<String?>(null) }
    var showWorkloadSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var editingPeriod by remember { mutableStateOf<ResolvedPeriod?>(null) }

    val (weekStart, weekEnd) = TimetableCalculator.getWeekDateRange(
        weekNumber = uiState.selectedWeekNumber,
        schoolConfig = uiState.schoolConfig,
        weeks = uiState.weeks
    )
    val isLockedWeek = uiState.selectedWeekInfo?.isLocked == true
    val isCurrentWeekActive = uiState.selectedWeekNumber == uiState.currentWeekNumber && !uiState.isSundayPreview
    val todayCodeInWeek = if (isCurrentWeekActive) {
        uiState.todayDate.shortDayCode.takeIf { it in TimetableCalculator.SCHOOL_DAYS }
    } else {
        null
    }

    val filteredPeriods = when (selectedSessionFilter) {
        "morning" -> uiState.selectedWeekPeriods.filter { !it.isAfternoon }
        "afternoon" -> uiState.selectedWeekPeriods.filter { it.isAfternoon }
        else -> uiState.selectedWeekPeriods
    }

    val periodsByDay = TimetableCalculator.SCHOOL_DAYS.associateWith { day ->
        filteredPeriods.filter { it.day == day }
    }

    val showTapHint = filteredPeriods.isNotEmpty()
    // Fixed items above day cards in LazyColumn:
    // 0: Header, 1: Week Selector, 2: Lookup/Share, [3: Homeroom if canToggleHomeroom], Session Filter, View Mode Bar, [Quick Day Strip], [Hint]
    val dayCardsStartIndex = buildList {
        add("header")
        add("week")
        add("actions")
        if (uiState.canToggleHomeroom) add("homeroom")
        add("session_filter")
        add("view_mode")
        if (viewMode != TimetableViewMode.MATRIX) add("day_strip")
        if (showTapHint) add("hint")
    }.size

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (isCompact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 9.dp else 12.dp)
        ) {
            // 0. Header + Quick Matrix Toggle + Sync
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                viewModeKey = if (viewMode == TimetableViewMode.MATRIX) {
                                    TimetableViewMode.WEEK_CARDS.key
                                } else {
                                    TimetableViewMode.MATRIX.key
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("timetable_toggle_matrix")
                        ) {
                            Icon(
                                imageVector = if (viewMode == TimetableViewMode.MATRIX) {
                                    Icons.Rounded.ViewAgenda
                                } else {
                                    Icons.Rounded.GridOn
                                },
                                contentDescription = if (viewMode == TimetableViewMode.MATRIX) {
                                    "Xem dạng thẻ"
                                } else {
                                    "Xem bảng ma trận"
                                },
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

            // 1. Week Selector Card (supports horizontal swipe left/right to switch week)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(uiState.selectedWeekNumber) {
                            var dragSum = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragSum = 0f },
                                onHorizontalDrag = { _, dragAmount -> dragSum += dragAmount },
                                onDragEnd = {
                                    if (dragSum <= -70f && uiState.selectedWeekNumber < TimetableCalculator.MAX_WEEK) {
                                        onSelectWeek(uiState.selectedWeekNumber + 1)
                                    } else if (dragSum >= 70f && uiState.selectedWeekNumber > TimetableCalculator.MIN_WEEK) {
                                        onSelectWeek(uiState.selectedWeekNumber - 1)
                                    }
                                    dragSum = 0f
                                }
                            )
                        }
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
                                    if (uiState.selectedWeekNumber == uiState.currentWeekNumber) " • Tuần hiện tại" else " • Chạm về tuần này",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

            // 2. Quick Teacher Utilities Bar: Lookup, Share, Định mức, Tìm người đổi tiết
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    lookupInitialTab = 0
                                    lookupInitialSwapSlotId = null
                                    showLookupSheet = true
                                }
                                .testTag("timetable_lookup_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ManageSearch,
                                    contentDescription = "Tra cứu lớp và giáo viên",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tra cứu Lớp / GV",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (uiState.selectedWeekPeriods.isEmpty()) {
                                        onNotify("Tuần ${uiState.selectedWeekNumber} chưa có tiết nào để xuất ảnh.")
                                    } else {
                                        showExportSheet = true
                                    }
                                }
                                .testTag("timetable_share_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Chia sẻ ảnh TKB",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Chia sẻ ảnh TKB",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showWorkloadSheet = true }
                                .testTag("timetable_workload_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Assessment,
                                    contentDescription = "Xem định mức và thống kê số tiết",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Định mức (${uiState.workloadStats.weekTotalCount} tiết)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    lookupInitialTab = 1
                                    lookupInitialSwapSlotId = null
                                    showLookupSheet = true
                                }
                                .testTag("timetable_swap_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = "Tìm người đổi tiết hoặc dạy thay",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tìm người đổi tiết",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            // 3. Homeroom Toggle (if GVCN)
            if (uiState.canToggleHomeroom) {
                item {
                    HomeroomModeToggle(
                        homeroomClassName = uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty(),
                        showHomeroom = uiState.effectiveShowHomeroom,
                        onSelect = onToggleHomeroom
                    )
                }
            }

            // 4. Session Filter Segmented Bar (All / Morning / Afternoon)
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

            // 5. Option 2 View Mode Selector (1 Ngày | Thẻ tuần | Ma trận)
            item {
                ViewModeSelectorBar(
                    selectedMode = viewMode,
                    onSelectMode = { mode ->
                        viewModeKey = mode.key
                        if (mode == TimetableViewMode.SINGLE_DAY && selectedDayCode !in TimetableCalculator.SCHOOL_DAYS) {
                            selectedDayCode = defaultTodayCode
                        }
                    }
                )
            }

            // 6. Quick Day Selector Strip (T2..T7 with Nay & period indicators)
            if (viewMode != TimetableViewMode.MATRIX) {
                item {
                    QuickDayStripBar(
                        weekStartDate = weekStart,
                        selectedDayCode = selectedDayCode,
                        todayCode = todayCodeInWeek,
                        periodsByDay = periodsByDay,
                        onSelectDay = { dayCode ->
                            selectedDayCode = dayCode
                            if (viewMode == TimetableViewMode.WEEK_CARDS && filteredPeriods.isNotEmpty()) {
                                val dayIdx = TimetableCalculator.SCHOOL_DAYS.indexOf(dayCode)
                                if (dayIdx >= 0) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(dayCardsStartIndex + dayIdx)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // 7. Subtle hint about tap-to-note & changed periods alert
            if (showTapHint) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (uiState.changedPeriodCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SunsetOrange.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, SunsetOrange.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("changed_periods_alert_banner")
                            ) {
                                Text(
                                    text = "Cảnh báo thay đổi: Có ${uiState.changedPeriodCount} tiết khác ${uiState.changeComparisonLabel ?: "trước đó"} (được đánh dấu MỚI ĐỔI).",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        Text(
                            text = "Mẹo: Chạm vào tiết học để ghi chú hoặc tìm người đổi tiết • Chạm T2–T7 để chuyển nhanh.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // 8. Main Content: Single Day Card, Colorful Week Cards, or Matrix Grid
            when (viewMode) {
                TimetableViewMode.MATRIX -> {
                    item {
                        TimetableMatrixSection(
                            periods = uiState.selectedWeekPeriods,
                            showTeacher = uiState.effectiveShowHomeroom,
                            todayCode = todayCodeInWeek,
                            showMorning = selectedSessionFilter != "afternoon",
                            showAfternoon = selectedSessionFilter != "morning",
                            personalNoteLookup = { uiState.personalNoteFor(it) },
                            onPeriodClick = { editingPeriod = it }
                        )
                    }
                }

                TimetableViewMode.SINGLE_DAY -> {
                    item {
                        val dayIndex = TimetableCalculator.SCHOOL_DAYS.indexOf(selectedDayCode).coerceAtLeast(0)
                        val dayPeriods = periodsByDay[selectedDayCode].orEmpty()
                        val isToday = selectedDayCode == todayCodeInWeek
                        SingleDayScheduleCard(
                            dayCode = selectedDayCode,
                            dayIndex = dayIndex,
                            weekStartDate = weekStart,
                            timeConfig = uiState.schoolConfig.timeConfig,
                            isToday = isToday,
                            sessionFilter = selectedSessionFilter,
                            periods = dayPeriods,
                            showTeacherName = uiState.effectiveShowHomeroom,
                            personalNoteLookup = { uiState.personalNoteFor(it) },
                            onPeriodClick = { editingPeriod = it }
                        )
                    }
                }

                TimetableViewMode.WEEK_CARDS -> {
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
                        itemsIndexed(
                            items = TimetableCalculator.SCHOOL_DAYS,
                            key = { _, dayCode -> "day_card_$dayCode" }
                        ) { dayIndex, dayCode ->
                            val dayPeriods = periodsByDay[dayCode].orEmpty()
                            val isToday = dayCode == todayCodeInWeek
                            val isSelected = dayCode == selectedDayCode

                            DayScheduleCard(
                                dayCode = dayCode,
                                dayIndex = dayIndex,
                                weekStartDate = weekStart,
                                isToday = isToday,
                                isSelected = isSelected,
                                periods = dayPeriods,
                                showTeacherName = uiState.effectiveShowHomeroom,
                                personalNoteLookup = { uiState.personalNoteFor(it) },
                                onPeriodClick = { editingPeriod = it }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }

    if (showLookupSheet) {
        SchoolLookupSheet(
            uiState = uiState,
            onDismiss = { showLookupSheet = false },
            onNotify = onNotify,
            initialTab = lookupInitialTab,
            initialSwapSlotId = lookupInitialSwapSlotId
        )
    }

    if (showWorkloadSheet) {
        val modeSubtitle = if (uiState.effectiveShowHomeroom) {
            val clsName = TimetableCalculator.compactClassName(
                uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty()
            )
            "Lớp chủ nhiệm $clsName"
        } else {
            uiState.currentTeacher?.name ?: "Giáo viên"
        }
        WorkloadStatsSheet(
            stats = uiState.workloadStats,
            modeSubtitle = modeSubtitle,
            onDismiss = { showWorkloadSheet = false }
        )
    }

    if (showExportSheet) {
        val shareTitle = if (uiState.effectiveShowHomeroom) {
            val clsName = TimetableCalculator.compactClassName(
                uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty()
            )
            "TKB Lớp $clsName"
        } else {
            "TKB ${uiState.currentTeacher?.name ?: "Giáo viên"}"
        }
        val initialScope = when (selectedSessionFilter) {
            "morning" -> TimetableShareScope.MORNING
            "afternoon" -> TimetableShareScope.AFTERNOON
            else -> TimetableShareScope.FULL_DAY
        }
        TimetableExportSheet(
            title = shareTitle,
            subtitle = uiState.schoolConfig.name,
            weekLabel = "Tuần ${uiState.selectedWeekNumber}",
            dateRange = "${weekStart.formatDayMonth()} – ${weekEnd.formatDayMonth()}/${weekEnd.year}",
            periods = uiState.selectedWeekPeriods,
            showTeacher = uiState.effectiveShowHomeroom,
            initialScope = initialScope,
            onDismiss = { showExportSheet = false },
            onNotify = onNotify
        )
    }

    val targetPeriod = editingPeriod
    if (targetPeriod != null) {
        LessonNoteEditorSheet(
            period = targetPeriod,
            existingNote = uiState.personalNoteFor(targetPeriod).orEmpty(),
            onDismiss = { editingPeriod = null },
            onSave = { note -> onSaveLessonNote(targetPeriod, note) },
            onDelete = { onDeleteLessonNote(targetPeriod) },
            onFindSubstitute = if (!uiState.effectiveShowHomeroom) {
                {
                    lookupInitialTab = 1
                    lookupInitialSwapSlotId = targetPeriod.slotId
                    showLookupSheet = true
                }
            } else null
        )
    }
}

@Composable
private fun ViewModeSelectorBar(
    selectedMode: TimetableViewMode,
    onSelectMode: (TimetableViewMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TimetableViewMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { onSelectMode(mode) }
                        .testTag(mode.testTag)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickDayStripBar(
    weekStartDate: VnDate,
    selectedDayCode: String,
    todayCode: String?,
    periodsByDay: Map<String, List<ResolvedPeriod>>,
    onSelectDay: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TimetableCalculator.SCHOOL_DAYS.forEachIndexed { idx, dayCode ->
                val isToday = dayCode == todayCode
                val isSelected = dayCode == selectedDayCode
                val count = periodsByDay[dayCode]?.size ?: 0
                val date = weekStartDate.plusDays(idx)

                val bg = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
                val titleColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val subColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { onSelectDay(dayCode) }
                        .testTag("quick_day_$dayCode")
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = dayCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1
                    )
                    Text(
                        text = if (isToday) "Nay" else "${date.day}/${date.month}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = subColor,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    count == 0 -> Color.Transparent
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> EmeraldLive
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun DayScheduleCard(
    dayCode: String,
    dayIndex: Int,
    weekStartDate: VnDate,
    isToday: Boolean,
    isSelected: Boolean,
    periods: List<ResolvedPeriod>,
    showTeacherName: Boolean,
    personalNoteLookup: (ResolvedPeriod) -> String?,
    onPeriodClick: (ResolvedPeriod) -> Unit
) {
    val date = weekStartDate.plusDays(dayIndex)
    val morning = periods.filter { !it.isAfternoon }
    val afternoon = periods.filter { it.isAfternoon }
    val isCompact = LocalCompactDensity.current
    val palette = rememberDayBannerPalette(dayCode = dayCode, isToday = isToday, isSelected = isSelected)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isToday || isSelected) 1.75.dp else 1.dp,
            color = palette.borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_schedule_card_$dayCode")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Distinct Colored Header Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bannerBg)
                    .padding(horizontal = 14.dp, vertical = if (isCompact) 9.dp else 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.badgeBg
                ) {
                    Text(
                        text = dayCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.badgeText,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${date.vietnameseWeekday}, ${date.formatDayMonth()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.bannerText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isToday) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = EmeraldLive
                            ) {
                                Text(
                                    text = "HÔM NAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (periods.isNotEmpty()) {
                        val sessionParts = buildList {
                            if (morning.isNotEmpty()) add("Sáng ${morning.size}t")
                            if (afternoon.isNotEmpty()) add("Chiều ${afternoon.size}t")
                        }.joinToString(" · ")
                        Text(
                            text = sessionParts,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.bannerSubText,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    } else if (periods.isEmpty()) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        palette.badgeBg.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = if (periods.isEmpty()) "Nghỉ" else "${periods.size} tiết",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            periods.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> palette.badgeBg
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            if (periods.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Column(modifier = Modifier.padding(if (isCompact) 11.dp else 13.dp)) {
                    if (morning.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.WbSunny,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Buổi Sáng",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 6.dp)) {
                            morning.forEach { p ->
                                PeriodRowCard(
                                    period = p,
                                    showTeacherName = showTeacherName,
                                    personalNote = personalNoteLookup(p),
                                    onClick = { onPeriodClick(p) }
                                )
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
                                fontWeight = FontWeight.Bold,
                                color = SkyBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 6.dp)) {
                            afternoon.forEach { p ->
                                PeriodRowCard(
                                    period = p,
                                    showTeacherName = showTeacherName,
                                    personalNote = personalNoteLookup(p),
                                    onClick = { onPeriodClick(p) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleDayScheduleCard(
    dayCode: String,
    dayIndex: Int,
    weekStartDate: VnDate,
    timeConfig: TimeConfig,
    isToday: Boolean,
    sessionFilter: String,
    periods: List<ResolvedPeriod>,
    showTeacherName: Boolean,
    personalNoteLookup: (ResolvedPeriod) -> String?,
    onPeriodClick: (ResolvedPeriod) -> Unit
) {
    val date = weekStartDate.plusDays(dayIndex)
    val morning = periods.filter { !it.isAfternoon }
    val afternoon = periods.filter { it.isAfternoon }
    val palette = rememberDayBannerPalette(dayCode = dayCode, isToday = isToday, isSelected = true)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isToday) 2.dp else 1.5.dp,
            color = palette.borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("single_day_card_$dayCode")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bold Header Banner matching Web Option 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bannerBg)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${date.vietnameseWeekday} (${date.formatDayMonth()})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.bannerText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isToday) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = EmeraldLive
                            ) {
                                Text(
                                    text = "HÔM NAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                    } else {
                        palette.badgeBg.copy(alpha = 0.16f)
                    }
                ) {
                    val countText = when (sessionFilter) {
                        "morning" -> "Sáng ${morning.size}t"
                        "afternoon" -> "Chiều ${afternoon.size}t"
                        else -> if (afternoon.isNotEmpty()) "S${morning.size} · C${afternoon.size}" else "Sáng ${morning.size}t"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else palette.badgeBg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Morning Slots 1..5
            if (sessionFilter != "afternoon") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (slotNum in 1..5) {
                        val match = morning.firstOrNull { it.period == slotNum }
                        val (slotStart, _) = TimetableCalculator.calculatePeriodTimeRange(
                            day = dayCode,
                            period = slotNum,
                            session = "morning",
                            config = timeConfig
                        )
                        SingleDaySlotRow(
                            slotLabel = "Tiết $slotNum",
                            timeText = slotStart.formatHHmm(),
                            period = match,
                            showTeacherName = showTeacherName,
                            personalNote = match?.let(personalNoteLookup),
                            onClick = { if (match != null) onPeriodClick(match) }
                        )
                        if (slotNum < 5) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }

            // Afternoon Section / Footer
            if (sessionFilter != "morning") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (afternoon.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WbTwilight,
                                contentDescription = null,
                                tint = SunsetOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chiều: ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = SunsetOrange
                            )
                            Text(
                                text = "Không có lịch chiều",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Tháng ${date.month}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.WbTwilight,
                                contentDescription = null,
                                tint = SkyBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Buổi Chiều (HSG / CLB)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = SkyBlue
                            )
                        }
                        afternoon.forEach { p ->
                            PeriodRowCard(
                                period = p,
                                showTeacherName = showTeacherName,
                                personalNote = personalNoteLookup(p),
                                onClick = { onPeriodClick(p) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleDaySlotRow(
    slotLabel: String,
    timeText: String,
    period: ResolvedPeriod?,
    showTeacherName: Boolean,
    personalNote: String?,
    onClick: () -> Unit
) {
    val isNow = period?.liveStatus == PeriodLiveStatus.TEACHING_NOW
    val isNext = period?.liveStatus == PeriodLiveStatus.NEXT_PERIOD
    val isPast = period?.liveStatus == PeriodLiveStatus.PAST
    val isChanged = period?.isChanged == true

    val rowBg = when {
        isNow -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        isNext -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
        isChanged -> SunsetOrange.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val pillBg = when {
        isNow -> EmeraldLive
        isNext -> MaterialTheme.colorScheme.primary
        period != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val pillFg = when {
        isNow -> Color.White
        isNext -> MaterialTheme.colorScheme.onPrimary
        period != null -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .then(if (period != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = pillBg,
            modifier = Modifier.widthIn(min = 72.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = pillFg,
                    maxLines = 1
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = pillFg.copy(alpha = 0.88f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (period == null) {
            Text(
                text = "Trống tiết",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f)
            )
        } else {
            val shortClass = TimetableCalculator.compactClassName(period.className)
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (showTeacherName) period.subjectName else "$shortClass • ${period.subjectName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isNow) {
                        StatusTagPill(text = "ĐANG DẠY", bg = EmeraldLive, fg = Color.White)
                    } else if (isNext) {
                        StatusTagPill(text = "TIẾP THEO", bg = SunsetOrange, fg = Color.White)
                    }
                    if (isChanged) {
                        StatusTagPill(text = "MỚI ĐỔI", bg = SunsetOrange, fg = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = period.timeRangeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showTeacherName && period.teacherShortName.isNotBlank()) {
                        Text(
                            text = "GV: ${period.teacherShortName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
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
                                maxLines = 2
                            )
                        }
                    }
                }

                if (!personalNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = "Ghi chú riêng",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = personalNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            if (personalNote.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = "Thêm ghi chú",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                )
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
    showAfternoon: Boolean,
    personalNoteLookup: (ResolvedPeriod) -> String?,
    onPeriodClick: (ResolvedPeriod) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showMorning) {
            MatrixCard(
                title = "Bảng TKB Buổi Sáng",
                accentColor = MaterialTheme.colorScheme.primary,
                periods = periods.filter { !it.isAfternoon },
                showTeacher = showTeacher,
                todayCode = todayCode,
                personalNoteLookup = personalNoteLookup,
                onPeriodClick = onPeriodClick
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
                    todayCode = todayCode,
                    personalNoteLookup = personalNoteLookup,
                    onPeriodClick = onPeriodClick
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
    todayCode: String?,
    personalNoteLookup: (ResolvedPeriod) -> String?,
    onPeriodClick: (ResolvedPeriod) -> Unit
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
                                .width(78.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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
                            val isNow = match?.liveStatus == PeriodLiveStatus.TEACHING_NOW
                            val isNext = match?.liveStatus == PeriodLiveStatus.NEXT_PERIOD
                            val isChanged = match?.isChanged == true
                            val hasNote = match != null && !personalNoteLookup(match).isNullOrBlank()

                            val cellBg = when {
                                isNow -> EmeraldLive.copy(alpha = 0.22f)
                                isNext -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                isChanged -> SunsetOrange.copy(alpha = 0.16f)
                                match != null -> accentColor.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                            val cellBorder = when {
                                isNow -> BorderStroke(1.5.dp, EmeraldLive)
                                isNext -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
                                isChanged -> BorderStroke(1.5.dp, SunsetOrange)
                                else -> null
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cellBg,
                                border = cellBorder,
                                modifier = Modifier
                                    .width(78.dp)
                                    .padding(horizontal = 2.dp)
                                    .height(50.dp)
                                    .then(
                                        if (match != null) {
                                            Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onPeriodClick(match) }
                                        } else Modifier
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                ) {
                                    if (match != null) {
                                        if (isChanged) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(SunsetOrange)
                                            )
                                        }
                                        if (hasNote) {
                                            Text(
                                                text = "✎",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            )
                                        } else if (isNow) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldLive)
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = if (showTeacher) match.subjectName else TimetableCalculator.compactClassName(match.className),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isNow) EmeraldLive else MaterialTheme.colorScheme.onSurface,
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
}
