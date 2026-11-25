package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.LhlTimetableApp
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? LhlTimetableApp
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderAlarmReceiver.rescheduleFromLocal(context, app)
                WidgetUpdater.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
