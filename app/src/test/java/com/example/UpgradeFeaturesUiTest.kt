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
}
