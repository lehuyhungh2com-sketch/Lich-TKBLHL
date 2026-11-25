package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        runCatching { MainCalendarTimetableWidget().updateAll(context) }
        runCatching { CalendarWeatherWidget().updateAll(context) }
        runCatching { TimetableOnlyWidget().updateAll(context) }
    }
}
