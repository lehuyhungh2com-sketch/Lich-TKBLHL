package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.LhlTimetableApp
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.TimetableCalculator
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? LhlTimetableApp
        if (app == null) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_PRE_SYNC -> {
                        app.schoolRepository.syncIncremental(targetWeek = null, force = false)
                        app.weatherRepository.refreshWeather()
                        WidgetUpdater.updateAllWidgets(context)
                        rescheduleFromLocal(context, app)
                    }

                    AlarmScheduler.ACTION_DAILY_REMINDER -> {
                        handleDailyReminder(context, app)
                        rescheduleFromLocal(context, app)
                    }

                    AlarmScheduler.ACTION_PERIOD_REMINDER -> {
                        val period = intent.getIntExtra(AlarmScheduler.EXTRA_PERIOD_NUM, 1)
                        val className = intent.getStringExtra(AlarmScheduler.EXTRA_CLASS_NAME).orEmpty()
                        val subjectName = intent.getStringExtra(AlarmScheduler.EXTRA_SUBJECT_NAME).orEmpty()
                        val startTime = intent.getStringExtra(AlarmScheduler.EXTRA_START_TIME).orEmpty()
                        val room = intent.getStringExtra(AlarmScheduler.EXTRA_ROOM).orEmpty()
                        val leadMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_LEAD_MINUTES, 10)
                        NotificationHelper.showBeforePeriodNotification(
                            context = context,
                            periodNumber = period,
                            className = className,
                            subjectName = subjectName,
                            startTimeText = startTime,
                            room = room,
                            leadMinutes = leadMinutes
                        )
                        WidgetUpdater.updateAllWidgets(context)
                    }

                    AlarmScheduler.ACTION_WIDGET_PERIOD_TICK -> {
                        WidgetUpdater.updateAllWidgets(context)
                    }

                    AlarmScheduler.ACTION_MIDNIGHT_ROLLOVER -> {
                        rescheduleFromLocal(context, app)
                        WidgetUpdater.updateAllWidgets(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleDailyReminder(context: Context, app: LhlTimetableApp) {
        val snapshot = app.schoolRepository.getLocalSnapshotForWeek()
        val teacher = snapshot.loggedInTeacher ?: return
        val prefs = snapshot.preferences
        if (!prefs.dailyReminderEnabled) return

        val today = VnDate.today()
        if (today.isSunday) return

        val weekInfo = snapshot.weeks.firstOrNull { it.weekNumber == snapshot.weekNumber }
        val weekPeriods = TimetableCalculator.resolveWeekPeriods(
            weekNumber = snapshot.weekNumber,
            rawSlots = snapshot.slots,
            schoolConfig = snapshot.schoolConfig,
            weekInfo = weekInfo,
            deletedSlotKeys = snapshot.deletedSlotKeys,
            teachers = snapshot.teachers,
            classes = snapshot.classes,
            subjects = snapshot.subjects,
            assignments = snapshot.assignments,
            filterTeacherId = teacher.id,
            todayDate = today,
            nowTime = VnTime.now()
        )
        val todayPeriods = weekPeriods.filter { it.day == today.shortDayCode }
        val morning = todayPeriods.filter { !it.isAfternoon }
        val afternoon = todayPeriods.filter { it.isAfternoon }

        if (morning.isNotEmpty() || afternoon.isNotEmpty()) {
            NotificationHelper.showDailyScheduleNotification(
                context = context,
                dayLabel = today.vietnameseWeekday,
                weekNumber = snapshot.weekNumber,
                morning = morning,
                afternoon = afternoon
            )
        }
        WidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        suspend fun rescheduleFromLocal(context: Context, app: LhlTimetableApp) {
            val snapshot = app.schoolRepository.getLocalSnapshotForWeek()
            val prefs = snapshot.preferences
            val today = VnDate.today()
            val teacher = snapshot.loggedInTeacher
            val todayPeriods = if (teacher != null && !today.isSunday) {
                val weekInfo = snapshot.weeks.firstOrNull { it.weekNumber == snapshot.weekNumber }
                TimetableCalculator.resolveWeekPeriods(
                    weekNumber = snapshot.weekNumber,
                    rawSlots = snapshot.slots,
                    schoolConfig = snapshot.schoolConfig,
                    weekInfo = weekInfo,
                    deletedSlotKeys = snapshot.deletedSlotKeys,
                    teachers = snapshot.teachers,
                    classes = snapshot.classes,
                    subjects = snapshot.subjects,
                    assignments = snapshot.assignments,
                    filterTeacherId = teacher.id,
                    todayDate = today,
                    nowTime = VnTime.now()
                ).filter { it.day == today.shortDayCode }
            } else {
                emptyList()
            }

            AlarmScheduler.scheduleAll(
                context = context,
                dailyReminderEnabled = prefs.dailyReminderEnabled,
                reminderHour = prefs.reminderHour,
                reminderMinute = prefs.reminderMinute,
                periodReminderEnabled = prefs.periodReminderEnabled,
                periodReminderLeadMinutes = prefs.periodReminderLeadMinutes,
                todayPeriods = todayPeriods
            )
        }
    }
}
