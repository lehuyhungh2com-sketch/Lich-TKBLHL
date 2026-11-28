package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.local.AppAccentPalette
import com.example.data.local.AppDensityMode
import com.example.data.local.AppFontScale
import com.example.data.local.UserPreferencesState
import com.example.domain.model.Teacher
import com.example.ui.MainUiState
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.theme.LhlTimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class UpgradeFeaturesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleUiState(): MainUiState {
        return MainUiState(
            isInitialized = true,
            currentTeacher = Teacher(
                id = "t1",
                name = "Nguyễn Thị Hoa",
                shortName = "Hoa (Nhạc)",
                email = "hoa.gvdn@gmail.com",
                department = "Tổ KHXH",
                mainSubject = "Âm nhạc",
                homeroomClass = "8A1",
                accountRole = "teacher"
            ),
            preferences = UserPreferencesState(
                loggedInTeacherId = "t1",
                loggedInEmail = "hoa.gvdn@gmail.com",
                periodReminderEnabled = true,
                periodReminderLeadMinutes = 10
            )
        )
    }

    @Test
    fun settingsScreen_rendersVersion2AndAllPersonalizationControls() {
        var selectedPalette: AppAccentPalette? = null
        var selectedFontScale: AppFontScale? = null
        var selectedDensity: AppDensityMode? = null
        var selectedLeadMinutes: Int? = null

        composeRule.setContent {
            LhlTimetableTheme {
                SettingsScreen(
                    uiState = sampleUiState(),
                    onSyncNow = {},
                    onUpdateAppTheme = {},
                    onUpdateAccentPalette = { selectedPalette = it },
                    onUpdateFontScale = { selectedFontScale = it },
                    onUpdateDensityMode = { selectedDensity = it },
                    onUpdateDailyReminder = { _, _, _ -> },
                    onTogglePeriodReminder = {},
                    onUpdatePeriodLeadMinutes = { selectedLeadMinutes = it },
                    onSendTestNotification = {},
                    onToggleGpsLocation = {},
                    onUpdateWidgetAppearance = { _, _, _, _, _, _ -> },
                    onRefreshWidgetsNow = {},
                    onLogout = {}
                )
            }
        }

        // Verify current version badge is visible at the top, role title is omitted, and update check button exists
        composeRule.onNodeWithTag("settings_version_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Phiên bản ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_check_update_button").assertIsDisplayed()
        composeRule.onNodeWithText("Giáo viên").assertDoesNotExist()
        composeRule.onNodeWithText("Ban Giám hiệu").assertDoesNotExist()

        // Scroll to & click Accent Palette swatch
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("accent_palette_emerald"))
        composeRule.onNodeWithTag("accent_palette_emerald").assertIsDisplayed().performClick()
        assertEquals(AppAccentPalette.EMERALD, selectedPalette)

        // Scroll to & click App Font Scale
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("app_font_large"))
        composeRule.onNodeWithTag("app_font_large").assertIsDisplayed().performClick()
        assertEquals(AppFontScale.LARGE, selectedFontScale)

        // Scroll to & click Density Mode
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("app_density_compact"))
        composeRule.onNodeWithTag("app_density_compact").assertIsDisplayed().performClick()
        assertEquals(AppDensityMode.COMPACT, selectedDensity)

        // Scroll to & click Period Reminder Lead Time (15 min)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("period_lead_15"))
        composeRule.onNodeWithTag("period_lead_15").assertIsDisplayed().performClick()
        assertEquals(15, selectedLeadMinutes)

        // Scroll to Widget Font Slider, Refresh & Copyright controls
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("widget_font_slider"))
        composeRule.onNodeWithTag("widget_font_slider").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_refresh_widgets_button"))
        composeRule.onNodeWithTag("settings_refresh_widgets_button").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_copyright_text"))
        composeRule.onNodeWithText("Phần mềm được phát triển bởi Lê Huy Hùng - Trường THCS Lê Hữu Lập").assertIsDisplayed()
        composeRule.onNodeWithText("v3.1 (Build 3)", substring = true).assertDoesNotExist()
    }

    @Test
    fun settingsScreen_syncAndLogoutButtonsShowFullTextEvenAtXLargeFontScale() {
        val xlargeState = sampleUiState().copy(
            preferences = sampleUiState().preferences.copy(appFontScale = AppFontScale.XLARGE)
        )
        composeRule.setContent {
            LhlTimetableTheme(fontScale = AppFontScale.XLARGE) {
                SettingsScreen(
                    uiState = xlargeState,
                    onSyncNow = {},
                    onUpdateAppTheme = {},
                    onUpdateAccentPalette = {},
                    onUpdateFontScale = {},
                    onUpdateDensityMode = {},
                    onUpdateDailyReminder = { _, _, _ -> },
                    onTogglePeriodReminder = {},
                    onUpdatePeriodLeadMinutes = {},
                    onSendTestNotification = {},
                    onToggleGpsLocation = {},
                    onUpdateWidgetAppearance = { _, _, _, _, _, _ -> },
                    onRefreshWidgetsNow = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_sync_now_button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_logout_button").assertIsDisplayed()
        composeRule.onNodeWithText("Đồng bộ TKB").assertIsDisplayed()
        composeRule.onNodeWithText("Đăng xuất").assertIsDisplayed()
    }

    @Test
    fun timetableScreen_rendersLookupAndShareButtonsAndOption2Modes() {
        val samplePeriod = com.example.domain.model.ResolvedPeriod(
            slotId = "t2-p1",
            week = 12,
            day = "T2",
            period = 1,
            session = "morning",
            classId = "8A1",
            className = "8A1",
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin",
            teacherId = "t1",
            teacherShortName = "Hoa",
            room = "Phòng Tin học",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(7, 15),
            endTime = com.example.domain.lunar.VnTime(8, 0)
        )
        val state = sampleUiState().copy(selectedWeekPeriods = listOf(samplePeriod))

        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        composeRule.onNodeWithTag("timetable_lookup_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_share_button").assertIsDisplayed()
        composeRule.onNodeWithText("Tra cứu Lớp / GV").assertIsDisplayed()
        composeRule.onNodeWithText("Chia sẻ ảnh TKB").assertIsDisplayed()

        // Option 2 mode selector & quick day strip
        composeRule.onNodeWithTag("timetable_mode_day").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_mode_cards").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_mode_matrix").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_day_T2").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_day_T7").assertIsDisplayed()

        // Switch to Single Day mode and select T2
        composeRule.onNodeWithTag("timetable_mode_day").performClick()
        composeRule.onNodeWithTag("quick_day_T2").performClick()
        composeRule.onNodeWithTag("single_day_card_T2").assertIsDisplayed()
        composeRule.onNodeWithText("8A1 • Tin học").assertIsDisplayed()
        composeRule.onNodeWithText("Phòng Tin học").assertIsDisplayed()

        // Switch to Matrix mode
        composeRule.onNodeWithTag("timetable_mode_matrix").performClick()
        composeRule.onNodeWithText("Bảng TKB Buổi Sáng").assertIsDisplayed()
    }

    @Test
    fun settingsWidgetPreview_showsRealTeacherScheduleWithoutHardcodedMusic() {
        val stateEmptyToday = MainUiState(
            isInitialized = true,
            currentTeacher = Teacher(
                id = "gv-hung",
                name = "Lê Huy Hùng",
                shortName = "Hùng",
                email = "lehuyhung.h2com@gmail.com",
                mainSubject = "Tin học"
            ),
            todayDate = com.example.domain.lunar.VnDate(2026, 11, 26),
            nowTime = com.example.domain.lunar.VnTime(14, 24),
            isSundayPreview = false,
            teacherTodayPeriods = emptyList()
        )

        composeRule.setContent {
            LhlTimetableTheme {
                SettingsScreen(
                    uiState = stateEmptyToday,
                    onSyncNow = {},
                    onUpdateAppTheme = {},
                    onUpdateAccentPalette = {},
                    onUpdateFontScale = {},
                    onUpdateDensityMode = {},
                    onUpdateDailyReminder = { _, _, _ -> },
                    onTogglePeriodReminder = {},
                    onUpdatePeriodLeadMinutes = {},
                    onSendTestNotification = {},
                    onToggleGpsLocation = {},
                    onUpdateWidgetAppearance = { _, _, _, _, _, _ -> },
                    onRefreshWidgetsNow = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("preview_widget_tkb"))
        composeRule.onNodeWithText("Hôm nay không có tiết dạy.").assertIsDisplayed()
        composeRule.onNodeWithText("8A1 · Âm nhạc", substring = true).assertDoesNotExist()
    }

    @Test
    fun settingsWidgetPreview_ignoresHomeroomModeAndShowsTeachersActualPeriods() {
        val teacherPeriod = com.example.domain.model.ResolvedPeriod(
            slotId = "s1",
            week = 12,
            day = "T5",
            period = 2,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin",
            teacherId = "gv-hung",
            teacherShortName = "Hùng",
            room = "P.Tin",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(8, 0),
            endTime = com.example.domain.lunar.VnTime(8, 45),
            liveStatus = com.example.domain.model.PeriodLiveStatus.UPCOMING
        )
        val homeroomOtherPeriod = teacherPeriod.copy(
            slotId = "s2",
            classId = "8A1",
            className = "8A1",
            subjectId = "sub-van",
            subjectName = "Ngữ văn",
            teacherId = "gv-other"
        )
        val stateWithHomeroom = MainUiState(
            isInitialized = true,
            currentTeacher = Teacher(
                id = "gv-hung",
                name = "Lê Huy Hùng",
                shortName = "Hùng",
                email = "lehuyhung.h2com@gmail.com",
                homeroomClass = "8A1"
            ),
            preferences = UserPreferencesState(
                loggedInTeacherId = "gv-hung",
                showHomeroomClass = true
            ),
            todayDate = com.example.domain.lunar.VnDate(2026, 11, 26),
            nowTime = com.example.domain.lunar.VnTime(7, 30),
            isSundayPreview = false,
            todayMorningPeriods = listOf(homeroomOtherPeriod),
            teacherTodayPeriods = listOf(teacherPeriod)
        )

        composeRule.setContent {
            LhlTimetableTheme {
                SettingsScreen(
                    uiState = stateWithHomeroom,
                    onSyncNow = {},
                    onUpdateAppTheme = {},
                    onUpdateAccentPalette = {},
                    onUpdateFontScale = {},
                    onUpdateDensityMode = {},
                    onUpdateDailyReminder = { _, _, _ -> },
                    onTogglePeriodReminder = {},
                    onUpdatePeriodLeadMinutes = {},
                    onSendTestNotification = {},
                    onToggleGpsLocation = {},
                    onUpdateWidgetAppearance = { _, _, _, _, _, _ -> },
                    onRefreshWidgetsNow = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("preview_widget_tkb"))
        composeRule.onNodeWithText("6A1 · Tin học").assertIsDisplayed()
        composeRule.onNodeWithText("8A1 · Ngữ văn").assertDoesNotExist()
    }

    @Test
    fun timetableScreen_shareButtonOpensExportOptionsAndRendersScopedBitmaps() {
        val morning = com.example.domain.model.ResolvedPeriod(
            slotId = "m1",
            week = 12,
            day = "T2",
            period = 1,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin",
            teacherId = "gv-hung",
            teacherShortName = "Hùng",
            room = "P.Tin",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(7, 15),
            endTime = com.example.domain.lunar.VnTime(8, 0)
        )
        val afternoon = morning.copy(
            slotId = "a1",
            day = "T4",
            period = 2,
            session = "afternoon",
            classId = "9A3",
            className = "9A3",
            startTime = com.example.domain.lunar.VnTime(14, 45),
            endTime = com.example.domain.lunar.VnTime(15, 30)
        )
        val periods = listOf(morning, afternoon)

        val state = sampleUiState().copy(selectedWeekPeriods = periods)
        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        composeRule.onNodeWithTag("timetable_share_button").performClick()
        composeRule.onNodeWithTag("export_scope_all").assertIsDisplayed()
        composeRule.onNodeWithTag("export_scope_morning").assertIsDisplayed()
        composeRule.onNodeWithTag("export_scope_afternoon").assertIsDisplayed()
        composeRule.onNodeWithTag("export_download_tkb_button").assertIsDisplayed()
        composeRule.onNodeWithTag("export_share_tkb_button").assertIsDisplayed()

        val fullBmp = com.example.util.TimetableShareHelper.renderWeeklyTimetableBitmap(
            title = "TKB Lê Huy Hùng",
            subtitle = "Trường THCS Lê Hữu Lập",
            weekLabel = "Tuần 12",
            dateRange = "23/11 – 29/11/2026",
            periods = periods,
            showTeacher = false,
            scope = com.example.util.TimetableShareScope.FULL_DAY
        )
        val morningBmp = com.example.util.TimetableShareHelper.renderWeeklyTimetableBitmap(
            title = "TKB Lê Huy Hùng",
            subtitle = "Trường THCS Lê Hữu Lập",
            weekLabel = "Tuần 12",
            dateRange = "23/11 – 29/11/2026",
            periods = periods,
            showTeacher = false,
            scope = com.example.util.TimetableShareScope.MORNING
        )
        val afternoonBmp = com.example.util.TimetableShareHelper.renderWeeklyTimetableBitmap(
            title = "TKB Lê Huy Hùng",
            subtitle = "Trường THCS Lê Hữu Lập",
            weekLabel = "Tuần 12",
            dateRange = "23/11 – 29/11/2026",
            periods = periods,
            showTeacher = false,
            scope = com.example.util.TimetableShareScope.AFTERNOON
        )
        org.junit.Assert.assertTrue(fullBmp.height > morningBmp.height)
        org.junit.Assert.assertTrue(morningBmp.height > afternoonBmp.height)
        fullBmp.recycle()
        morningBmp.recycle()
        afternoonBmp.recycle()
    }

    @Test
    fun timetableScreen_showsChangedBadge_opensWorkloadSheet_andOpensSwapTab() {
        val changedPeriod = com.example.domain.model.ResolvedPeriod(
            slotId = "chg1",
            week = 12,
            day = "T2",
            period = 1,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin",
            teacherId = "t1",
            teacherShortName = "Hoa",
            room = "P.Tin",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(7, 15),
            endTime = com.example.domain.lunar.VnTime(8, 0),
            isChanged = true
        )
        val colleague = Teacher(
            id = "t2",
            name = "Trần Văn Minh",
            shortName = "Minh",
            email = "minh@lhl.vn",
            department = "Tổ KHXH",
            mainSubject = "Ngữ văn"
        )
        val state = sampleUiState().copy(
            allTeachers = listOf(sampleUiState().currentTeacher!!, colleague),
            selectedWeekPeriods = listOf(changedPeriod),
            allSelectedWeekPeriods = listOf(changedPeriod),
            changedPeriodCount = 1,
            changeComparisonLabel = "so với Tuần 11",
            workloadStats = com.example.domain.model.WorkloadStatsSummary(
                selectedWeek = 12,
                weekMorningCount = 1,
                weekAfternoonCount = 0,
                weekAssignedQuota = 2,
                yearCachedWeeksCount = 2,
                yearCachedWeeksList = listOf(11, 12),
                yearMorningCount = 3,
                yearAfternoonCount = 1
            )
        )

        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        // Changed alert banner and MỚI ĐỔI badge are visible
        composeRule.onNodeWithTag("changed_periods_alert_banner").assertIsDisplayed()
        composeRule.onNodeWithText("MỚI ĐỔI").assertIsDisplayed()

        // Open Định mức sheet
        composeRule.onNodeWithTag("timetable_workload_button").performClick()
        composeRule.onNodeWithTag("workload_stats_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Định mức & Thống kê số tiết").assertIsDisplayed()

        // Dismiss sheet by pressing back or closing
        composeRule.onNode(androidx.compose.ui.test.hasContentDescription("Đóng thống kê")).performClick()

        // Open Tìm người đổi tiết directly on swap tab
        composeRule.onNodeWithTag("timetable_swap_button").performClick()
        composeRule.onNodeWithTag("swap_suggestion_t2").assertIsDisplayed()
        composeRule.onNodeWithText("Cùng tổ").assertIsDisplayed()
    }

    @Test
    fun calendarScreen_displaysEventDotsAndOpensAddEventSheet() {
        val today = com.example.domain.lunar.VnDate(2026, 11, 26)
        val sampleEvent = com.example.domain.model.CalendarEvent(
            id = 10L,
            title = "Họp tổ chuyên môn",
            note = "Phòng hội đồng",
            category = com.example.domain.model.CalendarEventCategory.MEETING,
            isLunar = false,
            year = 2026,
            month = 11,
            day = 26,
            timeLabel = "14:00"
        )
        var savedEvent: com.example.domain.model.CalendarEvent? = null
        val state = sampleUiState().copy(
            todayDate = today,
            calendarEvents = listOf(sampleEvent)
        )

        composeRule.setContent {
            LhlTimetableTheme {
                com.example.ui.screens.CalendarScreen(
                    uiState = state,
                    onJumpToWeek = {},
                    onSaveEvent = { savedEvent = it },
                    onDeleteEvent = {}
                )
            }
        }

        // Event dot on month cell and event row in list (scroll down LazyColumn for event card)
        composeRule.onNodeWithTag("calendar_event_dot_2026_11_26", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("calendar_event_item_10"))
        composeRule.onNodeWithTag("calendar_event_item_10").assertIsDisplayed()
        composeRule.onAllNodesWithText("Họp tổ chuyên môn")[0].assertIsDisplayed()

        // Open add event dialog & verify input
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("calendar_add_event_button"))
        composeRule.onNodeWithTag("calendar_add_event_button").performClick()
        composeRule.onNodeWithTag("calendar_event_title_input").assertIsDisplayed()
    }

    @Test
    fun timetableMatrix_renders4WeekAfternoonCycleAndHidesClubTeacher() {
        val morningSlot = com.example.domain.model.ResolvedPeriod(
            slotId = "m1",
            week = 12,
            day = "T2",
            period = 1,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-toan",
            subjectName = "Toán",
            subjectShort = "Toán",
            teacherId = "t1",
            teacherShortName = "Dũng",
            room = "P101",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(7, 15),
            endTime = com.example.domain.lunar.VnTime(8, 0)
        )
        val clubW9 = com.example.domain.model.ResolvedPeriod(
            slotId = "c_w9",
            week = 9,
            day = "T3",
            period = 3,
            session = "afternoon",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-clb",
            subjectName = "Sinh hoạt CLB",
            subjectShort = "CLB",
            teacherId = "t2",
            teacherShortName = "ẨnTênGV",
            room = "P101",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(15, 40),
            endTime = com.example.domain.lunar.VnTime(16, 25),
            hideAfternoonTeacher = true
        )
        val tinW12 = clubW9.copy(
            slotId = "c_w12",
            week = 12,
            day = "T4",
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin",
            teacherId = "t1",
            teacherShortName = "Hùng",
            hideAfternoonTeacher = false
        )

        val state = sampleUiState().copy(
            preferences = sampleUiState().preferences.copy(showHomeroomClass = true),
            homeroomClass = com.example.domain.model.SchoolClass(id = "6A1", name = "6A1", grade = 6),
            selectedWeekNumber = 12,
            selectedCycle = com.example.domain.model.TimetableCalculator.getAfternoonMonthCycle(12),
            selectedWeekPeriods = listOf(morningSlot, tinW12),
            selectedCycleAfternoonPeriods = listOf(clubW9, tinW12)
        )

        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        composeRule.onNodeWithTag("timetable_mode_matrix").performClick()
        composeRule.onNodeWithTag("afternoon_month_matrix_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("BUỔI CHIỀU — THÁNG 11 (Tuần 9, 10, 11, 12)").assertIsDisplayed()
        composeRule.onNodeWithText("Tuần 9").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Tuần 12").onLast().performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sinh hoạt CLB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ẨnTênGV").assertDoesNotExist()
        composeRule.onNodeWithText("Hùng").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun swapLookupSheet_usesUnifiedScrollableListWithCompactSummaryAndSuggestions() {
        val myPeriod = com.example.domain.model.ResolvedPeriod(
            slotId = "s1",
            week = 12,
            day = "T2",
            period = 1,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin",
            teacherId = "t1",
            teacherShortName = "Hoa",
            room = "P101",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(7, 15),
            endTime = com.example.domain.lunar.VnTime(8, 0)
        )
        val t2 = Teacher(id = "t2", name = "Chung Minh Cường", shortName = "Cường", email = "c@lhl.vn", department = "Nghệ thuật", mainSubject = "Mỹ thuật")
        val t3 = Teacher(id = "t3", name = "Lê Thị Hoa", shortName = "Hoa", email = "hoa2@lhl.vn", department = "Tổ KHXH", mainSubject = "Ngữ văn")
        val t4 = Teacher(id = "t4", name = "Phạm Văn Bắc", shortName = "Bắc", email = "bac@lhl.vn", department = "Tổ KHXH", mainSubject = "GDTC")

        val state = sampleUiState().copy(
            allTeachers = listOf(sampleUiState().currentTeacher!!, t2, t3, t4),
            selectedWeekPeriods = listOf(myPeriod),
            allSelectedWeekPeriods = listOf(myPeriod)
        )

        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        composeRule.onNodeWithTag("timetable_swap_button").performClick()
        composeRule.onNodeWithTag("swap_unified_list").assertIsDisplayed()
        composeRule.onNodeWithTag("swap_selected_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("swap_suggestion_t2").assertIsDisplayed()
        composeRule.onNodeWithTag("swap_suggestion_t3").assertIsDisplayed()
        composeRule.onNodeWithTag("swap_suggestion_t4").assertIsDisplayed()
    }

    @Test
    fun classTimetable_showsActualPeriodNumbersInMatrixAndLookupTab() {
        val gdtcRow1 = com.example.domain.model.ResolvedPeriod(
            slotId = "m1",
            week = 12,
            day = "T2",
            period = 1,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-gdtc",
            subjectName = "GDTC 2",
            subjectShort = "GDTC 2",
            teacherId = "t4",
            teacherShortName = "Bắc",
            room = "P101",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(7, 15),
            endTime = com.example.domain.lunar.VnTime(8, 0),
            rawPeriod = 1,
            actualPeriod = 2,
            baseSubjectName = "GDTC"
        )
        val hdtnRow3 = gdtcRow1.copy(
            slotId = "m3",
            period = 3,
            subjectId = "sub-hdtn",
            subjectName = "HĐTN 1",
            subjectShort = "HĐTN 1",
            teacherId = "t2",
            teacherShortName = "Thúy",
            startTime = com.example.domain.lunar.VnTime(8, 55),
            endTime = com.example.domain.lunar.VnTime(9, 40),
            rawPeriod = 3,
            actualPeriod = 1,
            baseSubjectName = "HĐTN"
        )
        val tinTeacherActual3 = gdtcRow1.copy(
            slotId = "t_tin",
            period = 3,
            subjectId = "sub-tin",
            subjectName = "Tin học",
            subjectShort = "Tin học",
            teacherId = "t1",
            teacherShortName = "Hoa",
            rawPeriod = 4,
            actualPeriod = 3,
            baseSubjectName = "Tin học"
        )

        val state = sampleUiState().copy(
            preferences = sampleUiState().preferences.copy(showHomeroomClass = true),
            homeroomClass = com.example.domain.model.SchoolClass(id = "6A1", name = "6A1", grade = 6),
            allClasses = listOf(com.example.domain.model.SchoolClass(id = "6A1", name = "6A1", grade = 6)),
            selectedWeekNumber = 12,
            selectedWeekPeriods = listOf(gdtcRow1, hdtnRow3),
            allSelectedWeekPeriods = listOf(tinTeacherActual3),
            allSelectedWeekClassPeriods = listOf(gdtcRow1, hdtnRow3)
        )

        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        // Homeroom class matrix displays "GDTC 2" and "HĐTN 1"
        composeRule.onNodeWithTag("timetable_mode_matrix").performClick()
        composeRule.onNodeWithText("GDTC 2").assertIsDisplayed()
        composeRule.onNodeWithText("HĐTN 1").assertIsDisplayed()

        // Lookup sheet Tab "TKB Lớp" reads allSelectedWeekClassPeriods (not teacher-view allSelectedWeekPeriods)
        composeRule.onNodeWithTag("timetable_lookup_button").performClick()
        composeRule.onNodeWithTag("lookup_tab_class").performClick()
        composeRule.onAllNodesWithText("GDTC 2").assertCountEquals(2)
        composeRule.onAllNodesWithText("GDTC 2").onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText("HĐTN 1").onLast().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_widgetFontResetChipDisplaysCleanlyWhenScaled() {
        var resetScale: Float? = null
        val scaledState = sampleUiState().copy(
            preferences = sampleUiState().preferences.copy(widgetFontScale = 1.15f)
        )
        composeRule.setContent {
            LhlTimetableTheme {
                SettingsScreen(
                    uiState = scaledState,
                    onSyncNow = {},
                    onUpdateAppTheme = {},
                    onUpdateAccentPalette = {},
                    onUpdateFontScale = {},
                    onUpdateDensityMode = {},
                    onUpdateDailyReminder = { _, _, _ -> },
                    onTogglePeriodReminder = {},
                    onUpdatePeriodLeadMinutes = {},
                    onSendTestNotification = {},
                    onToggleGpsLocation = {},
                    onUpdateWidgetAppearance = { _, _, _, _, fontScale, _ -> resetScale = fontScale },
                    onRefreshWidgetsNow = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("widget_font_reset_button"))
        composeRule.onNodeWithText("Cỡ chữ trên Widget").assertIsDisplayed()
        composeRule.onNodeWithText("115% (Lớn)").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_font_reset_button").assertIsDisplayed().performClick()
        assertEquals(1.0f, resetScale)
    }

    @Test
    fun schoolWideExport_displaysModeAndRendersHighResMorningAndAfternoonBitmaps() {
        val classes = listOf(
            com.example.domain.model.SchoolClass(id = "6A1", name = "6A1", grade = 6),
            com.example.domain.model.SchoolClass(id = "6A2", name = "6A2", grade = 6),
            com.example.domain.model.SchoolClass(id = "7A1", name = "7A1", grade = 7),
            com.example.domain.model.SchoolClass(id = "8A1", name = "8A1", grade = 8),
            com.example.domain.model.SchoolClass(id = "9A3", name = "9A3", grade = 9)
        )
        val morningSlot = com.example.domain.model.ResolvedPeriod(
            slotId = "sw_m1",
            week = 12,
            day = "T2",
            period = 2,
            session = "morning",
            classId = "6A1",
            className = "6A1",
            subjectId = "sub-su",
            subjectName = "LS&ĐL - Sử 3",
            subjectShort = "LS&ĐL - Sử 3",
            teacherId = "t1",
            teacherShortName = "Thúy",
            room = "P101",
            note = null,
            customLabel = null,
            startTime = com.example.domain.lunar.VnTime(8, 0),
            endTime = com.example.domain.lunar.VnTime(8, 45),
            rawPeriod = 2,
            actualPeriod = 3,
            baseSubjectName = "LS&ĐL - Sử"
        )
        val satP5Slot = morningSlot.copy(
            slotId = "sw_t7_5",
            day = "T7",
            period = 5,
            subjectId = "sub-hdtn",
            subjectName = "HĐTN 5",
            rawPeriod = 5,
            actualPeriod = 5,
            baseSubjectName = "HĐTN"
        )
        val afternoonClub = morningSlot.copy(
            slotId = "sw_a1",
            week = 10,
            day = "T3",
            period = 3,
            session = "afternoon",
            subjectId = "sub-clb",
            subjectName = "Sinh hoạt CLB",
            teacherShortName = "Ẩn",
            hideAfternoonTeacher = true
        )
        val state = sampleUiState().copy(
            allClasses = classes,
            selectedWeekNumber = 12,
            selectedWeekPeriods = listOf(morningSlot),
            allSelectedWeekClassPeriods = listOf(morningSlot),
            allSelectedCycleAfternoonPeriods = listOf(afternoonClub)
        )

        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = state,
                    onSelectWeek = {},
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onNotify = {}
                )
            }
        }

        composeRule.onNodeWithTag("timetable_share_button").performClick()
        composeRule.onNodeWithTag("export_mode_school").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("export_school_morning").assertIsDisplayed()
        composeRule.onNodeWithTag("export_school_afternoon").assertIsDisplayed()
        composeRule.onNodeWithText("Lưu ảnh PNG vào máy").assertIsDisplayed()
        composeRule.onNodeWithText("Chia sẻ ảnh (Zalo...)").assertIsDisplayed()

        // Verify high-resolution rendering (~2800px) & T7 dynamic 4-row vs 5-row height
        val bmpNoSat5 = com.example.util.SchoolWideTimetableRenderer.renderSchoolWideTimetableBitmap(
            classes = classes,
            morningPeriods = listOf(morningSlot),
            afternoonCyclePeriods = listOf(afternoonClub),
            scope = com.example.util.SchoolWideShareScope.MORNING,
            selectedWeekNumber = 12,
            dateRange = "23/11 – 28/11/2026"
        )
        val bmpWithSat5 = com.example.util.SchoolWideTimetableRenderer.renderSchoolWideTimetableBitmap(
            classes = classes,
            morningPeriods = listOf(morningSlot, satP5Slot),
            afternoonCyclePeriods = listOf(afternoonClub),
            scope = com.example.util.SchoolWideShareScope.MORNING,
            selectedWeekNumber = 12,
            dateRange = "23/11 – 28/11/2026"
        )
        val bmpAfternoon = com.example.util.SchoolWideTimetableRenderer.renderSchoolWideTimetableBitmap(
            classes = classes,
            morningPeriods = listOf(morningSlot),
            afternoonCyclePeriods = listOf(afternoonClub),
            scope = com.example.util.SchoolWideShareScope.AFTERNOON,
            selectedWeekNumber = 12,
            dateRange = "23/11 – 28/11/2026"
        )
        org.junit.Assert.assertTrue(bmpNoSat5.width >= 2600)
        org.junit.Assert.assertTrue(bmpWithSat5.height > bmpNoSat5.height)
        org.junit.Assert.assertTrue(bmpAfternoon.width >= 2600)

        val preview = com.example.util.TimetableShareHelper.createScaledPreview(bmpNoSat5, maxWidth = 1240)
        org.junit.Assert.assertEquals(1240, preview.width)

        preview.recycle()
        bmpNoSat5.recycle()
        bmpWithSat5.recycle()
        bmpAfternoon.recycle()
    }

    @Test
    fun updateBannerAndDialog_showVersionDiffAndChangelogBullets() {
        val updateInfo = com.example.data.repository.AppUpdateInfo(
            versionCode = 7,
            versionName = "3.5",
            title = "Phiên bản 3.5",
            publishedDateLabel = "05/12/2026",
            apkDownloadUrl = "https://example.com/v3.5.apk",
            apkFileName = "Lich-TKB-LHL-v3.5-vc7.apk",
            apkSizeBytes = 25_165_824L,
            releasePageUrl = "https://github.com/lehuyhungh2com-sketch/Lich-TKBLHL/releases",
            changelogSections = listOf(
                com.example.data.repository.VersionChangelogSection(
                    versionName = "3.5",
                    versionCode = 7,
                    publishedDateLabel = "05/12/2026",
                    items = listOf("Cải tiến hiển thị Lịch công tác", "Sửa lỗi múi giờ thông báo")
                ),
                com.example.data.repository.VersionChangelogSection(
                    versionName = "3.4",
                    versionCode = 6,
                    publishedDateLabel = "01/12/2026",
                    items = listOf("Tối ưu tốc độ đồng bộ TKB")
                )
            )
        )
        var openClicked = false
        val state = sampleUiState().copy(
            appUpdate = com.example.ui.AppUpdateUiState(
                availableUpdate = updateInfo,
                showDialog = true
            )
        )

        composeRule.setContent {
            LhlTimetableTheme {
                com.example.ui.screens.TodayScreen(
                    uiState = state,
                    onToggleHomeroom = {},
                    onSyncNow = {},
                    onRefreshWeather = {},
                    onOpenTimetable = {},
                    onSaveLessonNote = { _, _ -> },
                    onDeleteLessonNote = {},
                    onDismissBanner = {},
                    onOpenUpdateDialog = { openClicked = true }
                )
                com.example.ui.components.AppUpdateDialog(
                    updateState = state.appUpdate,
                    onStartDownloadAndInstall = {},
                    onInstallDownloadedApk = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("app_update_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Có phiên bản mới 3.5").assertIsDisplayed()
        composeRule.onNodeWithTag("app_update_banner_button").performClick()
        org.junit.Assert.assertTrue(openClicked)

        composeRule.onNodeWithTag("app_update_dialog").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cập nhật phiên bản mới").assertCountEquals(2)
        composeRule.onAllNodesWithText("Cập nhật phiên bản mới")[0].assertIsDisplayed()
        composeRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onAllNodesWithText("v3.5").assertCountEquals(2)
        composeRule.onAllNodesWithText("v3.5")[0].assertIsDisplayed()
        composeRule.onNodeWithText("v3.4").assertIsDisplayed()
        composeRule.onNodeWithText("Cải tiến hiển thị Lịch công tác").assertIsDisplayed()
        composeRule.onNodeWithText("Tối ưu tốc độ đồng bộ TKB").assertIsDisplayed()
        composeRule.onNodeWithTag("app_update_dialog_primary_button").assertIsDisplayed()
    }
}
