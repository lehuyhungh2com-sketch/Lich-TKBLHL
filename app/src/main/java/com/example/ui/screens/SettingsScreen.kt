package com.example.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppThemeMode
import com.example.data.local.WidgetTextTheme
import com.example.domain.lunar.VnTime
import com.example.domain.model.TimetableCalculator
import com.example.ui.MainUiState
import com.example.ui.components.LiveWidgetPreviewCard
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onSyncNow: () -> Unit,
    onUpdateAppTheme: (AppThemeMode) -> Unit,
    onUpdateDailyReminder: (Boolean, Int, Int) -> Unit,
    onTogglePeriodReminder: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onToggleGpsLocation: (Boolean) -> Unit,
    onUpdateWidgetAppearance: (Float, WidgetTextTheme) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    val teacher = uiState.currentTeacher

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSendTestNotification()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleGpsLocation(granted)
    }

    val todayAllPeriods = uiState.todayMorningPeriods + uiState.todayAfternoonPeriods

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Cài đặt",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 1. TEACHER ACCOUNT & SYNC CARD
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Person,
                    title = "Tài khoản giáo viên"
                ) {
                    if (teacher != null) {
                        Text(
                            text = teacher.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = teacher.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = teacher.roleLabelVietnamese,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (!uiState.homeroomClassId.isNullOrBlank()) {
                                val hcLabel = TimetableCalculator.compactClassName(
                                    uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty()
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "GVCN $hcLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val lastSyncText = uiState.syncMetadata?.lastSyncedAtMillis?.takeIf { it > 0L }?.let { ts ->
                        val fmt = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN"))
                        "Đồng bộ gần nhất: ${fmt.format(Date(ts))}"
                    } ?: "Chưa đồng bộ"

                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSyncNow,
                            enabled = !uiState.isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_sync_now_button")
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang đồng bộ...", maxLines = 1)
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đồng bộ TKB", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("settings_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Đăng xuất",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đăng xuất", maxLines = 1)
                        }
                    }
                }
            }

            // 2. APP THEME (SYSTEM / LIGHT / DARK)
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.DarkMode,
                    title = "Giao diện ứng dụng"
                ) {
                    SegmentedOptionBar(
                        options = AppThemeMode.entries.map {
                            SegmentOption(it, it.label, "app_theme_${it.key}")
                        },
                        selected = prefs.appThemeMode,
                        onSelect = onUpdateAppTheme
                    )
                }
            }

            // 3. DAILY 06:00 AM REMINDER & PERIOD REMINDER
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Alarm,
                    title = "Nhắc lịch dạy"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nhắc buổi sáng (Thứ 2 – Thứ 7)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Báo lịch lúc ${VnTime(prefs.reminderHour, prefs.reminderMinute).formatHHmm()} khi có tiết",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.dailyReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onUpdateDailyReminder(enabled, prefs.reminderHour, prefs.reminderMinute)
                            },
                            modifier = Modifier.testTag("switch_daily_reminder")
                        )
                    }

                    if (prefs.dailyReminderEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Giờ gửi thông báo",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> onUpdateDailyReminder(true, h, m) },
                                        prefs.reminderHour,
                                        prefs.reminderMinute,
                                        true
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("pick_reminder_time_button")
                            ) {
                                Text(
                                    text = VnTime(prefs.reminderHour, prefs.reminderMinute).formatHHmm(),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nhắc trước mỗi tiết 10 phút",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.periodReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onTogglePeriodReminder(enabled)
                            },
                            modifier = Modifier.testTag("switch_period_reminder")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSendTestNotification()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gửi thử thông báo")
                    }
                }
            }

            // 4. WEATHER & GPS SETTINGS
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.LocationOn,
                    title = "Thời tiết"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dùng vị trí GPS",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (prefs.useGpsLocation) {
                                    prefs.lastLocationName
                                } else {
                                    "Mặc định: Hậu Lộc, Thanh Hóa"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.useGpsLocation,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    onToggleGpsLocation(false)
                                }
                            },
                            modifier = Modifier.testTag("switch_gps_weather")
                        )
                    }
                }
            }

            // 5. HOME SCREEN WIDGET CUSTOMIZATION + LIVE PREVIEW (No clutter notes)
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Widgets,
                    title = "Widget màn hình chính"
                ) {
                    LiveWidgetPreviewCard(
                        today = uiState.todayDate,
                        now = uiState.nowTime,
                        weather = uiState.weather,
                        todayPeriods = todayAllPeriods,
                        weekNumber = uiState.currentWeekNumber,
                        opacity = prefs.widgetOpacity,
                        textTheme = prefs.widgetTextTheme
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Độ mờ nền Widget",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (prefs.widgetOpacity <= 0.02f) {
                                "Trong suốt 100%"
                            } else {
                                "${(prefs.widgetOpacity * 100).roundToInt()}%"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = prefs.widgetOpacity,
                        onValueChange = { newOpacity ->
                            onUpdateWidgetAppearance(newOpacity, prefs.widgetTextTheme)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("widget_opacity_slider")
                    )

                    Text(
                        text = "Màu chữ Widget",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SegmentedOptionBar(
                        options = WidgetTextTheme.entries.map {
                            SegmentOption(it, it.label, "widget_theme_${it.key}")
                        },
                        selected = prefs.widgetTextTheme,
                        onSelect = { onUpdateWidgetAppearance(prefs.widgetOpacity, it) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
