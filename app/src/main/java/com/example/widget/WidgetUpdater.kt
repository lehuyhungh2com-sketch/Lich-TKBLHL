package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WidgetUpdater {
    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick.asStateFlow()

    suspend fun updateAllWidgets(context: Context) {
        _refreshTick.value = System.currentTimeMillis()
        runCatching { MainCalendarTimetableWidget().updateAll(context) }
        runCatching { CalendarWeatherWidget().updateAll(context) }
        runCatching { TimetableOnlyWidget().updateAll(context) }
        runCatching { WeatherForecastTimetableWidget().updateAll(context) }
    }
}
