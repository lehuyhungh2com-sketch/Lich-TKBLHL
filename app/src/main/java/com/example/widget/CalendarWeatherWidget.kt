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
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.wrapContentSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.remote.WeatherCodeMapper

class CalendarWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWeatherWidget()
}

class CalendarWeatherWidget : GlanceAppWidget() {

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
            CalendarWeatherWidgetContent(data)
        }
    }
}

@Composable
private fun CalendarWeatherWidgetContent(data: WidgetRenderData) {
    val context = LocalContext.current
    val palette = data.palette
    val openTodayAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TODAY)
        }
    )

    val rootBgModifier = if (palette.isFullyTransparent) {
        GlanceModifier
    } else {
        GlanceModifier.cornerRadius(data.cornerRadiusDp.dp).background(palette.backgroundColor)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(rootBgModifier)
            .clickable(openTodayAction)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = data.greeting,
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = data.sp(12f)
                    ),
                    maxLines = 1
                )
                val clockLayout = if (palette.useLightText) {
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
                Text(
                    text = data.solarDateText,
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = data.sp(13f),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                if (palette.isFullyTransparent) {
                    Text(
                        text = data.lunarBadgeText,
                        style = TextStyle(
                            color = ColorProvider(palette.pillText),
                            fontSize = data.sp(11f),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                } else {
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
                                fontSize = data.sp(11f),
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(data.weatherIconRes),
                        contentDescription = data.weatherDesc,
                        modifier = GlanceModifier.size(30.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = data.weather?.let { "${it.currentTemp}°" } ?: "--°",
                        style = TextStyle(
                            color = ColorProvider(palette.primaryText),
                            fontSize = data.sp(30f, maxSp = 40f),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = data.weather?.let { "${data.weatherDesc} • ${it.todayRainProb}%" }
                        ?: data.weatherDesc,
                    style = TextStyle(
                        color = ColorProvider(palette.primaryText),
                        fontSize = data.sp(11f)
                    ),
                    maxLines = 1
                )
                Text(
                    text = data.weather?.let { "${it.locationName} • ↑${it.todayHigh}° ↓${it.todayLow}°" }
                        ?: "Hậu Lộc",
                    style = TextStyle(
                        color = ColorProvider(palette.secondaryText),
                        fontSize = data.sp(10f)
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        WidgetWeekStripRow(data = data, onClickAction = openTodayAction)

        if (data.forecasts3Days.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                data.forecasts3Days.forEachIndexed { idx, f ->
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (idx == 0) "H.NAY" else f.date.forecastWeekdayLabel,
                            style = TextStyle(
                                color = ColorProvider(palette.secondaryText),
                                fontSize = data.sp(10f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(WeatherCodeMapper.widgetIconRes(f.weatherCode)),
                                contentDescription = null,
                                modifier = GlanceModifier.size(18.dp)
                            )
                            Spacer(modifier = GlanceModifier.width(3.dp))
                            Text(
                                text = "${f.precipitationProbability}%",
                                style = TextStyle(
                                    color = ColorProvider(palette.rainBlue),
                                    fontSize = data.sp(10f),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${f.tempMin}°/${f.tempMax}°",
                            style = TextStyle(
                                color = ColorProvider(palette.primaryText),
                                fontSize = data.sp(11f),
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
