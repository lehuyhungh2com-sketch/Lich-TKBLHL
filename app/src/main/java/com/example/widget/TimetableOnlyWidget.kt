package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.domain.model.PeriodLiveStatus

class TimetableOnlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableOnlyWidget()
}

class TimetableOnlyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataBuilder.load(context)
        provideContent {
            TimetableOnlyWidgetContent(data)
        }
    }
}

@Composable
private fun TimetableOnlyWidgetContent(data: WidgetRenderData) {
    val context = LocalContext.current
    val palette = data.palette
    val openTimetableAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TIMETABLE)
        }
    )

    val rootBgModifier = if (palette.isFullyTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(20.dp).background(palette.backgroundColor)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(rootBgModifier)
            .clickable(openTimetableAction)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = if (data.isSundayPreview) {
                        "Dự kiến ${data.targetDayLabel}"
                    } else {
                        "TKB ${data.today.vietnameseWeekday} • Tuần ${data.weekNumber}"
                    },
                    style = TextStyle(
                        color = ColorProvider(palette.accentOrange),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${data.solarDateText} (${data.lunarBadgeText})",
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
            if (data.isLoggedIn && data.teacherName.isNotBlank()) {
                Text(
                    text = data.teacherName,
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        val allPeriods = data.morningPeriods + data.afternoonPeriods
        when {
            !data.isLoggedIn -> {
                Text(
                    text = "Chạm để đăng nhập tài khoản giáo viên THCS Lê Hữu Lập.",
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = 12.sp
                    ),
                    maxLines = 2
                )
            }

            allPeriods.isEmpty() -> {
                Text(
                    text = if (data.isSundayPreview) {
                        "Thứ Hai tuần tới chưa có tiết dạy."
                    } else {
                        "Hôm nay không có tiết dạy."
                    },
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = 12.sp
                    ),
                    maxLines = 2
                )
            }

            else -> {
                allPeriods.take(5).forEach { period ->
                    val statusTag = when (period.liveStatus) {
                        PeriodLiveStatus.TEACHING_NOW -> " • Đang dạy"
                        PeriodLiveStatus.NEXT_PERIOD -> " • Tiếp theo"
                        else -> ""
                    }
                    val sessionPrefix = if (period.isAfternoon) "C" else "S"
                    val rowBg = if (period.liveStatus == PeriodLiveStatus.TEACHING_NOW ||
                        period.liveStatus == PeriodLiveStatus.NEXT_PERIOD
                    ) {
                        palette.scheduleContainer
                    } else {
                        palette.backgroundColor.copy(alpha = 0f)
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(8.dp)
                            .background(rowBg)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = period.startTime.formatHHmm(),
                            style = TextStyle(
                                color = ColorProvider(palette.accentOrange),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = "$sessionPrefix-T${period.period} ${com.example.domain.model.TimetableCalculator.compactClassName(period.className)} (${period.subjectShort})$statusTag",
                            style = TextStyle(
                                color = ColorProvider(palette.primaryText),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
