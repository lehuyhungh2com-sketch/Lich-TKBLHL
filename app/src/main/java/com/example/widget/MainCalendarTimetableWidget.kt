package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator

class MainCalendarTimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MainCalendarTimetableWidget()
}

class MainCalendarTimetableWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataBuilder.load(context)
        provideContent {
            MainWidgetContent(data)
        }
    }
}

@Composable
private fun MainWidgetContent(data: WidgetRenderData) {
    val context = LocalContext.current
    val palette = data.palette

    val openTodayAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TODAY)
        }
    )

    val openTimetableAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TIMETABLE)
        }
    )

    val rootBgModifier = if (palette.isFullyTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(22.dp).background(palette.backgroundColor)
    }

    val scheduleBgModifier = if (palette.isFullyTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(14.dp).background(palette.scheduleContainer)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(rootBgModifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // TOP HEADER: Clock + Solar/Lunar (Left) | Weather (Right)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openTodayAction),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = data.greeting,
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                val clockLayout = if (palette.useLightText) {
                    R.layout.widget_clock_light
                } else {
                    R.layout.widget_clock_dark
                }
                AndroidRemoteViews(
                    remoteViews = RemoteViews(context.packageName, clockLayout),
                    modifier = GlanceModifier.padding(vertical = 1.dp)
                )

                Text(
                    text = data.solarDateText,
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(palette.pillBackground)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = data.lunarBadgeText,
                        style = TextStyle(
                            color = ColorProvider(palette.pillText),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Right Weather Column
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(data.weatherIconRes),
                        contentDescription = data.weatherDesc,
                        modifier = GlanceModifier.size(28.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = data.weather?.let { "${it.currentTemp}°" } ?: "--°",
                        style = TextStyle(
                            color = ColorProvider(palette.primaryText),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }

                val rainText = data.weather?.let { " • ${it.todayRainProb}% mưa" } ?: ""
                Text(
                    text = "${data.weatherDesc}$rainText",
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                val locationAndHiLo = data.weather?.let {
                    "${it.locationName} • ↑${it.todayHigh}° ↓${it.todayLow}°"
                } ?: "Hậu Lộc, Thanh Hóa"
                Text(
                    text = locationAndHiLo,
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Subtle divider line when transparent so layout breathes cleanly without a murky box
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.dividerColor)
        ) {}

        Spacer(modifier = GlanceModifier.height(6.dp))

        // SPACIOUS TODAY'S TEACHING TIMETABLE (7-day strip removed for generous TKB visibility)
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .then(scheduleBgModifier)
                .clickable(openTimetableAction)
                .padding(
                    horizontal = if (palette.isFullyTransparent) 2.dp else 10.dp,
                    vertical = if (palette.isFullyTransparent) 2.dp else 8.dp
                )
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val title = when {
                    !data.isLoggedIn -> "Thời khóa biểu THCS Lê Hữu Lập"
                    data.isSundayPreview -> "Dự kiến ${data.targetDayLabel}"
                    else -> "Lịch dạy hôm nay • Tuần ${data.weekNumber}"
                }
                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(palette.accentOrange),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
                if (data.isLoggedIn && data.teacherName.isNotBlank()) {
                    Text(
                        text = data.teacherName,
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            val allPeriods = data.morningPeriods + data.afternoonPeriods

            when {
                !data.isLoggedIn -> {
                    Text(
                        text = "Chạm để đăng nhập xem TKB cá nhân",
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
                            "Thứ Hai chưa có tiết dạy"
                        } else {
                            "Hôm nay không có tiết dạy"
                        },
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = 12.sp
                        ),
                        maxLines = 2
                    )
                }

                else -> {
                    allPeriods.take(6).forEach { period ->
                        WidgetPeriodLine(period = period, palette = palette)
                    }
                    if (allPeriods.size > 6) {
                        Text(
                            text = "+ ${allPeriods.size - 6} tiết nữa — chạm để xem đủ",
                            style = TextStyle(
                                color = ColorProvider(palette.secondaryText),
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPeriodLine(
    period: ResolvedPeriod,
    palette: WidgetPalette
) {
    val shortClass = TimetableCalculator.compactClassName(period.className)
    val sessionTag = if (period.isAfternoon) "C" else "S"
    val timeColor = if (period.isAfternoon) palette.rainBlue else palette.accentOrange
    val statusBadge = when (period.liveStatus) {
        PeriodLiveStatus.TEACHING_NOW -> " • Đang dạy"
        PeriodLiveStatus.NEXT_PERIOD -> " • Tiếp"
        else -> ""
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${period.startTime.formatHHmm()} ${sessionTag}T${period.period}",
            style = TextStyle(
                color = ColorProvider(timeColor),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "$shortClass • ${period.subjectShort}$statusBadge",
            style = TextStyle(
                color = ColorProvider(palette.primaryText),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
internal fun WidgetWeekStripRow(
    data: WidgetRenderData,
    onClickAction: androidx.glance.action.Action
) {
    val palette = data.palette
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(onClickAction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        data.weekStrip.forEach { day ->
            val isToday = day.isToday
            val isSunday = day.solar.isSunday
            val dayLabelColor = when {
                isToday -> Color.White
                isSunday -> palette.sundayColor
                else -> palette.secondaryText
            }
            val solarColor = if (isToday) Color.White else palette.primaryText
            val lunarColor = if (isToday) Color(0xFFFFEDD5) else palette.secondaryText

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(12.dp)
                    .then(
                        if (isToday) GlanceModifier.background(palette.accentOrange)
                        else GlanceModifier
                    )
                    .padding(vertical = 5.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.solar.shortDayCode,
                    style = TextStyle(
                        color = ColorProvider(dayLabelColor),
                        fontSize = 10.sp,
                        fontWeight = if (isToday || isSunday) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
                Text(
                    text = day.solar.day.toString(),
                    style = TextStyle(
                        color = ColorProvider(solarColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
                Text(
                    text = day.lunar.formatCellLabel(),
                    style = TextStyle(
                        color = ColorProvider(lunarColor),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
