package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        // Verify v2.0 badge is visible at the top
        composeRule.onNodeWithTag("settings_version_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Phiên bản 2.0").assertIsDisplayed()

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

        // Scroll to Widget Corner / Font / Show Subject / Refresh controls
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_refresh_widgets_button"))
        composeRule.onNodeWithTag("settings_refresh_widgets_button").assertIsDisplayed()
    }

    @Test
    fun timetableScreen_rendersLookupAndShareButtons() {
        composeRule.setContent {
            LhlTimetableTheme {
                TimetableScreen(
                    uiState = sampleUiState(),
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
}
