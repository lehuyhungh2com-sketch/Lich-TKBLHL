package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.LhlTimetableApp
import com.example.widget.WidgetUpdater

/**
 * Lightweight offline periodic worker (every 15 minutes) ensuring all Home Screen widgets
 * stay synchronized with the real-time teaching period status even on devices with strict
 * battery optimization or when SCHEDULE_EXACT_ALARM is not granted.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LhlTimetableApp ?: return Result.success()
        runCatching { ReminderAlarmReceiver.rescheduleFromLocal(applicationContext, app) }
        WidgetUpdater.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
