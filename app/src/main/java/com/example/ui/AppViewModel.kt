package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LhlTimetableApp
import com.example.data.local.AppAccentPalette
import com.example.data.local.AppDensityMode
import com.example.data.local.AppFontScale
import com.example.data.local.AppThemeMode
import com.example.data.local.LessonNoteEntity
import com.example.data.local.SyncMetadataEntity
import com.example.data.local.UserPreferencesState
import com.example.data.local.WidgetCornerStyle
import com.example.data.local.WidgetFontSize
import com.example.data.local.WidgetTextTheme
import com.example.data.repository.LoginResult
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.LunarDate
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.Teacher
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.WeatherSnapshot
import com.example.domain.model.WeekInfo
import com.example.notification.NotificationHelper
import com.example.notification.ReminderAlarmReceiver
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val isInitialized: Boolean = false,
    val isFirebaseConfigured: Boolean = true,
    val configuredProjectId: String? = null,
    val preferences: UserPreferencesState = UserPreferencesState(),
    val currentTeacher: Teacher? = null,
    val homeroomClass: SchoolClass? = null,
    val allTeachers: List<Teacher> = emptyList(),
    val allClasses: List<SchoolClass> = emptyList(),
    val schoolConfig: SchoolConfig = SchoolConfig(),
    val weeks: List<WeekInfo> = emptyList(),
    val currentWeekNumber: Int = 1,
    val selectedWeekNumber: Int = 1,
    val selectedWeekInfo: WeekInfo? = null,
    val todayDate: VnDate = VnDate.today(),
    val todayLunar: LunarDate = LunarCalendar.convertSolar2Lunar(VnDate.today()),
    val nowTime: VnTime = VnTime.now(),
    val isSundayPreview: Boolean = VnDate.today().isSunday,
    val todayMorningPeriods: List<ResolvedPeriod> = emptyList(),
    val todayAfternoonPeriods: List<ResolvedPeriod> = emptyList(),
    val teacherTodayPeriods: List<ResolvedPeriod> = emptyList(),
    val selectedWeekPeriods: List<ResolvedPeriod> = emptyList(),
    val allSelectedWeekPeriods: List<ResolvedPeriod> = emptyList(),
    val currentWeekPeriods: List<ResolvedPeriod> = emptyList(),
    val lessonNotes: Map<String, String> = emptyMap(),
    val weather: WeatherSnapshot? = null,
    val syncMetadata: SyncMetadataEntity? = null,
    val isSyncing: Boolean = false,
    val isRefreshingWeather: Boolean = false,
    val syncStatusBanner: String? = null,
    val loginLoading: Boolean = false,
    val loginError: String? = null
) {
    val isLoggedIn: Boolean get() = preferences.isLoggedIn && currentTeacher != null
    val homeroomClassId: String? get() = homeroomClass?.id ?: currentTeacher?.homeroomClass
    val canToggleHomeroom: Boolean get() = !homeroomClassId.isNullOrBlank()
    val effectiveShowHomeroom: Boolean get() = preferences.showHomeroomClass && canToggleHomeroom

    fun personalNoteFor(period: ResolvedPeriod): String? {
        val key = LessonNoteEntity.buildKey(
            week = period.week,
            day = period.day,
            period = period.period,
            session = period.session,
            classId = period.classId
        )
        return lessonNotes[key]?.takeIf { it.isNotBlank() }
    }
}

private data class LocalCatalogBundle(
    val config: SchoolConfig,
    val teachers: List<Teacher>,
    val classes: List<SchoolClass>,
    val subjects: List<com.example.domain.model.Subject>,
    val assignments: List<com.example.domain.model.Assignment>,
    val weeks: List<WeekInfo>,
    val deletedKeys: Set<String>
)

private data class TransientStatus(
    val isSyncing: Boolean = false,
    val isRefreshingWeather: Boolean = false,
    val syncStatusBanner: String? = null,
    val loginLoading: Boolean = false,
    val loginError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LhlTimetableApp
    private val schoolRepo = app.schoolRepository
    private val weatherRepo = app.weatherRepository
    private val prefsRepo = app.preferences

    private val clockTickFlow = MutableStateFlow(VnDate.today() to VnTime.now())
    private val selectedWeekOverride = MutableStateFlow<Int?>(null)
    private val transientStatus = MutableStateFlow(TransientStatus())
    private var widgetUpdateJob: Job? = null

    private val catalogFlow = combine(
        schoolRepo.schoolConfigFlow,
        schoolRepo.teachersFlow,
        schoolRepo.classesFlow,
        schoolRepo.subjectsFlow,
        schoolRepo.assignmentsFlow
    ) { config, teachers, classes, subjects, assignments ->
        LocalCatalogBundle(
            config = config,
            teachers = teachers,
            classes = classes,
            subjects = subjects,
            assignments = assignments,
            weeks = emptyList(),
            deletedKeys = emptySet()
        )
    }.combine(
        combine(schoolRepo.weeksFlow, schoolRepo.deletedSlotKeysFlow) { w, d -> w to d }
    ) { base, (weeks, deletedKeys) ->
        base.copy(weeks = weeks, deletedKeys = deletedKeys)
    }

    private val currentWeekFlow = combine(catalogFlow, clockTickFlow) { catalog, (today, _) ->
        TimetableCalculator.calculateCurrentWeek(
            today = today,
            schoolConfig = catalog.config,
            weeks = catalog.weeks
        )
    }

    private val effectiveSelectedWeekFlow = combine(
        currentWeekFlow,
        selectedWeekOverride
    ) { currentWeek, override ->
        override ?: currentWeek
    }

    private val currentWeekSlotsFlow = currentWeekFlow.flatMapLatest { week ->
        schoolRepo.observeSlotsForWeek(week)
    }

    private val selectedWeekSlotsFlow = effectiveSelectedWeekFlow.flatMapLatest { week ->
        schoolRepo.observeSlotsForWeek(week)
    }

    private val metaAndWeatherFlow = combine(
        weatherRepo.weatherFlow,
        schoolRepo.syncMetadataFlow,
        prefsRepo.preferencesFlow,
        transientStatus,
        schoolRepo.lessonNotesFlow
    ) { weather, syncMeta, prefs, status, notes ->
        MetaWeatherPrefs(weather, syncMeta, prefs, status, notes)
    }

    val uiState: StateFlow<MainUiState> = combine(
        catalogFlow,
        clockTickFlow,
        currentWeekFlow,
        effectiveSelectedWeekFlow,
        combine(currentWeekSlotsFlow, selectedWeekSlotsFlow, metaAndWeatherFlow) { curSlots, selSlots, mwp ->
            Triple(curSlots, selSlots, mwp)
        }
    ) { catalog, (today, now), currentWeek, selectedWeek, (currentWeekSlots, selectedWeekSlots, mwp) ->
        val prefs = mwp.prefs
        val currentTeacher = prefs.loggedInTeacherId?.let { id ->
            catalog.teachers.firstOrNull { it.id == id }
        }
        val homeroomClassId = currentTeacher?.let {
            TimetableCalculator.resolveHomeroomClassId(it, catalog.classes)
        }
        val homeroomClass = homeroomClassId?.let { hcId ->
            catalog.classes.firstOrNull { it.id.equals(hcId, ignoreCase = true) }
        }

        val showHomeroom = prefs.showHomeroomClass && !homeroomClassId.isNullOrBlank()
        val filterTeacherId = if (showHomeroom) null else currentTeacher?.id
        val filterClassId = if (showHomeroom) homeroomClassId else null

        val isSundayPreview = today.isSunday
        val targetDayCode = if (isSundayPreview) "T2" else today.shortDayCode
        val currentWeekInfo = catalog.weeks.firstOrNull { it.weekNumber == currentWeek }
        val selectedWeekInfo = catalog.weeks.firstOrNull { it.weekNumber == selectedWeek }

        val currentWeekResolved = if (currentTeacher != null) {
            TimetableCalculator.resolveWeekPeriods(
                weekNumber = currentWeek,
                rawSlots = currentWeekSlots,
                schoolConfig = catalog.config,
                weekInfo = currentWeekInfo,
                deletedSlotKeys = catalog.deletedKeys,
                teachers = catalog.teachers,
                classes = catalog.classes,
                subjects = catalog.subjects,
                assignments = catalog.assignments,
                filterTeacherId = filterTeacherId,
                filterClassId = filterClassId,
                todayDate = if (isSundayPreview) null else today,
                nowTime = if (isSundayPreview) null else now
            )
        } else {
            emptyList()
        }

        val allSelectedWeekResolved = if (currentTeacher != null) {
            TimetableCalculator.resolveWeekPeriods(
                weekNumber = selectedWeek,
                rawSlots = selectedWeekSlots,
                schoolConfig = catalog.config,
                weekInfo = selectedWeekInfo,
                deletedSlotKeys = catalog.deletedKeys,
                teachers = catalog.teachers,
                classes = catalog.classes,
                subjects = catalog.subjects,
                assignments = catalog.assignments,
                filterTeacherId = null,
                filterClassId = null,
                todayDate = if (selectedWeek == currentWeek && !isSundayPreview) today else null,
                nowTime = if (selectedWeek == currentWeek && !isSundayPreview) now else null
            )
        } else {
            emptyList()
        }

        val selectedWeekResolved = if (currentTeacher != null) {
            TimetableCalculator.resolveWeekPeriods(
                weekNumber = selectedWeek,
                rawSlots = selectedWeekSlots,
                schoolConfig = catalog.config,
                weekInfo = selectedWeekInfo,
                deletedSlotKeys = catalog.deletedKeys,
                teachers = catalog.teachers,
                classes = catalog.classes,
                subjects = catalog.subjects,
                assignments = catalog.assignments,
                filterTeacherId = filterTeacherId,
                filterClassId = filterClassId,
                todayDate = if (selectedWeek == currentWeek && !isSundayPreview) today else null,
                nowTime = if (selectedWeek == currentWeek && !isSundayPreview) now else null
            )
        } else {
            emptyList()
        }

        val todayPeriods = currentWeekResolved.filter { it.day == targetDayCode }
        val teacherTodayPeriods = if (!showHomeroom) {
            todayPeriods
        } else if (currentTeacher != null) {
            TimetableCalculator.resolveWeekPeriods(
                weekNumber = currentWeek,
                rawSlots = currentWeekSlots,
                schoolConfig = catalog.config,
                weekInfo = currentWeekInfo,
                deletedSlotKeys = catalog.deletedKeys,
                teachers = catalog.teachers,
                classes = catalog.classes,
                subjects = catalog.subjects,
                assignments = catalog.assignments,
                filterTeacherId = currentTeacher.id,
                filterClassId = null,
                todayDate = if (isSundayPreview) null else today,
                nowTime = if (isSundayPreview) null else now
            ).filter { it.day == targetDayCode }
        } else {
            emptyList()
        }

        MainUiState(
            isInitialized = true,
            isFirebaseConfigured = schoolRepo.isFirebaseConfigured(),
            configuredProjectId = schoolRepo.configuredProjectId(),
            preferences = prefs,
            currentTeacher = currentTeacher,
            homeroomClass = homeroomClass,
            allTeachers = catalog.teachers.sortedBy { it.name },
            allClasses = catalog.classes.sortedWith(compareBy({ it.grade }, { it.name })),
            schoolConfig = catalog.config,
            weeks = catalog.weeks,
            currentWeekNumber = currentWeek,
            selectedWeekNumber = selectedWeek,
            selectedWeekInfo = selectedWeekInfo,
            todayDate = today,
            todayLunar = LunarCalendar.convertSolar2Lunar(today),
            nowTime = now,
            isSundayPreview = isSundayPreview,
            todayMorningPeriods = todayPeriods.filter { !it.isAfternoon },
            todayAfternoonPeriods = todayPeriods.filter { it.isAfternoon },
            teacherTodayPeriods = teacherTodayPeriods,
            selectedWeekPeriods = selectedWeekResolved,
            allSelectedWeekPeriods = allSelectedWeekResolved,
            currentWeekPeriods = currentWeekResolved,
            lessonNotes = mwp.lessonNotes,
            weather = mwp.weather,
            syncMetadata = mwp.syncMeta,
            isSyncing = mwp.status.isSyncing,
            isRefreshingWeather = mwp.status.isRefreshingWeather,
            syncStatusBanner = mwp.status.syncStatusBanner,
            loginLoading = mwp.status.loginLoading,
            loginError = mwp.status.loginError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            isFirebaseConfigured = schoolRepo.isFirebaseConfigured(),
            configuredProjectId = schoolRepo.configuredProjectId()
        )
    )

    init {
        startClockTicker()
        refreshWeatherSilently()
        syncOnStartupIfLoggedIn()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (true) {
                clockTickFlow.value = VnDate.today() to VnTime.now()
                delay(15_000L)
            }
        }
    }

    private fun syncOnStartupIfLoggedIn() {
        viewModelScope.launch {
            val prefs = prefsRepo.getSnapshot()
            if (prefs.isLoggedIn) {
                syncNow(force = false)
            }
        }
    }

    fun clearLoginError() {
        transientStatus.value = transientStatus.value.copy(loginError = null)
    }

    fun dismissBanner() {
        transientStatus.value = transientStatus.value.copy(syncStatusBanner = null)
    }

    fun showBannerMessage(message: String) {
        transientStatus.value = transientStatus.value.copy(syncStatusBanner = message)
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            transientStatus.value = transientStatus.value.copy(
                loginLoading = true,
                loginError = null
            )
            when (val result = schoolRepo.login(email, password)) {
                is LoginResult.Success -> {
                    transientStatus.value = transientStatus.value.copy(
                        loginLoading = false,
                        loginError = null,
                        syncStatusBanner = "Chào mừng ${result.teacher.name}!"
                    )
                    ReminderAlarmReceiver.rescheduleFromLocal(app, app)
                    WidgetUpdater.updateAllWidgets(app)
                }

                is LoginResult.Error -> {
                    transientStatus.value = transientStatus.value.copy(
                        loginLoading = false,
                        loginError = result.message
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            schoolRepo.logout()
            selectedWeekOverride.value = null
            transientStatus.value = TransientStatus()
            WidgetUpdater.updateAllWidgets(app)
        }
    }

    fun syncNow(force: Boolean = true) {
        viewModelScope.launch {
            transientStatus.value = transientStatus.value.copy(isSyncing = true)
            val outcome = schoolRepo.syncIncremental(
                targetWeek = selectedWeekOverride.value,
                force = force
            )
            transientStatus.value = transientStatus.value.copy(
                isSyncing = false,
                syncStatusBanner = outcome.message
            )
            ReminderAlarmReceiver.rescheduleFromLocal(app, app)
            WidgetUpdater.updateAllWidgets(app)
        }
    }

    fun selectWeek(weekNumber: Int) {
        val clamped = weekNumber.coerceIn(TimetableCalculator.MIN_WEEK, TimetableCalculator.MAX_WEEK)
        selectedWeekOverride.value = clamped
        viewModelScope.launch {
            transientStatus.value = transientStatus.value.copy(isSyncing = true)
            val outcome = schoolRepo.ensureWeekLoaded(clamped)
            transientStatus.value = transientStatus.value.copy(
                isSyncing = false,
                syncStatusBanner = if (outcome.offlineFallback) outcome.message else null
            )
        }
    }

    fun toggleHomeroomMode(showHomeroom: Boolean) {
        viewModelScope.launch {
            prefsRepo.setShowHomeroomClass(showHomeroom)
        }
    }

    fun saveLessonNote(period: ResolvedPeriod, content: String) {
        viewModelScope.launch {
            schoolRepo.saveLessonNote(
                week = period.week,
                day = period.day,
                period = period.period,
                session = period.session,
                classId = period.classId,
                content = content
            )
            WidgetUpdater.updateAllWidgets(app)
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = if (content.isBlank()) "Đã xóa ghi chú tiết dạy." else "Đã lưu ghi chú riêng trên máy."
            )
        }
    }

    fun deleteLessonNote(period: ResolvedPeriod) {
        saveLessonNote(period, "")
    }

    fun refreshWeatherSilently() {
        viewModelScope.launch {
            transientStatus.value = transientStatus.value.copy(isRefreshingWeather = true)
            weatherRepo.refreshWeather()
            transientStatus.value = transientStatus.value.copy(isRefreshingWeather = false)
            WidgetUpdater.updateAllWidgets(app)
        }
    }

    fun updateDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepo.setDailyReminder(enabled, hour, minute)
            ReminderAlarmReceiver.rescheduleFromLocal(app, app)
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = if (enabled) {
                    "Đã bật nhắc lịch dạy lúc ${VnTime(hour, minute).formatHHmm()} (Thứ 2 – Thứ 7)."
                } else {
                    "Đã tắt thông báo nhắc lịch dạy buổi sáng."
                }
            )
        }
    }

    fun togglePeriodReminder(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPeriodReminderEnabled(enabled)
            ReminderAlarmReceiver.rescheduleFromLocal(app, app)
            val lead = uiState.value.preferences.periodReminderLeadMinutes
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = if (enabled) {
                    "Đã bật nhắc trước mỗi tiết dạy $lead phút."
                } else {
                    "Đã tắt nhắc trước mỗi tiết dạy."
                }
            )
        }
    }

    fun updatePeriodReminderLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setPeriodReminderLeadMinutes(minutes)
            ReminderAlarmReceiver.rescheduleFromLocal(app, app)
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = "Đã đổi thời gian nhắc trước tiết thành $minutes phút."
            )
        }
    }

    fun sendTestReminderNotification() {
        val state = uiState.value
        NotificationHelper.showDailyScheduleNotification(
            context = app,
            dayLabel = if (state.isSundayPreview) "Thứ Hai" else state.todayDate.vietnameseWeekday,
            weekNumber = state.currentWeekNumber,
            morning = state.todayMorningPeriods,
            afternoon = state.todayAfternoonPeriods,
            forceWhenEmpty = true
        )
        transientStatus.value = transientStatus.value.copy(
            syncStatusBanner = "Đã gửi thông báo thử nghiệm."
        )
    }

    fun setUseGpsLocation(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setUseGpsLocation(enabled)
            refreshWeatherSilently()
        }
    }

    fun updateAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            prefsRepo.setAppThemeMode(mode)
        }
    }

    fun updateAppAccentPalette(palette: AppAccentPalette) {
        viewModelScope.launch {
            prefsRepo.setAppAccentPalette(palette)
        }
    }

    fun updateAppFontScale(scale: AppFontScale) {
        viewModelScope.launch {
            prefsRepo.setAppFontScale(scale)
        }
    }

    fun updateAppDensityMode(mode: AppDensityMode) {
        viewModelScope.launch {
            prefsRepo.setAppDensityMode(mode)
        }
    }

    fun updateWidgetAppearance(
        transparentMode: Boolean,
        opacity: Float,
        textTheme: WidgetTextTheme,
        cornerStyle: WidgetCornerStyle,
        fontSize: WidgetFontSize,
        showSubject: Boolean
    ) {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = viewModelScope.launch {
            val isTransparent = transparentMode || opacity <= 0.02f
            val normalizedOpacity = if (isTransparent) 0f else opacity.coerceIn(0.05f, 1f)
            prefsRepo.setWidgetAppearance(
                transparentMode = isTransparent,
                opacity = normalizedOpacity,
                textTheme = textTheme,
                cornerStyle = cornerStyle,
                fontSize = fontSize,
                showSubject = showSubject
            )
            WidgetUpdater.updateAllWidgets(app)
        }
    }

    fun refreshAllWidgetsNow() {
        viewModelScope.launch {
            ReminderAlarmReceiver.rescheduleFromLocal(app, app)
            WidgetUpdater.updateAllWidgets(app)
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = "Đã cập nhật lại toàn bộ Widget theo thời gian thực."
            )
        }
    }
}

private data class MetaWeatherPrefs(
    val weather: WeatherSnapshot?,
    val syncMeta: SyncMetadataEntity?,
    val prefs: UserPreferencesState,
    val status: TransientStatus,
    val lessonNotes: Map<String, String>
)
