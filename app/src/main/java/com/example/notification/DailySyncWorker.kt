package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.LhlTimetableApp
import com.example.widget.WidgetUpdater

class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LhlTimetableApp ?: return Result.success()
        val prefs = app.preferences.getSnapshot()
        if (prefs.isLoggedIn) {
            app.schoolRepository.syncIncremental(targetWeek = null, force = false)
        }
        app.weatherRepository.refreshWeather()
        ReminderAlarmReceiver.rescheduleFromLocal(applicationContext, app)
        WidgetUpdater.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
