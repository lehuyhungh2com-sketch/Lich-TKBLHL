package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.remote.FirestoreReadOnlyClient
import com.example.data.remote.OpenMeteoApi
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.SchoolRepository
import com.example.data.repository.WeatherRepository
import com.example.notification.NotificationHelper
import com.example.notification.ReminderAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LhlTimetableApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    lateinit var database: AppDatabase
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var firestoreClient: FirestoreReadOnlyClient
        private set
    lateinit var schoolRepository: SchoolRepository
        private set
    lateinit var weatherRepository: WeatherRepository
        private set
    lateinit var appUpdateRepository: AppUpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = AppPreferences(this)
        firestoreClient = FirestoreReadOnlyClient(this)
        schoolRepository = SchoolRepository(
            dao = database.schoolDao(),
            firestore = firestoreClient,
            preferences = preferences
        )
        weatherRepository = WeatherRepository(
            context = this,
            dao = database.schoolDao(),
            api = OpenMeteoApi.create(),
            preferences = preferences
        )
        appUpdateRepository = AppUpdateRepository(context = this)

        NotificationHelper.createChannels(this)
        appScope.launch {
            runCatching {
                ReminderAlarmReceiver.rescheduleFromLocal(this@LhlTimetableApp, this@LhlTimetableApp)
            }
            runCatching {
                com.example.widget.WidgetUpdater.updateAllWidgets(this@LhlTimetableApp)
            }
        }
    }
}
