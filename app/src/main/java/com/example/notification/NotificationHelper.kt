package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator

object NotificationHelper {

    const val CHANNEL_DAILY = "lhl_daily_schedule_reminder"
    const val CHANNEL_PERIOD = "lhl_period_reminder"
    private const val DAILY_NOTIFICATION_ID = 6001
    private const val PERIOD_NOTIFICATION_BASE_ID = 7000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val dailyChannel = NotificationChannel(
                CHANNEL_DAILY,
                context.getString(R.string.notification_channel_daily_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_daily_desc)
            }

            val periodChannel = NotificationChannel(
                CHANNEL_PERIOD,
                context.getString(R.string.notification_channel_period_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_period_desc)
            }

            manager.createNotificationChannel(dailyChannel)
            manager.createNotificationChannel(periodChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showDailyScheduleNotification(
        context: Context,
        dayLabel: String,
        weekNumber: Int,
        morning: List<ResolvedPeriod>,
        afternoon: List<ResolvedPeriod>,
        forceWhenEmpty: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        if (!forceWhenEmpty && morning.isEmpty() && afternoon.isEmpty()) return

        createChannels(context)
        val summary = TimetableCalculator.formatNotificationSummary(dayLabel, morning, afternoon)
        val title = "Lịch dạy hôm nay ($dayLabel • Tuần $weekNumber)"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TIMETABLE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification_schedule)
            .setContentTitle(title)
            .setContentText(summary.replace("\n", " | "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(DAILY_NOTIFICATION_ID, notification)
        }
    }

    fun showBeforePeriodNotification(
        context: Context,
        periodNumber: Int,
        className: String,
        subjectName: String,
        startTimeText: String,
        room: String,
        leadMinutes: Int = 10,
        isAfternoon: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        createChannels(context)

        val roomPart = if (room.isNotBlank()) " • Phòng $room" else ""
        val sessionLabel = if (isAfternoon) "Buổi chiều" else "Tiết $periodNumber"
        val title = "Còn $leadMinutes phút vào $sessionLabel ($startTimeText)"
        val body = "Lớp $className — $subjectName$roomPart"
        val notifId = PERIOD_NOTIFICATION_BASE_ID + (if (isAfternoon) 50 else 0) + periodNumber

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TODAY)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PERIOD)
            .setSmallIcon(R.drawable.ic_notification_schedule)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notifId, notification)
        }
    }
}
