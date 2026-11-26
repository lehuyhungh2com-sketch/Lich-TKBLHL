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

enum class AppAccentPalette(val key: String, val label: String) {
    SUNSET_ORANGE("orange", "Cam ấm"),
    EMERALD("emerald", "Xanh ngọc"),
    OCEAN_BLUE("blue", "Xanh dương"),
    ROYAL_INDIGO("indigo", "Tím học đường"),
    DYNAMIC("dynamic", "Material You");

    companion object {
        fun fromKey(key: String?): AppAccentPalette =
            entries.firstOrNull { it.key == key } ?: SUNSET_ORANGE
    }
}

enum class AppFontScale(val key: String, val label: String, val multiplier: Float) {
    NORMAL("normal", "Vừa", 1.0f),
    LARGE("large", "Lớn", 1.12f),
    XLARGE("xlarge", "Rất lớn", 1.24f);

    companion object {
        fun fromKey(key: String?): AppFontScale =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

enum class AppDensityMode(val key: String, val label: String) {
    COMFORTABLE("comfortable", "Thoáng"),
    COMPACT("compact", "Cô đọng");

    companion object {
        fun fromKey(key: String?): AppDensityMode =
            entries.firstOrNull { it.key == key } ?: COMFORTABLE
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

enum class WidgetCornerStyle(val key: String, val label: String, val radiusDp: Int) {
    SHARP("sharp", "Gọn", 12),
    ROUNDED("rounded", "Bo vừa", 20),
    PILL("pill", "Bo tròn", 28);

    companion object {
        fun fromKey(key: String?): WidgetCornerStyle =
            entries.firstOrNull { it.key == key } ?: ROUNDED
    }
}

enum class WidgetFontSize(val key: String, val label: String, val deltaSp: Float) {
    SMALL("small", "Nhỏ", -1f),
    MEDIUM("medium", "Vừa", 0f),
    LARGE("large", "Lớn", 1.5f);

    companion object {
        fun fromKey(key: String?): WidgetFontSize =
            entries.firstOrNull { it.key == key } ?: MEDIUM
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
    val periodReminderLeadMinutes: Int = 10,
    val useGpsLocation: Boolean = false,
    val lastLatitude: Double = DEFAULT_LAT,
    val lastLongitude: Double = DEFAULT_LON,
    val lastLocationName: String = DEFAULT_LOCATION_NAME,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appAccentPalette: AppAccentPalette = AppAccentPalette.SUNSET_ORANGE,
    val appFontScale: AppFontScale = AppFontScale.NORMAL,
    val appDensityMode: AppDensityMode = AppDensityMode.COMFORTABLE,
    val widgetTransparentMode: Boolean = true,
    val widgetOpacity: Float = 0.0f,
    val widgetTextTheme: WidgetTextTheme = WidgetTextTheme.AUTO,
    val widgetCornerStyle: WidgetCornerStyle = WidgetCornerStyle.ROUNDED,
    val widgetFontSize: WidgetFontSize = WidgetFontSize.MEDIUM,
    val widgetShowSubject: Boolean = true
) {
    val isLoggedIn: Boolean get() = !loggedInTeacherId.isNullOrBlank()
    val isCompactDensity: Boolean get() = appDensityMode == AppDensityMode.COMPACT

    companion object {
        const val DEFAULT_LAT = 19.9333
        const val DEFAULT_LON = 105.9000
        const val DEFAULT_LOCATION_NAME = "Hậu Lộc"
        val LEAD_MINUTE_OPTIONS = listOf(5, 10, 15)
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
        val PERIOD_REMINDER_LEAD_MINUTES = intPreferencesKey("period_reminder_lead_minutes")
        val USE_GPS_LOCATION = booleanPreferencesKey("use_gps_location")
        val LAST_LAT = doublePreferencesKey("last_latitude")
        val LAST_LON = doublePreferencesKey("last_longitude")
        val LAST_LOC_NAME = stringPreferencesKey("last_location_name")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val APP_ACCENT_PALETTE = stringPreferencesKey("app_accent_palette")
        val APP_FONT_SCALE = stringPreferencesKey("app_font_scale")
        val APP_DENSITY_MODE = stringPreferencesKey("app_density_mode")
        val WIDGET_TRANSPARENT_MODE = booleanPreferencesKey("widget_transparent_mode")
        val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
        val WIDGET_TEXT_THEME = stringPreferencesKey("widget_text_theme")
        val WIDGET_CORNER_STYLE = stringPreferencesKey("widget_corner_style")
        val WIDGET_FONT_SIZE = stringPreferencesKey("widget_font_size")
        val WIDGET_SHOW_SUBJECT = booleanPreferencesKey("widget_show_subject")
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
            periodReminderLeadMinutes = (prefs[Keys.PERIOD_REMINDER_LEAD_MINUTES] ?: 10).coerceIn(5, 30),
            useGpsLocation = prefs[Keys.USE_GPS_LOCATION] ?: false,
            lastLatitude = prefs[Keys.LAST_LAT] ?: UserPreferencesState.DEFAULT_LAT,
            lastLongitude = prefs[Keys.LAST_LON] ?: UserPreferencesState.DEFAULT_LON,
            lastLocationName = prefs[Keys.LAST_LOC_NAME] ?: UserPreferencesState.DEFAULT_LOCATION_NAME,
            appThemeMode = AppThemeMode.fromKey(prefs[Keys.APP_THEME_MODE]),
            appAccentPalette = AppAccentPalette.fromKey(prefs[Keys.APP_ACCENT_PALETTE]),
            appFontScale = AppFontScale.fromKey(prefs[Keys.APP_FONT_SCALE]),
            appDensityMode = AppDensityMode.fromKey(prefs[Keys.APP_DENSITY_MODE]),
            widgetTransparentMode = prefs[Keys.WIDGET_TRANSPARENT_MODE] ?: true,
            widgetOpacity = prefs[Keys.WIDGET_OPACITY] ?: 0.0f,
            widgetTextTheme = WidgetTextTheme.fromKey(prefs[Keys.WIDGET_TEXT_THEME] ?: WidgetTextTheme.AUTO.key),
            widgetCornerStyle = WidgetCornerStyle.fromKey(prefs[Keys.WIDGET_CORNER_STYLE]),
            widgetFontSize = WidgetFontSize.fromKey(prefs[Keys.WIDGET_FONT_SIZE]),
            widgetShowSubject = prefs[Keys.WIDGET_SHOW_SUBJECT] ?: true
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

    suspend fun setPeriodReminderLeadMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PERIOD_REMINDER_LEAD_MINUTES] = minutes.coerceIn(5, 30)
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

    suspend fun setAppAccentPalette(palette: AppAccentPalette) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_ACCENT_PALETTE] = palette.key
        }
    }

    suspend fun setAppFontScale(scale: AppFontScale) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_FONT_SCALE] = scale.key
        }
    }

    suspend fun setAppDensityMode(mode: AppDensityMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_DENSITY_MODE] = mode.key
        }
    }

    suspend fun setWidgetAppearance(
        transparentMode: Boolean,
        opacity: Float,
        textTheme: WidgetTextTheme,
        cornerStyle: WidgetCornerStyle,
        fontSize: WidgetFontSize,
        showSubject: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_TRANSPARENT_MODE] = transparentMode
            prefs[Keys.WIDGET_OPACITY] = opacity.coerceIn(0f, 1f)
            prefs[Keys.WIDGET_TEXT_THEME] = textTheme.key
            prefs[Keys.WIDGET_CORNER_STYLE] = cornerStyle.key
            prefs[Keys.WIDGET_FONT_SIZE] = fontSize.key
            prefs[Keys.WIDGET_SHOW_SUBJECT] = showSubject
        }
    }
}
