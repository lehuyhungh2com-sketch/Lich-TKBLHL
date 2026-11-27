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
import com.example.data.local.WidgetTextTheme
import com.example.data.repository.LoginResult
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.LunarDate
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.AfternoonMonthCycle
import com.example.domain.model.Assignment
import com.example.domain.model.CalendarEvent
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.Teacher
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.TimetableSlot
import com.example.domain.model.WeatherSnapshot
import com.example.domain.model.WeekInfo
import com.example.domain.model.WorkloadStatsSummary
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
import kotlinx.coroutines.flow.flowOf
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
    val allAssignments: List<Assignment> = emptyList(),
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
    val selectedCycle: AfternoonMonthCycle = TimetableCalculator.getAfternoonMonthCycle(1),
    val selectedCycleAfternoonPeriods: List<ResolvedPeriod> = emptyList(),
    val allSelectedCycleAfternoonPeriods: List<ResolvedPeriod> = emptyList(),
    val currentWeekPeriods: List<ResolvedPeriod> = emptyList(),
    val changedPeriodCount: Int = 0,
    val changeComparisonLabel: String? = null,
    val workloadStats: WorkloadStatsSummary = WorkloadStatsSummary(),
    val lessonNotes: Map<String, String> = emptyMap(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
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

    private val previousWeekSlotsFlow = effectiveSelectedWeekFlow.flatMapLatest { week ->
        if (week > TimetableCalculator.MIN_WEEK) {
            schoolRepo.observeSlotsForWeek(week - 1)
        } else {
            flowOf(emptyList())
        }
    }

    private val selectedWeekSnapshotFlow = effectiveSelectedWeekFlow.flatMapLatest { week ->
        schoolRepo.observeSlotSnapshotForWeek(week)
    }

    private val selectedCycleSlotsFlow = effectiveSelectedWeekFlow.flatMapLatest { week ->
        val cycleWeeks = TimetableCalculator.getAfternoonMonthCycle(week).weeks
        schoolRepo.observeSlotsForWeeks(cycleWeeks)
    }

    private val baselineSlotsBundleFlow = combine(
        previousWeekSlotsFlow,
        selectedWeekSnapshotFlow,
        selectedCycleSlotsFlow
    ) { prevWeekSlots, syncSnapshotSlots, cycleSlots ->
        WeekAuxSlotsBundle(prevWeekSlots, syncSnapshotSlots, cycleSlots)
    }

    private val workloadStatsFlow = combine(
        catalogFlow,
        effectiveSelectedWeekFlow,
        selectedWeekSlotsFlow,
        schoolRepo.allSlotsFlow,
        prefsRepo.preferencesFlow
    ) { catalog, selectedWeek, selectedWeekSlots, allCachedSlots, prefs ->
        val currentTeacher = prefs.loggedInTeacherId?.let { id ->
            catalog.teachers.firstOrNull { it.id == id }
        }
        if (currentTeacher == null) {
            WorkloadStatsSummary(selectedWeek = selectedWeek)
        } else {
            val homeroomClassId = TimetableCalculator.resolveHomeroomClassId(currentTeacher, catalog.classes)
            val showHomeroom = prefs.showHomeroomClass && !homeroomClassId.isNullOrBlank()
            val filterTeacherId = if (showHomeroom) null else currentTeacher.id
            val filterClassId = if (showHomeroom) homeroomClassId else null
            val selectedWeekInfo = catalog.weeks.firstOrNull { it.weekNumber == selectedWeek }
            val selectedResolved = TimetableCalculator.resolveWeekPeriods(
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
                filterClassId = filterClassId
            )
            TimetableCalculator.calculateWorkloadStats(
                selectedWeek = selectedWeek,
                selectedWeekPeriods = selectedResolved,
                allCachedSlots = allCachedSlots,
                schoolConfig = catalog.config,
                weeks = catalog.weeks,
                deletedSlotKeys = catalog.deletedKeys,
                teachers = catalog.teachers,
                classes = catalog.classes,
                subjects = catalog.subjects,
                assignments = catalog.assignments,
                filterTeacherId = filterTeacherId,
                filterClassId = filterClassId
            )
        }
    }

    private val metaAndWeatherFlow = combine(
        weatherRepo.weatherFlow,
        schoolRepo.syncMetadataFlow,
        prefsRepo.preferencesFlow,
        transientStatus,
        combine(schoolRepo.lessonNotesFlow, schoolRepo.calendarEventsFlow) { notes, events ->
            notes to events
        }
    ) { weather, syncMeta, prefs, status, (notes, events) ->
        MetaWeatherPrefs(weather, syncMeta, prefs, status, notes, events)
    }

    private val slotsAndStatsBundleFlow = combine(
        currentWeekSlotsFlow,
        selectedWeekSlotsFlow,
        baselineSlotsBundleFlow,
        workloadStatsFlow,
        metaAndWeatherFlow
    ) { curSlots, selSlots, auxSlots, workloadStats, mwp ->
        SlotsAndMetaBundle(
            currentWeekSlots = curSlots,
            selectedWeekSlots = selSlots,
            previousWeekSlots = auxSlots.previousWeekSlots,
            snapshotWeekSlots = auxSlots.snapshotWeekSlots,
            cycleSlots = auxSlots.cycleSlots,
            workloadStats = workloadStats,
            meta = mwp
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        catalogFlow,
        clockTickFlow,
        currentWeekFlow,
        effectiveSelectedWeekFlow,
        slotsAndStatsBundleFlow
    ) { catalog, (today, now), currentWeek, selectedWeek, bundle ->
        val currentWeekSlots = bundle.currentWeekSlots
        val selectedWeekSlots = bundle.selectedWeekSlots
        val mwp = bundle.meta
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

        val selectedWeekRaw = if (currentTeacher != null) {
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

        // Compare against previous sync snapshot (same week) first if present and non-empty,
        // else compare against previous week's cached slots (week - 1) with zero Firestore reads.
        val (changedSlotIds, comparisonLabel) = if (currentTeacher != null && selectedWeekRaw.isNotEmpty()) {
            val snapResolved = if (bundle.snapshotWeekSlots.isNotEmpty()) {
                TimetableCalculator.resolveWeekPeriods(
                    weekNumber = selectedWeek,
                    rawSlots = bundle.snapshotWeekSlots,
                    schoolConfig = catalog.config,
                    weekInfo = selectedWeekInfo,
                    deletedSlotKeys = catalog.deletedKeys,
                    teachers = catalog.teachers,
                    classes = catalog.classes,
                    subjects = catalog.subjects,
                    assignments = catalog.assignments,
                    filterTeacherId = filterTeacherId,
                    filterClassId = filterClassId
                )
            } else {
                emptyList()
            }
            val snapDiffs = TimetableCalculator.detectChangedSlotIds(selectedWeekRaw, snapResolved)
            if (snapDiffs.isNotEmpty()) {
                snapDiffs to "so với bản đồng bộ trước"
            } else if (selectedWeek > TimetableCalculator.MIN_WEEK && bundle.previousWeekSlots.isNotEmpty()) {
                val prevWeekNum = selectedWeek - 1
                val prevWeekInfo = catalog.weeks.firstOrNull { it.weekNumber == prevWeekNum }
                val prevWeekResolved = TimetableCalculator.resolveWeekPeriods(
                    weekNumber = prevWeekNum,
                    rawSlots = bundle.previousWeekSlots,
                    schoolConfig = catalog.config,
                    weekInfo = prevWeekInfo,
                    deletedSlotKeys = catalog.deletedKeys,
                    teachers = catalog.teachers,
                    classes = catalog.classes,
                    subjects = catalog.subjects,
                    assignments = catalog.assignments,
                    filterTeacherId = filterTeacherId,
                    filterClassId = filterClassId
                )
                val prevDiffs = TimetableCalculator.detectChangedSlotIds(selectedWeekRaw, prevWeekResolved)
                if (prevDiffs.isNotEmpty()) {
                    prevDiffs to "so với Tuần $prevWeekNum"
                } else {
                    emptySet<String>() to null
                }
            } else {
                emptySet<String>() to null
            }
        } else {
            emptySet<String>() to null
        }

        val selectedWeekResolved = if (changedSlotIds.isEmpty()) {
            selectedWeekRaw
        } else {
            selectedWeekRaw.map { if (it.slotId in changedSlotIds) it.copy(isChanged = true) else it }
        }

        val effectiveCurrentWeekResolved = if (selectedWeek == currentWeek && changedSlotIds.isNotEmpty()) {
            currentWeekResolved.map { if (it.slotId in changedSlotIds) it.copy(isChanged = true) else it }
        } else {
            currentWeekResolved
        }

        val selectedCycle = TimetableCalculator.getAfternoonMonthCycle(selectedWeek)
        val cycleSlotsByWeek = bundle.cycleSlots.groupBy { it.week }
        val allSelectedCycleAfternoonResolved = if (currentTeacher != null) {
            selectedCycle.weeks.flatMap { wNum ->
                val wInfo = catalog.weeks.firstOrNull { it.weekNumber == wNum }
                TimetableCalculator.resolveWeekPeriods(
                    weekNumber = wNum,
                    rawSlots = cycleSlotsByWeek[wNum].orEmpty(),
                    schoolConfig = catalog.config,
                    weekInfo = wInfo,
                    deletedSlotKeys = catalog.deletedKeys,
                    teachers = catalog.teachers,
                    classes = catalog.classes,
                    subjects = catalog.subjects,
                    assignments = catalog.assignments,
                    filterTeacherId = null,
                    filterClassId = null,
                    todayDate = if (wNum == currentWeek && !isSundayPreview) today else null,
                    nowTime = if (wNum == currentWeek && !isSundayPreview) now else null
                ).filter { it.isAfternoon }
            }
        } else {
            emptyList()
        }
        val selectedCycleAfternoonResolved = if (currentTeacher != null) {
            selectedCycle.weeks.flatMap { wNum ->
                val wInfo = catalog.weeks.firstOrNull { it.weekNumber == wNum }
                TimetableCalculator.resolveWeekPeriods(
                    weekNumber = wNum,
                    rawSlots = cycleSlotsByWeek[wNum].orEmpty(),
                    schoolConfig = catalog.config,
                    weekInfo = wInfo,
                    deletedSlotKeys = catalog.deletedKeys,
                    teachers = catalog.teachers,
                    classes = catalog.classes,
                    subjects = catalog.subjects,
                    assignments = catalog.assignments,
                    filterTeacherId = filterTeacherId,
                    filterClassId = filterClassId,
                    todayDate = if (wNum == currentWeek && !isSundayPreview) today else null,
                    nowTime = if (wNum == currentWeek && !isSundayPreview) now else null
                ).filter { it.isAfternoon }
            }.map { p ->
                if (p.week == selectedWeek && p.slotId in changedSlotIds) p.copy(isChanged = true) else p
            }
        } else {
            emptyList()
        }

        val todayPeriods = effectiveCurrentWeekResolved.filter { it.day == targetDayCode }
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
            allAssignments = catalog.assignments,
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
            selectedCycle = selectedCycle,
            selectedCycleAfternoonPeriods = selectedCycleAfternoonResolved,
            allSelectedCycleAfternoonPeriods = allSelectedCycleAfternoonResolved,
            currentWeekPeriods = effectiveCurrentWeekResolved,
            changedPeriodCount = changedSlotIds.size,
            changeComparisonLabel = comparisonLabel,
            workloadStats = bundle.workloadStats,
            lessonNotes = mwp.lessonNotes,
            calendarEvents = mwp.calendarEvents,
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

    fun syncNow(force: Boolean = false) {
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

    fun saveCalendarEvent(event: CalendarEvent) {
        viewModelScope.launch {
            schoolRepo.saveCalendarEvent(event)
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = if (event.id == 0L) "Đã thêm sự kiện vào lịch cá nhân." else "Đã cập nhật sự kiện."
            )
        }
    }

    fun deleteCalendarEvent(eventId: Long) {
        viewModelScope.launch {
            schoolRepo.deleteCalendarEvent(eventId)
            transientStatus.value = transientStatus.value.copy(
                syncStatusBanner = "Đã xóa sự kiện khỏi lịch."
            )
        }
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
        fontScale: Float,
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
                fontScale = fontScale,
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
    val lessonNotes: Map<String, String>,
    val calendarEvents: List<CalendarEvent>
)

private data class WeekAuxSlotsBundle(
    val previousWeekSlots: List<TimetableSlot>,
    val snapshotWeekSlots: List<TimetableSlot>,
    val cycleSlots: List<TimetableSlot>
)

private data class SlotsAndMetaBundle(
    val currentWeekSlots: List<TimetableSlot>,
    val selectedWeekSlots: List<TimetableSlot>,
    val previousWeekSlots: List<TimetableSlot>,
    val snapshotWeekSlots: List<TimetableSlot>,
    val cycleSlots: List<TimetableSlot>,
    val workloadStats: WorkloadStatsSummary,
    val meta: MetaWeatherPrefs
)
