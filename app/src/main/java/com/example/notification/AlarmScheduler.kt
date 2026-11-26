package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.domain.lunar.VIETNAM_TZ
import com.example.domain.model.ResolvedPeriod
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AlarmScheduler {

    const val ACTION_DAILY_REMINDER = "com.example.action.DAILY_REMINDER"
    const val ACTION_PRE_SYNC = "com.example.action.PRE_SYNC"
    const val ACTION_PERIOD_REMINDER = "com.example.action.PERIOD_REMINDER"
    const val ACTION_WIDGET_PERIOD_TICK = "com.example.action.WIDGET_PERIOD_TICK"
    const val ACTION_MIDNIGHT_ROLLOVER = "com.example.action.MIDNIGHT_ROLLOVER"

    const val EXTRA_PERIOD_NUM = "extra_period_num"
    const val EXTRA_CLASS_NAME = "extra_class_name"
    const val EXTRA_SUBJECT_NAME = "extra_subject_name"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_ROOM = "extra_room"
    const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"

    private const val REQ_DAILY_REMINDER = 1001
    private const val REQ_PRE_SYNC = 1002
    private const val REQ_MIDNIGHT_ROLLOVER = 1003
    private const val REQ_PERIOD_BASE = 2000
    private const val REQ_WIDGET_TICK_BASE = 3000
    private const val WORK_PERIODIC_SYNC = "lhl_periodic_background_sync"
    private const val WORK_PERIODIC_WIDGET_TICK = "lhl_periodic_widget_tick"

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleAll(
        context: Context,
        dailyReminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        periodReminderEnabled: Boolean,
        periodReminderLeadMinutes: Int = 10,
        todayPeriods: List<ResolvedPeriod>
    ) {
        if (dailyReminderEnabled) {
            scheduleDailyReminder(context, reminderHour, reminderMinute)
            // Pre-sync 15 minutes before reminder (e.g., 05:45 AM for 06:00 AM reminder)
            val preMinutesTotal = ((reminderHour * 60 + reminderMinute) - 15 + 24 * 60) % (24 * 60)
            schedulePreSyncAlarm(context, preMinutesTotal / 60, preMinutesTotal % 60)
        } else {
            cancelAlarm(context, REQ_DAILY_REMINDER, ACTION_DAILY_REMINDER)
            schedulePreSyncAlarm(context, 5, 45)
        }

        if (periodReminderEnabled) {
            scheduleTodayPeriodReminders(context, todayPeriods, periodReminderLeadMinutes)
        } else {
            cancelTodayPeriodReminders(context)
        }

        scheduleMidnightRollover(context)
        scheduleTodayWidgetPeriodTicks(context, todayPeriods)
        scheduleWorkManagerBackup(context)
        schedulePeriodicWidgetRefresh(context)
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val triggerAt = nextTriggerMillis(hour, minute)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        setExactOrInexactAlarm(context, REQ_DAILY_REMINDER, triggerAt, intent)
    }

    fun schedulePreSyncAlarm(context: Context, hour: Int, minute: Int) {
        val triggerAt = nextTriggerMillis(hour, minute)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_PRE_SYNC
        }
        setExactOrInexactAlarm(context, REQ_PRE_SYNC, triggerAt, intent)
    }

    fun scheduleMidnightRollover(context: Context) {
        val triggerAt = nextTriggerMillis(0, 1)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_ROLLOVER
        }
        setExactOrInexactAlarm(context, REQ_MIDNIGHT_ROLLOVER, triggerAt, intent)
    }

    fun scheduleTodayPeriodReminders(
        context: Context,
        todayPeriods: List<ResolvedPeriod>,
        leadMinutes: Int = 10
    ) {
        cancelTodayPeriodReminders(context)
        val nowMillis = System.currentTimeMillis()
        val safeLead = leadMinutes.coerceIn(5, 30)

        todayPeriods.forEachIndexed { idx, period ->
            if (idx >= 12) return@forEachIndexed
            val notifyTime = period.startTime.plusMinutes(-safeLead)
            val cal = Calendar.getInstance(VIETNAM_TZ).apply {
                set(Calendar.HOUR_OF_DAY, notifyTime.hour)
                set(Calendar.MINUTE, notifyTime.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > nowMillis) {
                val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ACTION_PERIOD_REMINDER
                    putExtra(EXTRA_PERIOD_NUM, period.period)
                    putExtra(EXTRA_CLASS_NAME, period.className)
                    putExtra(EXTRA_SUBJECT_NAME, period.subjectName)
                    putExtra(EXTRA_START_TIME, period.startTime.formatHHmm())
                    putExtra(EXTRA_ROOM, period.room)
                    putExtra(EXTRA_LEAD_MINUTES, safeLead)
                }
                setExactOrInexactAlarm(context, REQ_PERIOD_BASE + idx, cal.timeInMillis, intent)
            }
        }
    }

    fun cancelTodayPeriodReminders(context: Context) {
        for (i in 0 until 12) {
            cancelAlarm(context, REQ_PERIOD_BASE + i, ACTION_PERIOD_REMINDER)
        }
    }

    /**
     * Schedules silent widget refresh ticks at the exact start, end, and 10-min pre-start
     * boundaries of today's teaching periods so live "Đang dạy" / "Tiếp theo" / "Đã dạy"
     * states update cleanly in real time.
     */
    fun scheduleTodayWidgetPeriodTicks(context: Context, todayPeriods: List<ResolvedPeriod>) {
        for (i in 0 until 30) {
            cancelAlarm(context, REQ_WIDGET_TICK_BASE + i, ACTION_WIDGET_PERIOD_TICK)
        }
        if (todayPeriods.isEmpty()) return

        val nowMillis = System.currentTimeMillis()
        val boundaryTimes = todayPeriods
            .flatMap { listOf(it.startTime.plusMinutes(-10), it.startTime, it.endTime) }
            .distinctBy { it.totalMinutes }
            .sortedBy { it.totalMinutes }

        var slotIdx = 0
        boundaryTimes.forEach { t ->
            if (slotIdx >= 30) return@forEach
            val cal = Calendar.getInstance(VIETNAM_TZ).apply {
                set(Calendar.HOUR_OF_DAY, t.hour)
                set(Calendar.MINUTE, t.minute)
                set(Calendar.SECOND, 2) // +2s so nowTime >= boundary cleanly
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > nowMillis) {
                val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ACTION_WIDGET_PERIOD_TICK
                }
                setExactOrInexactAlarm(context, REQ_WIDGET_TICK_BASE + slotIdx, cal.timeInMillis, intent)
                slotIdx++
            }
        }
    }

    private fun scheduleWorkManagerBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<DailySyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun schedulePeriodicWidgetRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC_WIDGET_TICK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance(VIETNAM_TZ)
        val target = Calendar.getInstance(VIETNAM_TZ).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis + 5_000L) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis
    }

    private fun setExactOrInexactAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        intent: Intent
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        }
    }

    private fun cancelAlarm(context: Context, requestCode: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = action
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }
}
