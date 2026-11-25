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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.LhlTimetableApp
import com.example.MainActivity
import com.example.R
import com.example.data.remote.WeatherCodeMapper
import com.example.domain.model.DailyForecast
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator
import com.example.notification.ReminderAlarmReceiver

class WeatherForecastTimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherForecastTimetableWidget()
}

class RefreshWeatherTimetableAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as? LhlTimetableApp ?: return
        runCatching { app.weatherRepository.refreshWeather() }
        runCatching { ReminderAlarmReceiver.rescheduleFromLocal(context, app) }
        WidgetUpdater.updateAllWidgets(context)
    }
}

class WeatherForecastTimetableWidget : GlanceAppWidget() {

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
            WeatherForecastTimetableContent(data)
        }
    }
}

/**
 * Widget 4: 3-tier rounded card matching the app's Weather card aesthetic:
 * - Tier 1: Location + Solar/Lunar Date + Refresh icon + Current Weather details (High/Low, Rain, Humidity, Wind).
 * - Tier 2: 4-day forecast rounded cards (H.NAY, T6, T7, CN) separated by hairline divider.
 * - Tier 3: 2-column Today's Teaching Timetable with real-time "Đang dạy" / "Tiếp theo" status highlights.
 * Supports both Frosted Rounded mode (1%..100% opacity) and Pure 100% Transparent mode (0% opacity).
 */
@Composable
private fun WeatherForecastTimetableContent(data: WidgetRenderData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = data.palette
    val isTransparent = palette.isFullyTransparent

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

    val rootBgModifier = if (isTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(24.dp).background(palette.backgroundColor)
    }

    val isNarrow = size.width > 0.dp && size.width < 220.dp
    val isCompactHeight = size.height > 0.dp && size.height < 210.dp
    val isTall = size.height >= 300.dp

    val showForecastTier = size.height == 0.dp || size.height >= 165.dp
    val maxPeriodRows = when {
        isCompactHeight -> 2
        isTall -> 4
        else -> 3
    }

    val outerPadH = if (isTransparent) 8.dp else 14.dp
    val outerPadV = if (isTransparent) 6.dp else 12.dp
    val tierGap = if (isCompactHeight) 6.dp else 9.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(rootBgModifier)
            .padding(horizontal = outerPadH, vertical = outerPadV),
        verticalAlignment = Alignment.Top
    ) {
        // ==================== TIER 1: HEADER & CURRENT WEATHER ====================
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(openTodayAction)
        ) {
            // Top Header Row: Pin + Location · Solar (Lunar) + Refresh button
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_location),
                    contentDescription = "Vị trí",
                    modifier = GlanceModifier.size(14.dp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                val locName = data.weather?.locationName ?: "Hậu Lộc"
                val headerDateText = "$locName · ${data.today.shortDayCode}, ${data.today.formatDayMonth()} (${data.lunarShortText})"
                Text(
                    text = headerDateText,
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
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
                        contentDescription = "Làm mới thời tiết và lịch dạy",
                        modifier = GlanceModifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(if (isCompactHeight) 4.dp else 6.dp))

            // Current Weather Details Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Weather Icon + Temperature + Description
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.defaultWeight()
                ) {
                    Image(
                        provider = ImageProvider(data.weatherIconRes),
                        contentDescription = data.weatherDesc,
                        modifier = GlanceModifier.size(if (isCompactHeight) 30.dp else 36.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column {
                        Text(
                            text = data.weather?.let { "${it.currentTemp}°C" } ?: "--°C",
                            style = TextStyle(
                                color = ColorProvider(palette.primaryText),
                                fontSize = if (isCompactHeight) 20.sp else 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = data.weatherDesc,
                            style = TextStyle(
                                color = ColorProvider(palette.secondaryText),
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Right: High/Low, Rain & Humidity, Wind
                if (!isNarrow) {
                    Column(horizontalAlignment = Alignment.End) {
                        val highLow = data.weather?.let { "↑ ${it.todayHigh}°  ↓ ${it.todayLow}°" } ?: "↑ --°  ↓ --°"
                        Text(
                            text = highLow,
                            style = TextStyle(
                                color = ColorProvider(palette.primaryText),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_water_drop),
                                contentDescription = null,
                                modifier = GlanceModifier.size(11.dp)
                            )
                            Spacer(modifier = GlanceModifier.width(3.dp))
                            val rainHum = data.weather?.let {
                                "Mưa ${it.todayRainProb}% · Ẩm ${it.humidity}%"
                            } ?: "Mưa --% · Ẩm --%"
                            Text(
                                text = rainHum,
                                style = TextStyle(
                                    color = ColorProvider(palette.secondaryText),
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                        if (!isCompactHeight) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(
                                        if (palette.useLightText) R.drawable.ic_widget_wind_light
                                        else R.drawable.ic_widget_wind_dark
                                    ),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(11.dp)
                                )
                                Spacer(modifier = GlanceModifier.width(3.dp))
                                Text(
                                    text = data.weather?.let { "Gió ${it.windSpeedKmh} km/h" } ?: "Gió -- km/h",
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

        // ==================== TIER 2: 4-DAY FORECAST ====================
        if (showForecastTier && data.forecasts4Days.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(tierGap))
            HairlineDivider(palette.dividerColor)
            Spacer(modifier = GlanceModifier.height(tierGap))

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(openTodayAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val days = data.forecasts4Days.take(if (isNarrow) 3 else 4)
                days.forEachIndexed { idx, forecast ->
                    if (idx > 0) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                    }
                    ForecastDayTile(
                        index = idx,
                        forecast = forecast,
                        palette = palette,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }

        // ==================== TIER 3: 2-COLUMN LIVE TIMETABLE ====================
        Spacer(modifier = GlanceModifier.height(tierGap))
        HairlineDivider(palette.dividerColor)
        Spacer(modifier = GlanceModifier.height(tierGap))

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(openTimetableAction)
        ) {
            val allPeriods = data.morningPeriods + data.afternoonPeriods
            val teachingNow = allPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.TEACHING_NOW }
            val nextPeriod = allPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.NEXT_PERIOD }

            // Timetable Header Row with Real-Time Active Status
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val leftTitle = when {
                    !data.isLoggedIn -> "Thời khóa biểu"
                    data.isSundayPreview -> "Thứ Hai · Tuần ${data.weekNumber}"
                    else -> "Lịch dạy · Tuần ${data.weekNumber}"
                }
                Text(
                    text = leftTitle,
                    style = TextStyle(
                        color = ColorProvider(palette.accentOrange),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )

                val statusText: String?
                val statusColor = when {
                    teachingNow != null -> {
                        val tag = if (teachingNow.isAfternoon) "C${teachingNow.period}" else "T${teachingNow.period}"
                        val cls = TimetableCalculator.compactClassName(teachingNow.className)
                        statusText = "● Đang dạy: $tag ($cls)"
                        palette.liveNowAccent
                    }
                    nextPeriod != null -> {
                        val tag = if (nextPeriod.isAfternoon) "C${nextPeriod.period}" else "T${nextPeriod.period}"
                        statusText = "● Tiếp: $tag (${nextPeriod.startTime.formatHHmm()})"
                        palette.accentOrange
                    }
                    data.isLoggedIn && !data.isSundayPreview && allPeriods.isNotEmpty() -> {
                        statusText = "Đã xong hôm nay"
                        palette.mutedPastText
                    }
                    else -> {
                        statusText = null
                        palette.secondaryText
                    }
                }

                if (statusText != null) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = ColorProvider(statusColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            when {
                !data.isLoggedIn -> {
                    Text(
                        text = "Chạm để đăng nhập xem thời khóa biểu giáo viên",
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }

                allPeriods.isEmpty() -> {
                    Text(
                        text = if (data.isSundayPreview) {
                            "Thứ Hai tuần mới chưa có tiết dạy"
                        } else {
                            "Hôm nay không có tiết dạy"
                        },
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }

                isNarrow -> {
                    // Single column fallback when resized very narrow
                    val visible = allPeriods.take(maxPeriodRows)
                    visible.forEachIndexed { idx, period ->
                        if (idx > 0) Spacer(modifier = GlanceModifier.height(4.dp))
                        TimetableGridTile(
                            period = period,
                            palette = palette,
                            modifier = GlanceModifier.fillMaxWidth()
                        )
                    }
                }

                else -> {
                    // Clean 2-column grid of rounded tiles (or pure floating cells when transparent)
                    val maxItems = maxPeriodRows * 2
                    val visible = allPeriods.take(maxItems)
                    val rows = visible.chunked(2)
                    rows.forEachIndexed { rowIdx, pair ->
                        if (rowIdx > 0) {
                            Spacer(modifier = GlanceModifier.height(if (isTransparent) 4.dp else 5.dp))
                        }
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimetableGridTile(
                                period = pair[0],
                                palette = palette,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            if (pair.size > 1) {
                                TimetableGridTile(
                                    period = pair[1],
                                    palette = palette,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            } else {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                        }
                    }

                    if (allPeriods.size > maxItems) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = "+${allPeriods.size - maxItems} tiết nữa...",
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
private fun ForecastDayTile(
    index: Int,
    forecast: DailyForecast,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val tileBg = if (palette.isFullyTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(14.dp).background(palette.cardTileBackground)
    }

    Column(
        modifier = modifier
            .then(tileBg)
            .padding(
                horizontal = 3.dp,
                vertical = if (palette.isFullyTransparent) 3.dp else 7.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (index == 0) "H.NAY" else forecast.date.forecastShortLabel,
            style = TextStyle(
                color = ColorProvider(palette.secondaryText),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(WeatherCodeMapper.widgetIconRes(forecast.weatherCode)),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp)
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = "${forecast.precipitationProbability}%",
            style = TextStyle(
                color = ColorProvider(palette.rainBlue),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Text(
            text = "${forecast.tempMin}°/${forecast.tempMax}°",
            style = TextStyle(
                color = ColorProvider(palette.primaryText),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun TimetableGridTile(
    period: ResolvedPeriod,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val isNow = period.liveStatus == PeriodLiveStatus.TEACHING_NOW
    val isNext = period.liveStatus == PeriodLiveStatus.NEXT_PERIOD
    val isPast = period.liveStatus == PeriodLiveStatus.PAST

    val shortClass = TimetableCalculator.compactClassName(period.className)
    val baseTag = if (period.isAfternoon) "C${period.period}" else "T${period.period}"
    val periodLabel = if (isNow) "● $baseTag" else baseTag

    val tagColor = when {
        isNow -> palette.liveNowAccent
        isNext -> palette.accentOrange
        isPast -> palette.mutedPastText
        period.isAfternoon -> palette.rainBlue
        else -> palette.accentOrange
    }

    val textColor = when {
        isNow -> palette.primaryText
        isPast -> palette.mutedPastText
        else -> palette.primaryText
    }

    val tileModifier = when {
        palette.isFullyTransparent -> GlanceModifier.padding(vertical = 2.dp, horizontal = 2.dp)
        isNow -> GlanceModifier
            .cornerRadius(12.dp)
            .background(palette.liveNowBackground)
            .padding(horizontal = 7.dp, vertical = 5.dp)
        else -> GlanceModifier
            .cornerRadius(12.dp)
            .background(palette.cardTileBackground)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    }

    Row(
        modifier = modifier.then(tileModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = periodLabel,
            style = TextStyle(
                color = ColorProvider(tagColor),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = "$shortClass · ${period.subjectName}",
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = 11.sp,
                fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

@Composable
private fun HairlineDivider(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    ) {}
}
