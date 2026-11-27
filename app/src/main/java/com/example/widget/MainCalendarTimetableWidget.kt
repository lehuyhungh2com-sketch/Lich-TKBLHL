package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod

class MainCalendarTimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MainCalendarTimetableWidget()
}

class MainCalendarTimetableWidget : GlanceAppWidget() {

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
            MainWidgetContent(data)
        }
    }
}

@Composable
private fun MainWidgetContent(data: WidgetRenderData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = data.palette
    val radius = data.cornerRadiusDp.dp

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
        GlanceModifier.cornerRadius(radius).background(palette.backgroundColor)
    }

    val isNarrow = size.width > 0.dp && size.width < 220.dp
    val isShort = size.height > 0.dp && size.height < 145.dp
    val isTall = size.height >= 210.dp

    val maxVisiblePeriods = when {
        isNarrow && isShort -> 2
        isNarrow -> 4
        isShort -> 3
        isTall -> 7
        else -> 5
    }
    val rowVerticalPadding = when {
        isShort -> 3.dp
        isTall -> 5.dp
        else -> 4.dp
    }

    val outerPaddingH = if (palette.isFullyTransparent) 8.dp else 14.dp
    val outerPaddingV = if (palette.isFullyTransparent) 6.dp else 12.dp

    if (isNarrow) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .then(rootBgModifier)
                .padding(horizontal = outerPaddingH, vertical = outerPaddingV),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(openTodayAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight().wrapContentHeight()) {
                    ClockBlock(context = context, useLightText = palette.useLightText)
                    Text(
                        text = "${data.today.vietnameseWeekday}, ${data.today.formatDayMonth()}",
                        style = TextStyle(
                            color = ColorProvider(palette.primaryText),
                            fontSize = data.sp(11f, minSp = 9.5f),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Column(
                    modifier = GlanceModifier.wrapContentSize(),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(data.weatherIconRes),
                            contentDescription = data.weatherDesc,
                            modifier = GlanceModifier.size(20.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Text(
                            text = data.weather?.let { "${it.currentTemp}°" } ?: "--°",
                            style = TextStyle(
                                color = ColorProvider(palette.primaryText),
                                fontSize = data.sp(18f),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = data.weather?.locationName ?: "Hậu Lộc",
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = data.sp(9f)
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.dividerColor)
            ) {}
            Spacer(modifier = GlanceModifier.height(6.dp))

            TimetableColumn(
                data = data,
                palette = palette,
                maxVisible = maxVisiblePeriods,
                rowVerticalPadding = rowVerticalPadding,
                onClickAction = openTimetableAction,
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight()
            )
        }
    } else {
        val leftColWidth = if (size.width > 0.dp) {
            (size.width * 0.37f).coerceIn(112.dp, 146.dp)
        } else {
            126.dp
        }

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .then(rootBgModifier)
                .padding(horizontal = outerPaddingH, vertical = outerPaddingV),
            verticalAlignment = Alignment.Top
        ) {
            // LEFT COLUMN: Live Clock + Solar/Lunar + Compact Weather
            Column(
                modifier = GlanceModifier
                    .width(leftColWidth)
                    .wrapContentHeight()
                    .clickable(openTodayAction),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.Top
            ) {
                ClockBlock(context = context, useLightText = palette.useLightText)

                Text(
                    text = "${data.today.vietnameseWeekday}, ${data.today.formatDayMonth()}",
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = data.sp(12f, minSp = 10f),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                if (palette.isFullyTransparent) {
                    Text(
                        text = data.lunarBadgeText,
                        style = TextStyle(
                            color = ColorProvider(palette.pillText),
                            fontSize = data.sp(10f),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(10.dp)
                            .background(palette.pillBackground)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = data.lunarBadgeText,
                            style = TextStyle(
                                color = ColorProvider(palette.pillText),
                                fontSize = data.sp(10f),
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(if (isShort) 6.dp else 10.dp))

                Row(
                    modifier = GlanceModifier.wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(data.weatherIconRes),
                        contentDescription = data.weatherDesc,
                        modifier = GlanceModifier.size(22.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Text(
                        text = data.weather?.let { "${it.currentTemp}°" } ?: "--°",
                        style = TextStyle(
                            color = ColorProvider(palette.primaryText),
                            fontSize = data.sp(21f),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.height(2.dp))

                val weatherLine = data.weather?.let { w ->
                    if (w.todayRainProb > 0) "${data.weatherDesc} · ${w.todayRainProb}%" else data.weatherDesc
                } ?: data.weatherDesc
                Text(
                    text = weatherLine,
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = data.sp(10f)
                    ),
                    maxLines = 1
                )

                if (!isShort) {
                    val locLine = data.weather?.let { w ->
                        "${w.locationName} · ${w.todayLow}°/${w.todayHigh}°"
                    } ?: "Hậu Lộc"
                    Text(
                        text = locLine,
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = data.sp(10f)
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // RIGHT COLUMN: Real-Time Highlighted Timetable Rows + Refresh Button
            TimetableColumn(
                data = data,
                palette = palette,
                maxVisible = maxVisiblePeriods,
                rowVerticalPadding = rowVerticalPadding,
                onClickAction = openTimetableAction,
                modifier = GlanceModifier
                    .defaultWeight()
                    .wrapContentHeight()
            )
        }
    }
}

@Composable
private fun ClockBlock(
    context: Context,
    useLightText: Boolean
) {
    val clockLayout = if (useLightText) {
        R.layout.widget_clock_light
    } else {
        R.layout.widget_clock_dark
    }
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AndroidRemoteViews(
            remoteViews = RemoteViews(context.packageName, clockLayout),
            modifier = GlanceModifier.wrapContentSize()
        )
    }
}

@Composable
private fun TimetableColumn(
    data: WidgetRenderData,
    palette: WidgetPalette,
    maxVisible: Int,
    rowVerticalPadding: Dp,
    onClickAction: Action,
    modifier: GlanceModifier = GlanceModifier
) {
    val liveBadge = data.liveStatusBadgeText()

    Column(
        modifier = modifier.clickable(onClickAction),
        horizontalAlignment = Alignment.Start,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerTitle = when {
                !data.isLoggedIn -> "Thời khóa biểu"
                data.isSundayPreview -> "Thứ Hai · Tuần ${data.weekNumber}"
                else -> "Lịch dạy · Tuần ${data.weekNumber}"
            }
            Text(
                text = headerTitle,
                style = TextStyle(
                    color = ColorProvider(palette.accentOrange),
                    fontSize = data.sp(11f, minSp = 9.5f),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .clickable(actionRunCallback<RefreshWeatherTimetableAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (palette.useLightText) R.drawable.ic_widget_refresh_light
                        else R.drawable.ic_widget_refresh_dark
                    ),
                    contentDescription = "Làm mới",
                    modifier = GlanceModifier.size(13.dp)
                )
            }
        }

        if (liveBadge != null) {
            Text(
                text = liveBadge.first,
                style = TextStyle(
                    color = ColorProvider(liveBadge.second),
                    fontSize = data.sp(9.5f),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        val allPeriods = data.morningPeriods + data.afternoonPeriods

        when {
            !data.isLoggedIn -> {
                Text(
                    text = "Chạm để đăng nhập xem TKB",
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
                        "Thứ Hai chưa có tiết dạy"
                    } else {
                        "Hôm nay không có tiết dạy"
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
                    SamsungStylePeriodRow(
                        data = data,
                        period = period,
                        palette = palette,
                        verticalPadding = rowVerticalPadding
                    )
                    if (index < visible.lastIndex) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(palette.dividerColor)
                        ) {}
                    }
                }
                if (allPeriods.size > maxVisible) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "+${allPeriods.size - maxVisible} tiết nữa...",
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
private fun SamsungStylePeriodRow(
    data: WidgetRenderData,
    period: ResolvedPeriod,
    palette: WidgetPalette,
    verticalPadding: Dp
) {
    val isNow = period.liveStatus == PeriodLiveStatus.TEACHING_NOW
    val isNext = period.liveStatus == PeriodLiveStatus.NEXT_PERIOD
    val isPast = period.liveStatus == PeriodLiveStatus.PAST

    val baseTag = if (period.isAfternoon) "Chiều" else "T${period.period}"
    val periodTag = when {
        isNow -> "●$baseTag"
        isPast -> "✓$baseTag"
        else -> baseTag
    }
    val tagColor = when {
        isNow -> palette.liveNowAccent
        isNext -> palette.nextPeriodAccent
        isPast -> palette.mutedPastText
        period.isAfternoon -> palette.rainBlue
        else -> palette.accentOrange
    }
    val textColor = if (isPast) palette.mutedPastText else palette.primaryText
    val timeColor = when {
        isNow -> palette.liveNowAccent
        isNext -> palette.nextPeriodAccent
        isPast -> palette.mutedPastText
        else -> palette.secondaryText
    }

    val highlightBg = when {
        isNow -> GlanceModifier
            .cornerRadius(8.dp)
            .background(palette.liveNowBackground)
            .padding(horizontal = 4.dp, vertical = verticalPadding)
        isNext -> GlanceModifier
            .cornerRadius(8.dp)
            .background(palette.nextPeriodBackground)
            .padding(horizontal = 4.dp, vertical = verticalPadding)
        else -> GlanceModifier.padding(vertical = verticalPadding)
    }
    val tagColWidth = ((if (data.afternoonPeriods.isNotEmpty()) 44f else 32f) * data.fontScale.coerceAtLeast(1f)).dp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .then(highlightBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = periodTag,
            style = TextStyle(
                color = ColorProvider(tagColor),
                fontSize = data.sp(10.5f, minSp = 9f),
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.width(tagColWidth),
            maxLines = 1
        )
        Text(
            text = data.formatPeriodTitle(period),
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = data.sp(11.5f, minSp = 9.5f),
                fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Medium
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = period.startTime.formatHHmm(),
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

@Composable
internal fun WidgetWeekStripRow(
    data: WidgetRenderData,
    onClickAction: Action
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
                        fontSize = data.sp(10f),
                        fontWeight = if (isToday || isSunday) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
                Text(
                    text = day.solar.day.toString(),
                    style = TextStyle(
                        color = ColorProvider(solarColor),
                        fontSize = data.sp(14f),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
                Text(
                    text = day.lunar.formatCellLabel(),
                    style = TextStyle(
                        color = ColorProvider(lunarColor),
                        fontSize = data.sp(10f),
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
