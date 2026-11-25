package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lhl_app_prefs")

enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Hệ thống"),
    LIGHT("light", "Sáng"),
    DARK("dark", "Tối");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class WidgetTextTheme(val key: String, val label: String) {
    AUTO("auto", "Tự động"),
    DARK("dark", "Chữ tối"),
    LIGHT("light", "Chữ sáng");

    companion object {
        fun fromKey(key: String?): WidgetTextTheme =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}

data class UserPreferencesState(
    val loggedInTeacherId: String? = null,
    val loggedInEmail: String? = null,
    val showHomeroomClass: Boolean = false,
    val dailyReminderEnabled: Boolean = true,
    val reminderHour: Int = 6,
    val reminderMinute: Int = 0,
    val periodReminderEnabled: Boolean = false,
    val useGpsLocation: Boolean = false,
    val lastLatitude: Double = DEFAULT_LAT,
    val lastLongitude: Double = DEFAULT_LON,
    val lastLocationName: String = DEFAULT_LOCATION_NAME,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val widgetTransparentMode: Boolean = true,
    val widgetOpacity: Float = 0.0f,
    val widgetTextTheme: WidgetTextTheme = WidgetTextTheme.AUTO
) {
    val isLoggedIn: Boolean get() = !loggedInTeacherId.isNullOrBlank()

    companion object {
        const val DEFAULT_LAT = 19.9333
        const val DEFAULT_LON = 105.9000
        const val DEFAULT_LOCATION_NAME = "Hậu Lộc"
    }
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val TEACHER_ID = stringPreferencesKey("logged_in_teacher_id")
        val TEACHER_EMAIL = stringPreferencesKey("logged_in_email")
        val SHOW_HOMEROOM = booleanPreferencesKey("show_homeroom_class")
        val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val PERIOD_REMINDER_ENABLED = booleanPreferencesKey("period_reminder_enabled")
        val USE_GPS_LOCATION = booleanPreferencesKey("use_gps_location")
        val LAST_LAT = doublePreferencesKey("last_latitude")
        val LAST_LON = doublePreferencesKey("last_longitude")
        val LAST_LOC_NAME = stringPreferencesKey("last_location_name")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val WIDGET_TRANSPARENT_MODE = booleanPreferencesKey("widget_transparent_mode")
        val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
        val WIDGET_TEXT_THEME = stringPreferencesKey("widget_text_theme")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        UserPreferencesState(
            loggedInTeacherId = prefs[Keys.TEACHER_ID],
            loggedInEmail = prefs[Keys.TEACHER_EMAIL],
            showHomeroomClass = prefs[Keys.SHOW_HOMEROOM] ?: false,
            dailyReminderEnabled = prefs[Keys.DAILY_REMINDER_ENABLED] ?: true,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 6,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            periodReminderEnabled = prefs[Keys.PERIOD_REMINDER_ENABLED] ?: false,
            useGpsLocation = prefs[Keys.USE_GPS_LOCATION] ?: false,
            lastLatitude = prefs[Keys.LAST_LAT] ?: UserPreferencesState.DEFAULT_LAT,
            lastLongitude = prefs[Keys.LAST_LON] ?: UserPreferencesState.DEFAULT_LON,
            lastLocationName = prefs[Keys.LAST_LOC_NAME] ?: UserPreferencesState.DEFAULT_LOCATION_NAME,
            appThemeMode = AppThemeMode.fromKey(prefs[Keys.APP_THEME_MODE]),
            widgetTransparentMode = prefs[Keys.WIDGET_TRANSPARENT_MODE] ?: true,
            widgetOpacity = prefs[Keys.WIDGET_OPACITY] ?: 0.0f,
            widgetTextTheme = WidgetTextTheme.fromKey(prefs[Keys.WIDGET_TEXT_THEME] ?: WidgetTextTheme.AUTO.key)
        )
    }

    suspend fun getSnapshot(): UserPreferencesState = preferencesFlow.first()

    suspend fun saveLoginSession(teacherId: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEACHER_ID] = teacherId
            prefs[Keys.TEACHER_EMAIL] = email
            prefs[Keys.SHOW_HOMEROOM] = false
        }
    }

    suspend fun clearLoginSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TEACHER_ID)
            prefs.remove(Keys.TEACHER_EMAIL)
            prefs[Keys.SHOW_HOMEROOM] = false
        }
    }

    suspend fun setShowHomeroomClass(showHomeroom: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_HOMEROOM] = showHomeroom
        }
    }

    suspend fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_REMINDER_ENABLED] = enabled
            prefs[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setPeriodReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PERIOD_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setUseGpsLocation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_GPS_LOCATION] = enabled
            if (!enabled) {
                prefs[Keys.LAST_LAT] = UserPreferencesState.DEFAULT_LAT
                prefs[Keys.LAST_LON] = UserPreferencesState.DEFAULT_LON
                prefs[Keys.LAST_LOC_NAME] = UserPreferencesState.DEFAULT_LOCATION_NAME
            }
        }
    }

    suspend fun saveResolvedLocation(lat: Double, lon: Double, locationName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_LAT] = lat
            prefs[Keys.LAST_LON] = lon
            prefs[Keys.LAST_LOC_NAME] = locationName
        }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_THEME_MODE] = mode.key
        }
    }

    suspend fun setWidgetAppearance(
        transparentMode: Boolean,
        opacity: Float,
        textTheme: WidgetTextTheme
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_TRANSPARENT_MODE] = transparentMode
            prefs[Keys.WIDGET_OPACITY] = opacity.coerceIn(0f, 1f)
            prefs[Keys.WIDGET_TEXT_THEME] = textTheme.key
        }
    }
}
