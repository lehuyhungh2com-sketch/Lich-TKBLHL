package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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

class TimetableOnlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableOnlyWidget()
}

class TimetableOnlyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialTick = WidgetUpdater.refreshTick.value
        val initialData = WidgetDataBuilder.load(context)
        provideContent {
            val tick by WidgetUpdater.refreshTick.collectAsState()
            var data by remember { mutableStateOf(initialData) }
            LaunchedEffect(tick) {
                if (tick != initialTick) {
                    data = WidgetDataBuilder.load(context)
                }
            }
            TimetableOnlyWidgetContent(data)
        }
    }
}

@Composable
private fun TimetableOnlyWidgetContent(data: WidgetRenderData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = data.palette
    val radius = data.cornerRadiusDp.dp

    val openTimetableAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TIMETABLE)
        }
    )

    val rootBgModifier = if (palette.isFullyTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(radius).background(palette.backgroundColor)
    }

    val isShort = size.height > 0.dp && size.height < 165.dp
    val isTall = size.height >= 250.dp
    val maxVisible = when {
        isShort -> 3
        isTall -> 7
        else -> 5
    }

    val liveBadge = data.liveStatusBadgeText()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(rootBgModifier)
            .clickable(openTimetableAction)
            .padding(
                horizontal = if (palette.isFullyTransparent) 10.dp else 14.dp,
                vertical = if (palette.isFullyTransparent) 8.dp else 12.dp
            )
    ) {
        // Header: Day + Week + Teacher + Quick Refresh
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                val titleText = if (data.isSundayPreview) {
                    "Dự kiến ${data.targetDayLabel}"
                } else {
                    "TKB ${data.today.vietnameseWeekday} · Tuần ${data.weekNumber}"
                }
                Text(
                    text = titleText,
                    style = TextStyle(
                        color = ColorProvider(palette.accentOrange),
                        fontSize = data.sp(13f, minSp = 10f),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${data.solarDateText} (${data.lunarShortText})",
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = data.sp(10f)
                    ),
                    maxLines = 1
                )
            }

            if (data.isLoggedIn && data.teacherName.isNotBlank()) {
                Text(
                    text = data.teacherName,
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = data.sp(11f, minSp = 9.5f),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
            }

            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .cornerRadius(13.dp)
                    .clickable(actionRunCallback<RefreshWeatherTimetableAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (palette.useLightText) R.drawable.ic_widget_refresh_light
                        else R.drawable.ic_widget_refresh_dark
                    ),
                    contentDescription = "Làm mới lịch dạy",
                    modifier = GlanceModifier.size(15.dp)
                )
            }
        }

        // Real-time Status Bar (distinct Emerald for TEACHING_NOW vs Orange for NEXT_PERIOD)
        if (liveBadge != null) {
            Spacer(modifier = GlanceModifier.height(5.dp))
            val isNowActive = data.morningPeriods.any { it.liveStatus == PeriodLiveStatus.TEACHING_NOW } ||
                data.afternoonPeriods.any { it.liveStatus == PeriodLiveStatus.TEACHING_NOW }
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(10.dp)
                    .background(if (isNowActive) palette.liveNowBackground else palette.cardTileBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = liveBadge.first,
                    style = TextStyle(
                        color = ColorProvider(liveBadge.second),
                        fontSize = data.sp(10.5f, minSp = 9f),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(7.dp))

        val allPeriods = data.morningPeriods + data.afternoonPeriods
        when {
            !data.isLoggedIn -> {
                Text(
                    text = "Chạm để đăng nhập tài khoản giáo viên THCS Lê Hữu Lập.",
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = data.sp(12f)
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
                        fontSize = data.sp(12f)
                    ),
                    maxLines = 2
                )
            }

            else -> {
                val visible = allPeriods.take(maxVisible)
                visible.forEachIndexed { index, period ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.height(if (palette.isFullyTransparent) 3.dp else 4.dp))
                    }
                    ModernTimetableRow(
                        data = data,
                        period = period
                    )
                }
                if (allPeriods.size > maxVisible) {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = "+${allPeriods.size - maxVisible} tiết nữa trong ngày...",
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = data.sp(10f)
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernTimetableRow(
    data: WidgetRenderData,
    period: ResolvedPeriod
) {
    val palette = data.palette
    val isNow = period.liveStatus == PeriodLiveStatus.TEACHING_NOW
    val isNext = period.liveStatus == PeriodLiveStatus.NEXT_PERIOD
    val isPast = period.liveStatus == PeriodLiveStatus.PAST

    val baseTag = if (period.isAfternoon) "Chiều" else "T${period.period}"
    val tagText = when {
        isNow -> "● $baseTag"
        isPast -> "✓ $baseTag"
        else -> baseTag
    }

    val rowBgModifier = when {
        isNow -> GlanceModifier
            .cornerRadius(12.dp)
            .background(palette.liveNowBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp)
        isNext -> GlanceModifier
            .cornerRadius(12.dp)
            .background(palette.nextPeriodBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp)
        palette.isFullyTransparent -> GlanceModifier.padding(horizontal = 4.dp, vertical = 3.dp)
        else -> GlanceModifier
            .cornerRadius(12.dp)
            .background(palette.cardTileBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    }

    val tagColor = when {
        isNow -> palette.liveNowAccent
        isNext -> palette.nextPeriodAccent
        isPast -> palette.mutedPastText
        period.isAfternoon -> palette.rainBlue
        else -> palette.accentOrange
    }

    val mainTextColor = if (isPast) palette.mutedPastText else palette.primaryText
    val timeText = when {
        isNow -> "${period.startTime.formatHHmm()}–${period.endTime.formatHHmm()}"
        else -> period.startTime.formatHHmm()
    }
    val timeColor = when {
        isNow -> palette.liveNowAccent
        isNext -> palette.nextPeriodAccent
        isPast -> palette.mutedPastText
        else -> palette.secondaryText
    }
    val tagColWidth = ((if (data.afternoonPeriods.isNotEmpty()) 48f else 38f) * data.fontScale.coerceAtLeast(1f)).dp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .then(rowBgModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tagText,
            style = TextStyle(
                color = ColorProvider(tagColor),
                fontSize = data.sp(11.5f, minSp = 9.5f),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = GlanceModifier.width(tagColWidth)
        )
        Text(
            text = data.formatPeriodTitle(period),
            style = TextStyle(
                color = ColorProvider(mainTextColor),
                fontSize = data.sp(12f, minSp = 10f),
                fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = timeText,
            style = TextStyle(
                color = ColorProvider(timeColor),
                fontSize = data.sp(10.5f, minSp = 9f),
                fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End
            ),
            maxLines = 1
        )
    }
}
